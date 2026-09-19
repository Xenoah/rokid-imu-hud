# APK生成後の検証記録

更新日：2026-09-19 UTC。

成果物は `dist/RokidHUD-0.1.5-debug.apk`。アプリID `dev.xenoah.rokidhud`、versionCode 7、versionName `0.1.5`、minSdk 31、targetSdk 35。Android 12以降向けの、デバッグ署名付き・CPU ABI非依存のAPKです。校正の修正は [calibration-fix-0.1.5.md](calibration-fix-0.1.5.md)、起動不具合の修正は [startup-fix-0.1.1.md](startup-fix-0.1.1.md) に記録しています。

## ビルド

Gradleの配布先へ接続できない問題が残っていたため、取得済みのAndroid/AOSPツールを直接使用する `tools/build-apk.py` を追加しました。架空のAndroidスタブや代替APIは作っていません。

1. AAPT2で実際のXMLリソース・Manifestをコンパイルしてリンク。
2. 公開Android API 36のandroid.jarを参照し、JDK 17でアプリ全体・演算部をコンパイル。
3. D8 8.11.6-devでJava 17バイトコードをDEXへ変換（min API 31）。
4. zipalignで4バイト整列。
5. apksignerでデバッグ署名し、API 31以上の署名検証。

元のGradle構成はcompileSdk 35です。今回の直接ビルドはAPI 36の公開スタブを使用しますが、ManifestのminSdk 31 / targetSdk 35を維持しています。アプリのAPI 33 receiverフラグはOSバージョンで分岐します。API 36でdeprecatedになった `Window.setDecorFitsSystemWindows` の警告が1件あります。このメソッドは対応対象のAndroid 12で使用できます。コンパイルエラーはありません。

Manifestの相対Activity名は、Gradleと同様にnamespaceで完全修飾名へ展開しています。applicationIdとの取り違えによる起動クラス欠落を避け、APKから読み戻して確認しました。0.1.1では画面初期化の順序と起動段階のログを修正しています。

直接ビルドは、公式SDKをインストールした環境でも以下で再現できます。

```sh
python3 tools/build-apk.py --sdk /path/to/android-sdk
# 個別に配置したツールを指定する場合
python3 tools/build-apk.py --android-jar /path/to/android.jar --tools-dir /path/to/build-tools
```

必要ファイル：aapt2、zipalign、lib/d8.jar、lib/apksigner.jar。Linuxの共有ライブラリ版zipalignでは同じツールのlib64も必要です。ネットワーク接続・Gradle・追加Pythonパッケージはこのビルドスクリプトの実行時には不要です。SDKツール自体はソースZIPに含めていません。

## APK生成後のチェック

| 検証 | 結果 |
| --- | --- |
| apksignerによる署名検証 | 合格、APK Signature Scheme v3、署名者1名 |
| 署名後APKの4バイト整列 | 合格 |
| Manifest・DEXの構造検査 | 11項目合格 |
| 起動Activityと実装クラスの一致 | `dev.xenoah.hud.MainActivity` を確認 |
| DEX内容 | 42クラス、406メソッド。センサー・融合・描画・演算の実装を確認 |
| 不要クラスの混入 | Androidスタブ、デスクトッププレビュー、テストクラスなし |
| 権限 | uses-permission宣言なし。通信・Bluetooth権限なし |
| 模擬IMU回帰試験 | 47項目合格。時間上限の追加に合わせて4件の運動拒否試験の対象期間を変更。詳細はverification.md |
| 診断CSV | 本番書式をJVMで実行。38列の一致を確認 |

1時間相当の模擬静止試験（200Hz、720,000サンプル）では、最大傾斜誤差0.008518°、最大水平合成G 0.000837 Gでした。これは固定バイアス＋ホワイトノイズという試験条件での**計算結果**であり、実機性能の測定値ではありません。

実行結果は `artifacts/apk-build-log.txt`、`apk-build-status.json`、`apk-inspection.txt`、`core-test-results.txt` に保存しています。

## 残る検証

この環境には接続されたRokid実機もAndroidエミュレーターもありません。修正版0.1.5をAndroid上で起動したという確認は行っていません。旧0.1.2の写真では約198Hz、0.1.3のユーザー動画では約247Hzのセンサー受信と起動・HUD描画を確認しましたが、校正が待機していました。動画から生IMUのバイアスと実回転を分離することはできません。正式公開時のGradleビルド・Android lintはGitHub Actionsの公開ゲートで実行します。物理IMUの種別・軸・実効Hz、ディスプレイの見え方、公式タッチイベントの配送、実機60fps、消費電力、熱、長時間ドリフトは実機で確認が必要です。

`tools/debug-device.py` は、ADB接続した実機へのインストール、デバッグ起動、20秒間のプロセス監視、校正後の有効CSV行の確認、Logcat採取をまとめます。ボタンイベントを注入して物理操作の代わりにする処理はありません。

```sh
python3 tools/debug-device.py --apk dist/RokidHUD-0.1.5-debug.apk
```

具体的な動作確認項目は [device-acceptance.md](device-acceptance.md)。署名が異なる旧APKをスクリプトが勝手に削除することはありません。今回のデバッグ署名鍵はソースZIPに含めていないため、別のPCでビルドしたAPKへ更新するときは署名の一致を確認してください。

## ツール参照

- [Android D8](https://developer.android.com/tools/d8)
- [AOSP R8/D8配布物](https://android.googlesource.com/platform/prebuilts/r8/+/refs/heads/main/r8.jar) — 使用JARのGit blob `ed6d5f67fd115b370ecbe6df264e8e90b42b4fae` を照合。
- [AOSP SDK tools](https://android.googlesource.com/platform/prebuilts/sdk/+/main/tools/)

全ツールの実ファイルSHA-256は `apk-build-status.json` に保存しています。
