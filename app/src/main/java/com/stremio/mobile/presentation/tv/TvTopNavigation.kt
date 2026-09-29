package com.stremio.mobile.presentation.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.stremio.mobile.R
import com.stremio.mobile.presentation.tv.theme.TvColors
import com.stremio.mobile.presentation.tv.theme.TvDimens

@Composable
internal fun TvTopNavigation(
    selected: TvTopLevelRoute,
    onSelect: (TvTopLevelRoute) -> Unit,
    onMoveDown: (TvTopLevelRoute) -> Unit,
    requesters: Map<TvTopLevelRoute, FocusRequester>,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth().height(42.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.ic_stremio_splash_logo),
            contentDescription = "Stremio",
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.width(2.dp))
        tvTopLevelDestinations.forEachIndexed { index, destination ->
            val requester = requesters.getValue(destination)
            val left = requesters.getValue(tvTopLevelDestinations.getOrElse(index - 1) { destination })
            val right = requesters.getValue(tvTopLevelDestinations.getOrElse(index + 1) { destination })
            Button(
                onClick = { onSelect(destination) },
                modifier = Modifier.height(38.dp)
                    .focusRequester(requester)
                    .focusProperties { this.left = left; this.right = right }
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.DirectionDown) {
                            onMoveDown(destination)
                            true
                        } else false
                    },
                shape = ButtonDefaults.shape(shape = RoundedCornerShape(TvDimens.controlRadius)),
                colors = ButtonDefaults.colors(
                    containerColor = if (selected == destination) TvColors.focusSoft else TvColors.background,
                    contentColor = if (selected == destination) TvColors.focus else TvColors.secondaryText,
                ),
            ) {
                Text(
                    text = when (destination) {
                        TvTopLevelRoute.Home -> "Home"
                        TvTopLevelRoute.Search -> "Search"
                    },
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
