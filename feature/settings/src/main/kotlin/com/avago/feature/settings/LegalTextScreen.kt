package com.avago.feature.settings

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView

/**
 * In-app legal text viewer for Privacy Policy and Terms of Service.
 * Mirrors iOS LegalTextViewController.swift — shows legal pages inside
 * the app rather than launching an external browser.
 */
enum class LegalTextType(val url: String) {
    PRIVACY_POLICY("https://avago.app/privacy"),
    TERMS_OF_SERVICE("https://avago.app/terms"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegalTextScreen(
    type: LegalTextType,
    onBack: () -> Unit,
) {
    val title = when (type) {
        LegalTextType.PRIVACY_POLICY -> stringResource(R.string.about_privacy_policy)
        LegalTextType.TERMS_OF_SERVICE -> stringResource(R.string.about_terms_of_service)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.about_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    loadUrl(type.url)
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}
