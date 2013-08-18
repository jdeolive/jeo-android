package org.jeo.android.geopkg;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jeo.data.FileDriver;
import org.jeo.data.FileVectorDriver;
import org.jeo.data.VectorDriver;
import org.jeo.feature.Schema;

/**
 * Driver for the GeoPackage format, that utilizes Android SQLite capabilities. 
 * 
 * @author Justin Deoliveira, OpenGeo
 */
public class GeoPackage extends FileVectorDriver<GeoPkgWorkspace> {

    public static GeoPkgWorkspace open(File file) {
        return new GeoPkgWorkspace(file); 
    }

    @Override
    public String getName() {
        return "GeoPackage";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("gpkg", "geopkg");
    }

    @Override
    public Class<GeoPkgWorkspace> getType() {
        return GeoPkgWorkspace.class;
    }

    @Override
    public GeoPkgWorkspace open(File file, Map<?, Object> opts)
            throws IOException {
        return new GeoPkgWorkspace(file);
    }

    @Override
    protected GeoPkgWorkspace create(File file, Map<?, Object> opts, Schema schema)
            throws IOException {
        GeoPkgWorkspace ws = open(file, opts);
        ws.create(schema);
        return ws;
    }
}
