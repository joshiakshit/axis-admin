package com.ash.axis.admin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable
internal fun AxisMark() {
    Canvas(Modifier.size(32.dp).semantics { contentDescription = "Axis" }) {
        // Same 24 × 18 geometry as ic_axis_logo, drawn without a cached vector bitmap.
        val scale = size.width / 24f
        val top = (size.height - 18f * scale) / 2f
        val path =
            Path().apply {
                moveTo(2.5f * scale, top + 15.5f * scale)
                lineTo(12f * scale, top + 2.5f * scale)
                lineTo(21.5f * scale, top + 15.5f * scale)
            }
        drawPath(path, Color.White, style = Stroke(3f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
