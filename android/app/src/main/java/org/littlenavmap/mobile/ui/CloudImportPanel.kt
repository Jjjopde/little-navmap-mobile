/*
 * Copyright 2026 Alexander Barthel and contributors
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package org.littlenavmap.mobile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
internal fun CloudImportPanel(
    simBriefState: SimBriefUiState,
    onSimBriefUsernameChange: (String) -> Unit,
    onSimBriefImport: () -> Unit,
    navigraphState: NavigraphUiState,
    onNavigraphExportUrlChange: (String) -> Unit,
    onNavigraphTokenChange: (String) -> Unit,
    onNavigraphImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CloudCard(
            title = "SIMBRIEF",
            description = localized(
                "Import the latest released dispatch by username.",
                "按用户名导入最新已发布的放行计划。",
            ),
        ) {
            OutlinedTextField(
                value = simBriefState.username,
                onValueChange = onSimBriefUsernameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(localized("SimBrief username", "SimBrief 用户名")) },
                singleLine = true,
                enabled = !simBriefState.isImporting,
            )
            Button(
                onClick = onSimBriefImport,
                enabled = simBriefState.username.isNotBlank() && !simBriefState.isImporting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(localized(if (simBriefState.isImporting) "Importing" else "Import dispatch", if (simBriefState.isImporting) "正在导入" else "导入放行计划"))
            }
            simBriefState.message?.let { CloudMessage(it) }
        }

        CloudCard(
            title = "NAVIGRAPH CLOUD",
            description = localized(
                "Use an OAuth export URL and a short-lived access token. The token is never saved.",
                "使用 OAuth 导出链接和短期访问令牌。令牌不会保存到设备。",
            ),
        ) {
            OutlinedTextField(
                value = navigraphState.exportUrl,
                onValueChange = onNavigraphExportUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(localized("Flight-plan export URL", "飞行计划导出链接")) },
                singleLine = true,
                enabled = !navigraphState.isImporting,
            )
            OutlinedTextField(
                value = navigraphState.accessToken,
                onValueChange = onNavigraphTokenChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(localized("OAuth access token", "OAuth 访问令牌")) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = !navigraphState.isImporting,
            )
            Button(
                onClick = onNavigraphImport,
                enabled = navigraphState.exportUrl.isNotBlank() && navigraphState.accessToken.isNotBlank() && !navigraphState.isImporting,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(6.dp),
            ) {
                Text(localized(if (navigraphState.isImporting) "Importing" else "Import route", if (navigraphState.isImporting) "正在导入" else "导入航路"))
            }
            navigraphState.message?.let { CloudMessage(it) }
        }
    }
}

@Composable
private fun CloudCard(
    title: String,
    description: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            content()
        }
    }
}

@Composable
private fun CloudMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
