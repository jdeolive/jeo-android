package org.jeo.android;

import java.io.File;

import org.jeo.android.geopkg.GeoPackage;
import org.jeo.android.mbtiles.MBTiles;
import org.jeo.data.DirectoryRegistry;
import org.jeo.data.DriverRegistry;

import org.jeo.data.StaticDriverRegistry;
import org.jeo.data.mem.Memory;
import org.jeo.geojson.GeoJSON;

import android.os.Environment;

/**
 * Data registry that exposes data from a directory named "GeoData" on the SDCard 
 * of the device, obtained from {@link Environment#getExternalStorageDirectory()}.
 * 
 * @author Justin Deoliveira, OpenGeo
 */
public class GeoDataRegistry extends DirectoryRegistry {

    static DriverRegistry DRIVERS = 
        new StaticDriverRegistry(new GeoPackage(), new MBTiles(), new GeoJSON(), new Memory());

    /**
     * Returns the GeoData directory handle.
     */
    public static File directory() {
        return new File(Environment.getExternalStorageDirectory(), "GeoData");
    }

    public GeoDataRegistry() {
        this(DRIVERS); 
    }

    public GeoDataRegistry(DriverRegistry drivers) {
        super(directory(), drivers);
    }
}
