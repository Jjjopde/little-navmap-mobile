/*
 * Copyright 2015-2026 Alexander Barthel (alex@littlenavmap.org)
 * SPDX-License-Identifier: GPL-3.0-or-later
 *
 * Modified for the Little Navmap Android client in 2026.
 */

package org.littlenavmap.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import org.littlenavmap.mobile.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ConnectionScreen(
    state: LittleNavmapUiState,
    onSchemeChanged: (String) -> Unit,
    onAddressChanged: (String) -> Unit,
    onPortChanged: (String) -> Unit,
    onConnect: () -> Unit,
    onBack: () -> Unit,
    appLanguage: AppLanguage,
    onLanguageChange: (AppLanguage) -> Unit,
    simBriefState: SimBriefUiState,
    onSimBriefUsernameChange: (String) -> Unit,
    onSimBriefImport: () -> Unit,
    navigraphState: NavigraphUiState,
    onNavigraphExportUrlChange: (String) -> Unit,
    onNavigraphTokenChange: (String) -> Unit,
    onNavigraphImport: () -> Unit,
) {
    val isConnecting = state.phase == ConnectionPhase.Connecting
    val focusManager = LocalFocusManager.current
    val portFocusRequester = androidx.compose.runtime.remember { FocusRequester() }
    var showLegalNotices by rememberSaveable { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .imePadding()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 520.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(28.dp))
            Image(
                painter = painterResource(R.drawable.navmap_logo),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(112.dp),
                contentScale = ContentScale.Fit,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                text = localized("Connect to Little Navmap", "连接 Little Navmap"),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = localized(
                    "Little Navmap desktop server for X-Plane 12, MSFS, or Prepar3D.",
                    "连接 X-Plane 12、MSFS 或 Prepar3D 的 Little Navmap 桌面服务器。",
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = localized("Protocol", "协议"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    PROTOCOLS.forEachIndexed { index, protocol ->
                        SegmentedButton(
                            selected = state.scheme == protocol,
                            onClick = { onSchemeChanged(protocol) },
                            shape = SegmentedButtonDefaults.itemShape(
                                index = index,
                                count = PROTOCOLS.size,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            enabled = !isConnecting,
                            label = { Text(protocol.uppercase()) },
                        )
                    }
                }

                OutlinedTextField(
                    value = state.address,
                    onValueChange = onAddressChanged,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(localized("Server address", "服务器地址")) },
                    enabled = !isConnecting,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Next,
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { portFocusRequester.requestFocus() },
                    ),
                )
                OutlinedTextField(
                    value = state.port,
                    onValueChange = onPortChanged,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(portFocusRequester),
                    label = { Text(localized("Port", "端口")) },
                    enabled = !isConnecting,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusManager.clearFocus()
                            onConnect()
                        },
                    ),
                )

                AnimatedVisibility(state.errorMessage != null) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = localized("Connection failed", "连接失败"),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = state.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        focusManager.clearFocus()
                        onConnect()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp),
                    enabled = !isConnecting,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    if (isConnecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.size(12.dp))
                    }
                    Text(
                        text = when {
                            isConnecting -> localized("Connecting", "正在连接")
                            state.phase == ConnectionPhase.Error -> localized("Retry", "重试")
                            else -> localized("Connect", "连接")
                        },
                    )
                }
            }
            Spacer(Modifier.height(28.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                shape = RoundedCornerShape(8.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("LITTLE NAVMAP", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text(
                        localized("Local network connection", "局域网连接"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            CloudImportPanel(
                simBriefState = simBriefState,
                onSimBriefUsernameChange = onSimBriefUsernameChange,
                onSimBriefImport = onSimBriefImport,
                navigraphState = navigraphState,
                onNavigraphExportUrlChange = onNavigraphExportUrlChange,
                onNavigraphTokenChange = onNavigraphTokenChange,
                onNavigraphImport = onNavigraphImport,
            )
            Spacer(Modifier.height(28.dp))
        }
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_back),
                contentDescription = stringResource(R.string.back),
            )
        }
        TextButton(
            onClick = { onLanguageChange(appLanguage.next()) },
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 48.dp),
        ) {
            Text(if (appLanguage == AppLanguage.English) "中" else "EN")
        }
        IconButton(
            onClick = { showLegalNotices = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_info),
                contentDescription = stringResource(R.string.legal_notices),
            )
        }
    }

    if (showLegalNotices) {
        ModalBottomSheet(
            onDismissRequest = { showLegalNotices = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        ) {
            LegalNoticesContent(onBack = { showLegalNotices = false })
        }
    }
}

private val PROTOCOLS = listOf("http", "https")
