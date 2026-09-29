package com.stremio.mobile.presentation.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.stremio.mobile.R
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvTheme

@Composable
internal fun TvStartupScreen() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            Image(painterResource(R.drawable.ic_stremio_splash_logo), "Stremio", Modifier.size(76.dp))
            Text("Stremio", style = MaterialTheme.typography.displaySmall, color = TvColors.primaryText, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(34.dp))
        CircularProgressIndicator(color = TvColors.accent, strokeWidth = 3.dp, modifier = Modifier.size(34.dp))
        Spacer(Modifier.height(16.dp))
        Text("Preparing Stremio", style = MaterialTheme.typography.bodyLarge, color = TvColors.secondaryText)
    }
}

@Preview(widthDp = 1920, heightDp = 1080)
@Composable
private fun TvStartupScreenPreview() {
    TvTheme { TvStartupScreen() }
}
