package org.jeo.android.graphics;

import static org.jeo.android.graphics.Util.*;
import static org.jeo.map.CartoCSS.TEXT_DX;
import static org.jeo.map.CartoCSS.TEXT_DY;
import static org.jeo.map.CartoCSS.TEXT_MAX_CHAR_ANGLE_DELTA;
import static org.jeo.map.CartoCSS.TEXT_MIN_PADDING;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import org.jeo.feature.Feature;
import org.jeo.geom.Geom;
import org.jeo.geom.GeomBuilder;
import org.jeo.map.Rule;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryFactory;
import com.vividsolutions.jts.geom.LineSegment;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Polygon;

public class Labeller {

    static final double DEFAULT_MAX_ANGLE_CHAR_DELTA = 22.5 * Math.PI/180.0;
    static final double HALFPI = Math.PI/2.0;

    Canvas canvas;
    TransformPipeline tx;

    public Labeller(Canvas canvas, TransformPipeline tx) {
        this.canvas = canvas;
        this.tx = tx;
    }

    public boolean layout(Label label, LabelIndex labels) {
        if (label instanceof PointLabel) {
            return layout((PointLabel)label, labels);
        }
        else if (label instanceof LineLabel) {
            return layout((LineLabel)label, labels);
        }
        
        return false;
    }

    boolean layout(PointLabel label, LabelIndex labels) {
        String text = label.getText();
        Feature f = label.getFeature();
        Geometry g = label.getGeometry();

        Rule rule = label.getRule();

        // get center in screen space
        Coordinate centroid = g.getCentroid().getCoordinate();

        PointF anchor = tx.getWorldToCanvas().map(centroid);
        
        // apply offsets
        anchor.x += rule.number(f, TEXT_DX, 0f);
        anchor.y += rule.number(f, TEXT_DY, 0f);

        Paint p = label.get(Paint.class, Paint.class);

        //compute bounds of this label
        Rect b = new Rect();
        p.getTextBounds(text, 0, text.length(), b);

        //padding
        float padding = rule.number(f, TEXT_MIN_PADDING, 0f);
        if (padding > 0f) {
            b = expand(b, padding);
        }

        //label.setAnchor(new Coordinate(center.x,center.y));
        //label.setBox(envelope(rectFromBottomLeft(center, b.width(), b.height())));

        RectF c = new RectF(b);
        tx.getCanvasToWorld().mapRect(c);

        centroid = tx.getCanvasToWorld().map(anchor);
        label.setAnchor(centroid);

        RectF box = null;
        switch(p.getTextAlign()) {
        case LEFT:
            box = rectFromBottomLeft(point(centroid), c.width(), c.height());
            break;
        case CENTER:
            box = rectFromBottomCenter(point(centroid), c.width(), c.height());
            break;
        case RIGHT:
            box = rectFromBottomRight(point(centroid), c.width(), c.height());
            break;
        }

        label.setBox(envelope(box));

        return labels.insert(label);
    }
   
    boolean layout(LineLabel label, LabelIndex labels) {
        String txt = label.getText();
        Rule rule = label.getRule();
        Feature f = label.getFeature();
        Geometry g = label.getGeometry();

        LineString line = null;
        if (g instanceof MultiLineString) {
            //TODO: handle multiple lines
            if (g.getNumGeometries() == 1) {
                line = (LineString) g.getGeometryN(0);
            }
        }
        else {
            line = (LineString) g;
        }
        if (line == null) {
            return false;
        }

        Paint p = label.get(Paint.class, Paint.class);

        // compute the bounds of the label with no rotation
        Rect r = new Rect();
        p.getTextBounds(txt, 0, txt.length(), r);

        // map it to world coordinates
        RectF bounds = new RectF(r);
        tx.getCanvasToWorld().mapRect(bounds);

        // ignore label if its too long for the line
        
        if (line.getLength() < bounds.width()) {
            // ignore this label
            return false;
        }

        // reverse
        if (line.getPointN(0).getX() > line.getPointN(line.getNumPoints()-1).getX()) {
            line = (LineString) line.reverse();
        }

        // compute width of individual letters
        float[] widths = new float[txt.length()];
        p.getTextWidths(txt, widths);

        // map the widths to world space
        float sum = 0;
        for (int i = 0; i < widths.length; i++) {
            RectF s = new RectF(0, 0, widths[i], 1);
            tx.getCanvasToWorld().mapRect(s);

            widths[i] = s.width();
            sum += s.width();
        }

        //TODO: properly figure out spacing between letters
        float space = tx.getCanvasToWorld().mapRadius(1);

        // allowable angle change in consecutive characters 
        double maxAngleDelta = 
            rule.number(f, TEXT_MAX_CHAR_ANGLE_DELTA, DEFAULT_MAX_ANGLE_CHAR_DELTA);

        //
        // sample points along the line for letters
        //
        List<LineSegment> path = new ArrayList<LineSegment>();

        LineSampler sampler = new LineSampler(line.getCoordinates());

        for (int i = 0; i < txt.length(); i++) {
            // get next point
            Coordinate c1 = sampler.sample();

            // advance by width of letter
            sampler.advance(widths[i]);

            // get point for end of letter
            Coordinate c2 = sampler.sample();

            if (c1 == null || c2 == null) {
                // ran out of room
                return false;
            }

            LineSegment seg = new LineSegment(c1,c2); 
            
            // check angle made with previous segment
            if (i > 0) {
                LineSegment prev = path.get(i-1);
                if (Math.abs(angle(seg) - angle(prev)) > maxAngleDelta) {
                    return false;
                }
            }

            path.add(seg);
            sampler.advance(space);
        }

        label.setPath(path);
        label.setShape(toShape(path, bounds.height()));
        return labels.insert(label);
    }


    Geometry toShape(List<LineSegment> path, double h) {
        //TODO: take into account letter alignment
        // turn the path into a single polygon by generating points orthogonal 
        // to the individual line segments 
        GeomBuilder gb = new GeomBuilder();

        LinkedList<Coordinate> top = new LinkedList<Coordinate>();
        for (int i = 0; i < path.size(); i++) {
            LineSegment seg = path.get(i);
            Coordinate p0 = seg.p0;
            Coordinate p1 = seg.p1;
            double theta = seg.angle();

            gb.points(p0.x, p0.y);

            // generate the perpendicular point at a distance of h 
            Coordinate p2 = new Coordinate();

            if (theta > 0) {
                if (theta <= HALFPI) {
                    //ne
                    double phi = Math.PI - (HALFPI + theta);
                    p2.x = (Math.cos(phi) * h - p0.x) * -1;
                    p2.y = Math.sin(phi) * h + p0.y;
                }
                else {
                    //nw
                    double phi = Math.PI - theta;
                    p2.x = Math.cos(phi) * h + p0.x; 
                    p2.y = Math.sin(phi) * h + p0.y;
                }
            }
            else {
                theta = Math.abs(theta);
                if (theta < HALFPI) {
                    double phi = HALFPI- theta;
                    p2.x = (Math.cos(phi) * h + p0.x);
                    p2.y = (Math.sin(phi) * h + p0.y);
                }
                else {
                    double phi = theta = HALFPI;
                    p2.x = Math.cos(phi) * h + p0.x;
                    p2.y = (Math.sin(phi) * h - p0.y) * -1;
                }
            }

            top.add(p2);
            if (i == path.size()-1) {
                gb.points(p1.x, p1.y);
                top.add(new Coordinate(p1.x + p2.x - p0.x, p1.y + p2.y - p0.y));
            }
        }

        for (Iterator<Coordinate> it = top.descendingIterator(); it.hasNext();) {
            Coordinate c = it.next();
            gb.points(c.x, c.y);
        }

        return gb.toPolygon();
    }

    Path smoothPath(Coordinate[] coords) {
        Path path = new Path();
        path.moveTo((float) coords[0].x, (float) coords[0].y);

        Iterator<Coordinate> ctrl = cubicSplineControlPoints(coords, 0).iterator();

        for (int i = 1; i < coords.length; i++) {
            Coordinate c = coords[i];
            Coordinate ctrl1 = ctrl.next();
            Coordinate ctrl2 = ctrl.next();

            path.cubicTo((float) ctrl1.x, (float) ctrl1.y, (float) ctrl2.x, (float) ctrl2.y, 
                (float) c.x, (float) c.y);
            
        }
        path.transform(tx.worldToCanvas);
        return path;
    }

    List<Coordinate> cubicSplineControlPoints(Coordinate[] coords, float alpha) {

        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("alpha must be between 0 and 1 inclusive");
        }

        if (coords.length < 2) {
            throw new IllegalArgumentException("number of coordinates must be <= 2");
        }

        int n = coords.length;

        List<Coordinate> ctrl = new ArrayList<Coordinate>();

        Coordinate curr = 
            new Coordinate(2 * coords[0].x - coords[1].x, 2 * coords[0].y - coords[1].y);
        Coordinate next = coords[0];

        Coordinate mid = new Coordinate();
        mid.x = (curr.x + next.x) / 2.0;
        mid.y = (curr.y + next.y) / 2.0;

        Coordinate midPrev = new Coordinate();

        Coordinate last = new Coordinate(
            2 * coords[n-1].x - coords[n-2].x, 2 * coords[n-1].y - coords[n-2].y);

        Coordinate anchor = new Coordinate();
        double dv = curr.distance(next);

        for (int i = 0; i < n; i++) {
            curr = next;
            next = i < n-1 ? coords[i+1] : last;

            midPrev.x = mid.x;
            midPrev.y = mid.y;
            
            mid.x = (curr.x + next.x) / 2.0;
            mid.y = (curr.y + next.y) / 2.0;

            double dvPrev = dv;
            dv = curr.distance(next);

            double p = dvPrev / (dvPrev + dv);

            anchor.x = midPrev.x + p * (mid.x - midPrev.x);
            anchor.y = midPrev.y + p * (mid.y - midPrev.y);

            double dx = anchor.x - curr.x;
            double dy = anchor.y - curr.y;

            if (i > 0) {
                ctrl.add(new Coordinate(alpha*(curr.x - midPrev.x + dx) + midPrev.x - dx,
                    alpha*(curr.y - midPrev.y + dy) + midPrev.y - dy));
            }
            if (i < n-1) {
                ctrl.add(new Coordinate(alpha*(curr.x - mid.x + dx) + mid.x - dx, 
                    alpha*(curr.y - mid.y + dy) + mid.y - dy));
            }
        }

        return ctrl;
    }

    public void render(Label label) {
        if (label instanceof PointLabel) {
            render((PointLabel)label);
        }
        else if (label instanceof LineLabel) {
            render((LineLabel)label);
        }
    }
    
    void render(PointLabel label) {
        tx.reset(canvas);

        Paint p = label.get(Paint.class, Paint.class);

        Coordinate a = label.getAnchor();
        PointF f = tx.getWorldToCanvas().map(a);

        canvas.drawText(label.getText(), f.x, f.y, p);

        tx.apply(canvas);
    }

    void render(LineLabel label) {
        tx.reset(canvas);
        
        String txt = label.getText();

        Paint p = label.get(Paint.class, Paint.class);

        List<LineSegment> path = label.getPath();
        for (int i = 0; i < txt.length(); i++) {
            LineSegment line = path.get(i);
            
            PointF p0 = tx.getWorldToCanvas().map(line.p0);
            PointF p1 = tx.getWorldToCanvas().map(line.p1);

            double theta = Math.atan((p1.y - p0.y) / (p1.x - p0.x));
            
            Matrix m = canvas.getMatrix();
            Matrix n = new Matrix(canvas.getMatrix());
            n.preRotate((float)Math.toDegrees(theta), p0.x, p0.y);

            //Paint debug = new Paint();
            //debug.setColor(Color.RED);
            //canvas.drawLine(p0.x, p0.y, p1.x, p1.y, debug);

            canvas.setMatrix(n);
            canvas.drawText(txt, i, i+1, p0.x, p0.y, p);
            canvas.setMatrix(m);
        }

        tx.apply(canvas);
        //canvas.drawTextOnPath(l.text, l.getPath(), 0, 0, l.get(Paint.class,Paint.class));
    }

    double angle(LineSegment l) {
        return Math.atan((l.p1.y - l.p0.y) / (l.p1.x - l.p0.x));
    }
}
