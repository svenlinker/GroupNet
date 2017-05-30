package icurves.gn

import icurves.description.AbstractBasicRegion
import icurves.description.AbstractCurve
import icurves.description.Description
import icurves.network.NetworkEdge
import java.util.HashSet

/**
 *
 *
 * @author Almas Baimagambetov (almaslvl@gmail.com)
 */
class GNDescription {


    val edges: List<NetworkEdge> = arrayListOf()

    companion object {
        @JvmStatic fun from(informalDescription: String, edgeDescription: String): Unit {

            // some basic ideas

            edgeDescription.split(",".toRegex())
                    .map { it.trim() }
                    .forEach {
                        it.split("-".toRegex()).forEach { println(it) }
                    }


//            val tmpZones = HashSet<AbstractBasicRegion>()
//            tmpZones.add(AbstractBasicRegion.OUTSIDE);
//
//            informalDescription.split(" +".toRegex())
//                    .map { it.map { AbstractCurve(it.toString()) } }
//                    .map { AbstractBasicRegion(it.toSet()) }
//                    .forEach { tmpZones.add(it) }
//
//            return Description(tmpZones.flatMap { it.inSet }.toSet(), tmpZones)
        }
    }
}