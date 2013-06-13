package org.jeo.android.graphics;

import org.jeo.feature.Feature;
import org.jeo.geom.Geom;
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

    public PointLabel(String text, Rule rule, Feature feature) {
        super(text, rule, feature);
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
        return Geom.toPolygon(box);
    }
}
