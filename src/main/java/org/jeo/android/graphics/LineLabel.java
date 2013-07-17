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
