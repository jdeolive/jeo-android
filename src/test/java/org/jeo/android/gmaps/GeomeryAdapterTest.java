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

import static org.junit.Assert.*;

import org.jeo.android.gmaps.GeometryAdapter;
import org.jeo.geom.Geom;
import org.junit.Before;
import org.junit.Test;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.PolygonOptions;
import com.google.android.gms.maps.model.PolylineOptions;

public class GeomeryAdapterTest {

    GeometryAdapter ga;

    @Before
    public void setUp() {
        ga = new GeometryAdapter();
    }

    @Test
    public void testPoint() {
        MarkerOptions opts = ga.adapt(Geom.point(0,0));
        assertNotNull(opts);

        assertEquals(new LatLng(0,0), opts.getPosition());
    }

    @Test
    public void testLineString() {
        PolylineOptions opts = ga.adapt(Geom.lineString(1,1, 2,2));
        assertNotNull(opts);

        assertEquals(2, opts.getPoints().size());
        assertEquals(new LatLng(1,1), opts.getPoints().get(0));
        assertEquals(new LatLng(2,2), opts.getPoints().get(1));
    }

    @Test
    public void testPolygon() {
        PolygonOptions opts = ga.adapt(Geom.polygon(1,1,2,2,3,3,1,1));
        assertNotNull(opts);

        assertEquals(4, opts.getPoints().size());
        assertEquals(new LatLng(1,1), opts.getPoints().get(0));
        assertEquals(new LatLng(2,2), opts.getPoints().get(1));
        assertEquals(new LatLng(3,3), opts.getPoints().get(2));
        assertEquals(new LatLng(1,1), opts.getPoints().get(3));
    }
}
