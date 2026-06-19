package com.example.util

object MarkdownFormatter {

    /**
     * Quick clean that removes the worst markdown noise without heavy processing.
     */
    fun clean(text: String): String {
        if (text.isBlank()) return text

        return text
            // Remove bold markers
            .replace("**", "")
            // Remove italic markers
            .replace(Regex("(?<!:)\\*(?!\\s)"), "")
            // Remove header markers
            .replace(Regex("(?m)^#{1,6}\\s"), "")
            // Remove standalone horizontal rules
            .replace(Regex("(?m)^---+$"), "")
            // Clean inline code
            .replace("`", "")
            // Replace bullet markers with clean bullets
            .replace(Regex("(?m)^[-*] "), "• ")
            // Fix triple newlines
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }
}
