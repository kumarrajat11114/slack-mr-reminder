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
    /*private val slackToken = System.getenv("SLACK_BOT_TOKEN")
    private val slackChannelId = System.getenv("SLACK_CHANNEL_ID")
    private val targetBranch = System.getenv("TARGET_BRANCH")
    private val gitRepository = System.getenv("GIT_REPOSITORY")
    private val gitUrl = System.getenv("GIT_URL")
    private val gitAccessToken = System.getenv("GIT_ACCESS_TOKEN")*/

    /**
        SLACK_BOT_TOKEN="xoxb-4581332862067-8144908232995-9PVmMQmMCPrbvxN7Jo9xmYsv"
        SLACK_APP_TOKEN="xapp-1-A08462VSKS9-8130473892599-ff036f9c9702ec70df697619dc2a0d2b4c9ac52412ed97668415d24a0d2f9a82"
        SLACK_CHANNEL_ID="C04GWR04W94"
        GIT_URL="https://github.com/"
        GIT_ACCESS_TOKEN="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"
        GIT_PROJECT_PATH="kumarrajat11114/UnlimintTestApp"
        TARGET_BRANCH="main"
     */

    private val slackBotToken = "xoxb-4581332862067-8144908232995-9PVmMQmMCPrbvxN7Jo9xmYsv"
    private val slackAppToken = "xapp-1-A08462VSKS9-8130473892599-ff036f9c9702ec70df697619dc2a0d2b4c9ac52412ed97668415d24a0d2f9a82"
    private val slackChannelId = "C04GWR04W94"
    private val gitUrl = "https://github.com"
    private val gitAccessToken = "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"
    private val gitRepository = "kumarrajat11114/UnlimintTestApp"
    private val targetBranch = "main"
    private val userMappings: Map<String, String> = mapOf(
        "kumarrajat11114" to "U084BFL4ZBN"
    ) // Map of GitHub usernames to Slack user IDs

    private val app = App()
    private val scheduler = Executors.newScheduledThreadPool(1)
    private val gitClient = GitClient(
        gitUrl = gitUrl,
        gitAccessToken = gitAccessToken,
        gitRepository = gitRepository,
        targetBranch = targetBranch,
        userMappings = userMappings
    )

    fun sendDailyReminder() {
        val pendingMrs = getPendingMRs()
        val message = formatMessage(pendingMrs)

        val slack = Slack.getInstance()
        try {
            slack.methods(slackBotToken).chatPostMessage { req ->
                req
                    .channel(slackChannelId)
                    .text(message)
                    .linkNames(true)
            }
        } catch (e: Exception) {
            println("Error sending message: ${e.message}")
        }
    }
    // Start the bot
    fun start() {
        // Create and start socket mode app
        val socketModeApp = SocketModeApp(slackAppToken, app)

        // Start the scheduler in a coroutine
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                val now = LocalDateTime.now()
                if (now.hour == 10 && now.minute == 0 &&
                    now.dayOfWeek.value in 1..5) {
                    sendDailyReminder()
                }
                delay(TimeUnit.MINUTES.toMillis(1))
            }
        }

        // Start socket mode app
        socketModeApp.start()
    }

    private fun getPendingMRs(): Map<String, MutableList<GitClient.GitMR>> {
        val pendingMRs = mutableMapOf<String, MutableList<GitClient.GitMR>>()

        try {
            val mergeRequests = gitClient.getMRs()

            if (mergeRequests.isNotEmpty()) {
                pendingMRs[gitRepository] = mutableListOf()
                mergeRequests.forEach {
                    pendingMRs[gitRepository]?.add(it)
                }
            }
        } catch (e: Exception) {
            println("Error fetching MRs: ${e.message}")
        }

        return pendingMRs
    }

    private fun formatMessage(pendingPrs: Map<String, List<GitClient.GitMR>>): String {
        if (pendingPrs.isEmpty()) {
            return "No pending merge requests for repository *$gitRepository* on branch *$targetBranch*! 🎉"
        }

        return buildString {
            appendLine("🔍 *Daily Pull Request Reminder*")
            appendLine("Repository: *${gitRepository}*")
            appendLine("Target Branch: *$targetBranch*\n")

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
            /*val requiredVars = listOf(
                "SLACK_BOT_TOKEN",
                "SLACK_CHANNEL_ID",
                "GIT_URL",
                "GIT_ACCESS_TOKEN",
                "TARGET_BRANCH"
            )

            val missingVars = requiredVars.filter { System.getenv(it).isNullOrEmpty() }
            if (missingVars.isNotEmpty()) {
                println("Error: Missing required environment variables: ${missingVars.joinToString(", ")}")
                return
            }*/

            val reminder = GitMrSlackReminder()
            reminder.sendDailyReminder()
        }
    }
}