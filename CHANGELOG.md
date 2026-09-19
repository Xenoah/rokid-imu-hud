# Changelog / 変更履歴

## [v0.1.1-preview](https://github.com/Xenoah/rokid-imu-hud/releases/tag/v0.1.1-preview)

DecorView生成前のInsetsController参照を避け、View生成後にシステムバーを設定するよう修正。起動失敗時のログ採取も改善しました。

Moves system-bar configuration after view creation, avoiding early InsetsController access. Improves log capture when startup fails.

## [v0.1.0-preview](https://github.com/Xenoah/rokid-imu-hud/releases/tag/v0.1.0-preview)

水平線、Pitch ladder、Roll目盛、固定機体マーカー、±2Gメーター、Peak Hold、3表示モードを実装。OS Quaternionを優先し、自前Mahony融合へフォールバックします。

Introduces the artificial horizon, pitch ladder, roll scale, fixed aircraft marker, ±2G meter, peak hold and three display modes. Prefers the OS quaternion, with Mahony fusion as a fallback.
