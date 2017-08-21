package icurves.diagram

import icurves.decomposition.DecomposerFactory
import icurves.description.AbstractBasicRegion
import icurves.description.AbstractCurve
import icurves.description.Description
import icurves.diagram.curve.CircleCurve
import icurves.diagram.curve.PathCurve
import icurves.graph.EulerDualEdge
import icurves.graph.EulerDualNode
import icurves.graph.GraphCycle
import icurves.graph.MED
import icurves.guifx.SettingsController
import icurves.recomposition.PiercingData
import icurves.recomposition.RecomposerFactory
import icurves.recomposition.RecompositionData
import icurves.util.BezierApproximation
import icurves.util.Profiler
import javafx.collections.FXCollections
import javafx.geometry.Point2D
import javafx.scene.paint.Color
import javafx.scene.shape.*
import org.apache.logging.log4j.LogManager
import java.util.*

/**
 * Diagram creator that uses simple cycles to add curves.
 * At least 1 Hamiltonian is always present.
 * This ensures that any description is drawable.
 *
 * @author Almas Baimagambetov (almaslvl@gmail.com)
 */
class DiagramCreator(val settings: SettingsController) {

    companion object {
        private val log = LogManager.getLogger(DiagramCreator::class.java)

        @JvmField val BASE_CURVE_RADIUS = 1500.0
    }

    /**
     * Maps abstract curve to its concrete version.
     * This is a 1 to 1 map since there are no duplicates.
     */
    val curveToContour = FXCollections.observableMap(LinkedHashMap<AbstractCurve, Curve>())

    val abRegionToBasicRegion = FXCollections.observableMap(LinkedHashMap<AbstractBasicRegion, BasicRegion>())

    /**
     * Abstract basic regions we have processed so far.
     */
    private val abstractRegions = ArrayList<AbstractBasicRegion>()

    /**
     * Basic regions for current step.
     * Immutable, gets rewritten every step.
     */
    private lateinit var basicRegions: List<BasicRegion>

    private lateinit var outsideBR: BasicRegion

    val shadedRegions = ArrayList<BasicRegion>()

    lateinit var modifiedDual: MED

    fun createDiagram(description: Description) {

        // all we need is decomposition; recomposition is almost no-op
        val dSteps = DecomposerFactory.newDecomposer(settings.decompType).decompose(description)
        val rSteps = RecomposerFactory.newRecomposer().recompose(dSteps)

        for (i in rSteps.indices) {
            val data = rSteps[i].addedCurveData

            val curve = when (i) {

                // 0 is base case (single curve)
                0 -> {
                    // we only have new zones here, don't add Abstract OUTSIDE as it's a special case
                    abstractRegions.addAll(data.newZones)

                    CircleCurve(data.addedCurve, BASE_CURVE_RADIUS, BASE_CURVE_RADIUS, BASE_CURVE_RADIUS)
                }

                else -> embedCurve(data)
            }

            curveToContour[data.addedCurve] = curve
        }

        if (settings.showMED()) {
            createBasicRegions()
            createMED()
        }

        log.trace("Generating shaded zones")

        val shaded = abstractRegions.minus(description.zones)

        shadedRegions.addAll(shaded.map { BasicRegion(it, curveToContour) })
    }

    /**
     * Side-effects:
     *
     * 1. creates MED
     * 2. chooses a cycle
     * 3. creates a curve
     * 4. updates abstract regions
     */
    private fun embedCurve(data: RecompositionData): Curve {
        log.trace("Embed curve with regions: ${data.splitZones}")

        var curve: Curve? = null

        createBasicRegions()

        if (data.isMaybeDoublePiercing()) {

            // we don't have a map between abstract OUTSIDE and br OUTSIDE, hence ?:
            val piercingData = PiercingData(4, data.splitZones.map { abRegionToBasicRegion[it] ?: outsideBR }, basicRegions)
            if (piercingData.isPiercing()) {

                curve = if (numCurvesSoFar() == 2) {
                    // special: use slightly better position and size
                    CircleCurve(data.addedCurve, BASE_CURVE_RADIUS * 1.5, BASE_CURVE_RADIUS * 2, BASE_CURVE_RADIUS)
                } else {
                    CircleCurve(data.addedCurve, piercingData.center!!.x, piercingData.center.y, piercingData.radius / 2)
                }

                abstractRegions.addAll(data.splitZones.map { it.moveInside(data.addedCurve) })
            }
        } else if (data.isMaybeSinglePiercing()) {

            // we don't have a map between abstract OUTSIDE and br OUTSIDE, hence ?:
            val piercingData = PiercingData(2, data.splitZones.map { abRegionToBasicRegion[it] ?: outsideBR }, basicRegions)
            if (piercingData.isPiercing()) {

                curve = if (numCurvesSoFar() == 1) {
                    // special: use slightly better position and size
                    CircleCurve(data.addedCurve, BASE_CURVE_RADIUS * 2, BASE_CURVE_RADIUS, BASE_CURVE_RADIUS)
                } else {
                    CircleCurve(data.addedCurve, piercingData.center!!.x, piercingData.center.y, piercingData.radius / 2)
                }

                abstractRegions.addAll(data.splitZones.map { it.moveInside(data.addedCurve) })
            }
        } else if (data.isNested()) {

            // this doesn't work when two nested piercings in the same br
            // possibly because the br polygon has a hole in it, like a doughnut
            // and so the center is not computed properly?

            // when nested we only have 1 basic region in "split"
            // we don't have a map between abstract OUTSIDE and br OUTSIDE, hence ?:
            val br = abRegionToBasicRegion[data.splitZones[0]] ?: outsideBR

            val center = br.center

            // minus signed distance because the point lies inside the polygon
            val maxRadius = -br.getPolygonShape().boundary().signedDistance(center.x, center.y)

            curve = CircleCurve(data.addedCurve, center.x, center.y, maxRadius / 2)

            abstractRegions.addAll(data.splitZones.map { it.moveInside(data.addedCurve) })
        }

        if (curve == null) {
            createMED()

            val cycle = modifiedDual.computeCycle(data.splitZones) ?: throw RuntimeException("Bug: Failed to find cycle")

            curve = PathCurve(data.addedCurve, cycle.path)

            if (cycle.lengthUnique() == 2) {
                curve = embedSinglePiercing(data.addedCurve, cycle.nodesUnique().map { it.zone })
            }

            // here a 2node or 3node cycle may have been upgraded to 4node
            if (cycle.lengthUnique() == 4) {
                curve = embedDoublePiercing(data.addedCurve, cycle.nodesUnique().map { it.zone })
            }

            if (settings.useSmooth() && curve !is CircleCurve) {
                val smoothedPath = smooth(cycle)

                curve = PathCurve(data.addedCurve, smoothedPath)
            }

            // we might've used more zones to get a cycle, so we make sure we capture all of the used ones
            // we also call distinct() to ensure we don't reuse the outside zone more than once
            abstractRegions.addAll(cycle.nodes.map { it.zone.abRegion.moveInside(data.addedCurve) }.distinct())
        }

        return curve
    }

    private fun embedSinglePiercing(abstractCurve: AbstractCurve, regions: List<BasicRegion>): Curve {
        val piercingData = PiercingData(2, regions, basicRegions)

        if (!piercingData.isPiercing()) {
            throw RuntimeException("Bug: not 1-piercing")
        }

        return CircleCurve(abstractCurve, piercingData.center!!.x, piercingData.center.y, piercingData.radius / 2)
    }

    private fun embedDoublePiercing(abstractCurve: AbstractCurve, regions: List<BasicRegion>): Curve {
        val piercingData = PiercingData(4, regions, basicRegions)

        if (!piercingData.isPiercing()) {
            throw RuntimeException("Bug: not 2-piercing")
        }

        return CircleCurve(abstractCurve, piercingData.center!!.x, piercingData.center.y, piercingData.radius / 2)
    }

    private fun smooth(cycle: GraphCycle<EulerDualNode, EulerDualEdge>): Path {
        Profiler.start("Smoothing")

        //val pathSegments = BezierApproximation.smoothPath2(cycle.nodes.map { it.point }.toMutableList(), settings.smoothFactor)
        val pathSegments = BezierApproximation.smoothPath2(cycle.smoothingData, settings.smoothFactor)

        val newPath = Path()

        // add moveTo
        newPath.elements.add(cycle.path.elements[0])

        for (path in pathSegments) {
            path.elements.removeAt(0)
            newPath.elements.addAll(path.elements)
        }

        // TODO: we still need to check integrity

//        for (j in cycle.nodes.indices) {
//            val node1 = cycle.nodes[j]
//            val node2 = if (j == cycle.nodes.size - 1) cycle.nodes[0] else cycle.nodes[j + 1]
//
//            // this is to enable joining MED ring with internal edges at C2 continuity
//            // remove first moveTo
////            pathSegments[j].elements.removeAt(0)
////
////            // add to new path
////            newPath.elements.addAll(pathSegments[j].elements)
////
////            if (true)
////                continue
//
//
//            // check if this is the MED ring segment
//            // No need to check if we use lines?
//            if (node1.zone.abRegion == AbstractBasicRegion.OUTSIDE && node2.zone.abRegion == AbstractBasicRegion.OUTSIDE) {
//                pathSegments[j].elements.removeAt(0)
//                newPath.elements.addAll(pathSegments[j].elements)
//
////                val lineTo = cycle.path.elements[j + 1] as LineTo
////
////                newPath.elements.addAll(lineTo)
//                continue
//            }
//
//            // the new curve segment must pass through the straddled curve
//            // and only through that curve
//            val abstractCurve = node1.zone.abRegion.getStraddledContour(node2.zone.abRegion).get()
//
//            if (isOK(pathSegments[j], abstractCurve, curveToContour.values.toList())) {
//                // remove first moveTo
//                pathSegments[j].elements.removeAt(0)
//
//                // add to new path
//                newPath.elements.addAll(pathSegments[j].elements)
//            } else {
//                // j + 1 because we skip the first moveTo
//                newPath.elements.addAll(cycle.path.elements[j + 1])
//            }
//        }

        newPath.fill = Color.TRANSPARENT
        newPath.elements.add(ClosePath())

        Profiler.end("Smoothing")

        return newPath
    }

    private fun createBasicRegions() {
        basicRegions = abstractRegions.map {
            val br = BasicRegion(it, curveToContour)
            abRegionToBasicRegion[it] = br
            return@map br
        }

        outsideBR = BasicRegion(AbstractBasicRegion.OUTSIDE, curveToContour)
    }

    private fun numCurvesSoFar() = curveToContour.size

    /**
     * Needs to be generated every time because curves change basic regions.
     *
     * TODO: we could potentially only compute br that have been changed by the curve
     */
    private fun createMED() {
        log.trace("Creating MED")

        modifiedDual = MED(basicRegions, curveToContour)

//        if (settings.globalMap["astar"] != null) {
//            println("Printing points")
//
//            debugPoints.clear()
//
//            val poly = settings.globalMap["astar"] as Polyline
//
//            var i = 0
//            while (i < poly.points.size) {
//
//                val point = Point2D(poly.points[i], poly.points[++i])
//                debugPoints.add(point)
//
//                i++
//            }
//        }
    }

    /**
     * Does curve segment [q] only pass through [actual] curve.
     */
    fun isOK(q: Shape, actual: AbstractCurve, curves: List<Curve>): Boolean {
        val list = curves.filter {
            val s = it.computeShape()
            s.fill = null
            s.stroke = Color.BROWN

            !Shape.intersect(s, q).getLayoutBounds().isEmpty()
        }

        if (list.size != 1)
            return false

        return list.get(0).abstractCurve == actual
    }

    /**
     * Does curve segment [q] intersect with any other curves.
     */
    fun intersects(q: Shape, curves: List<Curve>): Boolean {
        val list = curves.filter {
            val s = it.computeShape()
            s.fill = null
            s.stroke = Color.BROWN

            !Shape.intersect(s, q).getLayoutBounds().isEmpty()
        }

        return list.isNotEmpty()
    }
}