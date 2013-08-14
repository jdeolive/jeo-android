package org.jeo.android.graphics;

import static org.jeo.android.graphics.Util.rectFromCenter;
import static org.jeo.map.CartoCSS.*;

import java.io.IOException;
import java.util.List;

import org.jeo.data.Dataset;
import org.jeo.data.Query;
import org.jeo.data.Tile;
import org.jeo.data.TileCover;
import org.jeo.data.TilePyramid;
import org.jeo.data.TileSet;
import org.jeo.data.VectorData;
import org.jeo.feature.Feature;
import org.jeo.geom.CoordinatePath;
import org.jeo.geom.Envelopes;
import org.jeo.geom.Geom;
import org.jeo.map.Layer;
import org.jeo.map.Map;
import org.jeo.map.RGB;
import org.jeo.map.Rule;
import org.jeo.map.RuleList;
import org.jeo.map.Viewport;
import org.jeo.proj.Proj;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;
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

    /** the view/map being rendered */
    Viewport view;
    Map map;

    /** the transformation pipeline */
    TransformPipeline tx;

    /** canvas to draw to */
    Canvas canvas;

    /** label index */
    LabelIndex labels;

    /** label renderer */
    Labeller labeller;

    public Renderer(Canvas canvas) {
        this.canvas = canvas;
    }

    public TransformPipeline getTransform() {
        return tx;
    }

    public LabelIndex getLabels() {
        return labels;
    }

    public void init(Viewport view) {
        this.view = view;
        
        map = view.getMap();

        // initialize the transformation from world to screen
        tx = new TransformPipeline(view);
        tx.apply(canvas);

        // labels
        labels = new LabelIndex();
        labeller = new Labeller(canvas, tx);
    }

    public void render() {
        LOG.debug("Rendering map at " + view.getBounds());

        // background
        renderBackground();
        for (Layer l : map.getLayers()) {
            if (!l.isVisible()) {
                continue;
            }

            Dataset data = l.getData();
            if (data instanceof VectorData) {
                List<RuleList> rules = 
                    map.getStyle().getRules().selectById(l.getName(), true).flatten().zgroup();

                //allocate the buffers
                for (RuleList ruleList : rules) {
                    render((VectorData)data, ruleList);
                }
            }
            else {
                render((TileSet)data);
            }

        }

        //labels
        renderLabels();
        tx.reset(canvas);
        LOG.debug("Rendering complete");
    }

    void render(VectorData data, RuleList rules) {
        try {
            Query q = new Query().bounds(view.getBounds());

            // reproject
            if (data.getCRS() != null) {
                if (!Proj.equal(view.getCRS(), data.getCRS())) {
                    q.reproject(view.getCRS());
                }
            }
            else {
                LOG.debug(
                    "Layer "+data.getName()+" specifies no projection, assuming map projection");
            }

            for (Feature f : data.cursor(q)) {
                RuleList rs = rules.match(f);
                if (rs.isEmpty()) {
                    continue;
                }
              
                Rule r = rules.match(f).collapse();
                if (r != null) {
                    draw(f, r);
                }
            }
        } catch (IOException e) {
            LOG.error("Error querying layer " + data.getName(), e);
        }
    }

    void render(TileSet data) {
        tx.reset(canvas);

        try {
            TilePyramid pyr = data.getPyramid();

            TileCover cov = pyr.cover(view.getBounds(), view.getWidth(), view.getHeight());
            cov.fill(data);

            Rect dst = new Rect();

            Paint p = newPaint();

            double scx = cov.getGrid().getXRes() / view.iscaleX();
            double scy = cov.getGrid().getYRes() / view.iscaleY();

            dst.left = 0;
            for (int x = 0; x < cov.getWidth(); x++) {
                dst.bottom = canvas.getHeight();

                for (int y = 0; y < cov.getHeight(); y++) {
                    Tile t = cov.tile(x, y);

                    // clip source rectangle
                    Rect src = clipTile(t, pyr, map);

                    dst.right = dst.left + (int) (src.width() * scx);
                    dst.top = dst.bottom - (int) (src.height() * scy);

                    // load the bitmap
                    Bitmap img = bitmap(t);
                    canvas.drawBitmap(img, src, dst, p);

                    dst.bottom = dst.top;
                    //img.recycle();
                }

                dst.left = dst.right;
            }
        }
        catch(IOException e) {
            LOG.error("Error querying layer " + data.getName(), e);
        }
        catch(Exception e) {
            e.printStackTrace();
        }

        tx.apply(canvas);
    }

    Rect clipTile(Tile t, TilePyramid pyr, Map map) {
        Envelope tb = pyr.bounds(t);
        Envelope i = tb.intersection(view.getBounds());

        Rect rect = new Rect(0, 0, pyr.getTileWidth(), pyr.getTileHeight());

        int w = rect.width();
        int h = rect.height();

        rect.left += (i.getMinX() - tb.getMinX())/tb.getWidth() * w;
        rect.right -= (tb.getMaxX() - i.getMaxX())/tb.getWidth() * w;
        rect.top += (tb.getMaxY() - i.getMaxY())/tb.getHeight() * h; 
        rect.bottom -= (i.getMinY() - tb.getMinY())/tb.getHeight() * h;

        return rect;
    }

    Bitmap bitmap(Tile t) {
        byte[] data = t.getData();
        if (data == null) {
            return Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        }
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }

    void renderBackground() {
        tx.reset(canvas);

        RuleList rules = map.getStyle().getRules().selectByName("Map", false);
        if (rules.isEmpty()) {
            //nothing to do
            return;
        }

        Rule rule = rules.collapse();
        RGB bgColor = rule.color(map, BACKGROUND_COLOR, null);
        if (bgColor != null) {
            bgColor = bgColor.alpha(rule.number(map, OPACITY, 1f));

            Paint p = newPaint();
            p.setStyle(Paint.Style.FILL);
            p.setColor(color(bgColor));
            canvas.drawRect(new Rect(0, 0, view.getWidth(), view.getHeight()), p);
        }

        tx.apply(canvas);
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

        g = clipGeometry(g);
        if (g.isEmpty()) {
            return;
        }

        switch(Geom.Type.from(g)) {
        case POINT:
        case MULTIPOINT:
            drawPoint(f, rule);
            return;
        case LINESTRING:
        case MULTILINESTRING:
            drawLine(f, rule, g);
            return;
        case POLYGON:
        case MULTIPOLYGON:
            drawPolygon(f, rule, g);
            return;
        default:
            throw new UnsupportedOperationException();
        }
    }

    Geometry clipGeometry(Geometry g) {
        // TODO: doing a full intersection is sub-optimal, look at a more efficient clipping 
        // algorithm, like cohen-sutherland
        return g.intersection(Envelopes.toPolygon(view.getBounds()));
    }

    void drawPoint(Feature f, Rule rule) {
        // markers drawn in pixel space
        tx.reset(canvas);

        //TODO: marker type
        //String type = rule.string(f, MARKER_TYPE, "circle");
        
        RGB fillColor = rule.color(f, MARKER_FILL, null);
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

        Geometry point = f.geometry();

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

            CoordinatePath path = CoordinatePath.create(point,true,view.iscaleX(),view.iscaleY());
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

            CoordinatePath path = CoordinatePath.create(point,true,view.iscaleX(),view.iscaleY());
            while(path.hasNext()) {
                Coordinate c = path.next();
                canvas.drawOval(rectFromCenter(tx.getWorldToCanvas().map(c), width, height), paint);
            }
        }

        // labels
        String label = rule.eval(f, TEXT_NAME, String.class);
        if (label != null) {
            createPointLabel(label, rule, f, point);
        }

        tx.apply(canvas);
    }

    void createPointLabel(String label, Rule rule, Feature f, Geometry g) {
        PointLabel l = new PointLabel(label, rule, f, g);

        Paint p = newLabelPaint(rule, f);
        l.put(Paint.class, p);

        labeller.layout(l, labels);
    }

    void drawLine(Feature f, Rule rule, Geometry line) {

        // line color + width 
        RGB color = rule.color(f, LINE_COLOR, RGB.black);
        float width = strokeWidth(rule.number(f, LINE_WIDTH, 1f));

        // line join
        Join join = join(rule.string(f, LINE_JOIN, "miter"));
        
        // line cap 
        Cap cap = cap(rule.string(f, LINE_CAP, "butt"));

        // line dash
        float[] dash = dash(rule.numbers(f, LINE_DASHARRAY, (Float[])null));
        if (dash != null && dash.length % 2 != 0) {
            LOG.debug("dash specified odd number of entries");

            float[] tmp;
            if (dash.length > 2) {
                // strip off last
                tmp = new float[dash.length-1];
                System.arraycopy(dash, 0, tmp, 0, tmp.length);
            }
            else {
                // pad it
                tmp = new float[dash.length*2];
                System.arraycopy(dash, 0, tmp, 0, dash.length);
                System.arraycopy(dash, 0, tmp, dash.length, dash.length);
            }
        }

        float dashOffset = rule.number(f, LINE_DASH_OFFSET, 0f);
        
        //float gamma = rule.number(f, "line-gamma", 1f);
        //String gammaMethod = rule.string(f, "line-gamma-method", "power");

        String compOp = rule.string(f, LINE_COMP_OP, null);

        Paint paint = newPaint();
        paint.setStyle(Style.STROKE);
        paint.setColor(color(color));
        paint.setStrokeWidth(width);
        paint.setStrokeJoin(join);
        paint.setStrokeCap(cap);

        if (dash != null && dash.length > 0) {
            // scale the strokes
            for (int i = 0; i < dash.length; i++) {
                dash[i] = strokeWidth(dash[i]);
            }
            paint.setPathEffect(new DashPathEffect(dash, dashOffset));
        }
        if (compOp != null) {
            paint.setXfermode(pdMode(compOp));
        }

        Path path = path(line);
        canvas.drawPath(path, paint);

        //labels
        String label = rule.eval(f, TEXT_NAME, String.class);
        if (label != null) {
            createLineLabel(label, rule, f, line);
        }
    }

    void createLineLabel(String label, Rule rule, Feature f, Geometry g) {
        Paint p = newLabelPaint(rule, f);

        LineLabel l = new LineLabel(label, rule, f, g);
        l.put(Paint.class, p);

        labeller.layout(l, labels);
    }

    void drawPolygon(Feature f, Rule rule, Geometry poly) {

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

            Path path = path(poly);
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

            Path path = path(poly);
            canvas.drawPath(path, paint);
        }

        // labels
        String label = rule.eval(f, TEXT_NAME, String.class);
        if (label != null) {
            createPointLabel(label, rule, f, poly);
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

        CoordinatePath cpath = CoordinatePath.create(g, true,view.iscaleX(),view.iscaleY());
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

    float[] dash(Float[] dash) {
        if (dash == null) {
            return null;
        }

        float[] prim = new float[dash.length];
        for (int i = 0; i < prim.length; i++) {
            prim[i] = dash[i].floatValue();
        }
        return prim;
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
