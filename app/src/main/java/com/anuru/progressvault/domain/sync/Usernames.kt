package com.anuru.progressvault.domain.sync

/** Username formats accepted by LeetCode and GitHub. */
object Usernames {
    val LEETCODE = Regex("^[A-Za-z0-9_.-]{1,40}$")
    val GITHUB = Regex("^[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}$")

    fun isValidLeetCode(value: String) = value.isBlank() || LEETCODE.matches(value.trim())
    fun isValidGitHub(value: String) = value.isBlank() || GITHUB.matches(value.trim())
}
