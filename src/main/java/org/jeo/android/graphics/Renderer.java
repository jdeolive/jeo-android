package org.jeo.android.graphics;

import static org.jeo.android.graphics.Graphics.bitmap;
import static org.jeo.android.graphics.Graphics.color;
import static org.jeo.android.graphics.Graphics.labelPaint;
import static org.jeo.android.graphics.Graphics.linePaint;
import static org.jeo.android.graphics.Graphics.markFillPaint;
import static org.jeo.android.graphics.Graphics.markLinePaint;
import static org.jeo.android.graphics.Graphics.paint;
import static org.jeo.android.graphics.Graphics.polyFillPaint;
import static org.jeo.android.graphics.Graphics.polyLinePaint;
import static org.jeo.android.graphics.Graphics.rectFromCenter;
import static org.jeo.map.CartoCSS.BACKGROUND_COLOR;
import static org.jeo.map.CartoCSS.MARKER_HEIGHT;
import static org.jeo.map.CartoCSS.MARKER_WIDTH;
import static org.jeo.map.CartoCSS.OPACITY;
import static org.jeo.map.CartoCSS.TEXT_NAME;

import java.io.IOException;

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
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
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
            RuleList rules =
                map.getStyle().getRules().selectById(l.getName(), true).flatten();
            
            if (data instanceof VectorData) {
                for (RuleList ruleList : rules.zgroup()) {
                    render((VectorData)data, ruleList);
                }
            }
            else {
                render((TileSet)data, rules);
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

    void render(TileSet data, RuleList rules) {
        tx.reset(canvas);

        Rule rule = rules.collapse();

        try {
            TilePyramid pyr = data.getPyramid();

            TileCover cov = pyr.cover(view.getBounds(), view.getWidth(), view.getHeight());
            cov.fill(data);

            Rect dst = new Rect();

            Paint p = paint(null, rule);

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

            Paint p = paint(map, rule);
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

        float width = rule.number(f, MARKER_WIDTH, 10f);
        float height = rule.number(f, MARKER_HEIGHT, width);

        Paint fillPaint = markFillPaint(f, rule);
        Paint linePaint = markLinePaint(f, rule);
        
        Geometry point = f.geometry();

        if (fillPaint != null) {
            CoordinatePath path = 
                CoordinatePath.create(point).generalize(view.iscaleX(),view.iscaleY());
            while(path.hasNext()) {
                Coordinate c = path.next();
                canvas.drawOval(
                    rectFromCenter(tx.getWorldToCanvas().map(c), width, height), fillPaint);
            }
        }

        if (linePaint != null) {
            CoordinatePath path = 
                CoordinatePath.create(point).generalize(view.iscaleX(),view.iscaleY());
            while(path.hasNext()) {
                Coordinate c = path.next();
                canvas.drawOval(
                    rectFromCenter(tx.getWorldToCanvas().map(c), width, height), linePaint);
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

        Paint p = labelPaint(f, rule);
        l.put(Paint.class, p);

        labeller.layout(l, labels);
    }

    void drawLine(Feature f, Rule rule, Geometry line) {
        Path path = path(line);
        canvas.drawPath(path, linePaint(f, rule, tx.canvasToWorld));

        //labels
        String label = rule.eval(f, TEXT_NAME, String.class);
        if (label != null) {
            createLineLabel(label, rule, f, line);
        }
    }

    void createLineLabel(String label, Rule rule, Feature f, Geometry g) {
        Paint p = labelPaint(f, rule);

        LineLabel l = new LineLabel(label, rule, f, g);
        l.put(Paint.class, p);

        labeller.layout(l, labels);
    }

    void drawPolygon(Feature f, Rule rule, Geometry poly) {

        Paint fill = polyFillPaint(f, rule);
        Paint line = polyLinePaint(f, rule, tx.canvasToWorld);

        if (fill != null) {
            Path path = path(poly);
            canvas.drawPath(path, fill);
        }

        if (line != null) {
            Path path = path(poly);
            canvas.drawPath(path, line);
        }

        // labels
        String label = rule.eval(f, TEXT_NAME, String.class);
        if (label != null) {
            createPointLabel(label, rule, f, poly);
        }

        //drawPolygon(rp, buf, vpb.buffer(), color(polyFill), gamma, gammaMethod, color(lineColor), 
        //    lineWidth, lineGamma, lineGammaMethod, compOp);
    }

    Path path(Geometry g) {
        CoordinatePath cpath = 
            CoordinatePath.create(g).generalize(view.iscaleX(),view.iscaleY());
        return Graphics.path(cpath);
    }
}
