package com.example.util

import android.text.SpannableString
import android.text.Spanned
import android.text.style.BulletSpan
import android.text.style.LeadingMarginSpan
import android.text.style.StyleSpan
import android.graphics.Typeface
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.graphics.Color

object MarkdownFormatter {

    /**
     * Cleans and formats raw markdown text for display.
     * - Removes excessive symbols
     * - Converts **bold** and *italic*
     * - Cleans up headers (# ## ###)
     * - Normalizes lists and spacing
     */
    fun clean(text: String): String {
        if (text.isBlank()) return text

        var cleaned = text
            // Fix excessive bold markers
            .replace(Regex("\\*\\*\\*(.+?)\\*\\*\\*"), "**$1**")
            // Fix double bullets
            .replace(Regex("(?m)^[*-] [*-] "), "• ")
            // Clean empty list items
            .replace(Regex("(?m)^[*-]\\s*$"), "")
            // Remove trailing spaces before newlines
            .replace(Regex(" +\\n"), "\n")
            // Fix multiple blank lines
            .replace(Regex("\\n{3,}"), "\n\n")
            // Clean headers - keep them clean
            .replace(Regex("(?m)^#{1,6} (.+)$"), "$1")
            // Remove standalone markers
            .replace(Regex("(?m)^---+$"), "")
            // Clean up numbered list formatting
            .replace(Regex("(?m)^(\\d+)\\. (.+)$"), "$1. $2")
            // Fix bold spacing
            .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
            // Fix italic spacing  
            .replace(Regex("\\*(.+?)\\*"), "$1")
            // Remove backtick escapes
            .replace(Regex("`(.+?)`"), "$1")
            // Clean horizontal rules
            .replace(Regex("\\n---\\n"), "\n")
            // Final trim
            .trim()
        
        // Ensure proper spacing after periods
        cleaned = cleaned.replace(Regex("\\.(?=[A-Z])"), ".  ")
        
        // Fix bullet points to use consistent formatting
        cleaned = cleaned.replace(Regex("(?m)^[-*] "), "• ")
        
        return cleaned
    }

    /**
     * Formats text for AnnotatedString/Spannable with proper styling
     */
    fun formatForDisplay(text: String): SpannableString {
        val cleaned = clean(text)
        val spannable = SpannableString(cleaned)
        
        // Apply bold styling
        val boldPattern = Regex("\\*\\*(.+?)\\*\\*")
        boldPattern.findAll(cleaned).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            spannable.setSpan(
                StyleSpan(Typeface.BOLD),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        // Apply italic styling
        val italicPattern = Regex("\\*(.+?)\\*")
        italicPattern.findAll(cleaned).forEach { match ->
            val start = match.range.first
            val end = match.range.last + 1
            spannable.setSpan(
                StyleSpan(Typeface.ITALIC),
                start,
                end,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        
        // Apply bullet points with proper spacing
        var currentIndex = 0
        cleaned.split("\n").forEach { line ->
            if (line.trimStart().startsWith("•") || line.trimStart().startsWith("-")) {
                spannable.setSpan(
                    BulletSpan(24, Color.parseColor("#ECECEC")),
                    currentIndex,
                    currentIndex + line.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                spannable.setSpan(
                    LeadingMarginSpan.Standard(32),
                    currentIndex,
                    currentIndex + line.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            currentIndex += line.length + 1 // +1 for newline
        }
        
        return spannable
    }

    /**
     * Quick clean for Compose Text display
     */
    fun cleanForCompose(text: String): String {
        return clean(text)
    }
}
