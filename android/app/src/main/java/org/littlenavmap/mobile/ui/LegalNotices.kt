/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.RawRes
import androidx.annotation.StringRes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.littlenavmap.mobile.R

private const val SOURCE_REPOSITORY_URL =
    "https://github.com/albar965/littlenavmap"

private enum class LegalDocument(
    @StringRes val titleResource: Int,
    @RawRes vararg val resources: Int,
) {
    Gpl(R.string.gnu_gpl_title, R.raw.gpl_3_0),
    ThirdParty(
        R.string.third_party_notices,
        R.raw.third_party_notices,
        R.raw.apache_2_0,
        R.raw.lucide_license,
    ),
    Source(R.string.source_code, R.raw.source_code),
}

@Composable
internal fun LegalNoticesContent(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var document by rememberSaveable { mutableStateOf<LegalDocument?>(null) }
    val selectedDocument = document

    BackHandler {
        if (selectedDocument == null) onBack() else document = null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.9f)
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    if (selectedDocument == null) onBack() else document = null
                },
                modifier = Modifier.size(48.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_arrow_back),
                    contentDescription = stringResource(R.string.back),
                )
            }
            Text(
                text = stringResource(
                    selectedDocument?.titleResource ?: R.string.legal_notices,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
        HorizontalDivider()

        if (selectedDocument == null) {
            LegalOverview(onDocumentSelected = { document = it })
        } else {
            LegalDocumentText(selectedDocument)
        }
    }
}

@Composable
private fun LegalOverview(
    onDocumentSelected: (LegalDocument) -> Unit,
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
        contentPadding = PaddingValues(vertical = 12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.legal_summary),
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        item {
            LegalDocument.entries.forEach { document ->
                ListItem(
                    headlineContent = { Text(stringResource(document.titleResource)) },
                    supportingContent = {
                        Text(
                            stringResource(
                                when (document) {
                                    LegalDocument.Gpl -> R.string.gnu_gpl_summary
                                    LegalDocument.ThirdParty -> R.string.third_party_summary
                                    LegalDocument.Source -> R.string.source_code_summary
                                },
                            ),
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 64.dp)
                        .clickable { onDocumentSelected(document) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}

@Composable
private fun LegalDocumentText(document: LegalDocument) {
    val context = LocalContext.current
    val text = remember(document, context.resources) {
        document.resources.joinToString(
            separator = "\n\n============================================================\n\n",
        ) { resource ->
            context.resources.openRawResource(resource).bufferedReader().use { it.readText() }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(),
    ) {
        if (document == LegalDocument.Source) {
            TextButton(
                onClick = { openExternalUri(context, SOURCE_REPOSITORY_URL) },
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 12.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_external_link),
                    contentDescription = null,
                )
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.open_source_repository))
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(
                start = 24.dp,
                top = 16.dp,
                end = 24.dp,
                bottom = 28.dp,
            ),
        ) {
            item {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
