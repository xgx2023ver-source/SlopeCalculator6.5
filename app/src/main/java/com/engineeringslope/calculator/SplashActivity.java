package com.engineeringslope.calculator;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowCompat;

/**
 * SplashActivity -- 启动闪屏页
 *
 * 职责:
 *   1. 应用冷启动时展示品牌图标,掩盖 MainActivity 的加载耗时
 *   2. 播放一段"图标放大 + 渐隐消失"的丝滑动画
 *   3. 动画结束后自动跳转到主界面 MainActivity
 *
 * 生命周期: onCreate -> 动画播放 -> launchMainActivity -> finish(), 不再返回
 */
public class SplashActivity extends AppCompatActivity {

    /* ===== 常量 ===== */

    /**
     * 整个闪屏动画的总时长(毫秒)
     * 拆分: 静止展示 200ms + 放大渐隐 800ms + 跳转过渡 = 800ms
     * 实际控制跳转时机的是 postDelayed(200) + animSet(800) = 1000ms 后跳转
     */
    private static final int SPLASH_DURATION = 1800;

    /* ===== 生命周期 ===== */

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // ---------- 全屏 & 刘海屏适配 ----------
        Window window = getWindow();

        // Android P (9.0) 及以上支持刘海屏(水滴屏/挖孔屏)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = window.getAttributes();
            // SHORT_EDGES = 内容延伸到刘海区域两侧,实现真正的全面屏效果
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            window.setAttributes(lp);
        }

        // setDecorFitsSystemWindows(false) = 沉浸式布局
        // 系统栏(状态栏,导航栏)不再挤压内容,内容绘制到系统栏背后
        // 配合 xml 中的 fitsSystemWindows / padding 使用来手动控制避让
        WindowCompat.setDecorFitsSystemWindows(window, false);

        // 设置状态栏颜色为深蓝,与闪屏背景统一
        window.setStatusBarColor(Color.parseColor("#5076b3"));

        // ---------- 加载布局 ----------
        setContentView(R.layout.activity_splash);

        // 查找布局中的图标 ImageView,标题文字 和根容器
        ImageView iconView = findViewById(R.id.splashIcon);
        TextView titleView = findViewById(R.id.splashTitle);
        View rootView = findViewById(R.id.splashRoot);

        // 设置根布局背景色为深蓝(与状态栏一致,视觉上没有断裂感)
        rootView.setBackgroundColor(Color.parseColor("#5076b3"));

        // ---------- 启动动画 ----------
        startSplashAnimation(iconView, titleView);
    }

    /* ===== 动画逻辑 ===== */

    /**
     * 分两阶段执行闪屏动画:
     *
     *   +-------------------------------------------------+
     *   |  阶段1(0~200ms): 图标静止显示,用户看清品牌     |
     *   |  阶段2(200~1000ms): 图标静止 + 透明度渐隐到0   |
     *   |       动画结束后 -> 跳转 MainActivity           |
     *   +-------------------------------------------------+
     *
     * @param iconView  需要做动画的图标 ImageView
     * @param titleView "工程坡计算器"标题 TextView
     */
    private void startSplashAnimation(ImageView iconView, TextView titleView) {

        // postDelayed 200ms: 让图标先静止展示一小段时间
        // 目的: (1) 给用户辨识品牌的时间  (2) 等待 MainActivity 完成初始化
        new Handler(Looper.getMainLooper()).postDelayed(() -> {

            /* ---------- 构建属性动画 ---------- */

            // scaleX: 图标 X 轴从 1.0 缩放到 1.0(水平)
            ObjectAnimator scaleX = ObjectAnimator.ofFloat(iconView, "scaleX", 1f, 1.0f);

            // scaleY: 图标 Y 轴从 1.0 缩放到 1.0(垂直)
            ObjectAnimator scaleY = ObjectAnimator.ofFloat(iconView, "scaleY", 1f, 1.0f);

            // alpha: 图标从完全不透明(1.0) 渐隐到完全透明(0.0)
            // 实际的"模糊"效果在这里用透明度模拟--越放越大越看不清 = 伪模糊
            ObjectAnimator alpha = ObjectAnimator.ofFloat(iconView, "alpha", 1f, 0f);

            /* ---------- 组合动画 ---------- */

            AnimatorSet animSet = new AnimatorSet();
            // playTogether = 三个动画同时执行(放大和渐隐同步进行)
            animSet.playTogether(scaleX, scaleY, alpha);

            // 动画时长 800ms
            animSet.setDuration(800);

            // AccelerateDecelerateInterpolator = 先慢后快再慢的缓动曲线
            // 效果: 启动时柔和加速,结束时柔和减速,整体感觉"丝滑"
            animSet.setInterpolator(new AccelerateDecelerateInterpolator());

            /* ---------- 动画结束回调 ---------- */

            animSet.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    // 动画播完后,跳转到主界面
                    launchMainActivity();
                }
            });

            // 启动动画
            animSet.start();

            // 图标开始模糊淡出的同时,"工程坡计算器"文字淡入显示
            //new Handler(Looper.getMainLooper()).postDelayed(() ->
                    //titleView.animate().alpha(1f).setDuration(300).start(), 0);
            titleView.animate().alpha(1f).setDuration(300).withEndAction(() -> 
                    titleView.animate().alpha(0f).setDuration(500).start()).start();

        }, 200); // <- 延迟 200ms 后才开始执行(阶段1的静止展示时间)
    }

    /* ===== 页面跳转 ===== */

    /**
     * 跳转到 MainActivity 并结束当前 SplashActivity
     *
     * 注意:
     *   - finish() 后 SplashActivity 从回栈中移除,用户按返回不会回到闪屏
     *   - overridePendingTransition 使用 fade_in/fade_out 实现淡入淡出过渡,
     *     让 MainActivity 的出现和闪屏的消失衔接得更自然
     */
    private void launchMainActivity() {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        // fade_in = MainActivity 淡入; fade_out = SplashActivity 淡出
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        // 销毁闪屏页,释放内存,不留在回栈中
        finish();
    }

    /* ===== 返回键拦截 ===== */

    /**
     * 拦截返回键,禁止用户在闪屏期间返回
     *
     * 原因:
     *   - 闪屏是应用入口,按返回应该退出应用(由系统处理)
     *   - 如果不拦截,用户可能按返回后看到空白或异常状态
     *   - 空实现 = 吞掉返回事件,什么都不做
     */
    @Override
    public void onBackPressed() {
        // 空实现: 禁止返回
    }
}
