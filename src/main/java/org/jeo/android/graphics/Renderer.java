package org.jeo.android.graphics;

import static org.jeo.android.graphics.Util.rectFromCenter;
import static org.jeo.map.Carto.COMP_OP;
import static org.jeo.map.Carto.LINE_CAP;
import static org.jeo.map.Carto.LINE_COLOR;
import static org.jeo.map.Carto.LINE_COMP_OP;
import static org.jeo.map.Carto.LINE_JOIN;
import static org.jeo.map.Carto.LINE_OPACITY;
import static org.jeo.map.Carto.LINE_WIDTH;
import static org.jeo.map.Carto.MARKER_COMP_OP;
import static org.jeo.map.Carto.MARKER_FILL;
import static org.jeo.map.Carto.MARKER_FILL_OPACITY;
import static org.jeo.map.Carto.MARKER_HEIGHT;
import static org.jeo.map.Carto.MARKER_LINE_COLOR;
import static org.jeo.map.Carto.MARKER_LINE_OPACITY;
import static org.jeo.map.Carto.MARKER_LINE_WIDTH;
import static org.jeo.map.Carto.MARKER_WIDTH;
import static org.jeo.map.Carto.POLYGON_COMP_OP;
import static org.jeo.map.Carto.POLYGON_FILL;
import static org.jeo.map.Carto.POLYGON_OPACITY;
import static org.jeo.map.Carto.TEXT_ALIGN;
import static org.jeo.map.Carto.TEXT_FILL;
import static org.jeo.map.Carto.TEXT_HALO_FILL;
import static org.jeo.map.Carto.TEXT_HALO_RADIUS;
import static org.jeo.map.Carto.TEXT_NAME;
import static org.jeo.map.Carto.TEXT_SIZE;

import java.io.IOException;
import java.util.List;

import org.jeo.data.Query;
import org.jeo.data.VectorData;
import org.jeo.feature.Feature;
import org.jeo.geom.CoordinatePath;
import org.jeo.geom.Geom;
import org.jeo.map.Layer;
import org.jeo.map.Map;
import org.jeo.map.RGB;
import org.jeo.map.Rule;
import org.jeo.map.RuleList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;

/**
 * Renders a map to an Android {@link Canvas}.
 * <p>
 * Usage:
 * <pre><code>
 * Canvas canvas = ...;
 * Map map = ...;
 *
 * Renderer r = new Renderer(canvas);
 * r.init(map);
 * r.render();
 * 
 * </code></pre>
 * </p>
 * @author Justin Deoliveira, OpenGeo
 */
public class Renderer {

    static Logger LOG = LoggerFactory.getLogger(Renderer.class);

    /** the map being rendered */
    Map map;

    /** the transformation pipeline */
    TransformPipeline tx;

    /** canvas to draw to */
    Canvas canvas;

    /** label index */
    LabelIndex labels;

    /** label renderer */
    Labeller labeller;
    
    /** invert y */
    boolean invertY;

    public Renderer(Canvas canvas) {
        this.canvas = canvas;
    }

    public TransformPipeline getTransform() {
        return tx;
    }

    public LabelIndex getLabels() {
        return labels;
    }

    public void init(Map map) {
        this.map = map;

        // initialize the transformation from world to screen
        tx = new TransformPipeline(map, canvas);
        tx.setWorldToScreen();

        // labels
        labels = new LabelIndex();
        labeller = new Labeller(canvas, tx);
    }

    public void render() {
        for (Layer l : map.getLayers()) {
            List<RuleList> rules = 
                map.getStyle().getRules().selectById(l.getName(), true).flatten().zgroup();

            //allocate the buffers
            for (RuleList RuleList : rules) {
                render((VectorData) l.getData(), RuleList);
            }
        }

        //labels
        renderLabels();
        tx.unset();
    }

    void render(VectorData data, RuleList RuleList) {
        try {
            for (Feature f : data.cursor(new Query().bounds(map.getBounds()))) {
                RuleList rs = RuleList.match(f);
                if (rs.isEmpty()) {
                    continue;
                }
              
                Rule r = RuleList.match(f).collapse();
                if (r != null) {
                    draw(f, r);
                }
            }
        } catch (IOException e) {
            LOG.error("Error querying layer " + data.getName(), e);
        }
    }

    void renderLabels() {
        for (Label l : labels.all()) {
            labeller.render(l);
        }
    }

    void draw(Feature f, Rule rule) {
        Geometry g = f.geometry();
        if (g == null) {
            return;
        }

        switch(Geom.Type.from(g)) {
        case POINT:
        case MULTIPOINT:
            drawPoint(f, rule);
            return;
        case LINESTRING:
        case MULTILINESTRING:
            drawLine(f, rule);
            return;
        case POLYGON:
        case MULTIPOLYGON:
            drawPolygon(f, rule);
            return;
        default:
            throw new UnsupportedOperationException();
        }
    }

    void drawPoint(Feature f, Rule rule) {
        // markers drawn in pixel space
        tx.unset();

        //TODO: marker type
        //String type = rule.string(f, MARKER_TYPE, "circle");
        
        RGB fillColor = rule.color(f, MARKER_FILL, RGB.black);
        if (fillColor != null) {
            fillColor = fillColor.alpha(rule.number(f, MARKER_FILL_OPACITY, 1f));
        }
        float width = rule.number(f, MARKER_WIDTH, 10f);
        float height = rule.number(f, MARKER_HEIGHT, width);

        RGB lineColor = rule.color(f, MARKER_LINE_COLOR, null);
        if (lineColor != null) {
            lineColor = lineColor.alpha(rule.number(f, MARKER_LINE_OPACITY, 1f));
        }

        float lineWidth = rule.number(f, MARKER_LINE_WIDTH, 1f); 

        String compOp = rule.string(f, COMP_OP, null);
        String markCompOp = rule.string(f, MARKER_COMP_OP, null);
        String lineCompOp = rule.string(f, LINE_COMP_OP, null);

        if (fillColor == null && lineColor == null) {
            return;
        }

        if (fillColor != null) {
            Paint paint = newPaint();
            paint.setStyle(Style.FILL);
            paint.setColor(color(fillColor));

            if (markCompOp != null || compOp != null) {
                PorterDuffXfermode pdMode = pdMode(markCompOp != null ? markCompOp : compOp);
                if (pdMode != null) {
                    paint.setXfermode(pdMode);
                }
            }

            CoordinatePath path = 
                CoordinatePath.create(f.geometry(), true, map.iscaleX(), map.iscaleY());
            while(path.hasNext()) {
                Coordinate c = path.next();
                canvas.drawOval(rectFromCenter(tx.getWorldToCanvas().map(c), width, height), paint);
            }
        }

        if (lineColor != null) {
            Paint paint = newPaint();
            paint.setStyle(Style.STROKE); 
            paint.setColor(color(lineColor));
            paint.setStrokeWidth(lineWidth);

            if (lineCompOp != null || compOp != null) {
                PorterDuffXfermode pdMode = pdMode(lineCompOp != null ? lineCompOp : compOp);
                if (pdMode != null) {
                    paint.setXfermode(pdMode);
                }
            }

            CoordinatePath path = 
                CoordinatePath.create(f.geometry(), true, map.iscaleX(), map.iscaleY());
            while(path.hasNext()) {
                Coordinate c = path.next();
                canvas.drawOval(rectFromCenter(tx.getWorldToCanvas().map(c), width, height), paint);
            }
        }

        // labels
        String label = rule.eval(f, TEXT_NAME, String.class);
        if (label != null) {
            createPointLabel(label, rule, f);
        }

        tx.setWorldToScreen();
    }

    void createPointLabel(String label, Rule rule, Feature f) {
        PointLabel l = new PointLabel(label, rule, f);

        Paint p = newLabelPaint(rule, f);
        l.put(Paint.class, p);

        labeller.layout(l, labels);
    }

    void drawLine(Feature f, Rule rule) {

        // line color + width 
        RGB color = rule.color(f, LINE_COLOR, RGB.black);
        float width = strokeWidth(rule.number(f, LINE_WIDTH, 1f));

        // line join
        Join join = join(rule.string(f, LINE_JOIN, "miter"));
        
        // line cap 
        Cap cap = cap(rule.string(f, LINE_CAP, "butt"));

        // line dash
        double[] dash = null; /*rule.numbers("line-dasharray", null);*/ 
        if (dash != null && dash.length % 2 != 0) {
            throw new IllegalArgumentException("line-dasharray pattern must be even length");
        }

        //float gamma = rule.number(f, "line-gamma", 1f);
        //String gammaMethod = rule.string(f, "line-gamma-method", "power");

        String compOp = rule.string(f, LINE_COMP_OP, null);

        Paint paint = newPaint();
        paint.setStyle(Style.STROKE);
        paint.setColor(color(color));
        paint.setStrokeWidth(width);

        if (compOp != null) {
            paint.setXfermode(pdMode(compOp));
        }

        Geometry line = f.geometry();

        Path path = path(line);
        canvas.drawPath(path, paint);

        //labels
        String label = rule.eval(f, TEXT_NAME, String.class);
        if (label != null) {
            createLineLabel(label, rule, f);
        }
    }

    void createLineLabel(String label, Rule rule, Feature f) {
        Paint p = newLabelPaint(rule, f);

        LineLabel l = new LineLabel(label, rule, f);
        l.put(Paint.class, p);

        labeller.layout(l, labels);
    }

    void drawPolygon(Feature f, Rule rule) {

        // fill color
        RGB polyFill = rule.color(f, POLYGON_FILL, null);
        if (polyFill != null) {
            // look for an opacity to apply
            polyFill = polyFill.alpha(rule.number(f, POLYGON_OPACITY, 1f));
        }

        //float gamma = rule.number(f, "polygon-gamma", 1f);
        //String gammaMethod = rule.string(f, "polygon-gamma-method", "power");

        // line color
        RGB lineColor = rule.color(f, LINE_COLOR, null);
        if (lineColor != null) {
            // look for an opacity to apply
            lineColor = lineColor.alpha(rule.number(f, LINE_OPACITY, 1f));
        }

        // line width
        float lineWidth = strokeWidth((float) rule.number(f, LINE_WIDTH, 1f));

        String compOp = rule.string(f, COMP_OP, null);
        String polyCompOp = rule.string(f, POLYGON_COMP_OP, null);
        String lineCompOp = rule.string(f, LINE_COMP_OP  , null);

        //float lineGamma = rule.number(f, "line-gamma", 1f);
        //String lineGammaMethod = rule.string(f, "line-gamma-method", "power");

        if (polyFill != null) {
            Paint paint = newPaint();
            paint.setStyle(Style.FILL);
            paint.setColor(color(polyFill));

            if (polyCompOp != null || compOp != null) {
                PorterDuffXfermode pdMode = pdMode(polyCompOp != null ? polyCompOp : compOp);
                if (pdMode != null) {
                    paint.setXfermode(pdMode);
                }
            }

            Path path = path(f.geometry());
            canvas.drawPath(path, paint);
        }

        if (lineColor != null) {
            Paint paint = newPaint();
            paint.setStyle(Style.STROKE);
            paint.setColor(color(lineColor));
            paint.setStrokeWidth(lineWidth);

            if (lineCompOp != null || compOp != null) {
                PorterDuffXfermode pdMode = pdMode(lineCompOp != null ? lineCompOp : compOp);
                if (pdMode != null) {
                    paint.setXfermode(pdMode);
                }
            }

            Path path = path(f.geometry());
            canvas.drawPath(path, paint);
        }

        // labels
        String label = rule.eval(f, TEXT_NAME, String.class);
        if (label != null) {
            createPointLabel(label, rule, f);
        }

        //drawPolygon(rp, buf, vpb.buffer(), color(polyFill), gamma, gammaMethod, color(lineColor), 
        //    lineWidth, lineGamma, lineGammaMethod, compOp);
    }

    Paint newPaint() {
        return new Paint(Paint.ANTI_ALIAS_FLAG);
    }

    Paint newLabelPaint(Rule rule, Feature f) {
        Paint p = newPaint();
        p.setColor(color(rule.color(f, TEXT_FILL, RGB.black)));
        p.setTextSize(rule.number(f, TEXT_SIZE, 10f));
        p.setTextAlign(align(rule.string(f, TEXT_ALIGN, "left")));

        RGB haloColor = rule.color(f, TEXT_HALO_FILL, null);
        if (haloColor != null) {
           float radius = rule.number(f, TEXT_HALO_RADIUS, 0f);
           p.setShadowLayer(radius, 0f, 0f, color(haloColor));
        }

        return p;
    }

    Path path(Geometry g) {
        Path path = new Path();

        CoordinatePath cpath = CoordinatePath.create(g, true, map.iscaleX(), map.iscaleY());
        O: while(cpath.hasNext()) {
            Coordinate c = cpath.next();
            float x = (float) c.x;
            float y = (float) c.y;

            switch(cpath.getStep()) {
            case MOVE_TO:
                path.moveTo(x, y);
                break;
            case LINE_TO:
                path.lineTo(x, y);
                break;
            case CLOSE:
                path.close();
                break;
            case STOP:
                break O;
            }
        }

        return path;
    }

    Cap cap(String str) {
        Cap cap = null;
        if (str != null) {
            try {
                cap = Cap.valueOf(str.toUpperCase());
            }
            catch(IllegalArgumentException e) {
                LOG.debug("Unrecognized " + LINE_CAP + " value: " + str);
            }
        }

        return cap != null ? cap : Cap.BUTT;
    }

    Join join(String str) {
        Join join = null;
        if (str != null) {
            try {
                join = Join.valueOf(str.toUpperCase());
            }
            catch(IllegalArgumentException e) {
                LOG.debug("Unrecognized " + LINE_JOIN + " value: " + str);
            }
        }

        return join != null ? join : Join.MITER;
    }

    Align align(String str) {
        Align align = null;
        if (str != null) {
            try {
                align = Align.valueOf(str.toUpperCase());
            }
            catch(IllegalArgumentException e) {
                LOG.debug("Unrecognized " + TEXT_ALIGN + " value: " + str);
            }
        }

        return align != null ? align : Align.LEFT;
    }

    int color(RGB rgb) {
        return Color.argb(rgb.getAlpha(), rgb.getRed(), rgb.getGreen(), rgb.getBlue());
    }

    float strokeWidth(float width) {
        if (width != 0f) {
            // since strokes are scaled by the map transform it into world space 
            width = tx.canvasToWorld.mapRadius(width);
        }
        return width;
    }

    PorterDuffXfermode pdMode(String compOp) {
        String pd = compOp.replace("-", "_").toUpperCase();
        try {
            return new PorterDuffXfermode(PorterDuff.Mode.valueOf(pd));
        }
        catch(IllegalArgumentException e) {
            LOG.debug("Unsupported composition operation: " + compOp);
        }
        return null;
    }
}
