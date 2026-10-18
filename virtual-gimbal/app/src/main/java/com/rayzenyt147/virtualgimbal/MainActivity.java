package com.rayzenyt147.virtualgimbal;

import android.Manifest;
import android.content.pm.PackageManager;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.app.AlertDialog;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import androidx.activity.ComponentActivity;
import androidx.annotation.NonNull;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutionException;

public class MainActivity extends ComponentActivity implements SensorEventListener {
    private static final int REQUEST_CAMERA = 41;
    private FrameLayout root;
    private PreviewView previewView;
    private Joystick joystick;
    private TextView status;
    private String mode = "BOTH";
    private boolean landscape = false;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private float lastX = Float.NaN, lastY = Float.NaN;
    private float panX = 0f, panY = 0f;
    private float zoom = 1.55f;
    private int accent = Color.rgb(91, 225, 190);

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        sensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        buildUi();
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) startCamera();
        else ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, REQUEST_CAMERA);
    }

    private void buildUi() {
        final boolean wide = getResources().getConfiguration().orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        previewView = new PreviewView(this);
        previewView.setScaleType(PreviewView.ScaleType.FILL_CENTER);
        FrameLayout.LayoutParams pv = new FrameLayout.LayoutParams(-1, -1);
        root.addView(previewView, pv);

        LinearLayout top = new LinearLayout(this);
        top.setOrientation(LinearLayout.VERTICAL);
        top.setPadding(dp(wide ? 8 : 16), dp(wide ? 5 : 14), dp(wide ? 8 : 16), dp(wide ? 3 : 8));
        top.setBackgroundColor(0x99090F15);
        TextView title = new TextView(this);
        title.setText("VIRTUAL GIMBAL");
        title.setTextColor(Color.WHITE);
        title.setTextSize(wide ? 13 : 17);
        title.setLetterSpacing(0.14f);
        title.setTypeface(null, 1);
        top.addView(title);
        status = new TextView(this);
        status.setText("LIVE PREVIEW  •  NO RECORDING");
        status.setTextColor(accent);
        status.setTextSize(wide ? 9 : 11);
        status.setPadding(0, dp(4), 0, 0);
        top.addView(status);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        root.addView(top, tp);

        // Keep the live camera unobstructed; settings live in a compact corner menu.
        Button settings = makeButton("⚙");
        settings.setTextSize(20);
        settings.setContentDescription("Settings");
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(dp(48), dp(44), Gravity.TOP | Gravity.END);
        sp.setMargins(0, dp(wide ? 4 : 10), dp(wide ? 6 : 10), 0);
        root.addView(settings, sp);

        joystick = new Joystick(this);
        int stickSize = wide ? 104 : 124;
        FrameLayout.LayoutParams jp = new FrameLayout.LayoutParams(dp(stickSize), dp(stickSize),
                wide ? (Gravity.BOTTOM | Gravity.START) : (Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        jp.setMargins(dp(wide ? 14 : 0), 0, 0, dp(wide ? 14 : 22));
        root.addView(joystick, jp);

        settings.setOnClickListener(v -> showSettings(wide));
        setContentView(root);
        updateModeButtons(modes);
        joystick.setOnMove((x, y) -> {
            if (mode.equals("JOYSTICK") || mode.equals("BOTH")) {
                panX = clamp(panX - x * dp(5), -dp(130), dp(130));
                panY = clamp(panY - y * dp(5), -dp(130), dp(130));
                applyPan();
            }
        });
    }

    private void showSettings(boolean wide) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(8), dp(18), dp(8));
        TextView modeLabel = new TextView(this);
        modeLabel.setText("CONTROL MODE");
        modeLabel.setTextColor(0xFFB8C6D2);
        modeLabel.setTextSize(12);
        modeLabel.setPadding(0, dp(6), 0, dp(6));
        panel.addView(modeLabel);
        LinearLayout modes = new LinearLayout(this);
        modes.setGravity(Gravity.CENTER);
        String[] choices = {"JOYSTICK", "GYRO", "BOTH"};
        for (String choice : choices) {
            Button b = makeButton(choice);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, dp(42), 1);
            bp.setMargins(dp(3), dp(2), dp(3), dp(8));
            modes.addView(b, bp);
            b.setOnClickListener(v -> {
                mode = choice;
                updateModeButtons(modes);
                lastX = lastY = Float.NaN;
                if (status != null) status.setText("CAMERA LIVE  •  " + mode + " CONTROL");
            });
        }
        panel.addView(modes);
        updateModeButtons(modes);
        Button orientation = makeButton(landscape ? "SWITCH TO PORTRAIT" : "SWITCH TO LANDSCAPE");
        panel.addView(orientation, new LinearLayout.LayoutParams(-1, dp(44)));
        orientation.setOnClickListener(v -> {
            landscape = !landscape;
            setRequestedOrientation(landscape ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        });
        TextView help = new TextView(this);
        help.setText("Move the joystick to reposition the camera crop. Tilt controls are available in GYRO and BOTH modes.");
        help.setTextColor(0xFFB8C6D2);
        help.setTextSize(12);
        help.setPadding(0, dp(12), 0, dp(4));
        panel.addView(help);
        new AlertDialog.Builder(this)
                .setTitle("VIRTUAL GIMBAL SETTINGS")
                .setView(panel)
                .setNegativeButton("CLOSE", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void updateModeButtons(LinearLayout row) {
        for (int i=0; i<row.getChildCount(); i++) {
            Button b = (Button)row.getChildAt(i);
            boolean selected = b.getText().toString().equals(mode);
            b.setTextColor(selected ? Color.BLACK : Color.WHITE);
            b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(selected ? accent : 0xFF263442));
        }
    }

    private Button makeButton(String text) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(11);
        b.setTextColor(Color.WHITE);
        b.setPadding(dp(4), 0, dp(4), 0);
        b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xFF263442));
        return b;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);
        future.addListener(() -> {
            try {
                ProcessCameraProvider provider = future.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                provider.unbindAll();
                provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview);
                status.setText("CAMERA LIVE  •  " + mode + " CONTROL");
            } catch (ExecutionException | InterruptedException e) {
                status.setText("CAMERA START FAILED");
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_CAMERA && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) startCamera();
        else status.setText("CAMERA PERMISSION REQUIRED");
    }

    private void applyPan() {
        if (previewView == null) return;
        previewView.setScaleX(zoom);
        previewView.setScaleY(zoom);
        previewView.setTranslationX(panX);
        previewView.setTranslationY(panY);
    }

    @Override public void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometer != null)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
    }
    @Override public void onPause() {
        if (sensorManager != null) sensorManager.unregisterListener(this);
        super.onPause();
    }
    @Override public void onSensorChanged(SensorEvent event) {
        if (!mode.equals("GYRO") && !mode.equals("BOTH")) { lastX = event.values[0]; lastY = event.values[1]; return; }
        float x = event.values[0], y = event.values[1];
        if (Float.isNaN(lastX)) { lastX=x; lastY=y; return; }
        float dx = x-lastX, dy = y-lastY;
        lastX=x; lastY=y;
        panX = clamp(panX + dx * 1.5f, -dp(130), dp(130));
        panY = clamp(panY + dy * 1.5f, -dp(130), dp(130));
        applyPan();
    }
    @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
    private float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    private interface MoveListener { void move(float x, float y); }
    private class Joystick extends View {
        private final Paint p = new Paint(3);
        private float cx, cy, knobX, knobY, radius;
        private MoveListener listener;
        Joystick(Context c) { super(c); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        void setOnMove(MoveListener l) { listener=l; }
        @Override protected void onDraw(Canvas c) {
            super.onDraw(c);
            cx=getWidth()/2f; cy=getHeight()/2f; radius=Math.min(cx,cy)*0.78f;
            p.setColor(0x553B5264); p.setStyle(Paint.Style.FILL); c.drawCircle(cx,cy,radius,p);
            p.setColor(0xFF526779); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); c.drawCircle(cx,cy,radius,p);
            p.setStyle(Paint.Style.FILL); p.setColor(accent);
            c.drawCircle(cx+knobX,cy+knobY,radius*0.31f,p);
            p.setColor(0xFF09201B); c.drawCircle(cx+knobX,cy+knobY,radius*0.11f,p);
        }
        @Override public boolean onTouchEvent(MotionEvent e) {
            if (!mode.equals("GYRO")) {
                float x=e.getX()-getWidth()/2f, y=e.getY()-getHeight()/2f;
                float max=Math.min(getWidth(),getHeight())*0.34f;
                float len=(float)Math.sqrt(x*x+y*y);
                if (len>max) { x=x*max/len; y=y*max/len; }
                if (e.getAction()==MotionEvent.ACTION_UP || e.getAction()==MotionEvent.ACTION_CANCEL) { knobX=knobY=0; invalidate(); return true; }
                knobX=x; knobY=y; invalidate();
                if (listener!=null) listener.move(x/max,y/max);
            }
            return true;
        }
    }
}
