package org.jeo.android.geopkg;

import org.jeo.filter.Spatial;
import org.jeo.sql.DbTypes;
import org.jeo.sql.FilterSQLEncoder;
import org.jeo.sql.PrimaryKey;

public class GeoPkgFilterSQLEncoder extends FilterSQLEncoder {

   @Override
    public Object visit(Spatial spatial, Object obj) {
        abort(spatial, "Spatial filters unsupported");
        return null;
    }
}
