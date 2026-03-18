# ♟ オセロ (Othello) - Java / Spring Boot

Spring Boot で作った AI 対戦オセロゲームです。GitHub + Render だけでデプロイできます。

## 機能

- 🎮 プレイヤー (黒) vs AI (白) 対戦
- 🤖 AI はミニマックス法 + αβ枝刈り (深さ 5)
- 💡 ヒント表示のON/OFF
- 🔄 いつでも新しいゲームを開始可能
- 📱 スマホ対応レスポンシブUI

## デプロイ手順

### 1. GitHub にプッシュ

```bash
git init
git add .
git commit -m "Initial commit"
git remote add origin https://github.com/<あなたのユーザー名>/othello.git
git push -u origin main
```

### 2. Render でデプロイ

1. [render.com](https://render.com) にサインイン
2. **New** → **Web Service** をクリック
3. GitHub リポジトリを接続
4. 以下の設定を入力:

| 項目 | 値 |
|------|-----|
| **Environment** | `Java` |
| **Build Command** | `mvn clean package -DskipTests` |
| **Start Command** | `java -jar target/othello-1.0.0.jar` |

5. **Create Web Service** をクリック
6. ビルドが完了したら、発行された URL にアクセス！

### ローカルで実行する場合

```bash
mvn spring-boot:run
```

→ http://localhost:8080 を開く

## 技術スタック

- **バックエンド**: Java 17 + Spring Boot 3.2
- **フロントエンド**: Vanilla HTML/CSS/JS（シングルページ）
- **AI**: ミニマックス法 + αβ枝刈り（深さ5）
- **ビルド**: Maven
- **ホスティング**: Render (Free Tier 対応)
