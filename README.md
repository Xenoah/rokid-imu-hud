> **非公式・非提携：本プロジェクトは個人開発の非公式アプリです。Rokid社との提携・公認・支援関係はありません。Rokidの名称は対応機種を説明するために使用しています。**
>
> **Unofficial and unaffiliated: This is an independent community project. It is not affiliated with, endorsed by, or sponsored by Rokid. The Rokid name is used only to identify compatible hardware.**

# IMU HUD for Rokid Glasses — 0.1.3-preview

[日本語](#概要--overview) | [English documentation](README.en.md) | [Releases](https://github.com/Xenoah/rokid-imu-hud/releases)

## 概要 / Overview

Rokid Glasses本体で動作する非公式の水平儀＋2軸Gメーター。スマートフォンやBluetooth接続なしで、姿勢・加速度の取得からHUD描画まで完結します。

An unofficial standalone artificial horizon and two-axis G meter for Rokid Glasses. IMU processing, attitude estimation and HUD rendering run entirely on the glasses, without a phone, Bluetooth link or server.

**装着中の頭の揺れを考慮 / Account for head movement during calibration**

[APK・ソースのダウンロード / Download APK and source](https://github.com/Xenoah/rokid-imu-hud/releases/tag/v0.1.3-preview)

> 履歴保存用プレリリース / Historical prerelease. 最新版は [Releases](https://github.com/Xenoah/rokid-imu-hud/releases/latest) を確認してください。


## 実機スクリーンショット / Device screenshots

**0.1.2の調査時にユーザーから提供された画像です。最新版の実機動作確認を示すものではありません。**
User-provided images from the 0.1.2 investigation; these do not demonstrate hardware validation of the latest version.

| スマートフォン / Phone HUD | Rokid Glasses / Calibration issue |
| --- | --- |
| <img src="docs/screenshots/phone-hud-0.1.2.png" width="220" alt="Phone HUD screenshot reported with 0.1.2"> | <img src="docs/screenshots/glasses-calibration-0.1.2.jpg" width="300" alt="Rokid Glasses calibration wait in 0.1.2"> |

グラス画像は約198Hzの入力と旧版の校正待機を記録したものです。下の480×400画像は本番描画コードによるデスクトッププレビューです。
The glasses image records approximately 198Hz input and the old calibration wait. The 480×400 image below is a desktop render made with the production HUD drawing code.

Rokid Glasses（Snapdragon AR1 / YodaOS-Sprite）本体で動作する、水平儀＋2軸GメーターのAndroidアプリ。Java 17で実装しています。通常動作時のスマートフォン、Bluetooth、外部センサー、ネット接続は不要です。

**0.1.3：グラス装着中の校正判定を修正。** 実機写真では加速度・ジャイロ各約198Hzの受信と `LAST: ROTATION DETECTED` が確認できました。OS姿勢が使える場合は、角速度の大きさだけでなく校正窓全体の姿勢変化も確認します。OS姿勢から求めた回転分を引いてジャイロバイアスを推定し、小さな頭の揺れをそのままゼロ点へ混ぜないよう変更しました。詳細は [docs/calibration-fix-0.1.3.md](docs/calibration-fix-0.1.3.md)。

**0.1.2：静止校正が終わらない問題への修正。** 姿勢センサーの更新間隔と静止中ノイズで校正が繰り返し初期化されるケースを再現し、時間窓の統計による判定へ変更しました。Quaternionが重力と整合しない場合はジャイロ＋加速度融合へ切り替えます。待機理由・加速度・ジャイロ値・取得Hzを画面に表示します。詳細は [docs/calibration-fix-0.1.2.md](docs/calibration-fix-0.1.2.md)。実機ログがないため、報告された個体の原因を確定したものではありません。

**0.1.1の起動修正も含みます。** 0.1.0ではDecorView生成前に `Window.getInsetsController()` を呼び、Android 12の実装でNullPointerExceptionが発生し得ました。画面生成後のView API呼び出しへ変更しています。調査根拠と検証範囲は [docs/startup-fix-0.1.1.md](docs/startup-fix-0.1.1.md) を参照してください。

**状態：署名済みデバッグAPKを生成済み。Androidコードのコンパイル、DEX変換、署名・整列検証、11項目のAPK内部検査、32項目の模擬IMU試験に合格しました。** `dist/RokidHUD-0.1.3-debug.apk` を同梱しています。0.1.2はユーザー提供写真でRokid実機の起動・描画・約198HzのIMU受信を確認しましたが、校正で待機していました。**修正版0.1.3の実機・エミュレーター試験は未実施です。** 実機60fps、継続的な取得レート、ボタン配送、軸の符号は未確認です。

今回は利用できるAOSP Androidビルドツールを直接実行し、APKを生成しました。Gradleのダウンロード制限は残っているため、Gradle経由のビルド・Android lintは未実施です。直接ビルドの再現方法と検証範囲は [docs/apk-debug-report.md](docs/apk-debug-report.md) を参照してください。

![480×400 HUD：アプリと同じ描画コードをJava2Dでレンダリングしたもの](artifacts/hud-combined-480x400.png)

## 実装内容

- 中央固定機体マーカー、姿勢に追従する水平線、10°ごとのPitch ladder、Roll目盛、Pitch/Roll数値。
- 小型の左右・前後Gメーター。±2G、同心円0.5G間隔、短い軌跡、LAT/LONG/PEAK数値。範囲外はドットを円周へ制限し、数値はそのまま表示します。
- OSの `TYPE_GAME_ROTATION_VECTOR` → `TYPE_ROTATION_VECTOR` → 加速度＋ジャイロのMahony方式という優先順位。
- センサー専用スレッド、vsync描画専用スレッド、任意のCSV専用スレッド。200Hzを要求し、描画は60fpsを目標にします。
- 重力除去→RESET時の基準Quaternionの逆回転→G換算→4Hz LPF。PEAKはフィルタ後の2軸合成値の最大。
- 約1秒の静止校正。動いている間は確定しません。RESET完了時は `RECENTERED` を1秒表示。
- 合成HUD／水平儀のみ／Gメーターのみの3モード。
- センサー欠落、ストリーム停止、Quaternion停止・精度不良の検知。古い値は `IMU STALE` として隠します。
- 本体を外す／つるを畳む場合の停止、復帰時の再校正。

## 公式資料と表示サイズの差

2026-09-18 UTCにRokid Open Platform、YodaOS-Sprite、CXR-S、および現行bare-metal開発ガイドの本文を確認しました。**Rokid Max / Station / YodaOS-Master用SDKは使用していません。**

依頼の480×400に対し、現行の[公式UIガイド](https://custom.rokid.com/prod/rokid_web/ff28c865a9634876be98cbc293588460/pc/us/index.html?documentId=161dddee340444108682adb8693fdb20)は物理解像度480×640、上下80pxを除く480×480を推奨しています。本実装は **480×400の論理HUD** を実際のSurfaceへ等方配置します。480×640ではx=0、y=120に配置し、上下の安全帯を守ります。480×400のSurfaceならそのまま表示します。実機では `adb shell wm size` と起動ログで照合してください。

黒は非発光、描画は緑のみです。OS上の他アプリへ重ねる特殊なoverlayではなく、前面Activityを本体ディスプレイへ描画します。光学系による像の位置調整や世界に固定した6DoF描画は行いません。

APIの根拠・URL・未確定事項は [docs/official-research.md](docs/official-research.md) にまとめています。

## ビルド（Windows）

1. Android Studio、JDK 17、Android SDK Platform 35、Build Tools 35.0.0、Platform Toolsを用意します。SDKライセンスは使用するPCで確認してください。
2. このフォルダーをAndroid StudioでOpenします。`local.properties` の `sdk.dir`、または `ANDROID_HOME` でAndroid SDKの場所を指定してください。
3. 初回Gradle同期にはインターネット接続が必要です。通常動作には不要です。
4. PowerShellで実行します。

```powershell
.\gradlew.bat :core:verify :app:assembleDebug :app:lintDebug
# または
.\tools\build.ps1
```

出力：`app/build/outputs/apk/debug/app-debug.apk`。デバッグ署名は各PCで生成されます。署名鍵が異なる既存APKへは上書きできません。

Linux/macOSは `./tools/build.sh`。Gradle 8.11.1の公式Wrapperを同梱しています。固定したAGP 8.9.2 / Gradle 8.11.1 / JDK 17 / compileSdk 35の構成です。minSdkは公式bare-metalガイドに合わせ31。公式サンプルのtargetSdk 36に対して、本プロジェクトのtargetSdkは35です。

`.github/workflows/android.yml` はGitHubへ配置した場合のビルド・APK保存用です。この納品時点でCIを実行したものではありません。

Gradleを使わず、インストール済みの公式SDKツールを直接使う場合：

```sh
python3 tools/build-apk.py --sdk /path/to/android-sdk
```

SDK Platform 35、Build Tools 35.0.0、JDK 17、Python 3が必要です。今回の作業環境では `--android-jar` と `--tools-dir` で取得済みツールを指定しました（API 36スタブでコンパイル、minSdk 31 / targetSdk 35）。APKのSHA-256とツールのSHA-256は `artifacts/apk-build-status.json` に記録しています。直接ビルドはGradleの代替経路で、Android lintの代替ではありません。

## 初回転送と起動

公式[Quick Start](https://custom.rokid.com/prod/rokid_web/ff28c865a9634876be98cbc293588460/pc/us/index.html?documentId=63837f8f9e3d4f3387517b224e5c9875)は、Rokid AI AppでのADB有効化と**データ転送対応の専用開発ケーブル**を案内しています。付属充電ケーブルのみではデバッグできない場合があります。開発ケーブルはRokid公式開発者窓口へ確認してください。

```powershell
adb devices
adb shell getprop ro.build.version.sdk
adb shell wm size
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n dev.xenoah.rokidhud/dev.xenoah.hud.MainActivity
```

同梱APKを使う場合のパスは `dist/RokidHUD-0.1.3-debug.apk` です。配布済み0.1.0／0.1.1／0.1.2と同一署名なので `adb install -r` で上書きできます。署名の異なる別ビルドがある場合、`INSTALL_FAILED_UPDATE_INCOMPATIBLE` になります。自動アンインストールは行いません。必要なログを保存してから旧版を削除すると、そのアプリの保存データも消去されます。

または `tools/install.ps1`。複数のAndroid機器が見える場合は `-Serial 実機シリアル` を付けます。ADBが `unauthorized` なら正規の認証手順を完了してください。

一度インストールした後は、ファームウェアのアプリ一覧から「Rokid HUD」を起動します。ファームウェアがサイドロードアプリを一覧表示しない場合の独立起動方法は実機確認が必要です。本アプリはLauncherを書き換えたり、システムをroot化したりしません。起動後にPCを外し、スマートフォンとBluetoothがなくても動作することを実機チェック表で確認してください。

## 本体操作

| 操作 | 動作 | 公式イベント |
| --- | --- | --- |
| 右タッチパネルを**2本指でダブルタップ** | 静止校正してRESET | `com.android.action.ACTION_TWO_FINGER_DOUBLE_TAP` |
| 1本指でシングルタップ | 3モード切替 | `KEYCODE_ENTER` のUP |
| 1本指でダブルタップ | 戻る／終了 | `KEYCODE_BACK` をOSへ委譲 |
| Function Button長押し、AI長押し、設定、ペアリング | OSの動作を維持 | 本アプリは購読・抑止しない |

RESETは前面・装着中のみ受け付け、重複イベントを750msで抑制します。2本指ダブルタップは公式掲載のイベントで、**任意に推測したキーコードではありません**。到達とファームウェア側の併存動作は実機未確認です。動的receiverのみを使い、`abortBroadcast()` は呼びません。2本指シングルタップは公式文書内で対応の記述が不一致なため採用していません。

## 校正

起動／RESET後に `CALIBRATING / KEEP HEAD STILL` を表示し、約1秒の連続した静止を待ちます。加速度・ジャイロを別々に集計し、最低各20サンプル、ジャイロ平均・分散、加速度分散・大きさ、姿勢変化を検査します。静止中の単発ノイズで毎回やり直す方式を廃止しました。校正時は最新ジャイロ／OS姿勢の150ms鮮度を確認し、ライブG計算用の30ms時刻差を校正には適用しません。

15秒で未完了なら `CALIBRATION WAIT` を表示し、直近の待機理由・ACC（重力を含む大きさ、静止時約1G）・GYRO（度/秒）・A/Gの実効Hzを併記します。`LAST:` は直近に静止窓を取り直した理由で、現在も動いていると断定する表示ではありません。0.1.3では `POSE`（窓の開始姿勢からの最大角度差）と `MEAN`（生ジャイロの平均ベクトルの大きさ、度/秒）も表示します。POSEはOS姿勢がない場合 `--` です。センサー欠落・不正な加速度スケール・持続する大きな運動を無条件で通過させるタイムアウトはありません。

OS姿勢がある場合は、窓内すべての姿勢サンプルについて最初からの角度差が2°以内か確認し、ジャイロ平均からOS姿勢で観測した平均角速度を引いて残留バイアスを推定します。推定バイアスの上限は0.10rad/sです。自前融合だけの場合は従来の厳しい静止条件を維持し、ジャイロ平均を残留バイアスとして取得します。OS Quaternion使用時は平均加速度と予測重力との差を加速度バイアスとして取得します。自前融合時は、単一静止姿勢では傾斜誤差と横方向バイアスを分離できないため、重力方向の残差のみ推定します。これは工場校正・6面校正の代替ではありません。

完了時の姿勢をGメーターの基準Quaternionとして保存し、Pitch/Roll基準、LPF、Peakを初期化。起動は `READY`、以降は `RECENTERED` を1秒表示します。**RESETは約1秒の静止後に成立**する操作です。持続加速中はゼロ点が汚染され得るので、静止状態で実施してください。

## 座標・演算・調整

[docs/coordinates-and-fusion.md](docs/coordinates-and-fusion.md) に数式と符号を記載しています。デバイス軸は公式の+X右、+Y上、+Z装着者側。LAT正＝右への加速度、LONG正＝前方（−Z）への加速度。ドットは加速度方向へ動き、慣性力の向きではありません。

`core/.../Config.java` の `G_FILTER_CUTOFF_HZ`、`HUD_FILTER_TAU_S`、`FUSION_KP`、静止判定しきい値を調整できます。標準は200Hz要求、G表示4Hz LPF、姿勢融合の表示平滑化12ms。レンダラー自身はセンサー値を積分しません。

実機軸が仕様と異なる場合は、実機手順で符号を確認し、`SensorController.java` の `AxisMap(1,2,3)` を正しい右手系の符号付き軸置換に変更します。加速度・ジャイロ・Quaternionを一緒に変換する実装です。未確認の自動推定で軸を変更しません。

## ログ

通常はセンサー一覧・選択・表示サイズ・イベント・異常をLogcatへ記録。校正中は約3秒ごとに待機理由、進捗、実効Hz、加速度／角速度の大きさ、標準偏差も記録します。CSVとHz表示を有効にするには初回起動時のIntentへ `debug` を指定します。

```powershell
adb shell am force-stop dev.xenoah.rokidhud
adb shell am start -n dev.xenoah.rokidhud/dev.xenoah.hud.MainActivity --ez debug true
adb logcat -v threadtime RokidHUD:I "*:S"
# 使用後、CSVとファームウェア・センサー情報をPCへ保存
.\tools\collect-logs.ps1
```

CSVはアプリのprivate領域 `files/hud-debug.csv`。10Hzで最大36,000行（約1時間）、次のdebug起動で上書き。校正理由・標準偏差・OS姿勢棄却に加え、0.1.3では `cal_pose_range_deg`、`cal_gyro_mean_rad_s` も記録します。GPS・音声・カメラ・ネット送信はありません。`run-as` によるCSV取得にはdebug APKが必要です。描画時間はCPUでの描画・post所要時間であり、光子が目に届くまでのmotion-to-display latencyの実測ではありません。

自前融合の実機確認：起動時に `--ez force_fusion true` を追加してください。

実機へ転送し、起動・プロセス生存・校正完了・アプリのログを15秒間確認するスクリプトも同梱しています。

```powershell
python .\tools\debug-device.py --apk .\dist\RokidHUD-0.1.3-debug.apk
# 複数端末がある場合は --serial 実機シリアル を追加
```

実行中は頭を静止させてください。結果は `device-logs/日時/result.json`、CSV、Logcatへ保存します。0.1.1ではプロセスID取得前に起動失敗しても、アプリのUIDで `startup-logcat.txt` を採取します。`RUNTIME_SMOKE_PASS` は起動・プロセス生存・有効センサーデータの確認であり、傾斜・ボタン・60fpsの合格判定ではありません。このスクリプト自体も接続実機では未実行です。疑似ADBによる3種類の失敗経路のログ保存試験を実施済みです。

## 検証

```powershell
.\tools\test-core.ps1
```

JDK 17のみで演算部の回帰試験を実行できます。Linuxは `tools/test-core.sh`。試験結果は [artifacts/core-test-results.txt](artifacts/core-test-results.txt)、検証範囲は [docs/verification.md](docs/verification.md)、実機記録用紙は [docs/device-acceptance.md](docs/device-acceptance.md)。プレビューは `tools/Preview.java` が本番の `HudRenderer` を使用して生成しています。Androidのフォント・アンチエイリアス・光学表示を再現した実機スクリーンショットではありません。

## 既知の制約

- **測るのはメガネ本体に加わった加速度です。車両固定IMUではないため、頭部運動・回転中心からの距離・歩行振動が混入します。** LPFだけで車両加速度と頭部加速度を分離することはできません。
- 6軸IMUだけでは持続する並進加速度と重力による傾きを完全に識別できません。自前融合では重力補正をゲートしますが、小さい持続加速度は傾きへ混入し得ます。OS Quaternionにも提供側の推定誤差があります。
- GAME_ROTATION_VECTOR／自前6軸融合のYawは長時間でドリフトし、RESET時の前後左右G軸へ影響し得ます。Pitch/Roll表示は重力方向から求めるためYaw単独の回転に依存しません。
- 静止校正は温度変化、スケール誤差、全軸バイアスを保証しません。センサーの実在種別・実効Hz・ノイズは個体／ファームウェアで変わります。
- OS姿勢サンプル間はバイアス除去済みジャイロで最大100msだけ姿勢を進めます。ライブG計算では、この姿勢と加速度の時刻差が30msを超える場合、または最後のOS姿勢から100msを超えた場合を有効値にしません。高速回転では時間ずれが見かけの加速度を生むため、実機でログ確認が必要です。
- ±90°付近のPitchではRollが不定になり、倒立で表示が切り替わり得ます。日常の頭部姿勢を対象とした表示です。
- 物理位置や速度を推定しません。加速度の二重積分もありません。航法・操縦・車両制御に用いる認証計器ではありません。
- 60fps・100Hz以上・連続電池持ちは目標値です。実機なしで達成を保証しません。

## 構成

| パス | 内容 |
| --- | --- |
| `app/` | Android Activity、公式入力、SensorManager、SurfaceView、ログ |
| `core/` | Quaternion、融合、校正、G演算、共通描画、32項目の試験 |
| `tools/` | ビルド、転送、ログ収集、JDKのみの試験、プレビュー生成 |
| `docs/` | 公式資料調査、数式、検証記録、実機チェック表 |
| `artifacts/` | 模擬試験結果と共通描画コードによるプレビュー |

本プロジェクト独自コードはMITライセンス。Gradle Wrapperのライセンスは `THIRD_PARTY_NOTICES.md` と `licenses/gradle-LICENSE.txt` を参照してください。
