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

import static org.jeo.android.graphics.Graphics.cap;
import static org.jeo.android.graphics.Graphics.color;
import static org.jeo.android.graphics.Graphics.dash;
import static org.jeo.android.graphics.Graphics.join;
import static org.jeo.android.graphics.Graphics.paint;
import static org.jeo.android.graphics.Graphics.pdMode;
import static org.jeo.android.graphics.Graphics.strokeWidth;
import static org.jeo.map.CartoCSS.COMP_OP;
import static org.jeo.map.CartoCSS.LINE_CAP;
import static org.jeo.map.CartoCSS.LINE_COLOR;
import static org.jeo.map.CartoCSS.LINE_COMP_OP;
import static org.jeo.map.CartoCSS.LINE_DASHARRAY;
import static org.jeo.map.CartoCSS.LINE_DASH_OFFSET;
import static org.jeo.map.CartoCSS.LINE_JOIN;
import static org.jeo.map.CartoCSS.LINE_OPACITY;
import static org.jeo.map.CartoCSS.LINE_WIDTH;
import static org.jeo.map.CartoCSS.MARKER_COMP_OP;
import static org.jeo.map.CartoCSS.MARKER_FILL;
import static org.jeo.map.CartoCSS.MARKER_FILL_OPACITY;
import static org.jeo.map.CartoCSS.MARKER_LINE_COLOR;
import static org.jeo.map.CartoCSS.MARKER_LINE_OPACITY;
import static org.jeo.map.CartoCSS.MARKER_LINE_WIDTH;
import static org.jeo.map.CartoCSS.POLYGON_COMP_OP;
import static org.jeo.map.CartoCSS.POLYGON_FILL;
import static org.jeo.map.CartoCSS.POLYGON_OPACITY;
import static org.jeo.map.CartoCSS.TEXT_ALIGN;
import static org.jeo.map.CartoCSS.TEXT_FILL;
import static org.jeo.map.CartoCSS.TEXT_HALO_FILL;
import static org.jeo.map.CartoCSS.TEXT_HALO_RADIUS;
import static org.jeo.map.CartoCSS.TEXT_SIZE;

import org.jeo.data.Tile;
import org.jeo.feature.Feature;
import org.jeo.geom.CoordinatePath;
import org.jeo.map.RGB;
import org.jeo.map.Rule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Paint.Align;
import android.graphics.Paint.Cap;
import android.graphics.Paint.Join;
import android.graphics.Paint.Style;

/**
 * Collection of Android Canvas/2D utility methods.
 * 
 * @author Justin Deoliveira, OpenGeo
 */
public class Graphics {

    static Logger LOG = LoggerFactory.getLogger(Graphics.class);

    public static Paint paint(Object obj, Rule rule) {
        Paint p = new Paint();
        if (rule.bool(obj, "anti-alias", true)) {
            p.setAntiAlias(true);
        }
        return p;
    }

    public static Paint labelPaint(Object obj, Rule rule) {
        Paint p = paint(obj, rule);
        p.setColor(color(rule.color(obj, TEXT_FILL, RGB.black)));
        p.setTextSize(rule.number(obj, TEXT_SIZE, 10f));
        p.setTextAlign(align(rule.string(obj, TEXT_ALIGN, "left")));

        RGB haloColor = rule.color(obj, TEXT_HALO_FILL, null);
        if (haloColor != null) {
           float radius = rule.number(obj, TEXT_HALO_RADIUS, 0f);
           p.setShadowLayer(radius, 0f, 0f, color(haloColor));
        }

        return p;
    }

    public static Paint markFillPaint(Object obj, Rule rule) {
        //TODO: marker type
        //String type = rule.string(f, MARKER_TYPE, "circle");

        RGB fillColor = rule.color(obj, MARKER_FILL, null);
        if (fillColor == null) {
            return null;
        }

        fillColor = fillColor.alpha(rule.number(obj, MARKER_FILL_OPACITY, 1f));

        String compOp = rule.string(obj, COMP_OP, null);
        String markCompOp = rule.string(obj, MARKER_COMP_OP, null);

        Paint paint = paint(obj, rule);
        paint.setStyle(Style.FILL);
        paint.setColor(color(fillColor));

        if (markCompOp != null || compOp != null) {
            PorterDuffXfermode pdMode = pdMode(markCompOp != null ? markCompOp : compOp);
            if (pdMode != null) {
                paint.setXfermode(pdMode);
            }
        }

        return paint;
    }

    public static Paint markLinePaint(Object obj, Rule rule) {
        RGB lineColor = rule.color(obj, MARKER_LINE_COLOR, null);
        if (lineColor == null) {
            return null;
        }

        lineColor = lineColor.alpha(rule.number(obj, MARKER_LINE_OPACITY, 1f));

        float lineWidth = rule.number(obj, MARKER_LINE_WIDTH, 1f);
        
        String compOp = rule.string(obj, COMP_OP, null);
        String lineCompOp = rule.string(obj, LINE_COMP_OP, null);

        Paint paint = paint(obj, rule);
        paint.setStyle(Style.STROKE); 
        paint.setColor(color(lineColor));
        paint.setStrokeWidth(lineWidth);

        if (lineCompOp != null || compOp != null) {
            PorterDuffXfermode pdMode = pdMode(lineCompOp != null ? lineCompOp : compOp);
            if (pdMode != null) {
                paint.setXfermode(pdMode);
            }
        }

        return paint;
    }

    public static Paint linePaint(Feature f, Rule rule, Matrix tx) {
        // line color + width 
        RGB color = rule.color(f, LINE_COLOR, RGB.black);
        float width = strokeWidth(rule.number(f, LINE_WIDTH, 1f), tx);

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

        Paint paint = paint(f, rule);
        paint.setStyle(Style.STROKE);
        paint.setColor(color(color));
        paint.setStrokeWidth(width);
        paint.setStrokeJoin(join);
        paint.setStrokeCap(cap);

        if (dash != null && dash.length > 0) {
            // scale the strokes
            for (int i = 0; i < dash.length; i++) {
                dash[i] = strokeWidth(dash[i], tx);
            }
            paint.setPathEffect(new DashPathEffect(dash, dashOffset));
        }
        if (compOp != null) {
            paint.setXfermode(pdMode(compOp));
        }

        return paint;
    }

    public static Paint polyFillPaint(Feature f, Rule rule) {
        // fill color
        RGB polyFill = rule.color(f, POLYGON_FILL, null);
        if (polyFill == null) {
            return null;
        }
       
        // look for an opacity to apply
        polyFill = polyFill.alpha(rule.number(f, POLYGON_OPACITY, 1f));

        String compOp = rule.string(f, COMP_OP, null);
        String polyCompOp = rule.string(f, POLYGON_COMP_OP, null);

        Paint paint = paint(f, rule);
        paint.setStyle(Style.FILL);
        paint.setColor(color(polyFill));

        if (polyCompOp != null || compOp != null) {
            PorterDuffXfermode pdMode = pdMode(polyCompOp != null ? polyCompOp : compOp);
            if (pdMode != null) {
                paint.setXfermode(pdMode);
            }
        }

        return paint;
    }

    public static Paint polyLinePaint(Feature f, Rule rule, Matrix m) {
        // line color
        RGB lineColor = rule.color(f, LINE_COLOR, null);
        if (lineColor == null) {
            return null;
        }

        // look for an opacity to apply
        lineColor = lineColor.alpha(rule.number(f, LINE_OPACITY, 1f));

        // line width
        float lineWidth = strokeWidth((float) rule.number(f, LINE_WIDTH, 1f), m);

        String compOp = rule.string(f, COMP_OP, null);
        String lineCompOp = rule.string(f, LINE_COMP_OP  , null);

        Paint paint = paint(f, rule);
        paint.setStyle(Style.STROKE);
        paint.setColor(color(lineColor));
        paint.setStrokeWidth(lineWidth);

        if (lineCompOp != null || compOp != null) {
            PorterDuffXfermode pdMode = pdMode(lineCompOp != null ? lineCompOp : compOp);
            if (pdMode != null) {
                paint.setXfermode(pdMode);
            }
        }

        return paint;
    }

    public static Cap cap(String str) {
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

    public static Join join(String str) {
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

    public static Align align(String str) {
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

    public static int color(RGB rgb) {
        return Color.argb(rgb.getAlpha(), rgb.getRed(), rgb.getGreen(), rgb.getBlue());
    }

    public static float[] dash(Float[] dash) {
        if (dash == null) {
            return null;
        }

        float[] prim = new float[dash.length];
        for (int i = 0; i < prim.length; i++) {
            prim[i] = dash[i].floatValue();
        }
        return prim;
    }

    public static PorterDuffXfermode pdMode(String compOp) {
        String pd = compOp.replace("-", "_").toUpperCase();
        try {
            return new PorterDuffXfermode(PorterDuff.Mode.valueOf(pd));
        }
        catch(IllegalArgumentException e) {
            LOG.debug("Unsupported composition operation: " + compOp);
        }
        return null;
    }

    public static Path path(CoordinatePath path) {
        Path p = new Path();

        O: while(path.hasNext()) {
            Coordinate c = path.next();
            float x = (float) c.x;
            float y = (float) c.y;

            switch(path.getStep()) {
            case MOVE_TO:
                p.moveTo(x, y);
                break;
            case LINE_TO:
                p.lineTo(x, y);
                break;
            case CLOSE:
                p.close();
                break;
            case STOP:
                break O;
            }
        }

        return p;
    }

    public static Rect expand(Rect r, float val) {
        Rect n = new Rect(r);
    
        n.top -= val;
        n.left -= val;
        n.bottom += val;
        n.right += val;
    
        return n;
    }
    
    public static RectF rectFromCenter(PointF p, float width, float height) {
        float left = p.x - width/2f;
        float top = p.y - height/2f;

        return new RectF(left, top, left + width, top + height);
    }
    
    public static RectF rectFromBottomLeft(PointF p, float width, float height) {
        float left = p.x;
        float top = p.y + height;
        
        return new RectF(left, top, left + width, p.y);
    }

    public static RectF rectFromBottomCenter(PointF p, float width, float height) {
        float left = p.x - width/2f;
        float top = p.y + height;

        return new RectF(left, top, left + width, p.y);
    }

    public static RectF rectFromBottomRight(PointF p, float width, float height) {
        float left = p.x - width;
        float top = p.y + height;

        return new RectF(left, top, p.x, p.y);
    }

    public static Envelope envelope(RectF rect) {
        //double h = map.getHeight();
        //return new Envelope(rect.left, rect.right, h - rect.top, h - rect.bottom);
        return new Envelope(rect.left, rect.right, rect.bottom, rect.top);
    }

    public static RectF rect(Envelope e) {
        return rect(e, new RectF());
    }
    
    public static RectF rect(Envelope e, RectF r) {
        r.left = (float)e.getMinX();
        r.top = (float)e.getMaxY();
        r.right = (float)e.getMaxX();
        r.bottom = (float) e.getMinY();
        return r;
    }
    
    public static PointF point(Coordinate c) {
        return point(c, new PointF());
    }

    public static PointF point(Coordinate c, PointF p) {
        p.x = (float) c.x;
        p.y = (float) c.y;
        return p;
    }

    static float strokeWidth(float width, Matrix m) {
        if (width != 0f && m != null) {
            // since strokes are scaled by the map transform it into world space 
            width = m.mapRadius(width);
        }
        return width;
    }

    public static Bitmap bitmap(Tile t) {
        byte[] data = t.getData();
        if (data == null) {
            return Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888);
        }
        return BitmapFactory.decodeByteArray(data, 0, data.length);
    }
}
