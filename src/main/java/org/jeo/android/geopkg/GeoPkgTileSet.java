package org.jeo.android.geopkg;

import java.io.IOException;

import org.jeo.data.Cursor;
import org.jeo.data.Tile;
import org.jeo.data.TilePyramid;
import org.jeo.data.TileSet;

public class GeoPkgTileSet extends GeoPkgDataset<TileEntry> implements TileSet {

    GeoPkgTileSet(TileEntry entry, GeoPkgWorkspace geopkg) {
        super(entry, geopkg);
    }

    @Override
    public TilePyramid getPyramid() {
        return entry.getTilePyramid();
    }

    @Override
    public Tile read(long z, long x, long y) throws IOException {
        Cursor<Tile> c = read(z,z,x,x,y,y);
        try {
            if (c.hasNext()) {
                return c.next();
            }
            return null;
        }
        finally {
            c.close();
        }
    }

    @Override
    public Cursor<Tile> read(
        long z1, long z2, long x1, long x2, long y1, long y2) throws IOException {
        return geopkg.cursor(entry, z1, z2, x1, x2, y1, y2);
    }
}
