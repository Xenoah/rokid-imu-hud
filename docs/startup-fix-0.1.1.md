# 0.1.1 起動不具合の修正

「起動できない」という報告を受け、配布済み0.1.0のMainActivityとコンパイル済みコードを確認しました。実機ログは受領していないため、ユーザー端末で発生した例外そのものの断定はしていません。

## 確認した問題

0.1.0の `onCreate` は、`setContentView` より前に `Window.getInsetsController()` を呼んでいました。[AOSP Android 12のPhoneWindow](https://github.com/aosp-mirror/platform_frameworks_base/blob/android12-release/core/java/com/android/internal/policy/PhoneWindow.java) では、このメソッドが内部の `mDecor` を直接参照します。未生成の場合は呼び出し中にNullPointerExceptionになります。戻り値へのnullチェックでは回避できません。

`setDecorFitsSystemWindows` と `addFlags` だけではDecorViewの生成は保証されません。これはセンサーの有無やキャリブレーションより前に起き得る問題で、前版の演算試験・APK構造検査では検出できていませんでした。

## 修正

- 最初にHUDのViewを生成し、`setContentView` でWindowへ設定。
- `getDecorView().getWindowInsetsController()` を使い、生成済みViewからcontrollerを取得してnullを検査。
- Windowへの接続前にcontrollerが得られない場合に備え、フォーカス取得時にもシステムバー非表示を適用。
- `onCreate`、画面生成完了、`onResume` の到達をLogcatへ記録。
- インストーラーがソースZIP内の同梱APKを選べるよう修正。
- 診断スクリプトが起動直後の終了でもUIDを使ってログを保存するよう修正。

アプリIDと署名鍵は0.1.0から変更せず、versionCodeを2へ更新しました。配布済み0.1.0には上書きインストールできます。

## 検証

| 検証 | 結果 |
| --- | --- |
| 修正前後のコンパイル済みActivityの呼び出し順序 | 修正前の早すぎる呼び出しを確認。修正後のView生成・nullチェックを確認 |
| Androidコンパイル・DEX生成・APK署名・整列 | 成功 |
| 旧版と新版の署名証明書SHA-256 | 一致 |
| APK構造検査 | 11項目合格 |
| 演算回帰試験 | 16項目合格 |
| 診断スクリプトの失敗経路試験 | 疑似ADBで3項目合格。インストール失敗、Activity起動エラー、即時終了 |
| Android/Rokid実機上の起動 | 未実施。接続実機・エミュレーターなし |

署名証明書SHA-256：`2b81b9ecb6381e44864effce1a587ab7772443bef86467408b4d3fb277d6c501`。

各結果は `artifacts/startup-order-review.txt`、`apk-build-log.txt`、`apk-inspection.txt`、`core-test-results.txt`、`debug-capture-tests.txt` に保存しています。疑似ADBの試験結果を実機での成功とは扱いません。

## 上書きインストール

```sh
adb install -r RokidHUD-0.1.1-debug.apk
adb shell am start -W -n dev.xenoah.rokidhud/dev.xenoah.hud.MainActivity
```

ZIPを展開して使う場合は `tools/install.ps1` または `tools/install.sh` でも転送できます。まだ失敗する場合は次を実行し、生成された `device-logs` 内の `startup-logcat.txt`、`start.txt`、`result.json` を確認します。

```sh
python3 tools/debug-device.py --apk dist/RokidHUD-0.1.1-debug.apk
```

アプリ一覧に出ない・インストール自体に失敗する・黒画面で停止するなど、即時クラッシュ以外の症状は別の原因の可能性が残ります。
