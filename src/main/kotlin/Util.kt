// Format duration in a more readable way
fun formatDuration(days: Long): String = when {
    days == 0L -> "today"
    days == 1L -> "1 day"
    days < 7 -> "$days days"
    days < 30 -> {
        val weeks = days / 7
        val remainingDays = days % 7
        when {
            remainingDays == 0L -> "$weeks ${if (weeks == 1L) "week" else "weeks"}"
            else -> "$weeks ${if (weeks == 1L) "week" else "weeks"} and " +
                    "$remainingDays ${if (remainingDays == 1L) "day" else "days"}"
        }
    }
    else -> {
        val months = days / 30
        val remainingDays = days % 30
        when {
            remainingDays == 0L -> "$months ${if (months == 1L) "month" else "months"}"
            else -> "$months ${if (months == 1L) "month" else "months"} and " +
                    "$remainingDays ${if (remainingDays == 1L) "day" else "days"}"
        }
    }
}