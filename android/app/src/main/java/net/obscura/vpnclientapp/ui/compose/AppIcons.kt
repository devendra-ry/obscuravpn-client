package net.obscura.vpnclientapp.ui.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object AppIcons {
    val ChevronRight: ImageVector by lazy {
        strokeIcon(
            "ChevronRight",
            "M9 6 L15 12 L9 18",
        )
    }

    val ChevronDown: ImageVector by lazy {
        strokeIcon(
            "ChevronDown",
            "M6 9 L12 15 L18 9",
        )
    }

    val Check: ImageVector by lazy {
        strokeIcon(
            "Check",
            "M5 13 L9 17 L19 7",
        )
    }

    val Search: ImageVector by lazy {
        strokeIcon(
            "Search",
            "M11 4 A7 7 0 1 0 11 18 A7 7 0 1 0 11 4 Z",
            "M16.5 16.5 L21 21",
        )
    }

    val Close: ImageVector by lazy {
        strokeIcon(
            "Close",
            "M6 6 L18 18",
            "M18 6 L6 18",
        )
    }

    val ArrowDown: ImageVector by lazy {
        strokeIcon(
            "ArrowDown",
            "M12 5 L12 19",
            "M6 13 L12 19 L18 13",
        )
    }

    val ArrowUp: ImageVector by lazy {
        strokeIcon(
            "ArrowUp",
            "M12 19 L12 5",
            "M6 11 L12 5 L18 11",
        )
    }

    val Globe: ImageVector by lazy {
        strokeIcon(
            "Globe",
            "M12 2 A10 10 0 1 0 12 22 A10 10 0 1 0 12 2 Z",
            "M2 12 L22 12",
            "M12 2 C15 6 15 18 12 22",
            "M12 2 C9 6 9 18 12 22",
        )
    }

    val Phone: ImageVector by lazy {
        strokeIcon(
            "Phone",
            "M7 3 H17 A2 2 0 0 1 19 5 V19 A2 2 0 0 1 17 21 H7 A2 2 0 0 1 5 19 V5 A2 2 0 0 1 7 3 Z",
            "M11 18 H13",
        )
    }

    val Speedometer: ImageVector by lazy {
        strokeIcon(
            "Speedometer",
            "M4.9 19.1 A10 10 0 1 1 19.1 19.1",
            "M12 13 L16.5 8.5",
            "M12 13 m-1.2 0 a1.2 1.2 0 1 0 2.4 0 a1.2 1.2 0 1 0 -2.4 0",
        )
    }

    val Eye: ImageVector by lazy {
        strokeIcon(
            "Eye",
            "M2 12 C4.5 7 8 5 12 5 C16 5 19.5 7 22 12 C19.5 17 16 19 12 19 C8 19 4.5 17 2 12 Z",
            "M12 9 A3 3 0 1 0 12 15 A3 3 0 1 0 12 9 Z",
        )
    }

    val EyeOff: ImageVector by lazy {
        strokeIcon(
            "EyeOff",
            "M3 3 L21 21",
            "M10.6 10.6 A3 3 0 0 0 13.4 13.4",
            "M9.9 5.2 A10 10 0 0 1 22 12 C20.7 14.6 18.8 16.5 16.5 17.6",
            "M6.6 6.6 C4.6 8 3.1 9.8 2 12 C4.5 17 8 19 12 19 C13.5 19 15 18.7 16.3 18.1",
        )
    }

    val Pin: ImageVector by lazy {
        strokeIcon(
            "Pin",
            "M9 4 H15 L14 10 L17 13 V14 H7 V13 L10 10 Z",
            "M12 14 V21",
        )
    }

    val Copy: ImageVector by lazy {
        strokeIcon(
            "Copy",
            "M9 9 H19 A1 1 0 0 1 20 10 V20 A1 1 0 0 1 19 21 H9 A1 1 0 0 1 8 20 V10 A1 1 0 0 1 9 9 Z",
            "M5 15 H4 A1 1 0 0 1 3 14 V4 A1 1 0 0 1 4 3 H14 A1 1 0 0 1 15 4 V5",
        )
    }

    val Warning: ImageVector by lazy {
        strokeIcon(
            "Warning",
            "M12 3 L22 20 H2 Z",
            "M12 10 V14",
            "M12 17 V17.5",
        )
    }

    val Info: ImageVector by lazy {
        strokeIcon(
            "Info",
            "M12 3 A9 9 0 1 0 12 21 A9 9 0 1 0 12 3 Z",
            "M12 11 L12 16",
            "M12 8 L12 8.01",
        )
    }

    val ExternalLink: ImageVector by lazy {
        strokeIcon(
            "ExternalLink",
            "M14 4 H20 V10",
            "M10 14 L20 4",
            "M18 14 V19 A1 1 0 0 1 17 20 H5 A1 1 0 0 1 4 19 V7 A1 1 0 0 1 5 6 H10",
        )
    }

    val WifiOff: ImageVector by lazy {
        strokeIcon(
            "WifiOff",
            "M2 2 L22 22",
            "M8.5 16.5 A5 5 0 0 1 15.5 16.5",
            "M5 13 A10 10 0 0 1 11 10.2",
            "M15.5 10.7 A10 10 0 0 1 19 13",
            "M12 20 H12.01",
        )
    }

    val ShieldCheck: ImageVector by lazy {
        strokeIcon(
            "ShieldCheck",
            "M12 3 L4 7 V12 C4 16.5 7.4 20.6 12 22 C16.6 20.6 20 16.5 20 12 V7 Z",
            "M8.5 12.5 L11 15 L15.5 10",
        )
    }

    val ShieldOff: ImageVector by lazy {
        strokeIcon(
            "ShieldOff",
            "M12 3 L4 7 V12 C4 16.5 7.4 20.6 12 22 C16.6 20.6 20 16.5 20 12 V7 Z",
            "M9 12 L15 12",
        )
    }

    private fun strokeIcon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(
                name = name,
                defaultWidth = 24.dp,
                defaultHeight = 24.dp,
                viewportWidth = 24f,
                viewportHeight = 24f,
            )
            .apply {
                for (d in paths) {
                    path(
                        stroke = SolidColor(Color.Black),
                        strokeLineWidth = 2f,
                        strokeLineCap = StrokeCap.Round,
                        strokeLineJoin = StrokeJoin.Round,
                    ) {
                        addPathNodes(d)
                    }
                }
            }
            .build()

    private fun androidx.compose.ui.graphics.vector.PathBuilder.addPathNodes(data: String) {
        val nodes = androidx.compose.ui.graphics.vector.PathParser().parsePathString(data).toNodes()
        for (node in nodes) {
            when (node) {
                is androidx.compose.ui.graphics.vector.PathNode.MoveTo -> moveTo(node.x, node.y)
                is androidx.compose.ui.graphics.vector.PathNode.RelativeMoveTo -> moveToRelative(node.dx, node.dy)
                is androidx.compose.ui.graphics.vector.PathNode.LineTo -> lineTo(node.x, node.y)
                is androidx.compose.ui.graphics.vector.PathNode.RelativeLineTo -> lineToRelative(node.dx, node.dy)
                is androidx.compose.ui.graphics.vector.PathNode.HorizontalTo -> horizontalLineTo(node.x)
                is androidx.compose.ui.graphics.vector.PathNode.RelativeHorizontalTo ->
                    horizontalLineToRelative(node.dx)
                is androidx.compose.ui.graphics.vector.PathNode.VerticalTo -> verticalLineTo(node.y)
                is androidx.compose.ui.graphics.vector.PathNode.RelativeVerticalTo -> verticalLineToRelative(node.dy)
                is androidx.compose.ui.graphics.vector.PathNode.CurveTo ->
                    curveTo(node.x1, node.y1, node.x2, node.y2, node.x3, node.y3)
                is androidx.compose.ui.graphics.vector.PathNode.ArcTo ->
                    arcTo(
                        node.horizontalEllipseRadius,
                        node.verticalEllipseRadius,
                        node.theta,
                        node.isMoreThanHalf,
                        node.isPositiveArc,
                        node.arcStartX,
                        node.arcStartY,
                    )
                is androidx.compose.ui.graphics.vector.PathNode.RelativeArcTo ->
                    arcToRelative(
                        node.horizontalEllipseRadius,
                        node.verticalEllipseRadius,
                        node.theta,
                        node.isMoreThanHalf,
                        node.isPositiveArc,
                        node.arcStartDx,
                        node.arcStartDy,
                    )
                is androidx.compose.ui.graphics.vector.PathNode.Close -> close()
                else -> Unit
            }
        }
    }
}
