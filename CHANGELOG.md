# Changelog / 変更履歴

## [v0.1.5](https://github.com/Xenoah/rokid-imu-hud/releases/tag/v0.1.5)

通常は約1秒で校正。10秒で静止判定を緩和し、15秒でも未完了なら新鮮で妥当なIMU入力から暫定基準でHUDを開始します。緩和・暫定開始はAPPROXと表示。欠測や不正入力ではIMU DATA ERRORを表示し、無期限に校正を続けません。

Normally calibrates in about one second. Relaxes stillness criteria after 10 seconds and starts with a provisional reference after 15 seconds if fresh, plausible IMU input is available. Relaxed/provisional results display APPROX. Missing or invalid input produces IMU DATA ERROR instead of waiting indefinitely.

## [v0.1.4-preview](https://github.com/Xenoah/rokid-imu-hud/releases/tag/v0.1.4-preview)

OS姿勢だけの変化で生IMUの集計窓を消去しないように変更。不安定なOS姿勢は棄却し、本体の加速度・ジャイロによる融合へ切り替えます。

Keeps the raw-IMU statistics window when only OS attitude changes. Rejects unstable OS attitude and switches to accelerometer/gyroscope fusion on the glasses.

## [v0.1.3-preview](https://github.com/Xenoah/rokid-imu-hud/releases/tag/v0.1.3-preview)

OS姿勢の変化も静止判定に使用し、OS姿勢から求めた回転分を差し引いてジャイロバイアスを推定します。

Uses OS attitude changes alongside angular velocity for calibration, subtracting estimated real rotation when estimating gyro bias.

## [v0.1.2-preview](https://github.com/Xenoah/rokid-imu-hud/releases/tag/v0.1.2-preview)

時間窓の統計による静止判定、低頻度のOS姿勢への対応、重力と整合しない姿勢の棄却を追加。待機理由、ACC、GYRO、取得Hzを表示します。

Uses windowed statistics for stillness detection, tolerates slower OS attitude updates, and rejects attitude inconsistent with gravity. Displays the wait reason, accelerometer, gyroscope and measured sample rates.

## [v0.1.1-preview](https://github.com/Xenoah/rokid-imu-hud/releases/tag/v0.1.1-preview)

DecorView生成前のInsetsController参照を避け、View生成後にシステムバーを設定するよう修正。起動失敗時のログ採取も改善しました。

Moves system-bar configuration after view creation, avoiding early InsetsController access. Improves log capture when startup fails.

## [v0.1.0-preview](https://github.com/Xenoah/rokid-imu-hud/releases/tag/v0.1.0-preview)

水平線、Pitch ladder、Roll目盛、固定機体マーカー、±2Gメーター、Peak Hold、3表示モードを実装。OS Quaternionを優先し、自前Mahony融合へフォールバックします。

Introduces the artificial horizon, pitch ladder, roll scale, fixed aircraft marker, ±2G meter, peak hold and three display modes. Prefers the OS quaternion, with Mahony fusion as a fallback.
