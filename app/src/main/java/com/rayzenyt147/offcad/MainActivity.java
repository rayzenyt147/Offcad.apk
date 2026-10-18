package com.rayzenyt147.offcad;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.Window;
import android.view.WindowManager;
import android.view.View;
import android.widget.Toast;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int REQUEST_SAVE = 4101;
    private static final int REQUEST_OPEN = 4102;
    private WebView webView;
    private ValueCallback<Uri[]> fileChooserCallback;
    private String pendingSaveData;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        enableImmersiveMode();
        webView = new WebView(this);
        webView.setBackgroundColor(0xFF101820);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        webView.addJavascriptInterface(new OffcadBridge(), "OffcadBridge");
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null);
                fileChooserCallback = callback;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/json", "text/plain", "application/octet-stream"});
                try { startActivityForResult(intent, REQUEST_OPEN); }
                catch (Exception e) { fileChooserCallback = null; callback.onReceiveValue(null); Toast.makeText(MainActivity.this, "Unable to open file picker", Toast.LENGTH_LONG).show(); }
                return true;
            }
        });
        setContentView(webView);
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class OffcadBridge {
        @JavascriptInterface public void saveFile(String data) {
            runOnUiThread(() -> {
                pendingSaveData = data;
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "my-circuit.json");
                try { startActivityForResult(intent, REQUEST_SAVE); }
                catch (Exception e) { Toast.makeText(MainActivity.this, "Unable to open Save dialog", Toast.LENGTH_LONG).show(); }
            });
        }
        @JavascriptInterface public void autosaveProject(String content) {
            try {
                File folder = new File(getFilesDir(), "OFFCAD");
                if (!folder.exists() && !folder.mkdirs()) return;
                File target = new File(folder, "autosave.json");
                File temp = new File(folder, "autosave.tmp");
                try (FileOutputStream out = new FileOutputStream(temp)) {
                    out.write(content.getBytes(StandardCharsets.UTF_8));
                    out.getFD().sync();
                }
                if (target.exists() && !target.delete()) return;
                if (!temp.renameTo(target)) {
                    try (FileInputStream in = new FileInputStream(temp);
                         FileOutputStream out = new FileOutputStream(target)) {
                        byte[] buffer = new byte[8192]; int n;
                        while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                    }
                    temp.delete();
                }
            } catch (Exception ignored) { }
        }
        @JavascriptInterface public String loadAutosave() {
            File target = new File(new File(getFilesDir(), "OFFCAD"), "autosave.json");
            if (!target.isFile()) return "";
            try (InputStream in = new FileInputStream(target);
                 ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            } catch (Exception e) { return ""; }
        }
        @JavascriptInterface public void openFile() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"application/json", "text/json", "text/plain"});
                try { startActivityForResult(intent, REQUEST_OPEN); }
                catch (Exception e) { Toast.makeText(MainActivity.this, "Unable to open file picker", Toast.LENGTH_LONG).show(); }
            });
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_SAVE) {
            if (resultCode == RESULT_OK && data != null && data.getData() != null && pendingSaveData != null) {
                try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
                    if (out == null) throw new Exception("Cannot open destination");
                    out.write(pendingSaveData.getBytes(StandardCharsets.UTF_8));
                    Toast.makeText(this, "Project saved", Toast.LENGTH_SHORT).show();
                } catch (Exception e) {
                    Toast.makeText(this, "Save failed: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            }
            pendingSaveData = null;
            return;
        }
        if (requestCode == REQUEST_OPEN) {
            Uri uri = resultCode == RESULT_OK && data != null ? data.getData() : null;
            if (fileChooserCallback != null) {
                fileChooserCallback.onReceiveValue(uri == null ? null : new Uri[]{uri});
                fileChooserCallback = null;
            }
            if (uri != null && webView != null) {
                try (InputStream in = getContentResolver().openInputStream(uri);
                     ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    if (in == null) throw new Exception("Cannot read selected file");
                    byte[] buf = new byte[8192]; int n;
                    while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                    String content = new String(out.toByteArray(), StandardCharsets.UTF_8);
                    String escaped = org.json.JSONObject.quote(content);
                    webView.evaluateJavascript("window.offcadLoadFileText(" + escaped + ")", null);
                } catch (Exception e) {
                    Toast.makeText(this, "Could not read project file", Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    private void enableImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) enableImmersiveMode(); }
    @Override public void onBackPressed() { if (webView != null && webView.canGoBack()) webView.goBack(); else super.onBackPressed(); }
    @Override protected void onDestroy() { if (fileChooserCallback != null) fileChooserCallback.onReceiveValue(null); if (webView != null) webView.destroy(); super.onDestroy(); }
}