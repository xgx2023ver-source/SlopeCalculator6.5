# 工程坡计算器 Android App

这是一个将工程坡计算器Web应用封装为Android原生应用的项目。

## 功能特性

- **坡长计算**：支持已知高度差和已知坡高和坡底两种模式
- **面积与砖块**：坡面面积和砖块数量计算
- **土方量计算**：工程土方量估算
- **缓坡分段**：缓坡分段计算

## 特性

- ✅ 完全还原HTML原始功能、布局、排版
- ✅ 保留所有切换动画
- ✅ 震动反馈和语音播报
- ✅ 自适应刘海屏、水滴屏、挖孔屏
- ✅ 无需网络权限，纯离线运行
- ✅ 自动清理缓存
- ✅ 5秒卡死自动重启
- ✅ 丝滑闪屏动画

## 构建

### 本地构建

```bash
./gradlew assembleDebug
```

APK输出位置：`app/build/outputs/apk/debug/app-debug.apk`

### GitHub Actions 构建

1. 将项目推送到GitHub仓库
2. 进入仓库的 Actions 页面
3. 点击 "Build Android APK" workflow
4. 点击 "Run workflow"
5. 构建完成后在 Artifacts 中下载 APK

## 环境要求

- JDK 17
- Android SDK (compileSdk 34, minSdk 21)
- Gradle 8.3
