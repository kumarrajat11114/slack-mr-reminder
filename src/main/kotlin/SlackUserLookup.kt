import com.slack.api.Slack

class SlackUserLookup(
    private val slackToken: String
) {
    // Cache to store username -> ID mappings
    private val userCache = mutableMapOf<String, String>()

    /**
     * Looks up a Slack user ID by email address
     * Useful when GitHub emails match Slack emails
     */
    fun findUserByEmail(email: String?): String? {
        val slack = Slack.getInstance()
        return try {
            slack.methods(slackToken).usersLookupByEmail { req ->
                req.email(email)
            }.user?.id
        } catch (e: Exception) {
            println("Error looking up user by email: ${e.message}")
            null
        }
    }

    /**
     * Searches for a user by their display name or real name
     * Useful when GitHub usernames match Slack display names
     */
    fun findUserByName(username: String?): String? {
        // Check cache first
        userCache[username]?.let { return it }
        val slack = Slack.getInstance()
        try {
            // Get list of all users in the workspace
            val response = slack.methods(slackToken).usersList { req -> req }

            // Search for matching users
            val user = response.members?.find { member ->
                member.profile?.displayName?.equals(username, ignoreCase = true) == true ||
                        member.profile?.realName?.equals(username, ignoreCase = true) == true
            }

            // Cache and return the result
            return user?.id?.also { userCache[username ?: ""] = it }
        } catch (e: Exception) {
            println("Error looking up user by name: ${e.message}")
            return null
        }
    }

    /**
     * Builds a mapping of GitHub usernames to Slack IDs using a list of GitHub usernames
     */
    fun buildUserMapping(githubUsernames: List<String>): Map<String, String> {
        return githubUsernames.mapNotNull { username ->
            findUserByName(username)?.let { slackId ->
                username to slackId
            }
        }.toMap()
    }
}