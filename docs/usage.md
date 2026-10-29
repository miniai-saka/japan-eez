# 操作と設定

導入と最初の操作は[README](../README.md)を参照してください。ここでは、測量標の使い分けや領域の編集、設定項目をまとめています。

## 測量標を置く

5種類とも、固い上面を持つブロックの上に設置できます。厚みは0.2ブロックで、位置はXZ方向の中央に固定されます。支持ブロックを壊すと、測量標も撤去されます。

| 名前 | アイテムID | 幅 | クラフトに使うもの |
|---|---|---:|---|
| 三角点 | `japan_eez:triangulation_point` | 0.375ブロック | 石＋鉄インゴット |
| 水準点 | `japan_eez:benchmark` | 0.375ブロック | ネザークォーツ＋鉄インゴット |
| 金属鋲 | `japan_eez:metal_tack` | 0.25ブロック | 石＋金塊 |
| 測量鋲 | `japan_eez:survey_nail` | 0.25ブロック | 石＋鉄塊 |
| 境界標 | `japan_eez:boundary_stake` | 0.3125ブロック | 赤色の染料＋鉄インゴット |

レシピはいずれも不定形で、4個ずつ作れます。境界確認ツールは、コンパス＋アメジストの欠片＋鉄塊で作れます。

## 表示を切り替える

ツールを持って空中へ右クリックするか、Bキーを押すと設定画面が開きます。BキーはMinecraftのキー設定から変更できます。

基準点・鋲、基準線・海岸線、領有境界、領海、EEZ、大陸棚、領空の7項目を個別に選べます。チェックの状態は、そのクライアントに保存されます。

「表示高度を個別に指定」を有効にすると、表示する下限Y・上限Yを入力できます。これはサーバーで定めた範囲の中で、表示を絞るための設定です。領空の上限を広げたい場合は、サーバー側の`airspaceTopY`を変更してください。

左上には、海岸線の調査状況、現在地からの距離、海の種類、領域の状態が表示されます。この表示もチェックで切り替えられます。

## 領域を編集する

領域のIDには、英数字・`_`・`-`を48文字以内で使用できます。所有者名は64文字以内です。

```mcfunction
/jpboundary region create area_a A
/jpboundary region select area_a
```

`create`で作成すると、その領域が編集対象になります。別の領域を編集したい場合は、`select`で切り替えてください。

頂点は、ツールで測量標をShift＋右クリックして追加できます。測量標を置かず、座標から追加することもできます。

```mcfunction
/jpboundary region vertex area_a 100 64 100
/jpboundary region vertex area_a 120 64 100
/jpboundary region vertex area_a 120 64 120
/jpboundary region close area_a
```

頂点は指定したブロックの中心で結ばれます。3点以上で、面積のある、自己交差しない多角形にしてください。凹形も使用できます。

```mcfunction
/jpboundary region undo area_a
/jpboundary region owner area_a 日本
/jpboundary region height area_a -64 320
/jpboundary region baseline area_a true
/jpboundary region list
/jpboundary region delete area_a
```

- `undo`：最後の頂点を取り消します。
- `owner`：領域の所有者を変更します。
- `height`：領域の下限Y・上限Yを指定します。
- `baseline`：頂点を結ぶ線を「基準線」レイヤーにも表示します。2点の開いた線も使えます。
- `list`：領域の所有者、頂点数、確定状態、高度を確認できます。
- `delete`：指定した領域を削除します。

頂点を変更すると編集中になるので、最後に`close`で確定してください。重なった領域では、先に登録した領域を優先します。

測量標を撤去しても、領域の頂点は座標として残ります。また、測量基準線を引いても、海域の距離の起点は自動取得する海岸線のままです。

## 測量標の所有者を変更する

ツールで測量標を通常の右クリックで選択し、次のコマンドを実行してください。

```mcfunction
/jpboundary marker owner A
```

これは、その測量標の所有者を変更する操作です。領域の所有者は`region owner`で設定します。

## 権限と保存先

`/jpboundary tool`と`/jpboundary here`は、OP権限なしで使えます。領域の編集や共通設定の変更には、OP権限レベル2が必要です。

共通設定は`config/japan-eez.json`、クライアントの表示設定は`config/japan-eez-client.json`です。マルチプレイでは、サーバー側の共通設定が距離・高度・通知の基準になります。

領域と測量標は、ワールドの`data/japan_eez.dat`へ自動保存します。ネザーなどは各ディメンションの`data`配下に保存されます。

## 設定項目

コマンドで変更した値は、設定ファイルにも保存されます。項目名はTabキーで補完できます。

```mcfunction
/jpboundary config
/jpboundary config set extendedShelf false
/jpboundary config set notifications false
/jpboundary config set airspaceTopY 512
/jpboundary config set undergroundBottomY -128
/jpboundary config set displayTopY 512
/jpboundary config set defaultOwner 日本
```

| 項目 | 初期値 | 内容 |
|---|---:|---|
| `territorialDistance` | 12 | 領海距離。1〜64 |
| `eezDistance` | 200 | EEZ距離。領海距離以上、512以下 |
| `extendedDistance` | 350 | 延長大陸棚の上限。EEZ距離以上、768以下 |
| `extendedShelf` | true | 延長大陸棚を有効にする |
| `notifications` | true | 進入時のタイトルを表示する |
| `airspace` | true | 領空を判定・表示する |
| `defaultOwner` | 日本 | 海岸側に所有者のある領域がない場合の、海域の所有者 |
| `undergroundBottomY` | -64 | 地下の表示・海域判定の下限Y |
| `displayTopY` | 320 | 基準点・領有境界の表示上限Y |
| `airspaceTopY` | 320 | 領空判定・領空枠の上限Y |
| `renderRadius` | 128 | 周辺を表示する半径。32〜256ブロック |
| `surveyStep` | 4 | 海域の格子幅。1・2・4・8から選択 |
| `samplesPerTick` | 2048 | 全プレイヤー合計の1tickの調査量。128〜8192 |
| `updateTicks` | 40 | 枠を同期する間隔。初期値は2秒 |
| `showThroughBlocks` | true | 地面越しに枠を表示する |
| `placementRequiresOp` | false | 測量標の通常設置をOPに制限する |
| `maxMarkers` | 8192 | 測量標の通常設置数の上限 |
| `maxRegions` | 256 | 領域数の上限 |
| `maxVertices` | 256 | 1つの領域に登録する頂点数の上限 |

Yの設定は、設置場所からの高さではなく、ワールド内の絶対座標です。下限は上限より小さくしてください。表示高度は-2048〜4096の範囲内で設定します。

距離を広げる場合は、必要に応じて延長大陸棚の上限→EEZ→領海の順に変更してください。途中の設定でも「領海≦EEZ≦延長上限」を満たす必要があります。

領空の上限は、領域の高さとは独立しています。`airspaceTopY`を変更すると、クライアント側の表示にも反映されます。

### 色と測量標の設置可否

```mcfunction
/jpboundary config color eez C084FC
/jpboundary marker enabled metal_tack false
```

色は6桁の16進数で指定します。区分名は`markers`、`baseline`、`territory`、`territorial`、`eez`、`shelf`、`airspace`です。

`marker enabled`では、5種類それぞれの通常設置を有効・無効にできます。既設の測量標を削除する設定ではありません。OPによる`/setblock`のような直接編集は別です。

### 通知文

`territoryMessage`、`territorialMessage`、`eezMessage`、`shelfMessage`、`airspaceMessage`で変更できます。`{owner}`が所有者名に置き換わります。

```mcfunction
/jpboundary config set territoryMessage {owner}の領域です。
/jpboundary config set eezMessage EEZ範囲内 ~漁業・採掘可能~
```

領域や所有者が変わったときに通知します。通知を無効にしたい場合は、`notifications false`を指定してください。

### 追加Modのバイオーム

海洋バイオームが標準の海洋タグを持たない場合は、設定ファイルの`extraShallowBiomes`または`extraDeepBiomes`へ、`modid:biome_name`の形式で追加してください。両方に指定した場合は、深い海を優先します。

### ファイルを編集した場合

```mcfunction
/jpboundary config reload
```

設定が不正な場合は反映せず、実行中の設定を保持します。起動時に不正な設定が見つかった場合は、ファイルを上書きせずに初期値で起動し、ログへ記録します。

`/jpboundary refresh`は海岸線を再調査するコマンドです。生成時のバイオームを調べる方式なので、埋め立てや`/fillbiome`による変更を反映する操作ではありません。

## 領域をJSONで編集する

```mcfunction
/jpboundary region export
/jpboundary region import
```

現在のディメンションの設定を、`config/japan-eez-regions-minecraft_overworld.json`などへ書き出し・読み込みします。領域の所有者、頂点、確定状態、基準線表示、高度、既設測量標の所有者を編集できます。

`import`を実行すると、そのディメンションの領域一覧がファイルの内容に置き換わります。必要な領域がファイルに含まれていることを確認してから実行してください。形式や多角形が不正な場合は反映しません。測量標ブロックを新しく設置する操作ではありません。
