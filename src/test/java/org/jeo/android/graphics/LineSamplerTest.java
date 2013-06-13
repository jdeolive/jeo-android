package org.jeo.android.graphics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.jeo.geom.Geom;
import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;

public class LineSamplerTest {

    @Test
    public void testSample() {
        LineString line = Geom.lineString(0,0, 10,10, 20,20);

        LineSampler sampler = new LineSampler(line);

        assertCoordinate(sampler.sample(), 0, 0);

        sampler.advance(Math.sqrt(2));
        assertCoordinate(sampler.sample(), 1, 1);

        sampler.advance(line.getLength()-Math.sqrt(2));
        assertCoordinate(sampler.sample(), 20,20);

        sampler.advance(1);
        assertNull(sampler.sample());
        //assertNull(sampler.sample(line.getLength()+10));
    }

    void assertCoordinate(Coordinate c, double x, double y) {
        assertEquals(x, c.x, 0.1);
        assertEquals(y, c.y, 0.1);
    }
}
