package icurves.description

import java.util.*

/**
 * A description, D = (K, B, l), of an Euler diagram.
 *
 * @author Almas Baimagambetov (almaslvl@gmail.com)
 */
data class Description(private val curvesInternal: Set<AbstractCurve>, private val zonesInternal: Set<AbstractBasicRegion>) {

    val curves: SortedSet<AbstractCurve>
    val zones: SortedSet<AbstractBasicRegion>

    init {
        curves = Collections.unmodifiableSortedSet(curvesInternal.toSortedSet())
        zones = Collections.unmodifiableSortedSet(zonesInternal.toSortedSet())
    }

    fun getNumZonesIn(curve: AbstractCurve) = zones.filter { it.contains(curve) }.count()

    fun includesLabel(label: String) = curves.contains(AbstractCurve(label))

    fun includesZone(zone: AbstractBasicRegion) = zones.contains(zone)

    fun getInformalDescription(): String {
        val sb = StringBuilder();
        for (zone in zones) {
            for ((label) in zone.inSet) {
                sb.append(label);
            }

            sb.append(" ");
        }

        return sb.toString().trim();
    }

    override fun toString() = zones.map { it.toString() }.joinToString(",")

    companion object {
        @JvmStatic fun from(informalDescription: String): Description {
            val tmpZones = HashSet<AbstractBasicRegion>()
            tmpZones.add(AbstractBasicRegion.OUTSIDE);

            informalDescription.split(" +".toRegex())
                    .map { it.map { AbstractCurve(it.toString()) } }
                    .map { AbstractBasicRegion(it.toSet()) }
                    .forEach { tmpZones.add(it) }

            return Description(tmpZones.flatMap { it.inSet }.toSet(), tmpZones)
        }
    }
}