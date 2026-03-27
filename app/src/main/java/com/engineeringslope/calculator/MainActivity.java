package com.engineeringslope.calculator;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

import java.io.File;
import java.lang.reflect.Method;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    private WebView webView;
    private Handler handler;
    private Runnable freezeCheckRunnable;
    private static final long FREEZE_TIMEOUT = 5000; // 5 seconds
    private boolean isWebViewResponsive = true;

    // 反射持有 TTSBridge，编译期不依赖该类
    private Object ttsBridge;
    private Method mSpeak;
    private Method mStop;
    private Method mRelease;
    private Method mSetRate;
    private Method mSetPitch;
    private Method mIsReady;
    private Method mIsSpeaking;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 全屏 + 刘海屏适配
        setupFullScreen();

        setContentView(R.layout.activity_main);

        handler = new Handler(Looper.getMainLooper());

        // 反射初始化 TTS（TTSBridge 不存在则静默跳过）
        initTTSBridge();

        webView = findViewById(R.id.webView);
        setupWebView();

        // 冻结检测
        startFreezeDetection();

        // 加载HTML
        webView.loadUrl("file:///android_asset/3266.html");
    }

    /**
     * 通过反射加载 TTSBridge，找不到类就静默忽略。
     * 这样即使 TTSBridge.java 被删除，也不会影响编译和运行。
     */
    private void initTTSBridge() {
        try {
            Class<?> cls = Class.forName("com.engineeringslope.calculator.TTSBridge");
            java.lang.reflect.Constructor<?> ctor = cls.getConstructor(Context.class);
            ttsBridge = ctor.newInstance(this);
            mSpeak = cls.getMethod("speak", String.class);
            mStop = cls.getMethod("stop");
            mRelease = cls.getMethod("release");
            mSetRate = cls.getMethod("setRate", float.class);
            mSetPitch = cls.getMethod("setPitch", float.class);
            mIsReady = cls.getMethod("isReady");
            mIsSpeaking = cls.getMethod("isSpeaking");
        } catch (Exception e) {
            ttsBridge = null;
            Log.w(TAG, "TTSBridge not available, voice disabled");
        }
    }

    private void setupFullScreen() {
        Window window = getWindow();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }

        WindowCompat.setDecorFitsSystemWindows(window, false);

        WindowInsetsControllerCompat controller =
                new WindowInsetsControllerCompat(window, window.getDecorView());
        controller.setSystemBarsBehavior(
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);

        // 设置状态栏为透明，配合HTML header渐变
        window.setStatusBarColor(Color.TRANSPARENT);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 深色状态栏图标（适配浅色渐变背景）
            window.getDecorView().setSystemUiVisibility(
                    window.getDecorView().getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }

        // 保持屏幕常亮（可选）
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_NO_CACHE);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setUseWideViewPort(true);
        settings.setLoadWithOverviewMode(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(android.webkit.WebView view, String url) {
                super.onPageFinished(view, url);
                // 页面加载完成，重置冻结检测
                isWebViewResponsive = true;
            }
        });

        webView.setWebChromeClient(new WebChromeClient());

        // 添加JS接口用于震动和TTS
        webView.addJavascriptInterface(new WebAppInterface(this), "AndroidBridge");

        // 设置背景颜色
        webView.setBackgroundColor(Color.parseColor("#f2f4f6"));
    }

    private void startFreezeDetection() {
        freezeCheckRunnable = new Runnable() {
            @Override
            public void run() {
                if (!isWebViewResponsive) {
                    // 应用卡死超过5秒，自动重启
                    restartApp();
                    return;
                }
                isWebViewResponsive = false;

                // 通过JS ping检测WebView是否响应
                webView.evaluateJavascript(
                        "(function() { AndroidBridge.onPingResponse(); return 'ok'; })();",
                        null
                );

                handler.postDelayed(this, FREEZE_TIMEOUT);
            }
        };
        handler.postDelayed(freezeCheckRunnable, FREEZE_TIMEOUT);
    }

    private void restartApp() {
        Intent intent = new Intent(this, SplashActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
        Runtime.getRuntime().exit(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        webView.onPause();
    }

    @Override
    protected void onDestroy() {
        // 释放 TTS（反射调用）
        try {
            if (ttsBridge != null && mRelease != null) {
                mRelease.invoke(ttsBridge);
            }
        } catch (Exception ignored) {}
        ttsBridge = null;

        // 清理所有缓存
        cleanupCache();
        if (handler != null && freezeCheckRunnable != null) {
            handler.removeCallbacks(freezeCheckRunnable);
        }
        if (webView != null) {
            webView.removeAllViews();
            webView.destroy();
        }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            cleanupCache();
            super.onBackPressed();
        }
    }

    private void cleanupCache() {
        try {
            // 清理WebView缓存
            webView.clearCache(true);
            webView.clearHistory();
            webView.clearFormData();

            // 清理应用缓存目录
            File cacheDir = getCacheDir();
            if (cacheDir != null && cacheDir.exists()) {
                deleteRecursive(cacheDir);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void deleteRecursive(File fileOrDirectory) {
        if (fileOrDirectory.isDirectory()) {
            File[] children = fileOrDirectory.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        fileOrDirectory.delete();
    }

    // JavaScript接口
    public class WebAppInterface {
        Context context;

        WebAppInterface(Context context) {
            this.context = context;
        }

        @JavascriptInterface
        public void vibrate(int durationMs) {
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager vibratorManager =
                        (VibratorManager) getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = vibratorManager.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            }

            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(
                            durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(durationMs);
                }
            }
        }

        @JavascriptInterface
        public void onPingResponse() {
            isWebViewResponsive = true;
        }

        @JavascriptInterface
        public void showToast(String message) {
            handler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
        }

        // ========== TTS 桥接（反射调用，TTSBridge 不存在时静默忽略） ==========

        @JavascriptInterface
        public void ttsSpeak(String text) {
            handler.post(() -> {
                try {
                    if (ttsBridge != null && mSpeak != null) {
                        mSpeak.invoke(ttsBridge, text);
                    }
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void ttsStop() {
            handler.post(() -> {
                try {
                    if (ttsBridge != null && mStop != null) {
                        mStop.invoke(ttsBridge);
                    }
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void ttsSetRate(float rate) {
            handler.post(() -> {
                try {
                    if (ttsBridge != null && mSetRate != null) {
                        mSetRate.invoke(ttsBridge, rate);
                    }
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public void ttsSetPitch(float pitch) {
            handler.post(() -> {
                try {
                    if (ttsBridge != null && mSetPitch != null) {
                        mSetPitch.invoke(ttsBridge, pitch);
                    }
                } catch (Exception ignored) {}
            });
        }

        @JavascriptInterface
        public boolean ttsIsReady() {
            try {
                if (ttsBridge != null && mIsReady != null) {
                    return (boolean) mIsReady.invoke(ttsBridge);
                }
            } catch (Exception ignored) {}
            return false;
        }

        @JavascriptInterface
        public boolean ttsIsSpeaking() {
            try {
                if (ttsBridge != null && mIsSpeaking != null) {
                    return (boolean) mIsSpeaking.invoke(ttsBridge);
                }
            } catch (Exception ignored) {}
            return false;
        }
    }
}
