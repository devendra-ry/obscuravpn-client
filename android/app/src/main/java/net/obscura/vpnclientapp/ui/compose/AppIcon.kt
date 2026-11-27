package net.obscura.vpnclientapp.ui.compose

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import net.obscura.vpnclientapp.R

/** An adaptive icon's layers are 108dp, of which the launcher shows the middle 72dp. */
private const val ADAPTIVE_ICON_ZOOM = 108f / 72f

/**
 * The launcher icon. `R.drawable.ic_launcher` is an `<adaptive-icon>`, which `painterResource` cannot draw (it throws),
 * so this puts its two layers together the way a launcher does.
 */
@Composable
fun AppIcon(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 22))
            .background(colorResource(R.color.ic_launcher_background)),
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.fillMaxSize().scale(ADAPTIVE_ICON_ZOOM),
        )
    }
}
