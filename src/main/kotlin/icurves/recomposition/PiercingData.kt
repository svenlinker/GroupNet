package icurves.recomposition

import icurves.description.AbstractBasicRegion
import icurves.diagram.BasicRegion
import icurves.diagram.DiagramCreator
import icurves.guifx.SettingsController
import javafx.geometry.Point2D
import math.geom2d.polygon.MultiPolygon2D

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

            val map = cluster.map { it.getPolygonShape().vertices() }
                    .flatten()
                    .groupBy({ it.asInt })
                    // we search for vertices present along 2 region bounds (collisions)
                    .filter { it.value.size >= numRegions }
                    .filter { entry ->
                        cluster.all { it.getPolygonShape().vertices().map { it.asInt }.any { it.x == entry.key.x && it.y == entry.key.y } }
                    }
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
                    .groupBy { computeRadius(it) }
                    .toSortedMap()

            center = if (map.isEmpty()) null else map[map.lastKey()]!!.sortedByDescending { it.x }.firstOrNull()
        }

        if (center != null) {
            radius = computeRadius(center)
        } else {
            radius = 0.0
        }
    }

    fun isPiercing() = center != null

    private fun computeRadius(potentialCenter: Point2D): Double {
        return basicRegions
                .minus(cluster)
                .map {
                    if (it.getPolygonShape() is MultiPolygon2D) {

                        // check signed distance and also of the complement
                        val dist1 = Math.abs(it.getPolygonShape().boundary().signedDistance(potentialCenter.x, potentialCenter.y))
                        val dist2 = Math.abs(it.getPolygonShape().complement().boundary().signedDistance(potentialCenter.x, potentialCenter.y))

                        Math.min(dist1, dist2)
                    } else {
                        it.getPolygonShape().distance(potentialCenter.x, potentialCenter.y)
                    }
                }
                .sorted()
                // null occurs when we split a single curve?
                .firstOrNull() ?: DiagramCreator.BASE_CURVE_RADIUS * 2
    }
}