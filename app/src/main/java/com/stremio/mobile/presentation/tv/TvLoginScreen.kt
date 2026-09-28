package com.stremio.mobile.presentation.tv

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.stremio.mobile.presentation.state.AccountUiState

@Composable
internal fun TvLoginScreen(
    account: AccountUiState,
    onLogin: (String, String) -> Unit,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val loginFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) { emailFocus.requestFocus() }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 44.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Sign in to Stremio", style = MaterialTheme.typography.headlineLarge, color = Color.White)
        Spacer(Modifier.height(12.dp))
        Text("Use your Stremio account to continue", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(32.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                .focusRequester(emailFocus)
                .focusProperties {
                    up = emailFocus
                    down = passwordFocus
                },
            label = { Text("Email or username") },
            singleLine = true,
            enabled = !account.isLoading,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth()
                .focusRequester(passwordFocus)
                .focusProperties {
                    up = emailFocus
                    down = loginFocus
                },
            label = { Text("Password") },
            singleLine = true,
            enabled = !account.isLoading,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (!account.isLoading) onLogin(email.trim(), password) }),
            shape = RoundedCornerShape(12.dp),
        )
        Spacer(Modifier.height(18.dp))
        val loginFocused = remember { mutableStateOf(false) }
        TextButton(
            onClick = { if (!account.isLoading) onLogin(email.trim(), password) },
            modifier = Modifier.focusRequester(loginFocus)
                .focusProperties {
                    up = passwordFocus
                    down = loginFocus
                }
                .onFocusChanged { loginFocused.value = it.isFocused },
            border = BorderStroke(2.dp, if (loginFocused.value) MaterialTheme.colorScheme.primary else Color.Transparent),
            shape = RoundedCornerShape(12.dp),
        ) {
            if (account.isLoading) CircularProgressIndicator(modifier = Modifier.height(24.dp).widthIn(min = 24.dp), strokeWidth = 2.dp)
            else Text("Sign in", style = MaterialTheme.typography.titleMedium)
        }
        account.error?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
        }
    }
}
