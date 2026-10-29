# 開発用の手順

ビルドにはJava 17を使用します。初回は、Gradle・Fabric・Mojang・Mavenから必要なファイルを取得するため、ネットワーク接続が必要です。

## ビルド

Windowsでは、プロジェクトのルートで次を実行してください。

```powershell
.\gradlew.bat build
```

Linux・macOSでは、次のコマンドを使用できます。

```sh
chmod +x gradlew
./gradlew build
```

配布用Jarは`build/libs/japan-eez-1.20.1-1.0.0.jar`です。`-sources.jar`はソース参照用なので、ゲームの`mods`へ入れるファイルは通常のJarだけにしてください。

## 構成

- `src/main`：Mod本体、ブロックモデル、翻訳、レシピ
- `src/test`：多角形、距離、大陸棚の連続性などの自動テスト
- `src/qa`：専用サーバーとクライアントで使う実機検証用の補助Mod

Javaパッケージは`io.github.miniai_saka.eez`、Gradleのgroupは`io.github.miniai_saka`です。Mod IDは`japan_eez`、コマンドは`jpboundary`です。

## 実機検証用の補助Mod

開発者向けの補助Modは、次のコマンドでビルドできます。

```powershell
.\gradlew.bat remapQaJar
```

生成される`-qa.jar`は、通常の配布Jarに含まれない別のModです。初期化を実行するには、検証用サーバー・クライアントのJVMへ`-Deez.qa=true`を指定します。

補助Modは検証ワールドに足場や測量標を設置し、テスト用の領域・設定・プレイヤー状態を変更します。使用する場合は、専用の新しい検証ワールドを用意してください。通常のワールドで使う必要はありません。

## ライセンス

このプロジェクトのライセンスは[MIT License](../LICENSE)です。Minecraft本体や、依存する各ライブラリは配布Jarに同梱していません。ブロックモデルやアイテムは、ゲーム内にあるテクスチャを参照しています。
