package com.rmltd.workhourstracker.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/**
 * Color themes for Work Hours Tracker.
 *
 * Palette hexes are design-locked from theme-mockups/palettes.json (approved as-is).
 * Keys: purple | blue | red | green | orange. Default: purple.
 * Roles listed in that JSON are applied exactly; remaining Material3 on-color and variant roles
 * are neutral complements so ColorScheme is complete (not alternate brand colors).
 */


/** Light-mode primary swatch for Settings theme chips (locked palettes.json primaries). */
fun AppTheme.previewPrimary(): Color = when (this) {
    AppTheme.PURPLE -> Color(0xFF5B3F9E)
    AppTheme.BLUE -> Color(0xFF1565C0)
    AppTheme.RED -> Color(0xFFC62828)
    AppTheme.GREEN -> Color(0xFF2E7D4F)
    AppTheme.ORANGE -> Color(0xFFE65100)
}

// --- Purple (default; polished) ---
private val PurpleLight = lightColorScheme(
    primary = Color(0xFF5B3F9E),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFE9DDFF),
    onPrimaryContainer = Color(0xFF21005E),
    secondary = Color(0xFF655A7A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9DEF8),
    onSecondaryContainer = Color(0xFF211534),
    tertiary = Color(0xFF8E4E8C),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD6F5),
    onTertiaryContainer = Color(0xFF380038),
    background = Color(0xFFF7F2FA),
    onBackground = Color(0xFF1D1A22),
    surface = Color(0xFFF7F2FA),
    onSurface = Color(0xFF1D1A22),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454E),
    outline = Color(0xFF7A757F),
    outlineVariant = Color(0xFFCBC4CF)
)

private val PurpleDark = darkColorScheme(
    primary = Color(0xFFCFBDFF),
    onPrimary = Color(0xFF3A1D6E),
    primaryContainer = Color(0xFF4A2F85),
    onPrimaryContainer = Color(0xFFE9DDFF),
    secondary = Color(0xFFCDC2DB),
    onSecondary = Color(0xFF362B49),
    secondaryContainer = Color(0xFF4D4161),
    onSecondaryContainer = Color(0xFFE9DEF8),
    tertiary = Color(0xFFFFABE8),
    onTertiary = Color(0xFF561256),
    tertiaryContainer = Color(0xFF713573),
    onTertiaryContainer = Color(0xFFFFD6F5),
    background = Color(0xFF141218),
    onBackground = Color(0xFFE7E0E8),
    surface = Color(0xFF141218),
    onSurface = Color(0xFFE7E0E8),
    surfaceVariant = Color(0xFF49454E),
    onSurfaceVariant = Color(0xFFCBC4CF),
    outline = Color(0xFF948F99),
    outlineVariant = Color(0xFF49454E)
)

// --- Blue (locked palettes.json) ---
private val BlueLight = lightColorScheme(
    primary = Color(0xFF1565C0),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD1E4FF),
    onPrimaryContainer = Color(0xFF001D36),
    secondary = Color(0xFF545F70),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD8E3F8),
    onSecondaryContainer = Color(0xFF101C2B),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    background = Color(0xFFF8F9FF),
    onBackground = Color(0xFF1A1C1E),
    surface = Color(0xFFF8F9FF),
    onSurface = Color(0xFF1A1C1E),
    surfaceVariant = Color(0xFFDFE2EB),
    onSurfaceVariant = Color(0xFF43474E),
    outline = Color(0xFF73777F),
    outlineVariant = Color(0xFFC3C7CF)
)

private val BlueDark = darkColorScheme(
    primary = Color(0xFFA0CAFD),
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF0D47A1),
    onPrimaryContainer = Color(0xFFD1E4FF),
    secondary = Color(0xFFBCC7DB),
    onSecondary = Color(0xFF263141),
    secondaryContainer = Color(0xFF3C4758),
    onSecondaryContainer = Color(0xFFD8E3F8),
    tertiary = Color(0xFFD7BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF534060),
    onTertiaryContainer = Color(0xFFF2DAFF),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE2E2E6),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE2E2E6),
    surfaceVariant = Color(0xFF43474E),
    onSurfaceVariant = Color(0xFFC3C7CF),
    outline = Color(0xFF8D9199),
    outlineVariant = Color(0xFF43474E)
)

// --- Red (locked palettes.json) ---
private val RedLight = lightColorScheme(
    primary = Color(0xFFC62828),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410002),
    secondary = Color(0xFF775652),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDAD5),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF7A5730),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB3),
    onTertiaryContainer = Color(0xFF2B1700),
    background = Color(0xFFFFFBFF),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFF5DDDA),
    onSurfaceVariant = Color(0xFF534341),
    outline = Color(0xFF857370),
    outlineVariant = Color(0xFFD8C2BE)
)

private val RedDark = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    onPrimary = Color(0xFF690005),
    primaryContainer = Color(0xFF93000A),
    onPrimaryContainer = Color(0xFFFFDAD6),
    secondary = Color(0xFFE7BDB7),
    onSecondary = Color(0xFF442925),
    secondaryContainer = Color(0xFF5D3F3B),
    onSecondaryContainer = Color(0xFFFFDAD5),
    tertiary = Color(0xFFE8C08E),
    onTertiary = Color(0xFF452B05),
    tertiaryContainer = Color(0xFF5C4118),
    onTertiaryContainer = Color(0xFFFFDDB3),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF534341),
    onSurfaceVariant = Color(0xFFD8C2BE),
    outline = Color(0xFFA08C89),
    outlineVariant = Color(0xFF534341)
)

// --- Green (locked palettes.json) ---
private val GreenLight = lightColorScheme(
    primary = Color(0xFF2E7D4F),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFC8E6D0),
    onPrimaryContainer = Color(0xFF002110),
    secondary = Color(0xFF5B7C6A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD4E7DB),
    onSecondaryContainer = Color(0xFF0F1F16),
    tertiary = Color(0xFF4A7C59),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFCDE8D4),
    onTertiaryContainer = Color(0xFF002112),
    background = Color(0xFFF5F7F4),
    onBackground = Color(0xFF1A1C1A),
    surface = Color(0xFFF5F7F4),
    onSurface = Color(0xFF1A1C1A),
    surfaceVariant = Color(0xFFDDE5DB),
    onSurfaceVariant = Color(0xFF414941),
    outline = Color(0xFF717971),
    outlineVariant = Color(0xFFC1C9BE)
)

private val GreenDark = darkColorScheme(
    primary = Color(0xFF7BC896),
    onPrimary = Color(0xFF00391F),
    primaryContainer = Color(0xFF1B5C38),
    onPrimaryContainer = Color(0xFFC8E6D0),
    secondary = Color(0xFFB8CBBE),
    onSecondary = Color(0xFF24342A),
    secondaryContainer = Color(0xFF3A4C42),
    onSecondaryContainer = Color(0xFFD4E7DB),
    tertiary = Color(0xFFA3D2AC),
    onTertiary = Color(0xFF123D25),
    tertiaryContainer = Color(0xFF2F5A3C),
    onTertiaryContainer = Color(0xFFCDE8D4),
    background = Color(0xFF121A15),
    onBackground = Color(0xFFE1E3DF),
    surface = Color(0xFF121A15),
    onSurface = Color(0xFFE1E3DF),
    surfaceVariant = Color(0xFF414941),
    onSurfaceVariant = Color(0xFFC1C9BE),
    outline = Color(0xFF8B938A),
    outlineVariant = Color(0xFF414941)
)

// --- Orange (locked palettes.json) ---
private val OrangeLight = lightColorScheme(
    primary = Color(0xFFE65100),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDBCB),
    onPrimaryContainer = Color(0xFF341100),
    secondary = Color(0xFF755846),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDBCA),
    onSecondaryContainer = Color(0xFF2B1608),
    tertiary = Color(0xFF9A5520),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDDB8),
    onTertiaryContainer = Color(0xFF321200),
    background = Color(0xFFFFF8F4),
    onBackground = Color(0xFF201A17),
    surface = Color(0xFFFFF8F4),
    onSurface = Color(0xFF201A17),
    surfaceVariant = Color(0xFFF4DED4),
    onSurfaceVariant = Color(0xFF52443C),
    outline = Color(0xFF84746A),
    outlineVariant = Color(0xFFD7C2B8)
)

private val OrangeDark = darkColorScheme(
    primary = Color(0xFFFFB68F),
    onPrimary = Color(0xFF561F00),
    primaryContainer = Color(0xFFA33D00),
    onPrimaryContainer = Color(0xFFFFDBCB),
    secondary = Color(0xFFE6BEAB),
    onSecondary = Color(0xFF422B1C),
    secondaryContainer = Color(0xFF5B4030),
    onSecondaryContainer = Color(0xFFFFDBCA),
    tertiary = Color(0xFFFFB77C),
    onTertiary = Color(0xFF532200),
    tertiaryContainer = Color(0xFF6B3A0F),
    onTertiaryContainer = Color(0xFFFFDDB8),
    background = Color(0xFF1A1410),
    onBackground = Color(0xFFF0E0D6),
    surface = Color(0xFF1A1410),
    onSurface = Color(0xFFF0E0D6),
    surfaceVariant = Color(0xFF52443C),
    onSurfaceVariant = Color(0xFFD7C2B8),
    outline = Color(0xFF9F8D83),
    outlineVariant = Color(0xFF52443C)
)

fun colorSchemeFor(theme: AppTheme, darkTheme: Boolean): ColorScheme = when (theme) {
    AppTheme.PURPLE -> if (darkTheme) PurpleDark else PurpleLight
    AppTheme.BLUE -> if (darkTheme) BlueDark else BlueLight
    AppTheme.RED -> if (darkTheme) RedDark else RedLight
    AppTheme.GREEN -> if (darkTheme) GreenDark else GreenLight
    AppTheme.ORANGE -> if (darkTheme) OrangeDark else OrangeLight
}

@Composable
fun WorkHoursTheme(
    theme: AppTheme = AppTheme.DEFAULT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> colorSchemeFor(theme, darkTheme)
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
