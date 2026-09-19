# 公式資料調査・採用API

調査日：2026-09-18 UTC（日本時間2026-09-19）。公式サイトの本文をブラウザで確認。検索ツールではRokid文書の本文が取得できない場合がありましたが、以下の文書は画面表示まで確認しています。

## 対象と判断

| 対象 | 公式資料で確認したこと | 実装上の判断 |
| --- | --- | --- |
| [Rokid Open Platform](https://open.rokid.com/) | Glasses / YodaOS-SpriteにCXR-SとBMPが掲載。Master用UXR等とは区別 | Glasses本体向けBMP経路 |
| [bare-metal Introduction](https://custom.rokid.com/prod/rokid_web/ff28c865a9634876be98cbc293588460/pc/us/index.html) | Android 12/API31/Android Go、本体上の標準Androidアプリ、スマホ側SDK不要、6軸IMUと標準View | 独立した前面Activity、SensorManager、Canvas |
| [CXR-S Brief](https://custom.rokid.com/prod/rokid_web/57e35cd3ae294d16b1b8fc8dcbb1b7c7/pc/us/3fe1c87b945245bf8b6c50393f4da7b6.html) | モバイルCXR-Mとの接続監視と双方向データ通信 | HUDに通信機能は不要なので依存しない |
| [CXR-S SDK Import](https://custom.rokid.com/prod/rokid_web/57e35cd3ae294d16b1b8fc8dcbb1b7c7/pc/us/3fe1c87b945245bf8b6c50393f4da7b6.html?documentId=721052bc8fad4e648e074007f516fda4) | `com.rokid.cxr:cxr-service-bridge:1.0-20250519.061355-45`、Rokid Maven、minSdk 28 | 調査のみ。通信ブリッジをIMU APIとして扱わない |
| [IMU and Sensors](https://custom.rokid.com/prod/rokid_web/ff28c865a9634876be98cbc293588460/pc/us/index.html?documentId=28007d95eb7a46828f9ac7f19421867a) | ジャイロ＋加速度、SensorManager。+X右、+Y上、+Z装着者側。公式サンプルも頭振りによる軸確認を実施 | 文書の軸を標準に、正しい回転としてAxisMapで差分対応 |
| [Keys / Wear / Fold](https://custom.rokid.com/prod/rokid_web/ff28c865a9634876be98cbc293588460/pc/us/index.html?documentId=ca8fedf26d534e1fabb8a34d1fa24e98) | 動的BroadcastReceiver、KeyEvent、各action、装着・折畳み状態 | 掲載された2本指ダブルタップ、ENTER、装着・折畳みのみ採用 |
| [UI Design Guidelines](https://custom.rokid.com/prod/rokid_web/ff28c865a9634876be98cbc293588460/pc/us/index.html?documentId=161dddee340444108682adb8693fdb20) | 480×640、上下80px安全帯、緑、黒非発光、線画、表示面にタッチ焦点なし | 480×400のHUDを中央へ。画面タップ必須UIを作らない |
| [Quick Start](https://custom.rokid.com/prod/rokid_web/ff28c865a9634876be98cbc293588460/pc/us/index.html?documentId=63837f8f9e3d4f3387517b224e5c9875) | Rokid AI AppでADBを有効化、専用開発ケーブル、Gradle、adb install、scrcpy | 初回導入時のみPC・スマホを使用 |

公式IMUサンプルのhead ballはジャイロQuaternion積分のみと説明されています。本プロジェクトはその簡易姿勢算法をコピーせず、OS姿勢の探索と重力補正を追加しています。

## 確定していない仕様

- 実機で公開されるSensor一覧・ベンダー名・minDelay・最大値は、公式ガイド本文に具体的な値の保証がありません。本アプリが `getSensorList(TYPE_ALL)` と各Sensorのプロパティを実行時に取得して記録します。
- `TYPE_GAME_ROTATION_VECTOR` / `TYPE_ROTATION_VECTOR` の有無は未確認です。存在・登録成功・ストリーム到達を検出します。APIそのものはAndroid公式の実在APIです。
- 100Hz以上の実効入力、物理リフレッシュレート、表示遅延・電池持ちは実機未測定。200Hz要求値と実効値は別です。
- 右タッチパネル2本指ダブルタップは掲載されていますが、利用者のファームウェアでの配送と併存するシステム処理は実機未確認です。Function Button長押しは公式に電源操作と書かれているため選びません。
- 2本指シングルタップはKeyEvent表と文書末尾の非対応記述が一致しません。これには依存しません。
- 販売仕様の解像度と公式開発ガイドの解像度が一致するかは `wm size` / Surfaceログで確認します。依頼された480×400の図形比率は常に維持します。
- 今回受領できた画像は、水平儀と小型Gメーターが同居する1枚です。そこから機能を参照し、指定された2軸Gメーターの挙動も依頼文に基づいて実装しています。

## 使用するSDK / API

| SDK/API | 用途 |
| --- | --- |
| Android SDK compile35 / min31 / target35 | YodaOS-Sprite上の標準アプリ |
| `SensorManager.getSensorList / getDefaultSensor / registerListener` | センサー列挙・選択・取得。batch latency=0、専用Handler |
| `TYPE_ACCELEROMETER` | 重力を含むm/s² |
| `TYPE_GYROSCOPE` | rad/s、OS補正後の残留バイアスを校正 |
| `TYPE_GAME_ROTATION_VECTOR / TYPE_ROTATION_VECTOR` | 任意のOS姿勢入力。未搭載なら不要 |
| `SensorEvent.timestamp / accuracy` | 時間間隔、鮮度、整合、精度不良検出 |
| `BroadcastReceiver / IntentFilter / KeyEvent` | Rokid公式本体イベント |
| `SurfaceView / SurfaceHolder.lockHardwareCanvas / Canvas` | 前面HUDのハードウェア描画 |
| `Choreographer / Surface.setFrameRate` | vsync同期、60Hz表示要求 |
| `HandlerThread / Handler` | IMU・描画・ログの分離 |
| `WindowInsetsController / FLAG_KEEP_SCREEN_ON` | 全画面、前面装着中の画面維持 |

Android一次資料： [センサー概要](https://developer.android.com/develop/sensors-and-location/sensors/sensors_overview)、[モーションセンサー](https://developer.android.com/develop/sensors-and-location/sensors/sensors_motion)、[SurfaceHolder](https://developer.android.com/reference/android/view/SurfaceHolder)。ビルドの対応バージョンは[AGP 8.9公式表](https://developer.android.com/build/releases/agp-8-9-0-release-notes)を確認。

スマートフォンアプリ、CXR-M、Bluetooth権限、Internet権限、OpenXR、Max/Station SDK、隠しAPI、root、独自の未確認 `RokidImuApi` 等は使っていません。
