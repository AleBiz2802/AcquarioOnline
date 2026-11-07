package com.acquarioonline.ui.section

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.acquarioonline.R
import com.acquarioonline.data.ble.BleStatus
import com.acquarioonline.ui.components.FormTextField
import com.acquarioonline.ui.components.PasswordVisibilityToggle

/** "Fragment" di creazione acquario: nome, Wi-Fi, password e IP del broker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAquariumSheet(
    form: AquariumFormState,
    status: BleStatus,
    onNameChange: (String) -> Unit,
    onSsidChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onBrokerIpChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSubmit: () -> Unit
) {
    val progress = status.progressRes()
    val busy = progress != null
    var showPassword by rememberSaveable { mutableStateOf(false) }

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
                text = stringResource(R.string.new_aquarium),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.aquarium_form_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FormTextField(
                value = form.wifiSsid,
                onValueChange = onSsidChange,
                label = stringResource(R.string.aquarium_wifi_ssid),
                error = form.ssidError,
                enabled = !busy
            )
            FormTextField(
                value = form.wifiPassword,
                onValueChange = onPasswordChange,
                label = stringResource(R.string.aquarium_wifi_password),
                enabled = !busy,
                keyboardType = KeyboardType.Password,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    PasswordVisibilityToggle(visible = showPassword, onToggle = { showPassword = !showPassword })
                }
            )
            FormTextField(
                value = form.brokerIp,
                onValueChange = onBrokerIpChange,
                label = stringResource(R.string.aquarium_broker_ip),
                error = form.brokerError,
                enabled = !busy,
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done,
                onImeDone = onSubmit
            )
            FormTextField(
                value = form.name,
                onValueChange = onNameChange,
                label = stringResource(R.string.aquarium_name),
                error = form.nameError,
                enabled = !busy
            )




            form.formError?.let {
                Text(
                    text = stringResource(it),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            if (progress != null) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(text = stringResource(progress), style = MaterialTheme.typography.bodyMedium)
            }
            if (status is BleStatus.Failed) {
                Text(
                    text = stringResource(status.error.messageRes()),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = onSubmit, enabled = !busy) {
                    Text(stringResource(R.string.send_to_arduino))
                }
            }
        }
    }
}
