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

public class FeatureCursor extends Cursor<Feature> {

    android.database.Cursor cursor;
    Schema schema;
    Boolean next = null;
    GeoPkgGeomReader geomReader;

    FeatureCursor(android.database.Cursor cursor, Schema schema) {
        this.cursor = cursor;
        this.schema = schema;
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
    
                //TODO: feature id
                return new BasicFeature(null, values, schema);
            }
            return null;
        }
        finally {
            next = null;
        }

    }

    @Override
    public void close() throws IOException {
        if (cursor != null) {
            cursor.close();
            cursor = null;
        }
    }
}
