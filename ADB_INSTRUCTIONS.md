# ADBを使用したPDRアプリの実行手順

## 前提条件
1. Android Studio がインストールされている
2. Android SDK と ADB がインストールされている  
3. Androidデバイスで開発者オプションとUSBデバッグが有効

## 手順1: デバイスの確認
```bash
adb devices
```
このコマンドでデバイスが `device` として表示されることを確認してください。

## 手順2: プロジェクトのビルド（Android Studio使用）
1. Android Studio で PDR_test フォルダを開く
2. [Build] → [Make Project] または Ctrl+F9
3. [Build] → [Build Bundle(s) / APK(s)] → [Build APK(s)]

## 手順3: APKの場所確認
ビルド後、APKファイルは以下の場所に生成されます：
```
app/build/outputs/apk/debug/app-debug.apk
```

## 手順4: ADBでAPKインストール
```bash
cd "c:\FUCKIN_ONEDRIVE\PDR_test"
adb install app\build\outputs\apk\debug\app-debug.apk
```

## 手順5: アプリの起動
```bash
adb shell am start -n com.example.pdrapp/.MainActivity
```

## 手順6: 開発中の便利なADBコマンド

### ログの確認
```bash
adb logcat | findstr "PDRApp"
```

### アプリのアンインストール
```bash
adb uninstall com.example.pdrapp
```

### 再インストール（-r フラグで既存アプリを置き換え）
```bash
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### デバイス情報の確認
```bash
adb shell getprop ro.build.version.release  # Androidバージョン
adb shell getprop ro.product.model          # デバイス名
```

### センサー情報の確認
```bash
adb shell dumpsys sensorservice
```

## 手順7: 権限の手動設定（必要な場合）
```bash
# 位置情報権限
adb shell pm grant com.example.pdrapp android.permission.ACCESS_FINE_LOCATION
adb shell pm grant com.example.pdrapp android.permission.ACCESS_COARSE_LOCATION

# 活動認識権限  
adb shell pm grant com.example.pdrapp android.permission.ACTIVITY_RECOGNITION
```

## トラブルシューティング

### デバイスが認識されない場合
1. USBケーブルを確認
2. デバイスで「このコンピューターを常に信頼する」を選択
3. 開発者オプション → USBデバッグを再度有効化

### インストールエラーの場合
```bash
# 既存アプリを完全削除
adb uninstall com.example.pdrapp
# 再インストール
adb install app\build\outputs\apk\debug\app-debug.apk
```

### アプリが起動しない場合
```bash
# アプリのログを確認
adb logcat -s PDRApp
```

## Android Studio を使用しない場合の代替案

### Gradle Wrapperが動作しない場合
1. Android Studio でプロジェクトを開く
2. [File] → [Sync Project with Gradle Files]
3. 自動的にGradle Wrapperが修復される

### コマンドラインでのビルド（Gradle Wrapper使用）
```bash
# Windows
.\gradlew assembleDebug

# 成功後
adb install app\build\outputs\apk\debug\app-debug.apk
```

## 開発時の推奨ワークフロー

1. Android Studio でコード編集
2. [Run] → [Run 'app'] で直接デバイスにデプロイ
3. または手動で：
   - Build APK
   - ADB install
   - ADB start

これにより、PDRアプリケーションをAndroidデバイスで実行できます。
