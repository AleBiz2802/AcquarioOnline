package com.acquarioonline.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.acquarioonline.R
import com.acquarioonline.ui.components.FormTextField

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSectionSheet(
    form: SectionFormState,
    onNameChange: (String) -> Unit,
    onBrokerChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.add_section),
                style = MaterialTheme.typography.headlineSmall
            )
            FormTextField(
                value = form.name,
                onValueChange = onNameChange,
                label = stringResource(R.string.section_name),
                error = form.nameError
            )
            FormTextField(
                value = form.brokerAddress,
                onValueChange = onBrokerChange,
                label = stringResource(R.string.section_broker),
                error = form.brokerError,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                onImeDone = onConfirm
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = onConfirm) { Text(stringResource(R.string.create)) }
            }
        }
    }
}
