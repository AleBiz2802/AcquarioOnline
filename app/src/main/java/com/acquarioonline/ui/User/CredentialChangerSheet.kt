package com.acquarioonline.ui.User
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
import com.acquarioonline.ui.home.SectionFormState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CredentialChangerSheet(
    toChange: String,
    value: String,
    confirmValue: String,
    onValueChange: (String) -> Unit,
    onConfirmValueChange: (String) -> Unit,
    valueError: Int? = null,
    confirmError: Int? = null,
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
                text = stringResource(R.string.change_credentials),
                style = MaterialTheme.typography.headlineSmall
            )
            if (toChange == "email") {
                FormTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = stringResource(R.string.new_email),
                    error = valueError
                )
            } else if (toChange == "password") {
                FormTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = stringResource(R.string.new_password),
                    keyboardType = KeyboardType.Password,
                    error = valueError
                )
                FormTextField(
                    value = confirmValue,
                    onValueChange = onConfirmValueChange,
                    label = stringResource(R.string.confirm_password),
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done,
                    onImeDone = onConfirm,
                    error = confirmError
                )
            } else if (toChange == "name") {
                FormTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = stringResource(R.string.new_name),
                    error = valueError
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
            ) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = onConfirm) { Text(stringResource(R.string.save)) }
            }
        }
    }
}