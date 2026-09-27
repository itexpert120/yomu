package com.itexpert120.yomu.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

/**
 * A [Shape] drawn from a Material shape-library [Morph] at [progress] (0 = start, 1 = end). The
 * library polygons are normalized to a unit square, so the path is scaled to the layout size.
 */
class YomuMorphShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotation: Float = 0f,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path: Path = morph.toPath(progress = progress)
        val matrix = Matrix()
        if (rotation != 0f) {
            matrix.translate(size.width / 2f, size.height / 2f)
            matrix.rotateZ(rotation)
            matrix.translate(-size.width / 2f, -size.height / 2f)
        }
        matrix.scale(size.width, size.height)
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

/**
 * Selection-driven shape morph: rests on [unselected] and morphs to [selected] with the theme's
 * fast spatial spring, so selected controls read as "picked up" rather than just recoloured.
 */
@Composable
fun rememberSelectionMorphShape(
    selected: Boolean,
    unselected: RoundedPolygon,
    selectedPolygon: RoundedPolygon,
): Shape {
    val morph = remember(unselected, selectedPolygon) { Morph(unselected, selectedPolygon) }
    val progress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (yomuAnimationsEnabled()) {
            MaterialTheme.motionScheme.fastSpatialSpec()
        } else {
            androidx.compose.animation.core.snap()
        },
        label = "selectionMorph",
    )
    return remember(morph, progress) { YomuMorphShape(morph, progress) }
}

/**
 * Draws a Material shape-library [polygon] behind this element — scaled to [scale]× the element's
 * width, rotated by [rotation] degrees, and shifted from centre by [offsetX]/[offsetY] (fractions
 * of the width). For decorative hero moments only; never put text on it.
 */
fun Modifier.yomuDecorativeShape(
    polygon: RoundedPolygon,
    color: Color,
    scale: Float = 1.3f,
    rotation: Float = 0f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
): Modifier = this.drawBehind {
    val side = size.width * scale
    val outline = YomuMorphShape(Morph(polygon, polygon), 0f, rotation)
        .createOutline(Size(side, side), layoutDirection, this)
    val dx = size.width * offsetX * if (layoutDirection == LayoutDirection.Rtl) -1f else 1f
    translate(left = (size.width - side) / 2f + dx, top = (size.height - side) / 2f + size.width * offsetY) {
        drawOutline(outline, color)
    }
}
