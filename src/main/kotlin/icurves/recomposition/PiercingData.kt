package icurves.recomposition

import icurves.CurvesApp
import icurves.diagram.BasicRegion
import javafx.geometry.Point2D

/**
 *
 *
 * @author Almas Baimagambetov (almaslvl@gmail.com)
 */
class PiercingData(numRegions: Int, cluster: List<BasicRegion>, basicRegions: List<BasicRegion>) {

    val center: Point2D?
    val radius: Double

    init {
        if (numRegions == 4) {
            center = cluster.map { it.getPolygonShape().vertices() }
                    .flatten()
                    .groupBy({ it.asInt })
                    // we search for a vertex that is present in all 4 regions
                    .filter { it.value.size == 4 }
                    .map { Point2D(it.key.getX(), it.key.getY()) }
                    .firstOrNull()

        } else { // if 2

            val points = cluster.map { it.getPolygonShape().vertices() }
                    .flatten()
                    .groupBy({ it.asInt })
                    // we search for vertices present along 2 region bounds (collisions)
                    .filter { it.value.size == numRegions }
                    .map { Point2D(it.key.getX(), it.key.getY()) }

            val sum = points
                    .foldRight(Point2D.ZERO, { p, acc -> p.add(acc) })
                    .multiply(1.0 / points.size)

            // in some cases sum itself is a good point ...
            // but we choose one that is actually present along the 2 regions' bounds

            center = points.sortedBy { it.distance(sum) }
                    .firstOrNull()
        }

        if (center != null) {
            radius = basicRegions
                    .minus(cluster)
                    .map { it.getPolygonShape().distance(center.x, center.y) }
                    .sorted()
                    .first()
        } else {
            radius = 0.0
        }
    }

    fun isPiercing() = center != null
}