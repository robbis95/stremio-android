package com.stremio.mobile.presentation.tv

import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.stremio.mobile.data.model.MetaDetails
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvDimens
import com.stremio.mobile.presentation.tv.theme.tvFocusTreatment

@Composable
internal fun TvDetailsScreen(
    details: MetaDetails?,
    onBack: () -> Unit,
) {
    val backRequester = remember { FocusRequester() }
    var backFocused by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { backRequester.requestFocus() }

    Box(Modifier.fillMaxSize().background(TvColors.background).padding(horizontal = 64.dp, vertical = 38.dp)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(bottom = 54.dp),
            horizontalArrangement = Arrangement.spacedBy(42.dp),
        ) {
            Box(
                modifier = Modifier.size(width = 260.dp, height = 390.dp)
                    .border(1.dp, TvColors.divider, RoundedCornerShape(TvDimens.controlRadius)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = details?.item?.background ?: details?.item?.poster,
                    contentDescription = details?.item?.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (details == null || details.isLoading) CircularProgressIndicator()
            }
            Column(modifier = Modifier.weight(1f).padding(top = 8.dp)) {
                Text(
                    text = details?.item?.name ?: "Loading title…",
                    style = MaterialTheme.typography.headlineLarge,
                    color = TvColors.primaryText,
                )
                Spacer(Modifier.height(12.dp))
                val metadata = listOfNotNull(details?.year, details?.runtime, details?.item?.type?.replaceFirstChar { it.uppercase() })
                if (metadata.isNotEmpty()) {
                    Text(metadata.joinToString("  •  "), style = MaterialTheme.typography.titleMedium, color = TvColors.accent)
                    Spacer(Modifier.height(20.dp))
                }
                when {
                    details == null || details.isLoading -> Text("Loading details…", color = TvColors.secondaryText, style = MaterialTheme.typography.bodyLarge)
                    details.error != null -> Text(details.error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyLarge)
                    !details.description.isNullOrBlank() -> Text(details.description, color = TvColors.primaryText, style = MaterialTheme.typography.bodyLarge)
                    else -> Text("No description is available.", color = TvColors.secondaryText, style = MaterialTheme.typography.bodyLarge)
                }
                if (details?.genres?.isNotEmpty() == true) {
                    Spacer(Modifier.height(18.dp))
                    Text(details.genres.joinToString("  •  "), color = TvColors.secondaryText, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        TextButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.BottomStart)
                .focusRequester(backRequester)
                .focusProperties { up = backRequester }
                .onFocusChanged { backFocused = it.isFocused }
                .tvFocusTreatment(backFocused, TvDimens.controlRadius)
                .padding(horizontal = 12.dp, vertical = 5.dp),
        ) {
            Text("Back", style = MaterialTheme.typography.titleMedium)
        }
    }
}
