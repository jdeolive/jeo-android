package org.jeo.android.gmaps;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.model.LatLngBounds;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

/**
 * Android google maps utility class.
 * 
 * @author Justin Deoliveira, OpenGeo
 */
public class GMaps {

    /**
     * Returns the bounds of a google map.
     */
    public static Envelope bounds(GoogleMap map) {
        LatLngBounds llBounds = map.getProjection().getVisibleRegion().latLngBounds;
        return new GeometryAdapter().adapt(llBounds);
    }

    /**
     * Adapts a JTS geometry object to a google maps geometry.  
     */
    public static void adapt(Geometry g) {
        new GeometryAdapter().adapt(g);
    }

    /**
     * Adapts a JTS envelope object to a google maps bounds.  
     */
    public static LatLngBounds adapt(Envelope e) {
        return new GeometryAdapter().adapt(e);
    }
}
