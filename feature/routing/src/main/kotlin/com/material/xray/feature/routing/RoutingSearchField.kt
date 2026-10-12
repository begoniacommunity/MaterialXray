package com.material.xray.feature.routing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.material.xray.core.ui.components.SearchClearButton

@Composable
internal fun RoutingSearchField(state: TextFieldState, hint: String) {
    val colors = MaterialTheme.colorScheme
    val textStyle = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, color = colors.onSurface)
    val focusManager = LocalFocusManager.current
    val touching = LocalInputModeManager.current.inputMode == InputMode.Touch
    var keyboardRequested by remember { mutableStateOf(false) }
    BasicTextField(
        state = state,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .onFocusChanged { if (!it.isFocused) keyboardRequested = false }
            .onPreviewKeyEvent { event ->
                val opensKeyboard = !touching && !keyboardRequested && event.type == KeyEventType.KeyDown && event.key == Key.DirectionCenter
                if (opensKeyboard) keyboardRequested = true
                opensKeyboard
            },
        textStyle = textStyle,
        lineLimits = TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, showKeyboardOnFocus = touching || keyboardRequested),
        onKeyboardAction = { focusManager.clearFocus() },
        cursorBrush = SolidColor(if (WindowInsets.ime.getBottom(LocalDensity.current) > 0) colors.primary else Color.Transparent),
        decorator = { innerTextField ->
            // Settings' search_bar.xml uses a 72 dp row and SearchBarStyle_v2's 40 dp corners.
            Surface(
                shape = RoundedCornerShape(40.dp),
                color = Color.Transparent,
                border = BorderStroke(1.dp, colors.outlineVariant),
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 72.dp).padding(start = 24.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(24.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (state.text.isEmpty()) Text(hint, style = textStyle, maxLines = 1)
                        innerTextField()
                    }
                    SearchClearButton(state)
                }
            }
        },
    )
}
