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
package org.jeo.android.graphics;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.LineString;

/**
 * Samples a LineString object at arbitrary positions along the line string.
 * <p>
 * This sampler is stateful and maintains a current distance from the start of the line that is 
 * moved along with calls to the {@link #advance(double)} method. The {@link #sample()} method is 
 * used to obtain the coordinate at the current position. 
 * </p>
 * @author Justin Deoliveira, OpenGeo
 */
public class LineSampler {

    /** default comparison tolerance */
    final static double DEFAULT_TOL = 1e-7;

    /** the line coordinates */
    Coordinate[] line;

    /** index of current and next coordinate */
    int p, q;

    /** percentage between current coordinates */
    float f;

    /** comparison tolerance */
    double tol = DEFAULT_TOL;

    public LineSampler(LineString line) {
        this(line.getCoordinates());
    }

    public LineSampler(Coordinate[] line) {
        this.line = line;
        p = 0; q = 1;
        f = 0;
    }

    /**
     * Sets the tolerance to use when comparing distances.
     * <p>
     * The default tolerance is <tt>1e-7</tt>.
     * </p>
     */
    public void setTolerance(double tol) {
        this.tol = tol;
    }

    /**
     * Advances the position of the sampler along by the specified distance. 
     */
    public LineSampler advance(double dist) {
        while (dist > 0 && p < line.length-1) {
            double len = lenToNext();
            if (len == -1) {
                //no more
                break;
            }

            double remain = dist - len;
            if (remain > tol) {
                // advance to next and keep going
                dist -= len;
                p++;
                q++;
                f = 0;
            }
            else {
                // were here, readjust f
                f = (float) ((lenFromPrev() + dist) / line[p].distance(line[q]));
                break;
            }
        }

        return this;
    }

    /**
     * Samples the coordinate at the current position.
     * <p>
     * If the current position is past the end of the line string this method returns 
     * <code>null</code>.
     * </p>
     */
    public Coordinate sample() {
        if (p == line.length-1) {
            return null;
        }

        Coordinate c1 = line[p];
        Coordinate c2 = line[q];

        double x = (c2.x - c1.x) * f + c1.x;
        double y = (c2.y - c1.y) * f + c1.y;

        return new Coordinate(x,y);
    }

    double lenToNext() {
        if (p == line.length-1) {
            return -1;
        }

        double d = line[p].distance(line[q]);
        return d - d * f;
    }

    double lenFromPrev() {
        if (p == line.length-1) {
            return 0-1;
        }

        double d = line[p].distance(line[q]);
        return d * f;
    }
}
