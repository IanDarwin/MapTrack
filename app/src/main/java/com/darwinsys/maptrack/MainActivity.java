package com.darwinsys.maptrack;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;
import android.location.Location;
import android.location.LocationManager;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.api.IGeoPoint;
import org.osmdroid.api.IMapController;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.BoundingBox;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Overlay;
import org.osmdroid.views.overlay.ScaleBarOverlay;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

import com.darwinsys.maptrack.fileio.FileNameUtils;
import com.darwinsys.maptrack.fileio.GPSFileSaver;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = MainActivity.class.getName();
    public static final int INITIAL_ZOOM_LEVEL = 13;

    private MapView mMap;
    private ScaleBarOverlay mScaleBarOverlay;
    private TextView mNoteText;
    private boolean drawing;
    private static final float STROKE_WIDTH = 5f;

    /** Need to track this so the dirty region can accommodate the stroke. **/
    private static final float HALF_STROKE_WIDTH = STROKE_WIDTH / 2;

    private Paint paint = new Paint();
    private Path path = new Path();

    /**
     * Optimizes painting by invalidating the smallest possible area.
     */
    private float lastTouchX;
    private float lastTouchY;
    private final RectF dirtyRect = new RectF();

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Permissions handling bypassed for now as I set minSDK to 5.1

        // OSM Map setup gleaned from GitHub/osmdroid, at
        // https://github.com/osmdroid/osmdroid/wiki/How-to-use-the-osmdroid-library
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));

        // Inflate and create the view including the mMap
        setContentView(R.layout.activity_main);
        mMap = findViewById(R.id.map);
        mNoteText = findViewById(R.id.noteView);

        mMap.setTileSource(TileSourceFactory.MAPNIK);

        setEnableMapControls(true);

        // Get last known location (should use a callback to find current, but this will do for now)
        Location where = ((LocationManager)getSystemService(Context.LOCATION_SERVICE)).getLastKnownLocation(LocationManager.GPS_PROVIDER);

        if (where == null) {
            mNoteText.setText("Sorry, could not get current location");
            Log.wtf(TAG, "No Last Location, alas");
            return;
        }

        IMapController mapController = mMap.getController();
        mapController.setZoom(INITIAL_ZOOM_LEVEL);
        GeoPoint origin = new GeoPoint(where.getLatitude(), where.getLongitude());
        mapController.setCenter(origin);

        mMap.getOverlays().add(mDrawingOverlay);

        // map scale
        mScaleBarOverlay = new ScaleBarOverlay(mMap);
        mScaleBarOverlay.setAlignRight(true);
        mScaleBarOverlay.setAlignBottom(true);
        mMap.getOverlays().add(mScaleBarOverlay);

        paint.setAntiAlias(true);
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStrokeWidth(STROKE_WIDTH);
    }

    private void setEnableMapControls(Boolean b) {
        drawing = !b;
        mMap.setBuiltInZoomControls(b);
        mMap.setMultiTouchControls(b);
    }

    /**
     * The Overlay for drawing the line onto
     */
    private Overlay mDrawingOverlay = new Overlay() {
        @Override
        public void draw(Canvas canvas, MapView osmv, boolean shadow) {
            canvas.drawPath(path, paint);
        }
    };

    public void startDrawing(View v) {
        Log.d(TAG, "startDrawing");
        // We set the onTouchListener here, which disables all Map controls.
        // C'est la vie(w); we just want to draw lines on the map.
        // Fancier: a button to disable this so use can go back to dragging, zooming map.
        mMap.setOnTouchListener(onTouchListener);
        setEnableMapControls(false);
    }

    public void stopDrawing(View v) {
        Log.d(TAG, "stopDrawing");
        setEnableMapControls(true);
    }

    Executor threadPool = Executors.newSingleThreadExecutor();

    public void saveDrawing(View v) {
        if (line.size() == 0) {
            Toast.makeText(this, "Nothing to save", Toast.LENGTH_SHORT).show();
            return;
        }
        if (!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            Toast.makeText(this, "Unable to save; media not mounted", Toast.LENGTH_LONG).show();
            return;
        }
        Toast.makeText(this, "Saving " + line.size() + " points", Toast.LENGTH_SHORT).show();
        String fileName = FileNameUtils.getNextFilename();
        threadPool.execute( () -> {
            final BoundingBox bbox = mDrawingOverlay.getBounds();
            GPSFileSaver saver = new GPSFileSaver(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), fileName);
            saver.startFile();

            for (PointF pf : line) {
                final float x = pf.x;
                final float y = pf.y;

                final IGeoPoint iGeoPoint = mMap.getProjection().fromPixels((int) pf.x, (int) pf.y);
                saver.write(System.currentTimeMillis(), iGeoPoint.getLatitude(), iGeoPoint.getLongitude());
            }
            saver.close();
        });
        Toast.makeText(this, "File saved in " + fileName, Toast.LENGTH_LONG).show();
    }

    public void discardDrawing(View v) {
        line.clear();
        mMap.invalidate();
    }

    List<PointF> line = new ArrayList<>();
    float lastAddedX, lastAddedY;
    private void addPoint(float x, float y) {
        if (lastAddedX == x && lastAddedY == y) {
            return; // trim dupes
        }
        PointF point = new PointF(lastAddedX = x, lastAddedY = y);
        Log.d(TAG, "Added: " + point);
        line.add(point);
    }

    /**
     * The drawing code listener; original from my Android Cookbook
     * https://androidcookbook.com/Recipe.seam?recipeId=3512
     */
    private final View.OnTouchListener onTouchListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            Log.d(TAG, "onTouch: " + event + "; drawing: " + drawing);
            if (!drawing) {
                return mDrawingOverlay.onTouchEvent(event, mMap);
            }
            float eventX = event.getX();
            float eventY = event.getY();

            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    path.moveTo(eventX, eventY);
                    addPoint(eventX, eventY);
                    lastTouchX = eventX;
                    lastTouchY = eventY;
                    // No end point yet, so don't waste cycles invalidating.
                    return true;

                case MotionEvent.ACTION_MOVE:
                case MotionEvent.ACTION_UP:
                    // Start tracking the dirty region.
                    resetDirtyRect(eventX, eventY);

                    // When the hardware tracks events faster than
                    // they can be delivered to the app, the
                    // event will contain a history of those skipped points.
                    int historySize = event.getHistorySize();
                    for (int i = 0; i < historySize; i++) {
                        float historicalX = event.getHistoricalX(i);
                        float historicalY = event.getHistoricalY(i);
                        expandDirtyRect(historicalX, historicalY);
                        path.lineTo(historicalX, historicalY);
                        addPoint(historicalX, historicalY);
                    }

                    // After replaying history, connect the line to the touch point.
                    path.lineTo(eventX, eventY);
                    addPoint(eventX, eventY);
                    break;

                default:
                    Log.d(TAG, "Unknown touch event  " + event.toString());
                    return false;
            }

            // Include half the stroke width to avoid clipping.
            mMap.invalidate(
                    (int) (dirtyRect.left - HALF_STROKE_WIDTH),
                    (int) (dirtyRect.top - HALF_STROKE_WIDTH),
                    (int) (dirtyRect.right + HALF_STROKE_WIDTH),
                    (int) (dirtyRect.bottom + HALF_STROKE_WIDTH));

            lastTouchX = eventX;
            lastTouchY = eventY;

            return true;
        }

        /**
         * Called when replaying history to ensure the dirty region
         * includes all points.
         */
        private void expandDirtyRect(float historicalX, float historicalY) {
            if (historicalX < dirtyRect.left) {
                dirtyRect.left = historicalX;
            } else if (historicalX > dirtyRect.right) {
                dirtyRect.right = historicalX;
            }
            if (historicalY < dirtyRect.top) {
                dirtyRect.top = historicalY;
            } else if (historicalY > dirtyRect.bottom) {
                dirtyRect.bottom = historicalY;
            }
        }

        /**
         * Resets the dirty region when the motion event occurs.
         */
        private void resetDirtyRect(float eventX, float eventY) {

            // The lastTouchX and lastTouchY were set when the ACTION_DOWN
            // motion event occurred.
            dirtyRect.left = Math.min(lastTouchX, eventX);
            dirtyRect.right = Math.max(lastTouchX, eventX);
            dirtyRect.top = Math.min(lastTouchY, eventY);
            dirtyRect.bottom = Math.max(lastTouchY, eventY);
        }
    };

    public void onPause(){
        super.onPause();
        // Also pause the osmdroid configuration on pausing.
        mMap.onPause();  //needed for compass, my location overlays, v6.0.0 and up
    }

    public void onResume(){
        super.onResume();
        // Also refresh the osmdroid configuration
        mMap.onResume(); //needed for compass, my location overlays, v6.0.0 and up
    }
}
