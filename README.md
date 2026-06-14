# CSVデータ編集ツール

## 目的

このツールは、CSVデータを確認、編集、保存するためのJava Swingデスクトップアプリケーションです。
データグループごとのフォルダー構成を読み込み、CSVファイルだけを対象として扱います。
XMLなどCSV以外のファイルは編集対象にせず、ツリーにも表示しません。

## ビルド方法

Mavenでビルドします。

```sh
mvn clean package
```

自動検証だけを実行する場合は次のコマンドを使用します。

```sh
mvn test
```

## 起動方法

リポジトリ直下の `CSVデータ編集ツール起動.cmd` をダブルクリックすると起動できます。
初回起動時や変更後は、CMD内で必要なビルドと依存ライブラリのクラスパス生成を行います。

ビルド後、次のコマンドで起動します。

```sh
java -cp target/classes com.example.csveditor.app.CsvEditorApp
```

初期版の起動クラスは `com.example.csveditor.app.CsvEditorApp` です。

## 使い方

アプリ起動後、データグループのルートフォルダーを選択します。
左側のツリーには、選択したルート配下のフォルダーとCSVファイルだけが表示されます。
CSVファイルをダブルクリック、またはEnterキーで開くと、右側の編集エリアにCSVごとの編集カードが追加されます。
複数CSVを開いた場合は、タブではなく縦方向に並びます。
セルを編集すると、そのCSVカードが未保存状態になります。
保存ボタンでCSV単位の保存、再読み込みボタンでCSV単位の再読み込み、閉じるボタンでCSV単位のクローズを行います。
未保存のCSVを閉じる、再読み込みする、またはアプリを終了する場合は、保存する、破棄する、キャンセルする、を選択できます。

## フォルダー構成例

```text
Data01/
  Data01_01/
    sample001/
      input_parameters.csv
      thresholds.csv
      mappings.csv
      runtime_options.csv
      assertions.csv
    sample002/
      environment.csv
      feature_flags.csv
      endpoints.csv
      roles.csv
      validation_rules.csv
    sample003/
      customers.csv
      orders.csv
      products.csv
      messages.csv
      expected_results.csv
  Data01_02/
    sample001/
    sample002/
    sample003/
Data02/
  Data02_01/
    sample001/
    sample002/
    sample003/
Data03/
  Data03_01/
    sample001/
    sample002/
    sample003/
```

リポジトリ内の `samples/data` には、上記と同じ考え方で `Data01`、`Data02`、`Data03` の3つのルートフォルダーを用意しています。
各ルートフォルダーには3つのデータグループフォルダーがあり、各データグループフォルダーには `sample001`、`sample002`、`sample003` サブフォルダーがあります。
各サブフォルダーには5つのCSVを配置しています。
アプリでは、`samples/data/Data01` のように任意のルートフォルダーを選択して確認できます。
XMLなどCSV以外のファイルは表示、編集対象になりません。
同じ名前のCSVが別フォルダーにあっても、正規化済み絶対パスで別ファイルとして扱います。

## 文字コード方針

CSVの標準文字コードはCP932です。
Java上ではCP932相当の `windows-31j` または `MS932` を使用する方針です。
初期版では文字コードの自動判定は行いません。
CP932以外のCSVを読み込んだ場合、文字化けする可能性があります。

## ヘッダー方針

初期版ではCSVの1行目をヘッダー行として扱います。
1行目の値は、そのCSV専用のJTable列名として表示する方針です。
空のヘッダーや不足したヘッダーは、表示上 `Column 1` のような補助列名で補完します。
重複ヘッダーは、表示上 `項目名 (2)` のように識別できる名前に補完します。
この補完は表示用であり、元のヘッダー値を勝手に書き換えるものではありません。
ヘッダー無しCSVへの切り替えは初期版ではUIから提供しません。

## CSVごとの独立性

対象CSVは、ファイルごとに列名、列数、項目構造が異なる前提です。
複数CSVを開いた場合でも、CSV間で列定義を共有、統合、整列、マージしません。
1つのCSVにつき、独立したドキュメント、編集パネル、TableModel、JTableを持つ構成にします。

## バックアップ方針

保存時は、アプリ配置ルート直下の `.backup` フォルダーに日時付きバックアップを作成します。
開発中は、このリポジトリ直下の `.backup` がバックアップ保存先です。
実行可能JAR版では、JARファイルと同じフォルダーに同梱された `.backup` がバックアップ保存先です。
ポータブルZIP版では、ZIPを展開した `CSV-Data-Editor` フォルダー直下の `.backup` がバックアップ保存先です。
リリース成果物生成スクリプトは、実行可能JARの配置先とポータブルZIP内に `.backup` フォルダーを作成します。
ZIPに確実に含めるため、`.backup` には説明用の `README.txt` を同梱します。
`.backup` 配下には、選択したCSVルートフォルダー名と元CSVの相対パス構造を保持します。
例えば任意のCSVルートフォルダーを開き、`Data01_01/sample002/user.csv` を保存した場合、バックアップは `.backup/<ルートフォルダー名>/Data01_01/sample002/user.csv.20260614123045.bak` に作成します。
バックアップ名は `user.csv.20260614123045.bak` のように、元ファイル名へ日時と `.bak` を付ける形式です。
バックアップはCSVファイルごとに最新3世代まで残し、古い世代は保存時に削除します。
可能な範囲で一時ファイルへ書き出してから元ファイルへ置換します。
バックアップ作成や保存に失敗した場合は、元ファイルを可能な限り維持し、エラーダイアログで通知します。

## データグループの判定設定

データグループの判定は `config/data-grouping.json` で設定します。
デフォルトでは、ルートフォルダーから2階層目のフォルダーをデータグループフォルダーとして扱います。
例えば `Data01/Data01_01/sample002/endpoints.csv` の場合、`Data01_01` がデータグループ名になります。

```json
{
  "activePattern": "pathSegmentIndex",
  "patterns": {
    "pathSegmentPattern": {
      "regex": ".*_.*",
      "searchTarget": "directoriesOnly",
      "fallback": "firstDirectory"
    },
    "pathSegmentIndex": {
      "index": 1,
      "fallback": "firstDirectory"
    },
    "parentDirectory": {
      "levelsUpFromFile": 2,
      "fallback": "firstDirectory"
    }
  }
}
```

`activePattern` で使用する判定方式を選びます。
`pathSegmentIndex` の `index` は0始まりで、`1` がルートフォルダーから2階層目です。
設定ファイルが存在しない場合や不正な場合は、既存互換の「最初に `_` を含むフォルダー名」へフォールバックします。
設定変更後はアプリを再起動してください。

## サンプルデータ

リポジトリの `samples/data` 以下に、動作確認用のCSVを用意しています。
配布用の実行可能JARとポータブルZIPには、サンプルデータを含めません。
`Data01`、`Data02`、`Data03` の3ルート構成で、各ルートに `Data01_01`、`Data01_02`、`Data01_03` のようなデータグループフォルダーがあります。
各データグループには `sample001`、`sample002`、`sample003` サブフォルダーがあり、それぞれ5つのCSVを配置しています。
サンプルCSVはCP932で保存し、日本語、ファイルごとに異なる列名と列数、カンマ入り値、ダブルクォート入り値、空文字、空列、改行入り値を含みます。
CSV種別ごとに列数が異なり、同じCSV種別はルートやデータグループが変わっても同じ列数になるようにしています。
例えば `environment.csv` は全データグループで2列、`orders.csv` は全データグループで8列、`assertions.csv` は全データグループで16列です。
データ行数もCSV種別ごとに異なり、1行、2行、5行、20行などのCSVを含めています。
例えば `environment.csv` は1行、`customers.csv` と `assertions.csv` は20行です。

## 初期制約

初期版では検索、置換、CSV差分比較、外部実行連携、Excel風フィルターは実装対象外です。
Undo、Redoは各CSVパネル内で最大5操作まで保持し、`Ctrl+Z`、`Ctrl+Y` で操作できます。
行追加、行削除、行コピー、行貼り付けはCSVパネル内の操作として実装しています。
初期版では列追加、列削除、ヘッダー編集は実装対象外です。
元CSVのクォート有無、細かい整形、行ごとの元列数の完全保持は保証しません。
保存時はCSVとしての値の意味を保持することを優先します。
不揃い列CSVは、読み込み時にヘッダー行と全データ行の最大列数へ合わせて表示します。
保存時は、そのCSVのJTable列数に合わせて書き出します。
巨大CSVや非常に大量のファイルを含むフォルダーでは、読み込みや表示に時間がかかる可能性があります。
