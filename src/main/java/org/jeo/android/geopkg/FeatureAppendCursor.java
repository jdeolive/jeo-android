package org.jeo.android.geopkg;

import java.io.IOException;

import org.jeo.data.Cursor;
import org.jeo.feature.BasicFeature;
import org.jeo.feature.Feature;

public class FeatureAppendCursor extends Cursor<Feature> {

    FeatureEntry entry;
    GeoPkgWorkspace ws;

    Feature next;

    public FeatureAppendCursor(FeatureEntry entry, GeoPkgWorkspace ws) {
        super(Mode.APPEND);
        this.entry = entry;
        this.ws = ws;
    }

    @Override
    public boolean hasNext() throws IOException {
        return true;
    }

    @Override
    public Feature next() throws IOException {
        return next = new BasicFeature(null, ws.schema(entry));
    }

    @Override
    protected void doWrite() throws IOException {
        ws.insert(entry, next);
    }

    @Override
    public void close() throws IOException {
    }

}
