package com.rmltd.workhourstracker.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.annotation.ColorInt
import com.rmltd.workhourstracker.ui.theme.AppTheme

/**
 * RemoteViews chrome colors for the home-screen widget.
 * Mirrors [com.rmltd.workhourstracker.ui.theme.colorSchemeFor] locked hexes
 * (primary / primaryContainer / onSurface / onSurfaceVariant) per AppTheme × light/dark.
 * Status copy stays in [WidgetContent]; only chrome colors change with theme.
 */
data class WidgetChromeColors(
    @ColorInt val primary: Int,
    @ColorInt val primaryContainer: Int,
    @ColorInt val onSurface: Int,
    @ColorInt val onSurfaceVariant: Int
)

object WidgetThemeColors {

    fun resolve(theme: AppTheme, dark: Boolean): WidgetChromeColors = when (theme) {
        AppTheme.PURPLE -> if (dark) purpleDark else purpleLight
        AppTheme.BLUE -> if (dark) blueDark else blueLight
        AppTheme.RED -> if (dark) redDark else redLight
        AppTheme.GREEN -> if (dark) greenDark else greenLight
        AppTheme.ORANGE -> if (dark) orangeDark else orangeLight
        AppTheme.AQUA -> if (dark) aquaDark else aquaLight
    }

    /**
     * Rounded card fill + 3dp-equivalent top accent bar (matches widget_background.xml).
     * Sized for ImageView fitXY stretch across the widget cell.
     */
    fun buildBackgroundBitmap(
        @ColorInt fillColor: Int,
        @ColorInt accentColor: Int,
        widthPx: Int,
        heightPx: Int,
        cornerPx: Float,
        accentHeightPx: Float
    ): Bitmap {
        val w = widthPx.coerceAtLeast(1)
        val h = heightPx.coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val bounds = RectF(0f, 0f, w.toFloat(), h.toFloat())
        val radii = floatArrayOf(
            cornerPx, cornerPx, cornerPx, cornerPx,
            cornerPx, cornerPx, cornerPx, cornerPx
        )
        val fillPath = Path().apply { addRoundRect(bounds, radii, Path.Direction.CW) }
        paint.color = fillColor
        canvas.drawPath(fillPath, paint)

        val accentH = accentHeightPx.coerceAtLeast(1f).coerceAtMost(h.toFloat())
        val accentBounds = RectF(0f, 0f, w.toFloat(), accentH)
        val accentRadii = floatArrayOf(
            cornerPx, cornerPx, cornerPx, cornerPx,
            0f, 0f, 0f, 0f
        )
        val accentPath = Path().apply { addRoundRect(accentBounds, accentRadii, Path.Direction.CW) }
        paint.color = accentColor
        canvas.drawPath(accentPath, paint)
        return bmp
    }

    // --- Locked Theme.kt / palettes.json roles ---

    private val purpleLight = WidgetChromeColors(
        primary = 0xFF5B3F9E.toInt(),
        primaryContainer = 0xFFE9DDFF.toInt(),
        onSurface = 0xFF1D1A22.toInt(),
        onSurfaceVariant = 0xFF49454E.toInt()
    )
    private val purpleDark = WidgetChromeColors(
        primary = 0xFFCFBDFF.toInt(),
        primaryContainer = 0xFF4A2F85.toInt(),
        onSurface = 0xFFE7E0E8.toInt(),
        onSurfaceVariant = 0xFFCBC4CF.toInt()
    )
    private val blueLight = WidgetChromeColors(
        primary = 0xFF1565C0.toInt(),
        primaryContainer = 0xFFD1E4FF.toInt(),
        onSurface = 0xFF1A1C1E.toInt(),
        onSurfaceVariant = 0xFF43474E.toInt()
    )
    private val blueDark = WidgetChromeColors(
        primary = 0xFFA0CAFD.toInt(),
        primaryContainer = 0xFF0D47A1.toInt(),
        onSurface = 0xFFE2E2E6.toInt(),
        onSurfaceVariant = 0xFFC3C7CF.toInt()
    )
    private val redLight = WidgetChromeColors(
        primary = 0xFFC62828.toInt(),
        primaryContainer = 0xFFFFDAD6.toInt(),
        onSurface = 0xFF1C1B1F.toInt(),
        onSurfaceVariant = 0xFF534341.toInt()
    )
    private val redDark = WidgetChromeColors(
        primary = 0xFFFFB4AB.toInt(),
        primaryContainer = 0xFF93000A.toInt(),
        onSurface = 0xFFE6E1E5.toInt(),
        onSurfaceVariant = 0xFFD8C2BE.toInt()
    )
    private val greenLight = WidgetChromeColors(
        primary = 0xFF2E7D4F.toInt(),
        primaryContainer = 0xFFC8E6D0.toInt(),
        onSurface = 0xFF1A1C1A.toInt(),
        onSurfaceVariant = 0xFF414941.toInt()
    )
    private val greenDark = WidgetChromeColors(
        primary = 0xFF7BC896.toInt(),
        primaryContainer = 0xFF1B5C38.toInt(),
        onSurface = 0xFFE1E3DF.toInt(),
        onSurfaceVariant = 0xFFC1C9BE.toInt()
    )
    private val orangeLight = WidgetChromeColors(
        primary = 0xFFE65100.toInt(),
        primaryContainer = 0xFFFFDBCB.toInt(),
        onSurface = 0xFF201A17.toInt(),
        onSurfaceVariant = 0xFF52443C.toInt()
    )
    private val orangeDark = WidgetChromeColors(
        primary = 0xFFFFB68F.toInt(),
        primaryContainer = 0xFFA33D00.toInt(),
        onSurface = 0xFFF0E0D6.toInt(),
        onSurfaceVariant = 0xFFD7C2B8.toInt()
    )
    private val aquaLight = WidgetChromeColors(
        primary = 0xFF00838F.toInt(),
        primaryContainer = 0xFFB2EBF2.toInt(),
        onSurface = 0xFF161D1E.toInt(),
        onSurfaceVariant = 0xFF3F484A.toInt()
    )
    private val aquaDark = WidgetChromeColors(
        primary = 0xFF4DD0E1.toInt(),
        primaryContainer = 0xFF006064.toInt(),
        onSurface = 0xFFDEE3E4.toInt(),
        onSurfaceVariant = 0xFFBEC8CA.toInt()
    )
}
