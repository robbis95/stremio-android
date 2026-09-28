package com.stremio.mobile.presentation.tv

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.stremio.mobile.presentation.state.AccountUiState
import com.stremio.mobile.presentation.viewmodel.TvAccountLinkUiState

@Composable
internal fun TvLoginScreen(
    account: AccountUiState,
    linkState: TvAccountLinkUiState,
    onLogin: (String, String) -> Unit,
    onRequestNewLink: () -> Unit,
) {
    var showEmailLogin by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val emailFocus = remember { FocusRequester() }
    val passwordFocus = remember { FocusRequester() }
    val loginFocus = remember { FocusRequester() }

    Row(
        modifier = Modifier.fillMaxSize().padding(horizontal = 72.dp, vertical = 42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Column(
            modifier = Modifier.width(350.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val qrBitmap = remember(linkState.link?.link) { linkState.link?.link?.let(::createQrBitmap) }
            if (qrBitmap != null) {
                Image(
                    bitmap = qrBitmap.asImageBitmap(),
                    contentDescription = "QR code to link your Stremio account",
                    modifier = Modifier.size(320.dp).background(Color.White, RoundedCornerShape(12.dp)).padding(12.dp),
                )
            } else {
                Box(
                    modifier = Modifier.size(320.dp).background(Color(0xFF20242C), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (linkState.isLoading) CircularProgressIndicator()
                    else Text("QR unavailable", color = Color.White)
                }
            }
        }
        Spacer(Modifier.width(56.dp))
        Column(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Link Account", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text(
                "Scan the QR code or go to link.stremio.com and enter:",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                linkState.link?.code ?: "····",
                style = MaterialTheme.typography.displayMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                letterSpacing = MaterialTheme.typography.displayMedium.letterSpacing * 1.35f,
            )
            Spacer(Modifier.height(12.dp))
            val status = when {
                linkState.isLoading -> "Creating a secure link…"
                linkState.isConnecting -> "Link authorized. Signing in…"
                linkState.isChecking -> "Waiting for approval on your phone…"
                linkState.error != null -> linkState.error
                linkState.link != null -> "Use the code above to link your account."
                else -> "Connect to the internet to create a link."
            }
            Text(status, style = MaterialTheme.typography.bodyLarge, color = if (linkState.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(20.dp))
            Button(onClick = onRequestNewLink, enabled = !linkState.isLoading && !linkState.isConnecting) {
                Text(if (linkState.link == null) "Create link" else "Request a new link")
            }
            Spacer(Modifier.height(6.dp))
            TextButton(onClick = { showEmailLogin = !showEmailLogin }) {
                Text(if (showEmailLogin) "Back to account linking" else "Sign in with email/password")
            }

            if (showEmailLogin) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(emailFocus).focusProperties {
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
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus).focusProperties {
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
                Spacer(Modifier.height(12.dp))
                val focused = remember { mutableStateOf(false) }
                TextButton(
                    onClick = { if (!account.isLoading) onLogin(email.trim(), password) },
                    modifier = Modifier.focusRequester(loginFocus).focusProperties { up = passwordFocus; down = loginFocus }
                        .onFocusChanged { focused.value = it.isFocused },
                    border = BorderStroke(2.dp, if (focused.value) MaterialTheme.colorScheme.primary else Color.Transparent),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (account.isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    else Text("Sign in", style = MaterialTheme.typography.titleMedium)
                }
                account.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

private fun createQrBitmap(value: String): Bitmap? = runCatching {
    val hints = mapOf(EncodeHintType.MARGIN to 4)
    val matrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, 640, 640, hints)
    Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.RGB_565).also { bitmap ->
        for (x in 0 until matrix.width) for (y in 0 until matrix.height) {
            bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        }
    }
}.getOrNull()
