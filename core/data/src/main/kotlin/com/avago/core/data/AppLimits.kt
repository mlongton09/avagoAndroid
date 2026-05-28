package com.avago.core.data

/**
 * Centralized field-length and tier-gated list limits.
 * Mirrors iOS AppLimits.swift — values match PostgreSQL column widths.
 *
 * Use [FeatureFlags] for runtime-observable account-tier limits (max_assets, etc).
 * Use these constants for field-length validation at the UI layer.
 */
object AppLimits {

    // ── Field-length limits (PostgreSQL column widths) ────────────────────────

    const val ASSET_NAME           = 255
    const val ASSET_MAKE           = 100
    const val ASSET_MODEL          = 100
    const val ASSET_NOTES          = 5_000
    const val LOG_TITLE            = 255
    const val LOG_NOTES            = 5_000
    const val WO_TITLE             = 255
    const val WO_NOTES             = 5_000
    const val WO_COMMENT           = 2_000
    const val PART_NAME            = 255
    const val PART_NUMBER          = 100
    const val VENDOR_NAME          = 255
    const val DOC_TITLE            = 255
    const val TECH_NAME            = 100
    const val TECH_SKILLS          = 500
    const val TECH_CERTIFICATIONS  = 500
    const val LOCATION_NAME        = 255
    const val LABEL_TEMPLATE_NAME  = 100
    const val CHAT_MESSAGE         = 4_000
    const val SCHEDULE_TITLE       = 255
    const val SCHEDULE_NOTES       = 2_000

    // ── Tier-gated list limits (fallbacks when FeatureFlags not yet loaded) ───

    const val FREE_MAX_ASSETS          =   5
    const val PERSONAL_MAX_ASSETS      =  25
    const val BUSINESS_MAX_ASSETS      =  -1   // unlimited

    const val FREE_MAX_WO_PER_MONTH    =  10
    const val PERSONAL_MAX_WO_PER_MONTH = 50
    const val BUSINESS_MAX_WO_PER_MONTH = -1

    const val FREE_MAX_MEMBERS         =   1
    const val PERSONAL_MAX_MEMBERS     =   3
    const val BUSINESS_MAX_MEMBERS     =  -1
}
