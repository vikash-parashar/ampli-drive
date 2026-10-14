package com.vikash.ampli.drive.ui.theme

/**
 * Colours of one frosted-glass theme, as ARGB Longs so they can be checked in plain unit tests.
 *
 * The backdrop is a vertical gradient ([top] to [bottom]) with soft colour [blobs]; panels blur it
 * and lay [panel] on top. With "Reduce glass effects" (or no blur available) panels use the more
 * opaque [reducedPanel] instead, so text keeps its contrast without the blur.
 */
data class GlassPalette(
    val light: Boolean,
    val top: Long,
    val bottom: Long,
    val blobs: List<GlassBlob>,
    val panel: Long,
    val reducedPanel: Long,
    /** Turn card over the map: the map can't be blurred, so this is nearly opaque. */
    val overMap: Long,
    val text: Long,
    val muted: Long,
    val accent: Long,
    val onAccent: Long,
    /** Second gauge colour (the arc runs accent → gauge2). */
    val gauge2: Long,
    /** Bright top edge of a panel. */
    val edgeTop: Long,
    /** Faint bottom edge of a panel. */
    val edgeBottom: Long,
    /** Blur radius in dp. */
    val blurDp: Float = 28f,
) {
    /** Colours a panel can sit over: the two gradient ends and every blob laid on each of them. */
    fun backdropSamples(): List<Long> =
        listOf(top, bottom) + blobs.flatMap { b -> listOf(Contrast.over(b.color, top), Contrast.over(b.color, bottom)) }
}

/** A soft radial colour spot. [x], [y] and [radius] are fractions of the screen width/height. */
data class GlassBlob(val x: Float, val y: Float, val radius: Float, val color: Long)

/** The six glass palettes. Contrast is checked in GlassContrastTest. */
object GlassPalettes {

    /** G1, light and clear: pastel sky behind white frost. Free. */
    val Frosted = GlassPalette(
        light = true, top = 0xFFDCE8F7, bottom = 0xFFF3E6F2,
        blobs = listOf(
            GlassBlob(0.12f, 0.18f, 0.42f, 0xD97DD3FC),
            GlassBlob(0.86f, 0.12f, 0.38f, 0xCCC4B5FD),
            GlassBlob(0.72f, 0.92f, 0.45f, 0xCCFBCFE8),
            GlassBlob(0.08f, 0.96f, 0.36f, 0xBFA7F3D0),
        ),
        panel = 0x8CFFFFFF, reducedPanel = 0xD9FFFFFF, overMap = 0xE6FFFFFF,
        text = 0xFF0B1220, muted = 0xFF3B4658, accent = 0xFF0A6CFF, onAccent = 0xFFFFFFFF, gauge2 = 0xFF5AC8FA,
        edgeTop = 0xE6FFFFFF, edgeBottom = 0x40FFFFFF,
    )

    /** G2, dark frost over deep blue and violet. Free. */
    val MidnightFrost = GlassPalette(
        light = false, top = 0xFF0B1026, bottom = 0xFF1A1340,
        blobs = listOf(
            GlassBlob(0.15f, 0.2f, 0.42f, 0x8C3B82F6),
            GlassBlob(0.85f, 0.18f, 0.38f, 0x808B5CF6),
            GlassBlob(0.68f, 0.95f, 0.44f, 0x6606B6D4),
            GlassBlob(0.1f, 0.92f, 0.34f, 0x59EC4899),
        ),
        panel = 0x73141828, reducedPanel = 0xD9141828, overMap = 0xE6141828,
        text = 0xFFF5F7FF, muted = 0xFFB9C2DA, accent = 0xFF7DD3FC, onAccent = 0xFF04131A, gauge2 = 0xFFA78BFA,
        edgeTop = 0x4DFFFFFF, edgeBottom = 0x14FFFFFF,
    )

    /** G3, Ampli rose tint on dark plum. */
    val Rose = GlassPalette(
        light = false, top = 0xFF1A0710, bottom = 0xFF2A0A1F,
        blobs = listOf(
            GlassBlob(0.18f, 0.15f, 0.45f, 0x99E11D48),
            GlassBlob(0.88f, 0.25f, 0.36f, 0x73FB7185),
            GlassBlob(0.65f, 0.95f, 0.42f, 0x667C3AED),
            GlassBlob(0.06f, 0.9f, 0.3f, 0x40F59E0B),
        ),
        panel = 0x8C2A0716, reducedPanel = 0xE02A0716, overMap = 0xE62A0716,
        text = 0xFFFFF1F3, muted = 0xFFFBC9D2, accent = 0xFFFB7185, onAccent = 0xFF2A0716, gauge2 = 0xFFFDA4AF,
        edgeTop = 0x59FFD6DE, edgeBottom = 0x14FFFFFF,
    )

    /** G4, Car Hub brand cyan (#0891B2), light. */
    val Cyan = GlassPalette(
        light = true, top = 0xFFE0F7FA, bottom = 0xFFCFFAFE,
        blobs = listOf(
            GlassBlob(0.14f, 0.2f, 0.42f, 0x8C0891B2),
            GlassBlob(0.86f, 0.14f, 0.36f, 0x8022D3EE),
            GlassBlob(0.7f, 0.94f, 0.44f, 0x6638BDF8),
            GlassBlob(0.06f, 0.94f, 0.34f, 0x99A5F3FC),
        ),
        panel = 0xA6F0FDFF, reducedPanel = 0xE0F0FDFF, overMap = 0xEBF0FDFF,
        // The brand cyan is the gauge; the darker cyan-700 is the accent so it reads on white glass.
        text = 0xFF062A33, muted = 0xFF24525E, accent = 0xFF0E7490, onAccent = 0xFFFFFFFF, gauge2 = 0xFF0891B2,
        edgeTop = 0xE6FFFFFF, edgeBottom = 0x40FFFFFF,
    )

    /** G5, violet tint on dark indigo. */
    val Violet = GlassPalette(
        light = false, top = 0xFF12052E, bottom = 0xFF1E0B4A,
        blobs = listOf(
            GlassBlob(0.16f, 0.18f, 0.44f, 0x997C3AED),
            GlassBlob(0.86f, 0.2f, 0.36f, 0x80A855F7),
            GlassBlob(0.66f, 0.96f, 0.42f, 0x736366F1),
            GlassBlob(0.08f, 0.9f, 0.3f, 0x4DEC4899),
        ),
        panel = 0x801C0F3D, reducedPanel = 0xE01C0F3D, overMap = 0xE61C0F3D,
        text = 0xFFF5F0FF, muted = 0xFFCDBDF5, accent = 0xFFA78BFA, onAccent = 0xFF1C0F3D, gauge2 = 0xFFF0ABFC,
        edgeTop = 0x4DE9DDFF, edgeBottom = 0x14FFFFFF,
    )

    /** G6, warm amber and sunset light. */
    val Sunset = GlassPalette(
        light = true, top = 0xFFFFF1DC, bottom = 0xFFFFE0D0,
        blobs = listOf(
            GlassBlob(0.14f, 0.16f, 0.42f, 0x8CF59E0B),
            GlassBlob(0.86f, 0.2f, 0.38f, 0x80FB923C),
            GlassBlob(0.7f, 0.95f, 0.44f, 0x59F43F5E),
            GlassBlob(0.06f, 0.92f, 0.34f, 0x99FDE68A),
        ),
        panel = 0xA6FFF8F0, reducedPanel = 0xE0FFF8F0, overMap = 0xEBFFF8F0,
        text = 0xFF2A1405, muted = 0xFF6B4423, accent = 0xFFC2410C, onAccent = 0xFFFFFFFF, gauge2 = 0xFFF59E0B,
        edgeTop = 0xE6FFFFFF, edgeBottom = 0x40FFFFFF,
    )

    val all: List<GlassPalette> = listOf(Frosted, MidnightFrost, Rose, Cyan, Violet, Sunset)
}
