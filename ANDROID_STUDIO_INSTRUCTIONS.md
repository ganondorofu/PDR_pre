# Android Studio を使用した PDR アプリの実行手順

## 手順 1: Android Studio でプロジェクトを開く

1. Android Studio を起動
2. [Open an Existing Project] を選択
3. `c:\FUCKIN_ONEDRIVE\PDR_test` フォルダを選択
4. [OK] をクリック

## 手順 2: プロジェクトの同期

1. Android Studio がプロジェクトを読み込むと、上部に同期の通知が表示される場合があります
2. [Sync Now] をクリックしてプロジェクトを同期
3. Gradle の依存関係が自動的にダウンロードされます

## 手順 3: デバイスの設定確認

Android Studio の下部にある [Terminal] タブを開いて以下を実行：

```bash
adb devices
```

以下のように表示されることを確認：
```
List of devices attached
R5CX143KC8A    device
```

## 手順 4: アプリのビルドと実行

### 方法A: Android Studio の Run ボタンを使用（推奨）

1. ツールバーの緑色の ▶️ ボタン（Run 'app'）をクリック
2. ターゲットデバイスとして接続されたデバイスを選択
3. [OK] をクリック
4. アプリが自動的にビルドされ、デバイスにインストール・起動されます

### 方法B: コマンドラインでビルド

Android Studio のターミナルで：

```bash
# APK をビルド
.\gradlew assembleDebug

# APK をインストール
adb install app\build\outputs\apk\debug\app-debug.apk

# アプリを起動
adb shell am start -n com.example.pdrapp/.MainActivity
```

## 手順 5: 権限の確認と設定

アプリが起動したら、必要な権限を許可：

1. **位置情報権限**: "常に許可" または "アプリの使用中のみ許可" を選択
2. **身体活動権限**: "許可" を選択

手動で権限を設定する場合：
```bash
adb shell pm grant com.example.pdrapp android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.example.pdrapp android.permission.ACCESS_COARSE_LOCATION
adb shell pm grant com.example.pdrapp android.permission.ACTIVITY_RECOGNITION
```

## 手順 6: アプリの使用方法

1. **開始**: 画面下部の緑色の「開始」ボタンをタップ
2. **歩行**: スマートフォンを手に持つか、ポケットに入れて歩く
3. **経路確認**: 画面中央のキャンバスで歩行経路をリアルタイム確認
4. **統計確認**: 画面上部で歩数と移動距離を確認
5. **停止**: 赤色の「停止」ボタンで記録終了
6. **リセット**: 灰色の「リセット」ボタンで経路をクリア

## トラブルシューティング

### ビルドエラーが発生した場合

1. [File] → [Invalidate Caches and Restart] を試す
2. [Build] → [Clean Project] → [Rebuild Project] を実行

### デバイスが認識されない場合

```bash
# ADB サーバーを再起動
adb kill-server
adb start-server
adb devices
```

### アプリがクラッシュする場合

Android Studio の [Logcat] タブでエラーログを確認：

```bash
# またはターミナルで
adb logcat | findstr "com.example.pdrapp"
```

## 開発中の便利なコマンド

### アプリの再インストール
```bash
adb uninstall com.example.pdrapp
.\gradlew assembleDebug
adb install app\build\outputs\apk\debug\app-debug.apk
```

### リアルタイムログ監視
```bash
adb logcat -s AndroidRuntime -s System.err -s PDRApp
```

### センサーの動作確認
```bash
adb shell dumpsys sensorservice | findstr -i "accelerometer\|magnetometer\|gyroscope"
```

これで PDR アプリがデバイス上で動作し、実際の歩行経路をリアルタイムで追跡できるようになります！
