package org.jeo.android.graphics;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.jeo.geom.Geom;
import org.junit.Test;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineSegment;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.io.WKTReader;

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

       @Test
    public void testFoo() throws Exception {
        LineString line = (LineString) new WKTReader().read("LINESTRING (-107.001103197473 -35.3044510718099, -103.673277707928 -36.0988332360532, -100.181598246947 -35.6000218844845, -94.1958620281213 -35.3506162087001, -85.2172576998832 -36.597644587622, -78.7327101294891 -34.8518048571313, -67.758860394976 -30.3625026930123, -59.1269043379229 -28.6751901305161)");
        double[] widths = new double[]{2.8784332, 1.0280151, 2.2616272, 2.2616272, 2.056015, 1.0280151, 2.672821, 2.2616272, 2.2616272, 1.439209, 1.2336121};

        LineSampler sampler = new LineSampler(line);
        List<LineSegment> path = new ArrayList<LineSegment>();

        for (int i = 0; i < widths.length; i++) {
            // get next point
            Coordinate c1 = sampler.sample();
            sampler.advance(widths[i]);
            Coordinate c2 = sampler.sample();

            path.add(new LineSegment(c1,c2));
            sampler.advance(0.1);
            
        }

        for (int i = 0; i < widths.length; i++) {
            System.out.println(String.format("width = %f, length = %f", widths[i], path.get(i).getLength()));
        }
    }

    void assertCoordinate(Coordinate c, double x, double y) {
        assertEquals(x, c.x, 0.1);
        assertEquals(y, c.y, 0.1);
    }
}
