package com.acquarioonline.ui.components

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import com.acquarioonline.R

/** Campo di testo a riga singola con messaggio d'errore opzionale (risorsa stringa). */
@Composable
fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    @StringRes error: Int? = null,
    enabled: Boolean = true,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    onImeDone: () -> Unit = {},
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = { error?.let { Text(stringResource(it)) } },
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onImeDone() }),
        trailingIcon = trailingIcon
    )
}

/** Pulsante testuale "Mostra / Nascondi" da usare come trailingIcon dei campi password. */
@Composable
fun PasswordVisibilityToggle(visible: Boolean, onToggle: () -> Unit) {
    TextButton(onClick = onToggle) {
        Text(stringResource(if (visible) R.string.hide else R.string.show))
    }
}
