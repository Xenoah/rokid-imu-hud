# GitHub公開時の変更 / Publication changes

0.1.0〜0.1.4は過去の機能コードとAPKを保持したプレリリースです。0.1.5は正式リリースとして公開します。

Versions 0.1.0–0.1.4 preserve the earlier functional sources and APKs as prereleases. Version 0.1.5 is published as a regular release.

- 非公式・非提携の表記、日英README・リリース説明、版に対応したプレビュー、旧0.1.2の実機画像を追加。
- 0.1.5のversionNameを0.1.5、versionCodeを7へ更新。元の署名を維持。
- CIのsetup-androidにplatform-toolsを明示。廃止されたtoolsパッケージの取得失敗を修正。
- AGP 8.9.2のlintがAPI 31/32限定のregisterReceiver呼び出しにもフラグを要求したため、旧OS経路だけを専用メソッドへ分離し、UnspecifiedRegisterReceiverFlagをそのメソッドに限定して注記。API 33以上は従来どおりRECEIVER_EXPORTEDを明示。IMU・描画・入力動作は変更なし。
- 正式公開はGradleによるコンパイル・IMU試験・Android lintの成功を条件とする。公開APKは検証済みの元署名APKで、CIの一時署名APKに差し替えない。

The legacy receiver method is called only below API 33. Android 13 introduced the export flags; the newer-OS branch already sets RECEIVER_EXPORTED. The scoped annotation documents the guarded older-OS path without disabling lint for the project. Sensor processing and input behavior are unchanged.

Sources: [Android 13 receiver flags](https://developer.android.com/about/versions/13/features#runtime-receivers), [Android 14 receiver requirements](https://developer.android.com/about/versions/14/behavior-changes-14#runtime-receivers-exported).

A regular GitHub release does not imply physical-device validation, certified G accuracy or flight-instrument certification. APKs remain debug-signed for diagnostic access.
