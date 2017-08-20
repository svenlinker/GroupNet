package icurves.recomposition

import icurves.diagram.BasicRegion
import icurves.diagram.DiagramCreator
import icurves.guifx.SettingsController
import javafx.geometry.Point2D

/**
 *
 *
 * @author Almas Baimagambetov (almaslvl@gmail.com)
 */
class PiercingData(numRegions: Int, private val cluster: List<BasicRegion>, private val basicRegions: List<BasicRegion>) {

    val center: Point2D?
    val radius: Double

    init {
        if (numRegions == 4) {
            center = cluster.map { it.getPolygonShape().vertices() }
                    .flatten()
                    .groupBy({ it.asInt })
                    // we search for a vertex that is present in all 4 regions (sometimes we can have duplicates)
                    .filter { it.value.size >= 4 }
                    // ensure that each br in cluster has such a vertex
                    .filter { entry ->
                        cluster.all { it.getPolygonShape().vertices().map { it.asInt }.any { it.x == entry.key.x && it.y == entry.key.y } }
                    }
                    .map { Point2D(it.key.getX(), it.key.getY()) }
                    // select the bottom circle, then top
                    .sortedByDescending { it.y }
                    .firstOrNull()

        } else { // if 2

            val points = cluster.map { it.getPolygonShape().vertices() }
                    .flatten()
                    .groupBy({ it.asInt })
                    // we search for vertices present along 2 region bounds (collisions)
                    .filter { it.value.size == numRegions }
                    .map { Point2D(it.key.getX(), it.key.getY()) }
                    // remove vertices that occur in other basic region bounds
                    // to filter out the corner vertices
                    .minus(
                            basicRegions.minus(cluster)
                                    .map { it.getPolygonShape().vertices() }
                                    .flatten()
                                    .groupBy { it.asInt }
                                    .map { Point2D(it.key.getX(), it.key.getY()) }
                    )
                    // choose the one where we can fit largest circle
                    .sortedByDescending { computeRadius(it) }

            center = points.firstOrNull()
        }

        if (center != null) {
            radius = basicRegions
                    .minus(cluster)
                    .map { it.getPolygonShape().distance(center.x, center.y) }
                    .sorted()
                    .firstOrNull() ?: DiagramCreator.BASE_CURVE_RADIUS * 2
        } else {
            radius = 0.0
        }
    }

    fun isPiercing() = center != null

    private fun computeRadius(potentialCenter: Point2D): Double {
        return basicRegions
                .minus(cluster)
                .map { it.getPolygonShape().distance(potentialCenter.x, potentialCenter.y) }
                .sorted()
                // null occurs when we split a single curve?
                .firstOrNull() ?: DiagramCreator.BASE_CURVE_RADIUS * 2
    }
}