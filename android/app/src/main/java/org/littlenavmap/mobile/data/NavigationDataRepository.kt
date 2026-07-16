/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.data

import android.content.Context
import org.littlenavmap.mobile.model.NavigationDataCodec
import org.littlenavmap.mobile.model.NavigationDataPackage

class NavigationDataRepository(context: Context) {
    private val file = context.applicationContext.filesDir.resolve(FILE_NAME)

    fun load(): NavigationDataPackage? = runCatching {
        if (!file.exists()) return null
        NavigationDataCodec.decode(file.readText())
    }.getOrNull()

    fun replace(content: String): NavigationDataPackage {
        val data = NavigationDataCodec.decode(content)
        file.writeText(NavigationDataCodec.encode(data))
        return data
    }

    private companion object {
        const val FILE_NAME = "navigation-data.json"
    }
}
