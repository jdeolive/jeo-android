package org.jeo.android.geopkg;

import static java.lang.String.format;

import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

import org.jeo.android.geopkg.Entry.DataType;
import org.jeo.android.geopkg.geom.GeoPkgGeomWriter;
import org.jeo.data.Cursor.Mode;
import org.jeo.data.Cursors;
import org.jeo.data.DataRef;
import org.jeo.data.Dataset;
import org.jeo.data.Driver;
import org.jeo.data.FileData;
import org.jeo.data.Query;
import org.jeo.data.QueryPlan;
import org.jeo.data.TilePyramid;
import org.jeo.data.TilePyramidBuilder;
import org.jeo.data.VectorDataset;
import org.jeo.data.Workspace;
import org.jeo.feature.Feature;
import org.jeo.feature.Field;
import org.jeo.feature.Schema;
import org.jeo.feature.SchemaBuilder;
import org.jeo.filter.Filter;
import org.jeo.geom.Envelopes;
import org.jeo.geom.Geom;
import org.jeo.proj.Proj;
import org.jeo.sql.SQL;
import org.jeo.util.Key;
import org.osgeo.proj4j.CoordinateReferenceSystem;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

public class GeoPkgWorkspace implements Workspace, FileData {

    /** name of geopackage contents table */
    static final String GEOPACKAGE_CONTENTS = "geopackage_contents";
    
    /** name of geoemtry columns table */
    static final String GEOMETRY_COLUMNS = "geometry_columns";
    
    /** name of geoemtry columns table */
    static final String SPATIAL_REF_SYS = "spatial_ref_sys";
    
    /** name of tile metadata table */
    static final String TILE_TABLE_METADATA = "tile_table_metadata";
    
    /** name of tile matrix metadata table */
    static final String TILE_MATRIX_METADATA = "tile_matrix_metadata";

    File file;
    SQLiteDatabase db;

    GeoPkgGeomWriter geomWriter;
    
    public GeoPkgWorkspace(File file) {
        this.file = file;
        db = SQLiteDatabase.openOrCreateDatabase(file, null);
        geomWriter = new GeoPkgGeomWriter();
    } 

    @Override
    public Driver<?> getDriver() {
        return new GeoPackage();
    }

    @Override
    public Map<Key<?>, Object> getDriverOptions() {
        Map<Key<?>,Object> map = new HashMap<Key<?>, Object>();
        map.put(GeoPackage.FILE, file);
        return map;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public Iterable<DataRef<Dataset>> list() {
        Cursor c = 
            db.query(GEOPACKAGE_CONTENTS, new String[]{"table_name"}, null, null, null, null, null);
        try {
            List<DataRef<Dataset>> list = new ArrayList<DataRef<Dataset>>();
            while(c.moveToNext()) {
                list.add(new DataRef<Dataset>(c.getString(0), Dataset.class, getDriver(), this));
            }
            return list;
        }
        finally {
            c.close();
        }
    }
    
    @Override
    public Dataset get(String layer) throws IOException {
        FeatureEntry feature = feature(layer);
        if (feature != null) {
            return new GeoPkgVector(feature, this);
        }

        TileEntry tile = tile(layer);
        if (tile != null) {
            return new GeoPkgTileSet(tile, this);
        }

        return null;
    }
    
    @Override
    public VectorDataset create(Schema schema) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        if (db != null) {
           db.close();
           db = null;
        }
    }

    //
    // feature methods
    //
    Schema schema(FeatureEntry entry) {
        if (entry.getSchema() == null) {
            entry.setSchema(createSchema(entry));
        }
        return entry.getSchema();
    }

    long count(FeatureEntry entry, Query q) throws IOException {

        if (!Envelopes.isNull(q.getBounds())) {
            return Cursors.size(cursor(entry, q));
        }

        SQL sql = new SQL("SELECT count(*) FROM ").name(entry.getTableName());

        QueryPlan qp = new QueryPlan(q);
        encodeQuery(sql, q, qp);

        if (q.isFiltered() && !qp.isFiltered()) {
            return Cursors.size(cursor(entry, q));
        }

        Cursor c = db.rawQuery(log(sql.toString()), null);
        try {
            c.moveToNext();
            return c.getLong(0);
        }
        finally {
            c.close();
        }
    }

    org.jeo.data.Cursor<Feature> cursor(FeatureEntry entry, Query q) throws IOException {
        //TODO: selective attributes
        if (q.getMode() == Mode.APPEND) {
            return new FeatureAppendCursor(entry, this);
        }

        SQL sql = new SQL("SELECT * FROM ").name(entry.getTableName());

        QueryPlan qp = new QueryPlan(q);
        encodeQuery(sql, q, qp);

        return new FeatureCursor(db.rawQuery(log(sql.toString()), null), schema(entry));
    }

    void encodeQuery(SQL sql, Query q, QueryPlan qp) {
        if (!Filter.isTrueOrNull(q.getFilter())) {
            GeoPkgFilterSQLEncoder sqlfe = new GeoPkgFilterSQLEncoder();
            sqlfe.setPrepared(false);
            try {
                sql.add(" WHERE ").add(sqlfe.encode(q.getFilter(), null));
                qp.filtered();
            }
            catch(Exception e) {
                Log.d("geopkg", "Unable to natively encode filter: " + q.getFilter(), e);
            }
        }

        if (q.getLimit() != null) {
            sql.add(" LIMIT ").add(q.getLimit());
            qp.offsetted();
        }
        if (q.getOffset() != null) {
            sql.add(" OFFSET ").add(q.getOffset());
            qp.limited();
        }
    }

    Schema createSchema(FeatureEntry entry) {
        log(format("SELECT * FROM %s LIMIT 1", entry.getTableName()));

        Cursor c = db.query(entry.getTableName(), null, null, null, null, null, null, "1");
        boolean data = c.moveToNext();

        SchemaBuilder sb = Schema.build(entry.getTableName());
        for (int i = 0; i < c.getColumnCount(); i++) {
            String col = c.getColumnName(i);
            if (col.equals(entry.getGeometryColumn())) {
                CoordinateReferenceSystem crs = entry.getSrid() != null ? 
                    Proj.crs(entry.getSrid()) : null;
                sb.field(col, entry.getGeometryType().getType(), crs);
            }
            else {
                Class type = String.class;
                if (data) {
                    // can only access type info if we actually have data
                    switch(c.getType(i)) {
                    case Cursor.FIELD_TYPE_INTEGER:
                        type = Integer.class;
                        break;
                    case Cursor.FIELD_TYPE_FLOAT:
                        type = Double.class;
                        break;
                    case Cursor.FIELD_TYPE_BLOB:
                        type = byte[].class;
                        break;
                    }
                }
                
                sb.field(col, type);
            }
        }
        return sb.schema();
    }

    FeatureEntry feature(String name) {
        String sql = String.format(
            "SELECT a.*, b.f_geometry_column, b.geometry_type, b.coord_dimension" +
                    " FROM %s a, %s b" + 
                   " WHERE a.table_name = b.f_table_name" +
                     " AND a.table_name = ?" + 
                     " AND a.data_type = ?", 
                 GEOPACKAGE_CONTENTS, GEOMETRY_COLUMNS);

        String[] args = new String[]{name, DataType.Feature.value()};
        log(sql, (Object[]) args);

        Cursor c = db.rawQuery(sql, args);
        if (c.moveToNext()) {
            FeatureEntry e = new FeatureEntry();

            initEntry(e, c);
            e.setGeometryColumn(c.getString(10));
            e.setGeometryType(Geom.Type.from(c.getString(11)));
            e.setCoordDimension((c.getInt(12)));
            return e;
        }

        return null;
    }

    void insert(final FeatureEntry entry, final Feature feature) throws IOException {
        
        ContentValues values = new ContentValues();
        for (Field fld : schema(entry)) {
            String col = fld.getName();
            Object obj = feature.get(col);
            if (obj != null) {
                if (obj instanceof Geometry) {
                    values.put(col, geomWriter.write((Geometry)obj));
                }
                else {
                    if (obj instanceof Byte || obj instanceof Short || obj instanceof Integer) {
                        values.put(col, ((Number)obj).intValue());
                    }
                    if (obj instanceof Long) {
                        values.put(col, ((Number)obj).longValue());
                    }
                    if (obj instanceof Float || obj instanceof Double) {
                        values.put(col, ((Number)obj).doubleValue());
                    }
                    else {
                        values.put(col, obj.toString());
                    }
                }
            }
        }

        db.insert(entry.getTableName(), null, values);
    }

    //
    // tile methods
    //
    TileEntry tile(String name) {
        String sql = format(
            "SELECT a.*, b.is_times_two_zoom" +
             " FROM %s a, %s b" + 
            " WHERE a.table_name = ?" + 
              " AND a.data_type = ?", GEOPACKAGE_CONTENTS, TILE_TABLE_METADATA);


        String[] args = new String[]{name, DataType.Tile.value()};
        log(sql, (Object[]) args);

        Cursor c = db.rawQuery(sql, args);
        try {
            if (c.moveToNext()) {
                TileEntry e = new TileEntry();
    
                initEntry(e, c);
                e.setTimesTwoZoom(c.getInt(10) == 1);
    
                sql = format("SELECT * FROM %s WHERE t_table_name = ? ORDER BY zoom_level", 
                    TILE_MATRIX_METADATA);
                args = new String[]{name};

                log(sql, (Object[])args);

                Cursor d = db.rawQuery(sql,  args);
                try {
                    TilePyramidBuilder tpb = TilePyramid.build();
                    if (d.moveToNext()) {
                        tpb.tileSize(d.getInt(4), d.getInt(5));

                        do {
                            tpb.grid(d.getInt(1), d.getInt(2), d.getInt(3));
                        }
                        while(d.moveToNext());
                    }

                    e.setTilePyramid(tpb.pyramid());
                }
                finally {
                    d.close();
                }
                return e;
            }
        }
        finally {
            c.close();
        }
   
        return null;
    }

    TileCursor cursor(TileEntry entry, Long z1, Long z2, Long x1, Long x2, Long y1, 
        Long y2) {
        
        final List<String> q = new ArrayList<String>();
        if (z1 != null && z1 > -1) {
            q.add("zoom_level >= " + z1);
        }
        if (z2 != null && z2 > -1) {
            q.add("zoom_level <= " + z2);
        }
        if (x1 != null && x1 > -1) {
            q.add("tile_column >= " + x1);
        }
        if (x2 != null && x2 > -1) {
            q.add("tile_column <= " + x2);
        }
        if (y1 != null && y1 > -1) {
            q.add("tile_row >= " + y1);
        }
        if (y2 != null && y2 > -1) {
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
        Cursor c = db.query(entry.getTableName(), new String[]{"zoom_level", "tile_column", 
            "tile_row", "tile_data" }, where.toString(), null, null, null, null);
        return new TileCursor(c);
    }

    //
    // common utility methods
    //

    void initEntry(Entry e, Cursor c) {
        e.setTableName(c.getString(0));
        e.setIdentifier(c.getString(2));
        e.setDescription(c.getString(3));
        e.setLastChange(c.getString(4));

        e.setBounds(new Envelope(
            c.getDouble(5), c.getDouble(7), c.getDouble(6), c.getDouble(8)));
        e.setSrid(c.getInt(9));
    }

    String log(String sql, Object... params) {
        if (Log.isLoggable("geopkg", Log.DEBUG)) {
            if (params.length == 1 && params[0] instanceof Collection) {
                params = ((Collection)params[0]).toArray(); 
            }

            StringBuilder log = new StringBuilder(sql);
            if (params.length > 0) {
                log.append("; ");
                for (Object p : params) {
                    log.append(p).append(", ");
                }
                log.setLength(log.length()-2);
            }
            Log.d("geopkg", log.toString());
        }
        return sql;
    }

}
