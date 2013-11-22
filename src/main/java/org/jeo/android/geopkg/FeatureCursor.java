package org.jeo.android.geopkg;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.jeo.android.geopkg.geom.GeoPkgGeomReader;
import org.jeo.data.Cursor;
import org.jeo.feature.BasicFeature;
import org.jeo.feature.Feature;
import org.jeo.feature.Field;
import org.jeo.feature.Schema;

import com.vividsolutions.jts.geom.Geometry;
import org.jeo.sql.PrimaryKey;
import org.jeo.sql.PrimaryKeyColumn;

public class FeatureCursor extends Cursor<Feature> {

    android.database.Cursor cursor;
    final Schema schema;
    final PrimaryKey primaryKey;
    Boolean next = null;
    final GeoPkgGeomReader geomReader;
    Feature feature;
    final GeoPkgWorkspace workspace;
    final FeatureEntry entry;

    FeatureCursor(Mode mode, android.database.Cursor cursor, FeatureEntry entry, GeoPkgWorkspace workspace)
        throws IOException {
        super(mode);
        this.cursor = cursor;
        this.entry = entry;
        this.schema = workspace.schema(entry);
        this.primaryKey = workspace.primaryKey(entry);
        this.workspace = workspace;
        geomReader = new GeoPkgGeomReader();
    }

    @Override
    public boolean hasNext() throws IOException {
        if (next == null) {
            next = cursor.moveToNext();
        }
        return next;
    }
    
    @Override
    public Feature next() throws IOException {
        try {
            if (next != null && next.booleanValue()) {
                List<Field> fields = schema.getFields();
                List<Object> values = new ArrayList<Object>();
    
                for (int i = 0; i < fields.size(); i++) {
                    Field fld = fields.get(i);
                    Class t = fld.getType();
                    Object obj = null;
    
                    if (Geometry.class.isAssignableFrom(t)) {
                        obj = geomReader.read(cursor.getBlob(i));
                    }
                    else if (Long.class.equals(t)) {
                        obj = cursor.getLong(i);
                    }
                    else if (Integer.class.equals(t) || 
                        Short.class.equals(t) || Byte.class.equals(t)) {
                        obj = cursor.getInt(i);
                    }
                    else if (Double.class.equals(t) || Float.class.equals(t)) {
                        obj = cursor.getDouble(i);
                    }
                    else {
                        obj = cursor.getString(i);
                    }
    
                    values.add(obj);
                }

                String fid = null;
                if (primaryKey != null) {
                    StringBuilder buf = new StringBuilder();
                    for (PrimaryKeyColumn pkcol : primaryKey.getColumns()) {
                        int colidx = cursor.getColumnIndex(pkcol.getName());
                        Object obj = cursor.getString(colidx);
                        if (obj != null) {
                            buf.append(obj);
                        }
                        buf.append(".");
                    }

                    buf.setLength(buf.length() - 1);
                    fid = buf.toString();
                }

                return feature = new BasicFeature(fid, values, schema);
            }
            return feature = null;
        }
        finally {
            next = null;
        }

    }

    @Override
    protected void doRemove() throws IOException {
        if (feature == null) {
            throw new IllegalStateException("nothing to remove");
        }
        workspace.delete(entry, feature);
    }

    @Override
    protected void doWrite() throws IOException {
        if (feature == null) {
            throw new IllegalStateException("nothing to write");
        }
        workspace.update(entry, feature);
    }

    @Override
    public void close() throws IOException {
        if (cursor != null) {
            cursor.close();
            cursor = null;
        }
    }
}
