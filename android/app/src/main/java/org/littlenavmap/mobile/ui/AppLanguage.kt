/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf

/** The two supported app languages. Aviation identifiers remain unchanged. */
internal enum class AppLanguage(val tag: String) {
    English("en"),
    Chinese("zh-CN"),
    ;

    fun next(): AppLanguage = if (this == English) Chinese else English

    companion object {
        fun fromTag(tag: String?): AppLanguage = entries.firstOrNull { it.tag == tag } ?: English
    }
}

internal val LocalAppLanguage = compositionLocalOf { AppLanguage.English }

@Composable
internal fun localized(english: String, chinese: String): String =
    if (LocalAppLanguage.current == AppLanguage.Chinese) chinese else english
