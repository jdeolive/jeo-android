package org.jeo.android.mbtiles;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.jeo.data.Cursor;
import org.jeo.data.Driver;
import org.jeo.data.FileData;
import org.jeo.data.Tile;
import org.jeo.data.TilePyramid;
import org.jeo.data.TilePyramidBuilder;
import org.jeo.data.TileSet;
import org.jeo.geom.Envelopes;
import org.jeo.proj.Proj;
import org.jeo.util.Key;
import org.jeo.util.Util;
import org.osgeo.proj4j.CoordinateReferenceSystem;

import android.database.sqlite.SQLiteDatabase;

import com.vividsolutions.jts.geom.Envelope;

public class MBTileSet implements TileSet, FileData {

    static final String METADATA = "metadata";
    static final String TILES = "tiles";

    static final String PNG = "image/png";
    static final String JPEG = "image/jpeg";

    File file;
    SQLiteDatabase db;

    String tileFormat;

    public MBTileSet(File file) {
        this.file = file;
        db = SQLiteDatabase.openOrCreateDatabase(file, null);

        tileFormat = queryForTileFormat();
    }

    public String getTileFormat() {
        return tileFormat;
    }

    String queryForTileFormat() {
        android.database.Cursor c = db.query(METADATA, new String[]{"value"}, "name = ?", 
            new String[]{"format"}, null, null, null);
        try {
            if (c.moveToNext()) {
                String format = c.getString(0);
                return "jpg".equalsIgnoreCase(format) || JPEG.equalsIgnoreCase(format) ? JPEG : PNG;
            }
        }
        finally {
            c.close();
        }

        return PNG;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public Driver<?> getDriver() {
        return new MBTiles();
    }

    @Override
    public Map<Key<?>, Object> getDriverOptions() {
        return (Map) Collections.singletonMap(MBTiles.FILE, file);
    }

    @Override
    public String getName() {
        return Util.base(file.getName());
    }

    @Override
    public String getTitle() {
        android.database.Cursor c = db.query(METADATA, new String[]{"value"}, "name = ?", 
                new String[]{"name"}, null, null, null);
        try {
            if (c.moveToNext()) {
                return c.getString(0);
            }
        }
        finally {
            c.close();
        }

        return null;
    }

    @Override
    public String getDescription() {
        android.database.Cursor c = db.query(METADATA, new String[]{"value"}, "name = ?", 
                new String[]{"description"}, null, null, null);
        try {
            if (c.moveToNext()) {
                return c.getString(0);
            }
        }
        finally {
            c.close();
        }

        return null;
    }

    @Override
    public CoordinateReferenceSystem getCRS() throws IOException {
        return Proj.EPSG_900913;
    }

    @Override
    public Envelope bounds() throws IOException {
        android.database.Cursor c = db.query(METADATA, new String[]{"value"}, "name = ?", 
            new String[]{"bounds"}, null, null, null);
        try {
            if (c.moveToFirst()) {
                Envelope b = Envelopes.parse(c.getString(0));

                // bounds specified in wgs84
                return Proj.reproject(b, Proj.EPSG_4326, getCRS());
            }
        }
        finally {
            c.close();
        }

        // fall back to bounds of crs
        return Proj.bounds(getCRS());
    }
    
    @Override
    public TilePyramid getPyramid() throws IOException {
        TilePyramidBuilder tpb = TilePyramid.build();
        tpb.bounds(Proj.bounds(getCRS()));

        android.database.Cursor c = db.query(TILES, new String[]{"zoom_level"}, null, null, 
            "zoom_level", null, "zoom_level");
        try {
            while(c.moveToNext()) {
                int z = c.getInt(0);
                int d = (int) Math.pow(2, z);
                tpb.grid(z, d, d); 
            }
        }
        finally {
            c.close();
        }

        return tpb.pyramid();
    }
    
    @Override
    public Tile read(long z, long x, long y) throws IOException {
        String sql = String.format("SELECT tile_data FROM %s WHERE zoom_level = %d " +
            "AND tile_column = %d AND tile_row = %d", TILES, z, x, y);
        android.database.Cursor c = db.rawQuery(sql, null);
        try {
            if (c.moveToNext()) {
                return new Tile((int)z, (int)x, (int)y, c.getBlob(0), tileFormat);
            }
        }
        finally {
            c.close();
        }

        return null;
    }
    
    @Override
    public Cursor<Tile> read(long z1, long z2, long x1, long x2, long y1, long y2) throws IOException {
        final List<String> q = new ArrayList<String>();
        
        if (z1 > -1) {
            q.add("zoom_level >= " + z1);
        }
        if (z2 > -1) {
            q.add("zoom_level <= " + z2);
        }
        if (x1 > -1) {
            q.add("tile_column >= " + x1);
        }
        if (x2 > -1) {
            q.add("tile_column <= " + x2);
        }
        if (y1 > -1) {
            q.add("tile_row >= " + y1);
        }
        if (y2 > -1) {
            q.add("tile_row <= " + y2);
        }

        StringBuilder where = new StringBuilder();
        if (!q.isEmpty()) {
            for (String s : q) {
                where.append(s).append(" AND ");
            }
            where.setLength(where.length()-5);
        }

        //TODO: sort
        android.database.Cursor c = db.query(TILES, new String[]{"zoom_level", "tile_column", 
            "tile_row", "tile_data" }, where.toString(), null, null, null, null);
        return new TileCursor(c, this);
    }

    
    @Override
    public void close() {
        if (db != null) {
            db.close();
        }
        db = null;
    }
}
