package com.avago.core.permissions

object Permissions {
    // Assets
    const val ASSETS_VIEW = "assets.view"
    const val ASSETS_CREATE = "assets.create"
    const val ASSETS_EDIT = "assets.edit"
    const val ASSETS_DELETE = "assets.delete"

    // Log entries
    const val LOG_VIEW = "log.view"
    const val LOG_CREATE = "log.create"
    const val LOG_EDIT = "log.edit"
    const val LOG_DELETE = "log.delete"
    const val LOG_EDIT_OTHERS = "log.edit_others"

    // Work orders
    const val WO_VIEW = "work_orders.view"
    const val WO_CREATE = "work_orders.create"
    const val WO_EDIT = "work_orders.edit"
    const val WO_DELETE = "work_orders.delete"
    const val WO_ASSIGN = "work_orders.assign"
    const val WO_COMPLETE = "work_orders.complete"
    const val WO_APPROVE = "work_orders.approve"

    // Schedules
    const val SCHEDULES_VIEW = "schedules.view"
    const val SCHEDULES_CREATE = "schedules.create"
    const val SCHEDULES_EDIT = "schedules.edit"
    const val SCHEDULES_DELETE = "schedules.delete"

    // Inventory
    const val INVENTORY_VIEW = "inventory.view"
    const val INVENTORY_CREATE = "inventory.create"
    const val INVENTORY_EDIT = "inventory.edit"
    const val INVENTORY_DELETE = "inventory.delete"
    const val INVENTORY_RECEIVE = "inventory.receive"
    const val INVENTORY_ISSUE = "inventory.issue"
    const val INVENTORY_ADJUST = "inventory.adjust"
    const val INVENTORY_PURCHASE_ORDERS_VIEW = "inventory.purchase_orders.view"
    const val INVENTORY_PURCHASE_ORDERS_CREATE = "inventory.purchase_orders.create"
    const val INVENTORY_PURCHASE_ORDERS_APPROVE = "inventory.purchase_orders.approve"

    // Documents
    const val DOCS_VIEW = "docs.view"
    const val DOCS_CREATE = "docs.create"
    const val DOCS_DELETE = "docs.delete"

    // Reports
    const val REPORTS_VIEW = "reports.view"
    const val REPORTS_EXPORT = "reports.export"
    const val REPORTS_FINANCIAL = "reports.financial"

    // Members / People
    const val MEMBERS_VIEW = "members.view"
    const val MEMBERS_INVITE = "members.invite"
    const val MEMBERS_REMOVE = "members.remove"
    const val MEMBERS_ROLE_CHANGE = "members.role_change"

    // Chat
    const val CHAT_VIEW = "chat.view"
    const val CHAT_SEND = "chat.send"
    const val CHAT_DELETE_OTHERS = "chat.delete_others"

    // Settings / Account
    const val SETTINGS_VIEW = "settings.view"
    const val SETTINGS_EDIT = "settings.edit"
    const val ACCOUNT_BILLING = "account.billing"
    const val ACCOUNT_DELETE = "account.delete"

    // Vendors
    const val VENDORS_VIEW = "vendors.view"
    const val VENDORS_CREATE = "vendors.create"
    const val VENDORS_EDIT = "vendors.edit"
    const val VENDORS_DELETE = "vendors.delete"

    // Cycle counts
    const val CYCLE_COUNTS_VIEW = "cycle_counts.view"
    const val CYCLE_COUNTS_CREATE = "cycle_counts.create"
    const val CYCLE_COUNTS_RECONCILE = "cycle_counts.reconcile"

    // Tech profiles
    const val TECH_PROFILES_VIEW = "tech_profiles.view"
    const val TECH_PROFILES_CREATE = "tech_profiles.create"
    const val TECH_PROFILES_EDIT = "tech_profiles.edit"

    // AI / Scout
    const val AI_SCOUT = "ai.scout"
    const val AI_EXTRACT = "ai.extract"
}
