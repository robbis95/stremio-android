package com.stremio.mobile.presentation.tv

import android.graphics.Bitmap
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.stremio.mobile.R
import com.stremio.mobile.presentation.state.AccountUiState
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvDimens
import com.stremio.mobile.presentation.tv.theme.TvTheme
import com.stremio.mobile.presentation.tv.theme.tvFocusTreatment
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
        modifier = Modifier.fillMaxSize().padding(horizontal = TvDimens.safeHorizontal, vertical = TvDimens.safeVertical),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(76.dp),
    ) {
        Column(Modifier.width(490.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Image(painterResource(R.drawable.ic_stremio_splash_logo), "Stremio", Modifier.size(48.dp))
                Text("Stremio", color = TvColors.accent, style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(38.dp))
            Text("Sign in to Stremio", style = MaterialTheme.typography.displaySmall, color = TvColors.primaryText)
            Spacer(Modifier.height(16.dp))
            Text("Link your account to continue watching across your devices.", style = MaterialTheme.typography.bodyLarge, color = TvColors.secondaryText)
        }

        Surface(
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(TvDimens.cardRadius),
            color = TvColors.surface,
            shadowElevation = 8.dp,
            border = BorderStroke(1.dp, TvColors.divider),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(34.dp),
                horizontalArrangement = Arrangement.spacedBy(34.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val qrBitmap = remember(linkState.link?.link) { linkState.link?.link?.let(::createQrBitmap) }
                Box(
                    modifier = Modifier.size(286.dp).background(Color.White, RoundedCornerShape(14.dp)).padding(12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qrBitmap != null) {
                        Image(bitmap = qrBitmap.asImageBitmap(), contentDescription = "QR code to link your Stremio account", modifier = Modifier.fillMaxSize())
                    } else if (linkState.isLoading) {
                        CircularProgressIndicator(color = TvColors.accent)
                    } else {
                        Text("QR unavailable", color = TvColors.secondaryText, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text("Scan with your phone", style = MaterialTheme.typography.headlineSmall, color = TvColors.primaryText)
                    Spacer(Modifier.height(8.dp))
                    Text("Or visit link.stremio.com and enter", style = MaterialTheme.typography.bodyMedium, color = TvColors.secondaryText)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        linkState.link?.code ?: "····",
                        style = MaterialTheme.typography.displayMedium,
                        color = TvColors.primaryText,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 8.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    val status = when {
                        linkState.isLoading -> "Creating a secure link…"
                        linkState.isConnecting -> "Link approved. Signing in…"
                        linkState.isChecking -> "Waiting for approval on your phone…"
                        linkState.error != null -> linkState.error
                        linkState.link != null -> "Use the code above to link your account."
                        else -> "Connect to the internet to create a link."
                    }
                    Text(status, style = MaterialTheme.typography.bodyMedium, color = if (linkState.error != null) TvColors.error else TvColors.secondaryText)
                    Spacer(Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(
                            onClick = onRequestNewLink,
                            enabled = !linkState.isLoading && !linkState.isConnecting,
                            modifier = Modifier.height(TvDimens.buttonHeight),
                            shape = RoundedCornerShape(TvDimens.controlRadius),
                        ) {
                            Text(if (linkState.link == null) "Create link" else "Request a new link")
                        }
                        var emailToggleFocused by remember { mutableStateOf(false) }
                        TextButton(
                            onClick = { showEmailLogin = !showEmailLogin },
                            modifier = Modifier.onFocusChanged { emailToggleFocused = it.isFocused }
                                .tvFocusTreatment(emailToggleFocused, TvDimens.controlRadius),
                        ) {
                            Text(if (showEmailLogin) "Hide email sign-in" else "Sign in with email")
                        }
                    }
                    if (showEmailLogin) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = email, onValueChange = { email = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(emailFocus).focusProperties {
                                up = emailFocus; down = passwordFocus
                            },
                            label = { Text("Email or username") }, singleLine = true, enabled = !account.isLoading,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { passwordFocus.requestFocus() }),
                            shape = RoundedCornerShape(TvDimens.controlRadius),
                        )
                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth().focusRequester(passwordFocus).focusProperties {
                                up = emailFocus; down = loginFocus
                            },
                            label = { Text("Password") }, singleLine = true, enabled = !account.isLoading,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (!account.isLoading) onLogin(email.trim(), password) }),
                            shape = RoundedCornerShape(TvDimens.controlRadius),
                        )
                        Spacer(Modifier.height(8.dp))
                        var loginFocused by remember { mutableStateOf(false) }
                        TextButton(
                            onClick = { if (!account.isLoading) onLogin(email.trim(), password) },
                            modifier = Modifier.focusRequester(loginFocus).focusProperties { up = passwordFocus; down = loginFocus }
                                .onFocusChanged { loginFocused = it.isFocused }
                                .tvFocusTreatment(loginFocused, TvDimens.controlRadius),
                        ) {
                            if (account.isLoading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                            else Text("Sign in", style = MaterialTheme.typography.titleMedium)
                        }
                        account.error?.let { Text(it, color = TvColors.error, style = MaterialTheme.typography.bodyMedium) }
                    }
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

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun TvLoginScreenPreview() {
    TvTheme {
        TvLoginScreen(AccountUiState(), TvAccountLinkUiState(isLoading = true), {}, {})
    }
}
