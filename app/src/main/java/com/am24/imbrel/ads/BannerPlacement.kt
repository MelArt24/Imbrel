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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** The reservation stays present on all routes, so navigation targets never move into the ad. */
@Composable
fun BannerPlacement(
    height: Dp,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth().testTag("ad_placement")) {
        Spacer(Modifier.fillMaxWidth().height(16.dp).testTag("ad_separator"))
        HorizontalDivider(color = Color.White.copy(alpha = 0.15f))
        Box(Modifier.fillMaxWidth().height(height).testTag("ad_slot"), contentAlignment = Alignment.Center) {
            content()
        }
    }
}
