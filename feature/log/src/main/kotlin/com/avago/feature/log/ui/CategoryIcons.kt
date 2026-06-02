package com.avago.feature.log.ui

import androidx.compose.ui.graphics.Color

@Deprecated("Use com.avago.core.ui.categoryIconName", ReplaceWith("categoryIconName(categoryId)", "com.avago.core.ui.categoryIconName"))
fun categoryIconName(categoryId: String?): String = com.avago.core.ui.categoryIconName(categoryId)

@Deprecated("Use com.avago.core.ui.categoryBadgeColor", ReplaceWith("categoryBadgeColor(iconName)", "com.avago.core.ui.categoryBadgeColor"))
fun categoryBadgeColor(iconName: String): Color = com.avago.core.ui.categoryBadgeColor(iconName)

@Deprecated("Use com.avago.core.ui.categoryGroup", ReplaceWith("categoryGroup(categoryId)", "com.avago.core.ui.categoryGroup"))
fun categoryGroup(categoryId: String?): String = com.avago.core.ui.categoryGroup(categoryId)
