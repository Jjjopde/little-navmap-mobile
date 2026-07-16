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
                text = stringResource(R.string.connect_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Use a Little Navmap desktop server connected to X-Plane 12, MSFS, or Prepar3D.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(28.dp))

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(
                    text = stringResource(R.string.protocol),
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
                    label = { Text(stringResource(R.string.server_address)) },
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
                    label = { Text(stringResource(R.string.port)) },
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
                                text = stringResource(R.string.connection_failed),
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
                        text = stringResource(
                            when {
                                isConnecting -> R.string.connecting
                                state.phase == ConnectionPhase.Error -> R.string.retry
                                else -> R.string.connect
                            },
                        ),
                    )
                }
            }
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
