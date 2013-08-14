package org.jeo.android.graphics;

import org.jeo.map.Map;
import org.jeo.map.Viewport;
import org.osgeo.proj4j.CoordinateReferenceSystem;

import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Abstracts the affine transformations involved in the rendering pipeline. 
 * 
 * @author Justin Deoliveira, OpenGeo
 */
public class TransformPipeline implements Viewport.Listener {

    Transform worldToCanvas;
    Transform canvasToWorld;
    Transform canvasToScreen;

    public TransformPipeline(Viewport view) {
        view.bind(this);

        update(view);
    }

    public void update(Viewport view) {
        // transformation from map coordinates to canvas coordinates
        worldToCanvas = new Transform();
        worldToCanvas.preScale((float)view.scaleX(), (float)-view.scaleY());
        worldToCanvas.postTranslate((float)view.translateX(), (float)view.translateY());

        // inverse of above
        canvasToWorld = new Transform();
        worldToCanvas.invert(canvasToWorld);
    }

    /**
     * Returns the transform from map coordinates to canvas coordinates.
     * <p>
     * The transform is built with the following matrix:
     * <pre>
     * | scx  0   tx |
     * |  0  scy -ty |
     * |  0   0   1  |
     * </pre>
     * There <tt>scx</tt>, <tt>scx</tt>, <tt>tx</tt>, and <tt>ty</tt> are obtain from the 
     * {@link Map} class.
     * </p>
     */
    public Transform getWorldToCanvas() {
        return worldToCanvas;
    }

    /**
     * Returns the transform from canvas coordinates to map coordinates. 
     * <p>
     * This transform is the inverse of {@link #getWorldToCanvas()}.
     * </p>
     */
    public Transform getCanvasToWorld() {
        return canvasToWorld;
    }

    public void apply(Canvas canvas) {
        canvasToScreen = new Transform(canvas.getMatrix());

        Transform worldToScreen = new Transform(canvasToScreen);
        worldToScreen.preConcat(worldToCanvas);

        canvas.setMatrix(worldToScreen);
    }

    public void reset(Canvas canvas) {
        canvas.setMatrix(canvasToScreen);
    }

//    /**
//     * Returns the original transform from the canvas to the screen.
//     * <p>
//     * This transform is dependent on the screen layout. It is obtained from 
//     * {@link Canvas#getMatrix()}. 
//     * </p>
//     */
//    public Transform getCanvasToScreen() {
//        return canvasToScreen;
//    }
//
//    /**
//     * Returns the concatenated transform from map to screen.
//     * <p>
//     * This transform is obtained from multiplying the following transforms:
//     * <pre>
//     *   canvasToScreen * worldToCanvas
//     * </pre>
//     * </p>
//     * @return
//     */
//    public Transform getWorldToScreen() {
//        return worldToScreen;
//    }
//
//    /**
//     * Sets the transform on the underlying canvas to {@link #getWorldToScreen()}.
//     */
//    public void setWorldToScreen() {
//        canvas.setMatrix(worldToScreen);
//    }
//
//    /**
//     * Restores the canvas transform to its original {@link #getCanvasToScreen()} transform. 
//     */
//    public void unset() {
//        canvas.setMatrix(canvasToScreen);
//    }

    @Override
    public void onBoundsChanged(Viewport view, Envelope bounds, Envelope old) {
        update(view);
    }

    @Override
    public void onSizeChanged(Viewport view, int width, int height, int oldWidth, int oldHeight) {
        update(view);
    }

    @Override
    public void onCRSChanged(Viewport view, CoordinateReferenceSystem crs, CoordinateReferenceSystem old) {
    }

    /**
     * Extension of {@link Matrix} providing methods to transform between {@link PointF} and 
     * {@link Coordinate} objects. 
     */
    public static class Transform extends Matrix {

        public Transform() {
            super();
        }

        public Transform(Matrix src) {
            super(src);
        }

        public PointF map(Coordinate c) {
            float[] pt = new float[]{(float)c.x, (float)c.y};
            mapPoints(pt);
            return new PointF(pt[0], pt[1]);
        }

        public Coordinate map(PointF p) {
            float[] pt = new float[]{p.x, p.y};
            mapPoints(pt);
            return new Coordinate(pt[0], pt[1]);
        }
    }
}
