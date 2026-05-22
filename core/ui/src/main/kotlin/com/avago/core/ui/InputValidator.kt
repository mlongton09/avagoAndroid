package com.avago.core.ui

import android.util.Patterns
import java.util.Calendar

/**
 * Centralized form-field validation utilities.
 * All methods return null if the value is valid, or a non-null error message string if invalid.
 */
object InputValidator {

    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+\$")
    private val currencyRegex = Regex("^\\d+(\\.\\d{1,2})?\$")

    /** Returns null if the field is non-blank, or an error message if it is blank. */
    fun validateRequired(value: String, fieldName: String): String? {
        return if (value.isNotBlank()) null else "$fieldName is required"
    }

    /** Returns null if [value] matches a basic email pattern, or an error message. */
    fun validateEmail(value: String): String? {
        if (value.isBlank()) return "Email is required"
        return if (emailRegex.matches(value)) null else "Enter a valid email address"
    }

    /**
     * Returns null if [value] is a valid phone number (7–15 digits after stripping non-digits),
     * or an error message.
     */
    fun validatePhone(value: String): String? {
        if (value.isBlank()) return "Phone number is required"
        val digits = value.filter { it.isDigit() }
        return if (digits.length in 7..15) null else "Enter a valid phone number"
    }

    /**
     * Returns null if [value] parses as a positive number, or an error message.
     */
    fun validatePositiveNumber(value: String, fieldName: String): String? {
        if (value.isBlank()) return "$fieldName is required"
        val num = value.toDoubleOrNull()
            ?: return "$fieldName must be a number"
        return if (num > 0) null else "$fieldName must be greater than zero"
    }

    /** Returns null if [value] does not exceed [max] characters, or an error message. */
    fun validateMaxLength(value: String, max: Int, fieldName: String): String? {
        return if (value.length <= max) null else "$fieldName must be $max characters or fewer"
    }

    /**
     * Returns null if [value] is a valid URL (using Android's built-in Patterns.WEB_URL),
     * or an error message.
     */
    fun validateUrl(value: String): String? {
        if (value.isBlank()) return "URL is required"
        return if (Patterns.WEB_URL.matcher(value).matches()) null else "Enter a valid URL"
    }

    /**
     * Returns null if [value] is a 4-digit year in the range 1900–2100,
     * or an error message.
     */
    fun validateYear(value: String): String? {
        if (value.isBlank()) return "Year is required"
        val year = value.toIntOrNull()
            ?: return "Enter a valid 4-digit year"
        if (value.length != 4) return "Enter a valid 4-digit year"
        return if (year in 1900..2100) null else "Year must be between 1900 and 2100"
    }

    /**
     * Returns null if [value] is a valid currency amount (non-negative number with at most
     * 2 decimal places), or an error message.
     */
    fun validateCurrency(value: String): String? {
        if (value.isBlank()) return "Amount is required"
        return if (currencyRegex.matches(value)) null else "Enter a valid amount (e.g. 10 or 10.99)"
    }
}
