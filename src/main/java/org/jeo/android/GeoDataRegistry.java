package org.jeo.android;

import java.io.File;
import java.io.IOException;

import org.jeo.android.geopkg.GeoPackage;
import org.jeo.android.mbtiles.MBTiles;
import org.jeo.data.DataRef;
import org.jeo.data.DirectoryRegistry;
import org.jeo.data.DriverRegistry;
import org.jeo.data.JSONRegistry;
import org.jeo.data.Registry;
import org.jeo.data.StaticDriverRegistry;
import org.jeo.data.mem.Memory;
import org.jeo.geojson.GeoJSON;

import android.os.Environment;

/**
 * Data registry that exposes data from a directory.
 * <p>
 * If not overridden by the user the default directory is named "GeoData" on the root of the SDCard
 * of the device, obtained from {@link Environment#getExternalStorageDirectory()}. 
 * </p>
 * <p>
 * The registry will operate in one of two modes. If the specified data directory contains an 
 * <tt>index.json</tt> file it will operate as {@link JSONRegistry}. Otherwise it will simply scan 
 * the directory contains as {@link DirectoryRegistry}.
 * </p>
 * @author Justin Deoliveira, OpenGeo
 */
public class GeoDataRegistry implements Registry {

    static DriverRegistry DRIVERS = 
        new StaticDriverRegistry(new GeoPackage(), new MBTiles(), new GeoJSON(), new Memory());

    Registry delegate;

    /**
     * Returns the GeoData directory handle.
     */
    public static File directory() {
        return new File(Environment.getExternalStorageDirectory(), "GeoData");
    }

    public GeoDataRegistry() {
        this(null); 
    }

    public GeoDataRegistry(DriverRegistry drivers) {
        this(null, drivers);
    }

    public GeoDataRegistry(File dir, DriverRegistry drivers) {
        if (dir == null) {
            dir = directory();
        }

        if (drivers == null) {
            drivers = DRIVERS;
        }

        if (!dir.isDirectory()) {
            throw new IllegalArgumentException("not a directory: " + dir.getPath());
        }

        File index = new File(dir, "index.json");
        if (index.exists()) {
            delegate = new JSONRegistry(index, DRIVERS);
        }
        else {
            delegate = new DirectoryRegistry(dir, DRIVERS);
        }
    }

    @Override
    public Iterable<DataRef<?>> list() throws IOException {
        return delegate.list();
    }

    @Override
    public Object get(String name) throws IOException {
        return delegate.get(name);
    }

    @Override
    public void close() {
        delegate.close();
    }
}
