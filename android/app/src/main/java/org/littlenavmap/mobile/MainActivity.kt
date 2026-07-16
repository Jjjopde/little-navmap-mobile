/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import org.littlenavmap.mobile.ui.LittleNavmapApp
import org.littlenavmap.mobile.ui.LittleNavmapViewModel
import org.littlenavmap.mobile.ui.theme.LittleNavmapTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            LittleNavmapTheme {
                val viewModel: LittleNavmapViewModel = viewModel()
                val view = LocalView.current
                val isDarkTheme = androidx.compose.foundation.isSystemInDarkTheme()

                LaunchedEffect(viewModel.appLanguage) {
                    AppCompatDelegate.setApplicationLocales(
                        LocaleListCompat.forLanguageTags(viewModel.appLanguage.tag),
                    )
                }

                SideEffect {
                    WindowCompat.getInsetsController(window, view).apply {
                        isAppearanceLightStatusBars = !isDarkTheme
                        isAppearanceLightNavigationBars = !isDarkTheme
                    }
                }

                val shouldKeepScreenOn = viewModel.uiState.keepScreenOn &&
                    viewModel.uiState.phase == org.littlenavmap.mobile.ui.ConnectionPhase.Connected
                DisposableEffect(shouldKeepScreenOn) {
                    if (shouldKeepScreenOn) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                    onDispose { }
                }

                LittleNavmapApp(
                    viewModel = viewModel,
                    onExit = ::finish,
                )
            }
        }
    }
}
