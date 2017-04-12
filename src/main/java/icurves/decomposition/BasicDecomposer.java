package icurves.decomposition;

import icurves.description.AbstractBasicRegion;
import icurves.description.AbstractCurve;
import icurves.description.Description;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

public class BasicDecomposer implements Decomposer {

    private static final Logger log = LogManager.getLogger(Decomposer.class);

    private final DecompositionStrategy strategy;

    BasicDecomposer(DecompositionStrategy strategy) {
        this.strategy = strategy;
    }

    @Override
    public List<DecompositionStep> decompose(Description ad) {
//        if (ad.getNumZones() <= 0) {
//            throw new IllegalArgumentException("Abstraction description is empty: " + ad.toString());
//        }

        List<DecompositionStep> result = new ArrayList<>();

        while (true) {
            List<AbstractCurve> toRemove = strategy.curvesToRemove(ad);

            // checking for null because of alphabetic decomposition
            // when description is empty it returns null
            // we probably don't even need alphabetic decomposition
            if (toRemove.isEmpty() || toRemove.contains(null)) {
                break;
            }

            for (AbstractCurve curveToRemove : toRemove) {
                DecompositionStep step = takeStep(ad, curveToRemove);
                result.add(step);
                ad = step.to();
            }
        }

        log.info("Decomposition begin");
        result.forEach(log::info);
        log.info("Decomposition end");

        return result;
    }

    private DecompositionStep takeStep(Description ad, AbstractCurve curve) {
        Set<AbstractCurve> contours = new TreeSet<>(ad.getCurves());
        contours.remove(curve);

        Set<AbstractBasicRegion> zones = new TreeSet<>();
        Map<AbstractBasicRegion, AbstractBasicRegion> zonesMoved = new TreeMap<>();

        for (AbstractBasicRegion zone : ad.getZones()) {
            AbstractBasicRegion newZone = zone.moveOutside(curve);
            zones.add(newZone);

            if (!zone.equals(newZone)) {
                zonesMoved.put(zone, newZone);
            }
        }

        Description targetAD = new Description(contours, zones);
        return new DecompositionStep(ad, targetAD, zonesMoved, curve);
    }
}
