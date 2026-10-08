package com.componentvault.android.ui.screen

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.componentvault.android.R

internal fun stepQuantityText(
    text: String,
    change: Int,
    minimum: Int = 1,
    maximum: Int = Int.MAX_VALUE,
    skipZero: Boolean = false,
): String {
    require(change == -1 || change == 1)
    require(minimum <= maximum)
    val current = text.toIntOrNull()
    if (current == null) {
        return if (skipZero) (if (change < 0) -1 else 1).toString() else minimum.toString()
    }
    var next = (current.toLong() + change).coerceIn(minimum.toLong(), maximum.toLong()).toInt()
    if (skipZero && next == 0) next = change
    return next.toString()
}

@Composable
internal fun QuantityInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    minimum: Int = 1,
    maximum: Int = Int.MAX_VALUE,
    skipZero: Boolean = false,
    enabled: Boolean = true,
    fieldTestTag: String? = null,
) {
    val current = value.toIntOrNull()
    val hasRange = minimum <= maximum
    val decreaseEnabled = enabled && hasRange &&
        (current == null || stepQuantityText(value, -1, minimum, maximum, skipZero) != value)
    val increaseEnabled = enabled && hasRange &&
        (current == null || stepQuantityText(value, 1, minimum, maximum, skipZero) != value)
    val decreaseLabel = stringResource(R.string.quantity_step_decrease)
    val increaseLabel = stringResource(R.string.quantity_step_increase)

    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onValueChange(stepQuantityText(value, -1, minimum, maximum, skipZero)) },
            enabled = decreaseEnabled,
            modifier = Modifier.size(48.dp).semantics { contentDescription = decreaseLabel },
        ) {
            Text("−", style = MaterialTheme.typography.titleLarge)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f).then(
                if (fieldTestTag == null) Modifier else Modifier.testTag(fieldTestTag)
            ),
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = enabled,
        )
        IconButton(
            onClick = { onValueChange(stepQuantityText(value, 1, minimum, maximum, skipZero)) },
            enabled = increaseEnabled,
            modifier = Modifier.size(48.dp).semantics { contentDescription = increaseLabel },
        ) {
            Text("+", style = MaterialTheme.typography.titleLarge)
        }
    }
}
