/* Copyright 2013 The jeo project. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jeo.android.geopkg;

import java.io.IOException;

import org.jeo.data.Cursor;
import org.jeo.data.Tile;

public class TileCursor extends Cursor<Tile> {

    android.database.Cursor cursor;
    Boolean next = null;

    TileCursor(android.database.Cursor cursor) {
        this.cursor = cursor;
    }

    @Override
    public boolean hasNext() throws IOException {
        if (next == null) {
            next = cursor.moveToNext();
        }

        return next;
    }

    @Override
    public Tile next() throws IOException {
        try {
            if (next != null && next.booleanValue()) {
                Tile t = new Tile();
                t.setZ(cursor.getInt(0));
                t.setX(cursor.getInt(1));
                t.setY(cursor.getInt(2));
                t.setData(cursor.getBlob(3));
    
                return t;
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
