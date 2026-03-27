package com.engineeringslope.calculator;

import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Locale;

/**
 * TTS bridge —— 直接暴露给 WebView（@JavascriptInterface），
 * 省去 MainActivity 中的 WebAppInterface 中间层。
 *
 * WebView 调用： AndroidBridge.ttsSpeak("文字")
 *              AndroidBridge.ttsStop()
 *              AndroidBridge.ttsSetRate(1.5)
 *              AndroidBridge.ttsIsReady()
 */
public class TTSBridge {

    private static final String TAG = "TTSBridge";

    private final Context context;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private TextToSpeech tts;
    private volatile boolean initialized = false;
    private boolean speaking = false;

    // 竞态保护：TTS 尚未就绪时的待播报队列
    private final Queue<String> pendingQueue = new ArrayDeque<>();

    private OnSpeakListener listener;

    public interface OnSpeakListener {
        void onStart();
        void onDone();
        void onError();
    }

    public TTSBridge(Context context) {
        this.context = context;
        initTTS();
    }

    public void setListener(OnSpeakListener listener) {
        this.listener = listener;
    }

    private void initTTS() {
        tts = new TextToSpeech(context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.CHINESE);
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.w(TAG, "Chinese not available, fallback to default");
                    tts.setLanguage(Locale.getDefault());
                }
                tts.setPitch(1.0f);
                tts.setSpeechRate(1.0f);

                // API 21+ 用 AudioAttributes 指定媒体流（比 Bundle 参数兼容性好得多）
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    AudioAttributes attrs = new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build();
                    tts.setAudioAttributes(attrs);
                }

                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String utteranceId) {
                        speaking = true;
                        if (listener != null) listener.onStart();
                    }

                    @Override
                    public void onDone(String utteranceId) {
                        speaking = false;
                        if (listener != null) listener.onDone();
                    }

                    @Override
                    public void onError(String utteranceId) {
                        speaking = false;
                        if (listener != null) listener.onError();
                    }
                });

                initialized = true;

                // 就绪后，播放队列中积压的文本
                flushPending();
            } else {
                Log.e(TAG, "TTS init failed, status=" + status);
                try {
                    Intent installIntent = new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(installIntent);
                } catch (Exception e) {
                    Log.e(TAG, "Cannot open TTS install: " + e.getMessage());
                }
            }
        });
    }

    /**
     * TTS 引擎就绪后，把积压的文本全部播放。
     */
    private void flushPending() {
        String text;
        while ((text = pendingQueue.poll()) != null) {
            doSpeak(text);
        }
    }

    /**
     * 真正执行 TTS 播报。
     * 不传 Bundle 参数——兼容所有厂商 TTS 引擎。
     */
    private void doSpeak(String text) {
        if (tts == null) return;
        tts.stop();
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tts_" + System.currentTimeMillis());
    }

    /**
     * 对外接口：播报文本（JS 线程安全）。
     * 如果 TTS 尚未初始化完成，先加入队列等待。
     */
    @JavascriptInterface
    public void speak(String text) {
        if (text == null || text.isEmpty()) return;
        if (initialized) {
            // 确保在主线程执行 TTS 调用
            if (Looper.myLooper() == Looper.getMainLooper()) {
                doSpeak(text);
            } else {
                mainHandler.post(() -> doSpeak(text));
            }
        } else {
            pendingQueue.clear();
            pendingQueue.add(text);
        }
    }

    @JavascriptInterface
    public void stop() {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            doStop();
        } else {
            mainHandler.post(this::doStop);
        }
    }

    private void doStop() {
        if (tts != null) {
            tts.stop();
            speaking = false;
        }
        pendingQueue.clear();
    }

    @JavascriptInterface
    public void setRate(float rate) {
        mainHandler.post(() -> {
            if (tts != null) {
                tts.setSpeechRate(Math.max(0.1f, Math.min(3.0f, rate)));
            }
        });
    }

    @JavascriptInterface
    public void setPitch(float pitch) {
        mainHandler.post(() -> {
            if (tts != null) {
                tts.setPitch(Math.max(0.5f, Math.min(2.0f, pitch)));
            }
        });
    }

    @JavascriptInterface
    public boolean isSpeaking() {
        return speaking;
    }

    @JavascriptInterface
    public boolean isReady() {
        return initialized;
    }

    public void release() {
        pendingQueue.clear();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
        initialized = false;
        speaking = false;
    }

    // ===== 以下方法不暴露给 JS，仅 Java 调用 =====

    /** 获取 Context，供 vibration 等非 TTS 功能使用 */
    public Context getContext() {
        return context;
    }

    // ===== 震动（直接暴露给 JS）=====

    @JavascriptInterface
    public void vibrate(int durationMs) {
        try {
            Vibrator vibrator;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                VibratorManager mgr = (VibratorManager) context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE);
                vibrator = mgr.getDefaultVibrator();
            } else {
                vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
            }
            if (vibrator != null && vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator.vibrate(VibrationEffect.createOneShot(durationMs, VibrationEffect.DEFAULT_AMPLITUDE));
                } else {
                    vibrator.vibrate(durationMs);
                }
            }
        } catch (Exception ignored) {}
    }

    // ===== Toast（暴露给 JS）=====

    @JavascriptInterface
    public void showToast(String message) {
        mainHandler.post(() -> Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
    }

    // ===== 冻结检测回调 =====

    private Runnable pingCallback;

    /** 由 MainActivity 设置，用于冻结检测恢复响应 */
    public void setPingCallback(Runnable callback) {
        this.pingCallback = callback;
    }

    @JavascriptInterface
    public void onPingResponse() {
        if (pingCallback != null) pingCallback.run();
    }
}
