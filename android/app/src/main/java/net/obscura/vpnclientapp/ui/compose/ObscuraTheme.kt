package net.obscura.vpnclientapp.ui.compose

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.obscura.vpnclientapp.ui.OsStatus

object ObscuraPalette {
    val OrangeLight = Color(0xFFFF7A49)
    val Orange = Color(0xFFFF6025)
    val OrangeDark = Color(0xFFD9480F)
    val Gradient = Brush.verticalGradient(listOf(OrangeLight, Orange))

    val DarkBg = Color(0xFF1A1B1E)
    val DarkSurface = Color(0xFF25262B)
    val DarkSurfaceElevated = Color(0xFF2C2E33)
    val DarkBorder = Color(0xFF373A40)
    val DarkText = Color(0xFFC1C2C5)
    val DarkTextBright = Color(0xFFF1F3F5)
    val DarkMuted = Color(0xFF909296)

    val LightBg = Color(0xFFF8F9FA)
    val LightSurface = Color(0xFFFFFFFF)
    val LightSurfaceElevated = Color(0xFFF1F3F5)
    val LightBorder = Color(0xFFDEE2E6)
    val LightText = Color(0xFF212529)
    val LightMuted = Color(0xFF868E96)
    val LightOutline = Color(0xFFADB5BD)
    val DarkOutline = Color(0xFF5C5F66)

    val GreenDark = Color(0xFF40C057)
    val GreenLight = Color(0xFF2F9E44)
    val RedDark = Color(0xFFFF6B6B)
    val RedLight = Color(0xFFE03131)
}

@Immutable
data class ExtraColors(
    val success: Color,
    val successContainer: Color,
    val border: Color,
    val muted: Color,
    val isDark: Boolean,
)

val LocalExtraColors = staticCompositionLocalOf {
    ExtraColors(
        success = ObscuraPalette.GreenLight,
        successContainer = Color(0xFFEBFBEE),
        border = ObscuraPalette.LightBorder,
        muted = ObscuraPalette.LightMuted,
        isDark = false,
    )
}

private val DarkScheme =
    darkColorScheme(
        primary = ObscuraPalette.Orange,
        onPrimary = Color.White,
        primaryContainer = Color(0xFF4F2212),
        onPrimaryContainer = Color(0xFFFFD8C9),
        secondary = ObscuraPalette.DarkMuted,
        onSecondary = ObscuraPalette.DarkBg,
        // Material's own purple otherwise: segmented buttons, chips and the navigation pill use these.
        secondaryContainer = Color(0xFF4F2212),
        onSecondaryContainer = Color(0xFFFFD8C9),
        tertiary = ObscuraPalette.Orange,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = ObscuraPalette.DarkBg,
        surfaceContainerLow = ObscuraPalette.DarkSurface,
        surfaceContainer = ObscuraPalette.DarkSurface,
        surfaceContainerHigh = ObscuraPalette.DarkSurfaceElevated,
        surfaceContainerHighest = ObscuraPalette.DarkBorder,
        background = ObscuraPalette.DarkBg,
        onBackground = ObscuraPalette.DarkTextBright,
        surface = ObscuraPalette.DarkSurface,
        onSurface = ObscuraPalette.DarkTextBright,
        surfaceVariant = ObscuraPalette.DarkSurfaceElevated,
        onSurfaceVariant = ObscuraPalette.DarkMuted,
        outline = ObscuraPalette.DarkOutline,
        outlineVariant = ObscuraPalette.DarkBorder,
        error = ObscuraPalette.RedDark,
        onError = Color(0xFF2C0B0E),
        errorContainer = Color(0xFF491518),
        onErrorContainer = Color(0xFFFFC9C9),
    )

private val LightScheme =
    lightColorScheme(
        primary = ObscuraPalette.Orange,
        onPrimary = Color.White,
        primaryContainer = Color(0xFFFFEDE6),
        onPrimaryContainer = Color(0xFF5C1F06),
        secondary = ObscuraPalette.LightMuted,
        onSecondary = Color.White,
        // Material's own purple otherwise: segmented buttons, chips and the navigation pill use these.
        secondaryContainer = Color(0xFFFFE4D9),
        onSecondaryContainer = Color(0xFF5C1F06),
        tertiary = ObscuraPalette.Orange,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = ObscuraPalette.LightSurface,
        surfaceContainerLow = ObscuraPalette.LightBg,
        surfaceContainer = ObscuraPalette.LightSurface,
        surfaceContainerHigh = ObscuraPalette.LightSurfaceElevated,
        // The track of a switch that is off: grey, not the faint lavender that read as "disabled".
        surfaceContainerHighest = Color(0xFFE9ECEF),
        background = ObscuraPalette.LightBg,
        onBackground = ObscuraPalette.LightText,
        surface = ObscuraPalette.LightSurface,
        onSurface = ObscuraPalette.LightText,
        surfaceVariant = ObscuraPalette.LightSurfaceElevated,
        onSurfaceVariant = ObscuraPalette.LightMuted,
        outline = ObscuraPalette.LightOutline,
        outlineVariant = ObscuraPalette.LightBorder,
        error = ObscuraPalette.RedLight,
        onError = Color.White,
        errorContainer = Color(0xFFFFF5F5),
        onErrorContainer = Color(0xFF861010),
    )

private val ObscuraShapes =
    Shapes(
        small = RoundedCornerShape(8.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(16.dp),
        extraLarge = RoundedCornerShape(24.dp),
    )

private val ObscuraTypography =
    Typography(
        headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.SemiBold),
        titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
        titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.Normal),
        bodyMedium = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Normal),
        bodySmall = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Normal),
        labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold),
        labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
        labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
    )

@Composable
fun ObscuraTheme(
    colorSchemePref: OsStatus.ColorScheme,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (colorSchemePref) {
            OsStatus.ColorScheme.Dark -> true
            OsStatus.ColorScheme.Light -> false
            OsStatus.ColorScheme.Auto -> isSystemInDarkTheme()
        }
    val extra =
        if (darkTheme) {
            ExtraColors(
                success = ObscuraPalette.GreenDark,
                successContainer = Color(0xFF14351C),
                border = ObscuraPalette.DarkBorder,
                muted = ObscuraPalette.DarkMuted,
                isDark = true,
            )
        } else {
            ExtraColors(
                success = ObscuraPalette.GreenLight,
                successContainer = Color(0xFFEBFBEE),
                border = ObscuraPalette.LightBorder,
                muted = ObscuraPalette.LightMuted,
                isDark = false,
            )
        }
    CompositionLocalProvider(LocalExtraColors provides extra) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            shapes = ObscuraShapes,
            typography = ObscuraTypography,
            content = content,
        )
    }
}

val FontMono = FontFamily.Monospace
