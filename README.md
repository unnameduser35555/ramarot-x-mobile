# ramarot-x-mobile

Android端末だけで完結する、Fortnite内ミニゲーム「ラマロット」向けの自動化アプリです。  
PC/ADB接続は不要で、**アクセシビリティサービス + 画面解析**で自動タップします。

## 方式（端末のみ）
- `AccessibilityService.takeScreenshot()` でフレームを取得（約28ms周期）
- 青バー領域を検出
- バー内の **白線** と **水色ゾーン** を色しきい値で同時検出
- `whiteX` と `zoneCenterX` が一致するタイミング（または中心通過）でタップ
- 水色ゾーンの位置/サイズが毎回変化しても、毎フレーム再検出で追従
- すべて端末内で完結（USBデバッグ・ADB不要）

## 使い方
1. APKをインストールしてアプリを起動
2. 「アクセシビリティ設定を開く」からサービスをON
3. 「自動化開始」をタップ
4. Fortniteを前面にしてラマロット画面へ
5. 停止したいときはアプリに戻って「自動化停止」

## ファイル構成
- `app/src/main/java/com/ramarot/mobile/MainActivity.kt`  
  自動化ON/OFFとアクセシビリティ設定画面遷移
- `app/src/main/java/com/ramarot/mobile/RamarotAccessibilityService.kt`  
  スクリーンショット取得 + タイミング判定 + タップ本体
- `app/src/main/java/com/ramarot/mobile/TimingDetector.kt`  
  青バー/白線/水色ゾーン検出アルゴリズム
- `app/src/main/res/xml/accessibility_service_config.xml`  
  アクセシビリティサービス定義

## 注意
- 端末の画質補正や色味設定で検出感度が変わる場合があります。
- 必要に応じて `TimingDetector.kt` の色しきい値を調整してください。
