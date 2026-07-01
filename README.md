# 台球瞄准辅助悬浮窗

一款辅助台球游戏的悬浮窗应用，通过半透明叠加层帮助玩家找到最佳进球路线。

## 功能特性

- 半透明悬浮窗叠加在游戏画面上
- 支持0-3库反弹路线计算
- 可拖动调整白球、目标球和球袋位置
- 透明度、大小可调
- 多库反弹路线自动计算
- 屏幕边缘自动吸附
- 进程守护，防止被系统杀死

## 兼容性

- Android 7.0 (API 24) ~ Android 15 (API 35+)
- 适配刘海屏、折叠屏、平板
- 适配深色/浅色模式

## 构建

GitHub Actions 自动构建 APK，或本地使用 Android Studio 构建。

## 权限

- `SYSTEM_ALERT_WINDOW` - 悬浮窗权限
- `FOREGROUND_SERVICE` - 前台服务
- `FOREGROUND_SERVICE_SPECIAL_USE` - Android 14+ 前台服务类型
- `POST_NOTIFICATIONS` - Android 13+ 通知权限
- `RECEIVE_BOOT_COMPLETED` - 开机自启（可选）
- `SCHEDULE_EXACT_ALARM` - Android 12+ 精确闹钟（进程守护）
- `WAKE_LOCK` - 保持唤醒（进程守护）
