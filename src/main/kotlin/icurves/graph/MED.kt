package icurves.graph

import icurves.CurvesApp
import icurves.algorithm.EdgeRouter
import icurves.description.AbstractBasicRegion
import icurves.description.AbstractCurve
import icurves.diagram.BasicRegion
import icurves.diagram.Curve
import icurves.graph.cycles.CycleFinder
import icurves.guifx.SettingsController
import icurves.util.Converter
import icurves.util.Profiler
import javafx.geometry.Point2D
import javafx.scene.paint.Color
import javafx.scene.shape.*
import math.geom2d.polygon.SimplePolygon2D
import org.apache.logging.log4j.LogManager
import java.util.*
import java.util.stream.Collectors
import java.util.stream.Stream

/**
 * Modified Euler dual.
 *
 * @author Almas Baimagambetov (almaslvl@gmail.com)
 */
@Suppress("UNCHECKED_CAST")
class MED(val allBasicRegions: List<BasicRegion>, private val allContours: Map<AbstractCurve, Curve>) {

    private val log = LogManager.getLogger(javaClass)

    lateinit var nodes: MutableList<EulerDualNode>
    lateinit var edges: MutableList<EulerDualEdge>

    private val settings: SettingsController

    init {
        settings = CurvesApp.getInstance().settings

        computeEGD()

        computeMED()
    }

    private fun computeEGD() {
        Profiler.start("Creating EGD nodes")
        nodes = computeEGDNodes()
        Profiler.end("Creating EGD nodes")

        Profiler.start("Creating EGD edges")
        edges = computeEGDEdges()
        Profiler.end("Creating EGD edges")
    }

    private fun computeMED() {
        val (center, radius) = computeMEDRing()

        Profiler.start("Creating MED nodes")
        val nodesMED = computeMEDNodes(center, radius)

        // add the adjacent edges between outside and inside
        val outside = BasicRegion(AbstractBasicRegion.OUTSIDE, allContours)

        nodes.filter { it.zone.isTopologicallyAdjacent(outside) }
                .forEach { node ->
                    val closestMEDNode = nodesMED.sortedBy { it.point.distance(node.point) }.first()

                    edges.add(EulerDualEdge(node, closestMEDNode,
                            Line(node.point.x, node.point.y, closestMEDNode.point.x, closestMEDNode.point.y)))
                }

        // then add nodesMED to nodes
        nodes.addAll(nodesMED)
        Profiler.end("Creating MED nodes")

        Profiler.start("Creating MED edges")
        computeMEDRingEdges(nodesMED, center)
        Profiler.end("Creating MED edges")
    }

    /**
     * @return MED ring center, radius
     */
    private fun computeMEDRing(): Pair<Point2D, Double> {
        val bounds = allBasicRegions.map { it.getPolygonShape().boundingBox() }

        val minX = bounds.map { it.minX }.min()
        val minY = bounds.map { it.minY }.min()
        val maxX = bounds.map { it.maxX }.max()
        val maxY = bounds.map { it.maxY }.max()

        val center = Point2D((minX!! + maxX!!) / 2, (minY!! + maxY!!) / 2)

        val w = (maxX - minX)
        val h = (maxY - minY)

        // half diagonal of the bounds rectangle + distance between diagram and MED
        val radius = Math.sqrt(w*w + h*h) / 2 + settings.medSize

        return center.to(radius)
    }

    private fun computeEGDNodes(): MutableList<EulerDualNode> {
        return if (settings.isParallel) computeNodesParallel() else computeNodesSequential()
    }

    private fun computeNodesSequential(): MutableList<EulerDualNode> {
        return allBasicRegions.map { createNode(it) }.toMutableList()
    }

    private fun computeNodesParallel(): MutableList<EulerDualNode> {
        return Stream.of(*allBasicRegions.toTypedArray())
                .parallel()
                .map { createNode(it) }
                .collect(Collectors.toList()) as MutableList<EulerDualNode>
    }

    /**
     * Computes EGD edges based on given pairs of nodes.
     * An edge is constructed if basic regions of nodes are topologically adjacent.
     * Runs in parallel mode based on settings.
     */
    private fun computeEGDEdges(): MutableList<EulerDualEdge> {
        log.trace("Computing EGD edges")

        // go through each pair of nodes
        val pairs = ArrayList< Pair<EulerDualNode, EulerDualNode> >()

        for (i in nodes.indices) {
            var j = i + 1
            while (j < nodes.size) {
                val node1 = nodes[i]
                val node2 = nodes[j]

                pairs.add(node1.to(node2))

                j++
            }
        }

        var stream = Stream.of(*pairs.toTypedArray())

        if (settings.isParallel) {
            stream = stream.parallel()
        }

        return stream.filter { it.first.zone.isTopologicallyAdjacent(it.second.zone) }
                .map { createEdge(it.first, it.second) }
                .collect(Collectors.toList()) as MutableList<EulerDualEdge>
    }

    private fun createNode(zone: BasicRegion): EulerDualNode {
        return EulerDualNode(zone, zone.center)
    }

    /**
     * Creates an Euler dual edge between [node1] and [node2] represented by a polyline.
     */
    private fun createEdge(node1: EulerDualNode, node2: EulerDualNode): EulerDualEdge {
        log.trace("Creating edge: ${node1.zone} - ${node2.zone}")

        val p1 = node1.zone.center
        val p2 = node2.zone.center

        val line = Line(p1.x, p1.y, p2.x, p2.y)

        // the new curve segment must pass through the straddled curve
        // and only through that curve
        val curve = node1.zone.abRegion.getStraddledContour(node2.zone.abRegion).get()

        log.trace("Searching ${node1.zone} - ${node2.zone} : $curve")

        if (!isOK(line, curve, allContours.values.toList())) {
            val poly = EdgeRouter.route(node1.zone, node2.zone)

            if (poly.points.size == 4) {
                throw RuntimeException("Failed to route edge: ${node1.zone} - ${node2.zone}")
            }

            val points = arrayListOf<Double>()

            // shorten vertices by i values
            var i = 0
            while (i < poly.points.size - 2) {
                points.add(poly.points[i])
                points.add(poly.points[i+1])

                i += 32
            }

            points.addAll(poly.points.takeLast(2))

            val newPoly = Polyline(*points.toDoubleArray())

            settings.globalMap["astar"] = newPoly

            return EulerDualEdge(node1, node2, newPoly)
        }

        return EulerDualEdge(node1, node2, line)
    }

    /**
     * Does curve segment [q] only pass through [actual] curve.
     */
    private fun isOK(q: Shape, actual: AbstractCurve, curves: List<Curve>): Boolean {
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

    private fun computeMEDNodes(center: Point2D, radius: Double): List<EulerDualNode> {
        log.trace("Computing MED nodes")

        val polygonMED = Converter.toPolygon2D(Converter.makePolygon(radius.toInt(), 16))

        val firstPt = Point2D(center.x - radius, center.y)
        val vector = firstPt.subtract(polygonMED.vertex(0).x(), polygonMED.vertex(0).y())

        // make "distinct" nodes so that jgrapht doesn't think it's a loop
        // TODO: it shouldn't since we also check points, which ARE different
        return polygonMED.vertices().map { EulerDualNode(BasicRegion(AbstractBasicRegion.OUTSIDE, allContours), Point2D(it.x(), it.y()).add(vector)) }
    }

    /**
     * @param nodesMED nodes of MED placed in the outside zone
     * @param center center of the MED bounding circle
     */
    private fun computeMEDRingEdges(nodesMED: List<EulerDualNode>, center: Point2D) {
        // sort nodes along the MED ring
        // sorting is CCW from 0 (right) to 360
        Collections.sort(nodesMED, { node1, node2 ->
            val v1 = node1.point.subtract(center)
            val angle1 = vectorToAngle(v1)

            val v2 = node2.point.subtract(center)
            val angle2 = vectorToAngle(v2)

            (angle1 - angle2).toInt()
        })

        for (i in nodesMED.indices) {
            val node1 = nodesMED[i]
            val node2 = if (i == nodesMED.size - 1) nodesMED[0] else nodesMED[i+1]

            val p1 = node1.point
            val p2 = node2.point

            edges.add(EulerDualEdge(node1, node2, Line(p1.x, p1.y, p2.x, p2.y)))
        }
    }

    /**
     * A cycle is valid if it can be used to embed a curve.
     */
    private fun isValid(cycle: GraphCycle<EulerDualNode, EulerDualEdge>): Boolean {
        log.trace("Checking cycle: $cycle")

        // this ensures that we do not allow same vertices in the cycle
        // unless it's the outside vertex
        cycle.nodes.groupBy { it.zone.abRegion.toString() }.forEach {
            if (it.key != "{}" && it.value.size > 1) {
                log.trace("Discarding cycle because ${it.key} is present ${it.value.size} times")
                return false
            }
        }

        cycle.smoothingData = arrayListOf()

        val path = Path()
        val moveTo = MoveTo(cycle.nodes[0].point.x, cycle.nodes[0].point.y)
        path.elements.addAll(moveTo)

        var tmpPoint = cycle.nodes[0].point

        // add the first point (move to)
        cycle.smoothingData.add(tmpPoint)

        cycle.edges.map { it.curve }.forEach { q ->

            when(q) {

                is Line -> {
                    val lineTo = LineTo()

                    // we do this coz source and end vertex might be swapped
                    if (tmpPoint == Point2D(q.startX, q.startY)) {
                        lineTo.x = q.endX
                        lineTo.y = q.endY
                    } else {
                        lineTo.x = q.startX
                        lineTo.y = q.startY
                    }

                    tmpPoint = Point2D(lineTo.x, lineTo.y)

                    path.elements.add(lineTo)
                    cycle.smoothingData.add(tmpPoint)
                }

                is Polyline -> {

                    val start = Point2D(q.points[0], q.points[1])
                    val end = Point2D(q.points[q.points.size-2], q.points[q.points.size-1])

                    val normalOrder: Boolean

                    // we do this coz source and end vertex might be swapped
                    if (tmpPoint == start) {
                        normalOrder = true
                    } else {
                        normalOrder = false
                    }

                    tmpPoint = end

                    if (normalOrder) {
                        var i = 2
                        while (i < q.points.size) {

                            val point = Point2D(q.points[i], q.points[++i])
                            val lineTo = LineTo(point.x, point.y)

                            path.elements.add(lineTo)
                            cycle.smoothingData.add(point)

                            i++
                        }
                    } else {
                        var i = q.points.size-3
                        while (i > 0) {

                            val point = Point2D(q.points[i-1], q.points[i])
                            val lineTo = LineTo(point.x, point.y)

                            path.elements.add(lineTo)
                            cycle.smoothingData.add(point)

                            i -= 2
                        }
                    }
                }

                else -> {
                    throw IllegalArgumentException("Unknown edge shape: $q")
                }
            }
        }

        // TODO: we can use this polygon to check inside vertex?

//        17:09:38.474 [Diagram Creation Thread] INFO  icurves.graph.MED - Found cycles: 183496
//        Computing all cycles took: 33.697 sec
//        17:10:10.744 [Diagram Creation Thread] INFO  icurves.graph.MED - Valid cycles: 26746
//        Diagram creation took: 34.514 sec
        //val polygon = SimplePolygon2D(cycle.smoothingData.map { math.geom2d.Point2D(it.x, it.y) })

        // drop last duplicate of first moveTO
        cycle.smoothingData.removeAt(cycle.smoothingData.size - 1)



        path.elements.add(ClosePath())
        path.fill = Color.TRANSPARENT

        cycle.path = path

        // we filter those vertices that are not part of the cycle
        // then we check if filtered vertices are inside the cycle
        nodes.filter {

            // we do not need to check for ouside zone right?
            !cycle.contains(it)
            // fails for some reason
            //!cycle.nodes.contains(it)

        }.forEach {

            log.trace("Checking vertex $it")

            //if (polygon.contains(it.point.x, it.point.y)) {
            if (path.contains(it.point)) {
                log.trace("Discarding cycle because of inside vertex: ${it.point}")
                return false
            }
        }

        log.trace("Cycle is valid")
        return true
    }

    /**
     * Enumerate all simple cycles.
     */
    private fun enumerateCycles(): List<GraphCycle<EulerDualNode, EulerDualEdge>> {
        val graph = CycleFinder<EulerDualNode, EulerDualEdge>(EulerDualEdge::class.java)
        nodes.forEach { graph.addVertex(it) }
        edges.forEach { graph.addEdge(it.v1, it.v2, it) }

        return graph.computeCycles()
    }

    fun computeCycle(zonesToSplit: List<AbstractBasicRegion>): GraphCycle<EulerDualNode, EulerDualEdge>? {
        log.trace("Computing cycle for $zonesToSplit")

        Profiler.start("Enumerating cycles")
        val cycles = enumerateCycles()
        Profiler.end("Enumerating cycles")

        log.info("Found cycles: ${cycles.size}")

        //        check that cycle nodes are equal or superset of what is required        and is valid
        return cycles.find { it.nodes.map { it.zone.abRegion }.containsAll(zonesToSplit) && isValid(it) }
    }

    /**
     * @return angle in [0..360]
     */
    private fun vectorToAngle(v: Point2D): Double {
        var angle = -Math.toDegrees(Math.atan2(v.y, v.x))

        if (angle < 0) {
            val delta = 180 - (-angle)
            angle = delta + 180
        }

        return angle
    }
}