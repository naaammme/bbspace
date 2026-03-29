package com.naaammme.bbspace.feature.home.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.naaammme.bbspace.core.model.InterestChoose

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun InterestDialog(
    data: InterestChoose,
    onDismiss: () -> Unit,
    onConfirm: (Int, String, String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedGenderId by remember { mutableStateOf<Int?>(null) }
    var selectedAgeId by remember { mutableStateOf<Int?>(null) }
    val selected = remember { mutableStateOf(setOf<String>()) }

    val interestResult = remember(data) {
        data.items.joinToString(",") { item ->
            if (item.subItems.isEmpty()) "${item.id}"
            else item.subItems.joinToString(",") { sub -> "${item.id}.${sub.id}" }
        }
    }

    fun buildPosIds(): String {
        val parts = mutableListOf<String>()
        data.items.forEach { item ->
            if (item.subItems.isEmpty()) {
                if ("${item.id}" in selected.value) parts.add("${item.id}")
            } else {
                item.subItems.forEach { sub ->
                    if ("${item.id}.${sub.id}" in selected.value) parts.add("${item.id}.${sub.id}")
                }
            }
        }
        selectedGenderId?.let { parts.add("$it") }
        selectedAgeId?.let { parts.add("$it") }
        return parts.joinToString(",")
    }

    val canConfirm = selected.value.isNotEmpty() || selectedGenderId != null || selectedAgeId != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Text(data.title, style = MaterialTheme.typography.titleLarge)
            Text(data.subTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))

            if (data.genders.isNotEmpty()) {
                Text(data.genderTitle, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.genders.forEach { g ->
                        FilterChip(
                            selected = selectedGenderId == g.id,
                            onClick = { selectedGenderId = if (selectedGenderId == g.id) null else g.id },
                            label = { Text(g.title) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (data.ages.isNotEmpty()) {
                Text(data.ageTitle, style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.ages.forEach { a ->
                        FilterChip(
                            selected = selectedAgeId == a.id,
                            onClick = { selectedAgeId = if (selectedAgeId == a.id) null else a.id },
                            label = { Text(a.title) }
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            Text("你想看什么", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            data.items.forEach { item ->
                Text(item.name, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(vertical = 4.dp))
                if (item.subItems.isEmpty()) {
                    val key = "${item.id}"
                    FilterChip(
                        selected = key in selected.value,
                        onClick = {
                            selected.value = if (key in selected.value) selected.value - key else selected.value + key
                        },
                        label = { Text(item.name) }
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item.subItems.forEach { sub ->
                            val key = "${item.id}.${sub.id}"
                            FilterChip(
                                selected = key in selected.value,
                                onClick = {
                                    selected.value = if (key in selected.value) selected.value - key else selected.value + key
                                },
                                label = { Text(sub.name) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onConfirm(data.uniqueId, interestResult, buildPosIds()) },
                enabled = canConfirm,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(data.confirmText.ifEmpty { "确认" })
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}
