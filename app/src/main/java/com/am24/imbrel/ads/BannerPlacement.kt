package com.am24.imbrel.ads

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Reserve space only on Home, retaining the hidden content without reloading its Android View. */
@Composable
fun BannerPlacement(
    height: Dp,
    isHome: Boolean = true,
    content: @Composable () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().testTag("ad_placement").clipToBounds().layout { measurable, constraints ->
            val content = measurable.measure(constraints)
            layout(content.width, if (isHome) content.height else 0) {
                if (isHome) content.placeRelative(0, 0)
            }
        },
    ) {
        Spacer(Modifier.fillMaxWidth().height(16.dp).testTag("ad_separator"))
        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
        Box(Modifier.fillMaxWidth().height(height).testTag("ad_slot"), contentAlignment = Alignment.Center) {
            content()
        }
    }
}
