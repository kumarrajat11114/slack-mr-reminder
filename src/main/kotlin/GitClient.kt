import org.gitlab4j.api.GitLabApi
import org.gitlab4j.models.Constants
import org.kohsuke.github.GHIssueState
import org.kohsuke.github.GitHubBuilder
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

const val GITHUB = "github"
const val GITLAB = "gitlab"

class GitClient(
    private val gitUrl: String,
    private val gitAccessToken: String,
    private val gitRepository: String,
    private val targetBranch: String,
    private val userMappings: Map<String, String>
) {
    private val currentClientName: String = when {
        gitUrl.contains(GITHUB) -> GITHUB
        gitUrl.contains(GITLAB) -> GITLAB
        else -> throw Exception("Git client not found")
    }

    data class GitMR(
        val title: String,
        val url: String,
        val author: String,
        val assignee: String,
        val createdAt: String,
        val daysOpen: Long,
        val duration: String,
        val reviewers: String,
        val pipelineStatus: String,
        val hasConflicts: Boolean,
    )

    fun getMRs(): List<GitMR> {
        return when (currentClientName) {
            GITHUB -> {
                val currentClient = GitHubBuilder().withOAuthToken(gitAccessToken).build()
                val repo = currentClient
                    .getRepository(gitRepository)
                repo
                    .queryPullRequests()
                    .state(GHIssueState.OPEN)
                    .list()
                    .toList()
                    .map {
                        val createdDate = it.createdAt.toInstant()
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime()
                        val daysOpen = ChronoUnit.DAYS.between(
                            createdDate,
                            LocalDateTime.now()
                        )
                        GitMR(
                            title = it.title,
                            url = it.htmlUrl.toString(),
                            author = getSlackUserTag(it.user.login),
                            assignee = getSlackUserTag(it.assignee.login),
                            createdAt = createdDate.format(
                                DateTimeFormatter.ISO_DATE
                            ),
                            daysOpen = daysOpen,
                            duration = formatDuration(daysOpen),
                            reviewers = formatUserTags(it.listReviews().map { it.user.login }.distinct()),
                            pipelineStatus = repo.getCommit(it.head.sha).checkRuns.firstOrNull()?.let { checkRun ->
                                checkRun.conclusion ?: checkRun.status
                            }?.name ?: "",
                            hasConflicts = it.mergeable?.not() ?: false
                        )
                    }
            }

            GITLAB -> {
                val currentClient = GitLabApi(gitUrl, gitAccessToken)
                currentClient.mergeRequestApi.getMergeRequests(gitRepository, Constants.MergeRequestState.OPENED).map {
                    val createdDate = it.createdAt.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                    val daysOpen = ChronoUnit.DAYS.between(
                        createdDate,
                        LocalDateTime.now()
                    )
                    GitMR(
                        title = it.title,
                        url = it.webUrl,
                        author = getSlackUserTag(it.assignee.username),
                        assignee = getSlackUserTag(it.assignee.username),
                        createdAt = createdDate.format(
                            DateTimeFormatter.ISO_DATE
                        ),
                        daysOpen = daysOpen,
                        duration = formatDuration(daysOpen),
                        reviewers = formatUserTags(it.reviewers.map { reviewer -> reviewer.username }.distinct()),
                        pipelineStatus = it.pipeline.status.name,
                        hasConflicts = it.hasConflicts
                    )
                }
            }

            else -> {
                throw Exception("Git client not found")
            }
        }
    }

    private fun getSlackUserTag(githubUsername: String): String {
        // Look up the Slack user ID from the mapping
        return userMappings[githubUsername]?.let { "<@$it>" } ?: githubUsername
    }

    // Format a list of GitHub usernames into Slack tags
    private fun formatUserTags(usernames: List<String>): String {
        return usernames.joinToString(", ") { getSlackUserTag(it) }
    }
}