package org.jeo.android.mbtiles;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jeo.data.FileDriver;

/**
 * Driver for the MBTiles format, that utilizes Android SQLite capabilities.
 * 
 * @author Justin Deoliveira, OpenGeo
 */
public class MBTiles extends FileDriver<MBTileSet> {

    public static MBTileSet open(File file){
        return new MBTileSet(file);
    }

    @Override
    public String getName() {
        return "MBTiles";
    }

    @Override
    public List<String> getAliases() {
        return Arrays.asList("mbt");
    }

    @Override
    public Class<MBTileSet> getType() {
        return MBTileSet.class;
    }

    @Override
    public MBTileSet open(File file, Map<?, Object> opts) throws IOException {
        return new MBTileSet(file);
    }
}
