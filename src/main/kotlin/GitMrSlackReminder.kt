import com.slack.api.Slack
import com.slack.api.bolt.App
import com.slack.api.bolt.socket_mode.SocketModeApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GitMrSlackReminder {
    private val SLACK_BOT_TOKEN = System.getenv("SLACK_BOT_TOKEN")
    private val SLACK_APP_TOKEN = System.getenv("SLACK_APP_TOKEN")
    private val SLACK_CHANNEL_ID = System.getenv("SLACK_CHANNEL_ID")
    private val GIT_URL = System.getenv("GIT_URL")
    private val GIT_ACCESS_TOKEN = System.getenv("GIT_ACCESS_TOKEN")
    private val GIT_REPOSITORY = System.getenv("GIT_REPOSITORY")
    private val TARGET_BRANCH = System.getenv("TARGET_BRANCH")

    private val gitClient = GitClient(
        gitUrl = GIT_URL,
        gitAccessToken = GIT_ACCESS_TOKEN,
        gitRepository = GIT_REPOSITORY,
        targetBranch = TARGET_BRANCH,
        slackToken = SLACK_BOT_TOKEN
    )

    fun sendDailyReminder() {
        val pendingMrs = getPendingMRs()
        val message = formatMessage(pendingMrs)

        val slack = Slack.getInstance()
        try {
            slack.methods(SLACK_BOT_TOKEN).chatPostMessage { req ->
                req
                    .channel(SLACK_CHANNEL_ID)
                    .text(message)
                    .linkNames(true)
            }
        } catch (e: Exception) {
            println("Error sending message: ${e.message}")
        }
    }
    // Start the bot
    fun start() {
        // Start the scheduler in a coroutine
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                val now = LocalDateTime.now()
                if (now.hour == 10 && now.minute == 0 &&
                    now.dayOfWeek.value in 1..5) {
                    sendDailyReminder()
                }
                delay(TimeUnit.DAYS.toMillis(1))
                sendDailyReminder()
            }
        }
    }

    private fun getPendingMRs(): Map<String, MutableList<GitClient.GitMR>> {
        val pendingMRs = mutableMapOf<String, MutableList<GitClient.GitMR>>()

        try {
            val mergeRequests = gitClient.getMRs()

            if (mergeRequests.isNotEmpty()) {
                pendingMRs[GIT_REPOSITORY] = mutableListOf()
                mergeRequests.forEach {
                    pendingMRs[GIT_REPOSITORY]?.add(it)
                }
            }
        } catch (e: Exception) {
            println("Error fetching MRs: ${e.message}")
        }

        return pendingMRs
    }

    private fun formatMessage(pendingPrs: Map<String, List<GitClient.GitMR>>): String {
        if (pendingPrs.isEmpty()) {
            return "No pending merge requests for repository *$GIT_REPOSITORY* on branch *$TARGET_BRANCH*! 🎉"
        }

        return buildString {
            appendLine("🔍 *Daily Pull Request Reminder*")
            appendLine("Repository: *${GIT_REPOSITORY}*")
            appendLine("Target Branch: *$TARGET_BRANCH*\n")

            pendingPrs.forEach { (_, prs) ->
                prs.forEach { pr ->
                    appendLine("• <${pr.url}|${pr.title}>")
                    appendLine("  - Pending for: ${pr.duration}")
                    appendLine("  - Author: ${pr.author}")
                    if (pr.assignee.isNotEmpty()) {
                        appendLine("  - Assignee: ${pr.assignee}")
                    }
                    if (pr.reviewers.isNotEmpty()) {
                        appendLine("  - Reviewers: ${pr.reviewers}")
                    }

                    pr.pipelineStatus.let { status ->
                        val statusEmoji = when (status.toLowerCase()) {
                            "success" -> "✅"
                            "failure" -> "❌"
                            "pending" -> "⏳"
                            "running" -> "🔄"
                            else -> "❔"
                        }
                        appendLine("  - Checks: $statusEmoji $status")
                    }

                    if (pr.hasConflicts) {
                        appendLine("  - ⚠️ *Has conflicts!*")
                    }

                    if (pr.daysOpen >= 7) {
                        appendLine("  ⚠️ *This PR has been pending for over a week!*")
                    }

                    appendLine()
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val requiredVars = listOf(
                "SLACK_BOT_TOKEN",
                "SLACK_APP_TOKEN",
                "SLACK_CHANNEL_ID",
                "GIT_URL",
                "GIT_ACCESS_TOKEN",
                "GIT_REPOSITORY",
                "TARGET_BRANCH",
            )

            val missingVars = requiredVars.filter { System.getenv(it).isNullOrEmpty() }
            if (missingVars.isNotEmpty()) {
                println("Error: Missing required environment variables: ${missingVars.joinToString(", ")}")
                return
            }

            val reminder = GitMrSlackReminder()
            reminder.sendDailyReminder()
        }
    }
}