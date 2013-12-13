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

import java.util.List;

import org.jeo.feature.Feature;
import org.jeo.map.Rule;

import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.LineSegment;

/**
 * Label for lines or labels anchored to a line.
 * 
 * @author Justin Deoliveira, OpenGeo
 */
public class LineLabel extends Label {

    List<LineSegment> path;

    public LineLabel(String text, Rule rule, Feature feature, Geometry geom) {
        super(text, rule, feature, geom);
    }

    public List<LineSegment> getPath() {
        return path;
    }

    public void setPath(List<LineSegment> path) {
        this.path = path;
    }

    @Override
    public Envelope bounds() {
        return shape().getEnvelopeInternal();
    }

}
