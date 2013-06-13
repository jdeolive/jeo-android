package org.jeo.android.graphics;

import android.graphics.PointF;
import android.graphics.Rect;
import android.graphics.RectF;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

class Util {

    static Rect expand(Rect r, float val) {
        Rect n = new Rect(r);
    
        n.top -= val;
        n.left -= val;
        n.bottom += val;
        n.right += val;
    
        return n;
    }
    
    static RectF rectFromCenter(PointF p, float width, float height) {
        float left = p.x - width/2f;
        float top = p.y - height/2f;

        return new RectF(left, top, left + width, top + height);
    }
    
    static RectF rectFromBottomLeft(PointF p, float width, float height) {
        float left = p.x;
        float top = p.y + height;
        
        return new RectF(left, top, left + width, p.y);
    }

    static RectF rectFromBottomCenter(PointF p, float width, float height) {
        float left = p.x - width/2f;
        float top = p.y + height;

        return new RectF(left, top, left + width, p.y);
    }

    static RectF rectFromBottomRight(PointF p, float width, float height) {
        float left = p.x - width;
        float top = p.y + height;

        return new RectF(left, top, p.x, p.y);
    }

    static Envelope envelope(RectF rect) {
        //double h = map.getHeight();
        //return new Envelope(rect.left, rect.right, h - rect.top, h - rect.bottom);
        return new Envelope(rect.left, rect.right, rect.bottom, rect.top);
    }

    static PointF point(Coordinate c) {
        return new PointF((float) c.x, (float) c.y);
    }
}
