# iTap

**中文** | [English](#english)

---

## 中文

### 简介

**iTap** 是一款 Android 无障碍应用，在同一无障碍服务里提供两种能力：

| 名称 | 含义 | 操作方式 |
|------|------|----------|
| **AutoTap** | 自动连点 | 短按顶部悬浮钮开关；点击坐标带轻微随机抖动 |
| **Hold** | 双指长按手势 | 音量 **+** 与 **−** 在 **1.2 秒内**先后按下触发；再次双键进入释放 |

### 主要功能

- **AutoTap**：在屏幕固定区域循环注入短点击；每轮约 250 次后长休息；可停止/冷却。
- **Hold**：右指按下 → 双指阶段 → `continueStroke` 续接链；续接被系统取消时通过 `consumeNextIdleComboAsLiftOnly` 等标志做恢复与补抬处理。
- **互斥**：进入 Hold 前会停掉 AutoTap 并移除悬浮钮；需再用 AutoTap 时 **再点一次桌面图标**（发广播重新挂载面板）。
- **前台保活**：服务连接后使用低优先级通知前台化，减轻 OEM 杀进程（无法阻止系统在设置里关闭无障碍）。
- **悬浮窗类型**：`TYPE_ACCESSIBILITY_OVERLAY`，无需 `SYSTEM_ALERT_WINDOW`（避免部分 ColorOS 对 `continueStroke` 的限制）。

### 环境要求

- Android **API 26+**（`minSdk 26`）
- 在系统设置中启用 **iTap 手势服务**无障碍
- 部分机型建议：忽略电池优化、允许自启动、多任务锁定（见应用内提示）

### 构建

```bash
bash scripts/release-apk-and-clean.sh
```

产物：**`dist/iTap.apk`**（脚本会在复制后执行 `gradle clean`）。

### 文档

- **[design.md](design.md)**：架构、模块职责、状态机与文件索引（面向开发者 / AI 读代码）。

---

## English

### Overview

**iTap** is an Android **AccessibilityService** app that offers two capabilities in one service:

| Name | Role | How to use |
|------|------|-------------|
| **AutoTap** | Automatic tapping | Short-press the floating toggle; taps are injected with small random jitter |
| **Hold** | Two-finger hold gesture | Press **Volume +** and **Volume −** within **1.2s** (order either way); press the combo again to release |

### Highlights

- **AutoTap**: Repeating short taps at a computed screen position; ~250 taps per burst then a long rest; stop/cooldown handling.
- **Hold**: Phase 1 (right finger) → Phase 2 (two fingers, `willContinue`) → `continueStroke` chain; when the chain breaks, flags such as `consumeNextIdleComboAsLiftOnly` handle recovery and lift-only paths.
- **Exclusion**: Starting Hold stops AutoTap and **removes** the floating button; tap the **launcher icon** again to re-show the panel (broadcast).
- **Foreground**: A low-importance ongoing notification raises process priority (cannot prevent the user/OEM from disabling accessibility in Settings).
- **Overlay type**: `TYPE_ACCESSIBILITY_OVERLAY` — no `SYSTEM_ALERT_WINDOW` declaration (helps on some ColorOS builds with gesture continuation).

### Requirements

- Android **API 26+**
- Enable the **iTap** accessibility service in system settings
- On some OEMs: battery whitelist / autostart / recents lock (see in-app hints)

### Build

```bash
bash scripts/release-apk-and-clean.sh
```

Output: **`dist/iTap.apk`** (the script runs `gradle clean` after copying the APK).

### Further reading

- **[design.md](design.md)** — architecture, module boundaries, state machine summary, and file index for developers / AI-assisted navigation.
