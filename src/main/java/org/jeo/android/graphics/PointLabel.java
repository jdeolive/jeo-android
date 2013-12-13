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

import org.jeo.feature.Feature;
import org.jeo.geom.Envelopes;
import org.jeo.map.Rule;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;

/**
 * Label for points or labels anchored to a specific point.
 * 
 * @author Justin Deoliveira, OpenGeo
 */
public class PointLabel extends Label {

    Envelope box;
    Coordinate anchor;

    public PointLabel(String text, Rule rule, Feature feature, Geometry geom) {
        super(text, rule, feature, geom);
    }

    public Envelope getBox() {
        return box;
    }

    public void setBox(Envelope box) {
        this.box = box;
    }

    public Coordinate getAnchor() {
        return anchor;
    }

    public void setAnchor(Coordinate anchor) {
        this.anchor = anchor;
    }

    @Override
    public Envelope bounds() {
        return box;
    }

    @Override
    public Geometry shape() {
        return Envelopes.toPolygon(box);
    }
}
