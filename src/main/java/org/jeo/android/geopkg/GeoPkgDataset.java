package org.jeo.android.geopkg;

import java.io.IOException;
import java.util.Map;

import org.jeo.data.Dataset;
import org.jeo.data.Driver;
import org.jeo.proj.Proj;
import org.jeo.util.Key;
import org.osgeo.proj4j.CoordinateReferenceSystem;

import com.vividsolutions.jts.geom.Envelope;

public class GeoPkgDataset<T extends Entry> implements Dataset {

    T entry;
    GeoPkgWorkspace geopkg;

    protected GeoPkgDataset(T entry, GeoPkgWorkspace geopkg) {
        this.entry = entry;
        this.geopkg = geopkg;
    }

    @Override
    public Driver<?> getDriver() {
        return geopkg.getDriver();
    }

    @Override
    public Map<Key<?>, Object> getDriverOptions() {
        return geopkg.getDriverOptions();
    }

    @Override
    public String getName() {
        return entry.getTableName();
    }

    @Override
    public String getTitle() {
        return entry.getIdentifier();
    }

    @Override
    public String getDescription() {
        return entry.getDescription();
    }
    
    @Override
    public CoordinateReferenceSystem getCRS() throws IOException {
        if (entry.getSrid() != null) {
            return Proj.crs(entry.getSrid());
        }
        return null;
    }
    
    @Override
    public Envelope bounds() throws IOException {
        return entry.getBounds();
    }

    @Override
    public void close() {
    }

}
