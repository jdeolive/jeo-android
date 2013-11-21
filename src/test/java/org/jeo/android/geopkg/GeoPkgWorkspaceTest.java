package org.jeo.android.geopkg;

import org.junit.Test;
import static org.junit.Assert.*;

public class GeoPkgWorkspaceTest {

    @Test
    public void testPrimaryKeyRegex() {
        String parsed = GeoPkgWorkspace.parsePrimaryKeyColumn("CREATE TABLE \"urban_areas\" ( \"fid\" INTEGER PRIMARY KEY, \"the_geom\" BLOB, \"scalerank\" INTEGER, \"featurecla\" TEXT, \"area_sqkm\" REAL )");
        assertEquals("fid", parsed);
    }

}
