
package org.igv.data;

import org.igv.feature.LocusScore;
import org.igv.track.DataType;
import org.igv.track.WindowFunction;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author jrobinso
 */
public interface DataSource {

    double getDataMax();

    double getDataMin();

    List<LocusScore> getSummaryScoresForRange(
            String chr,
            int startLocation,
            int endLocation,
            int zoom);

    /**
     * Returns unwindowed source values for exact aggregation into caller-defined bins.
     * Implementations with direct raw access should override this default to avoid mutating
     * their current display window function during the query.
     */
    default List<LocusScore> getRawScoresForRange(
            String chr, int startLocation, int endLocation, int zoom) {
        synchronized (this) {
            WindowFunction original = getWindowFunction();
            try {
                setWindowFunction(WindowFunction.none);
                return getSummaryScoresForRange(chr, startLocation, endLocation, zoom);
            } finally {
                setWindowFunction(original);
            }
        }
    }

    DataType getDataType();

    void setWindowFunction(WindowFunction statType);

    boolean isLogNormalized();

    WindowFunction getWindowFunction();

    default Collection<WindowFunction> getAvailableWindowFunctions() {
        return Collections.EMPTY_LIST;
    }

    default boolean isIndexable() {
        return true;
    }

    /**
     * Release any resources (file handles, etc)
     */
    void dispose();
}
