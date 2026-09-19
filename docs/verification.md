# 検証記録

更新日：2026-09-19 UTC。実機は接続されていません。

## 実施した検証

| 検証 | 結果 | 範囲 |
| --- | --- | --- |
| 演算・共通描画Javaのコンパイル | 成功 | JDK 17.0.20、`--release 17 -Xlint:all` |
| 模擬IMU回帰試験 | 16項目合格 | `CoreTests`。ログは `artifacts/core-test-results.txt` |
| デスクトップ描画 | 7画像生成 | アプリと同一 `HudRenderer`、Java2Dアダプター |
| 表示確認 | 実施 | 480×400合成HUD、G単独、480×640安全領域。重なりと切れたラベルを修正 |
| Android APKビルド | **成功** | `tools/build-apk.py` でSDKツールを直接実行。Java→DEX→リソース結合→整列→署名 |
| APK署名・整列・内部検査 | **成功** | apksigner v3署名検証、zipalign、11項目のAPK/DEX検査 |
| 0.1.1起動処理の修正確認 | 実施 | AOSP Android 12との照合、修正前後の呼び出し順序確認。実機実行ではない |
| 診断スクリプトの失敗経路 | 3項目合格 | 疑似ADB。インストール失敗、Activity起動エラー、即時終了時のログ保存 |
| Gradle / Android lint | **未実施** | Gradle配布先へのネットワーク制限が継続 |
| 実機・エミュレーターでの起動 | **未実施** | 利用可能な実行環境なし |
| 実機センサー・操作・描画・電池試験 | **未実施** | 下記チェック表を同梱 |

実行コマンド：

```sh
bash tools/test-core.sh
java com.sun.tools.javac.Main --release 17 -cp core/build/manual -d core/build/manual tools/Preview.java
java -Djava.awt.headless=true -cp core/build/manual Preview artifacts
bash ./gradlew :core:verify :app:assembleDebug --no-daemon
```

最後のGradleコマンドは初回作業で、配布ファイルのダウンロード時に `java.net.SocketException: Network is unreachable` を返しました。その後、取得済みSDKツールを利用する直接ビルド経路を追加し、署名済みAPKを生成しました。詳細な実行ログ・今回のデバッグ範囲は [apk-debug-report.md](apk-debug-report.md) に記載しています。

## 模擬IMUの試験一覧

1. 静止時のG≈0、OS Quaternion経路、既知の加速度／ジャイロバイアスの推定。
2. 右傾斜＋Roll、左傾斜−Roll、傾斜だけではGが発生しない。
3. 上を見る＋Pitch、下を見る−Pitch、傾斜だけではGが発生しない。
4. 前進・制動・左右加速度の符号とPeakの定義。
5. 非ゼロのPitch/Roll/YawからRESETし、別の姿勢でもRESET座標のGを維持。
6. 傾いた状態でRESETした後の純Yaw回転がPitch/Rollに影響しない。
7. 加速度バイアス込みの100種類のランダム姿勢で重力が残らない。
8. 動いている校正を拒否し、静止後に復帰する。
9. 4Hz LPFの50msステップ応答と30Hz振動の抑制。
10. 古いデータ、逆順時刻、NaN入力をライブ値にしない。
11. 固定バイアス＋ホワイトノイズ、200Hz、720,000サンプル＝1時間相当の静止試験。
12. 自前融合でPitchとRollの回転方向を確認。
13. 大きい持続並進加速度を重力補正へ混ぜない。
14. OS姿勢から自前融合へ切り替えた際に旧座標を再利用しない。
15. 正しい軸置換がベクトルとQuaternionに一貫して適用され、鏡映を拒否する。
16. 480×400等倍、および480×640の公式安全帯内へ配置。

1時間相当の試験は時間を進めた計算であり、1時間実機を稼働した試験ではありません。温度変化・センサー固有ノイズ・Androidスケジューリング・電池・光学遅延は再現していません。数値結果を実機精度として転用しないでください。

## 実機で残る項目

[device-acceptance.md](device-acceptance.md) を使用してください。特にSensor種別、minDelay、実効Hz、ボタン配送、起動手段、表示方向、消費電力は実機依存です。プレビューの完成と実機での完成は区別します。
