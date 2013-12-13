/* Copyright 2013 The jeo project. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
