import os
import time
import webbrowser
import yfinance as yf
import matplotlib.pyplot as plt

# --- 東証プライム主要・超優良企業リスト ---
CORE_MARKET_STOCKS = {
    "1605": "INPEX", "1801": "大成建設", "1925": "大和ハウス", "1928": "積水ハウス", 
    "2502": "アサヒGHD", "2503": "キリンHD", "2802": "味の素", "2914": "JT", 
    "3382": "セブン＆アイ", "3402": "東レ", "3407": "旭化成", "3861": "王子HD", 
    "4005": "住友化学", "4188": "三菱ケミカルG", "4452": "花王", "4502": "武田薬品", 
    "4503": "アステラス製薬", "4507": "塩野義製薬", "4519": "中外製薬", "4523": "エーザイ", 
    "4568": "第一三共", "4661": "オリエンタルランド", "4901": "富士フイルム", "4911": "資生堂", 
    "5020": "ＥＮＥＯＳ", "5108": "ブリヂストン", "5201": "ＡＧＣ", "5401": "日本製鉄", 
    "5411": "ＪＦＥ", "5713": "住友金属鉱山", "5802": "住友電気工業", "6098": "リクルートHD", 
    "6178": "日本郵政", "6273": "ＳＭＣ", "6301": "小松製作所", "6367": "ダイキン工業", 
    "6501": "日立製作所", "6503": "三菱電機", "6594": "ニデック", "6702": "富士通", 
    "6752": "パナソニックHD", "6758": "ソニーG", "6861": "キーエンス", "6902": "デンソー", 
    "6954": "ファナック", "6971": "京セラ", "6981": "村田製作所", "7011": "三菱重工業", 
    "7201": "日産自動車", "7203": "トヨタ自動車", "7267": "本田技研工業", "7269": "スズキ", 
    "7309": "シマノ", "7733": "オリンパス", "7741": "ＨＯYA", "7751": "キヤノン", 
    "7974": "任天堂", "8001": "伊藤忠商事", "8002": "丸紅", "8031": "三井物産", 
    "8053": "住友商事", "8058": "三菱商事", "8113": "ユニ・チャーム", "8267": "イオン", 
    "8306": "三菱ＵＦＪフィナンシャルG", "8316": "三井住友フィナンシャルG", "8411": "みずほフィナンシャルG", 
    "8591": "オリックス", "8604": "野村HD", "8725": "ＭＳ＆ＡＤ", "8766": "東京海上HD", 
    "8801": "三井不動産", "8802": "三菱地所", "9020": "ＪＲ東日本", "9021": "ＪJR西日本", 
    "9022": "ＪＲ東海", "9101": "日本郵船", "9104": "商船三井", "9107": "川崎汽船", 
    "9201": "日本航空", "9202": "ＡＮＡHD", "9432": "日本電信電話", "9433": "ＫＤＤＩ", 
    "9434": "ソフトバンク", "9613": "ＮＴＴデータ", "9735": "セコム", "9983": "ファーストリテイリング", 
    "9984": "ソフトバンクG", "3397": "トリドールHD"
}

ALL_TICKERS = [f"{code}.T" for code in CORE_MARKET_STOCKS.keys()]
total_count = len(ALL_TICKERS)

# 画像フォルダの設定
IMAGE_DIR_STRICT = "strict_charts"
IMAGE_DIR_NORMAL = "static_charts"
os.makedirs(IMAGE_DIR_STRICT, exist_ok=True)
os.makedirs(IMAGE_DIR_NORMAL, exist_ok=True)

html_filename = "index.html"

def generate_html_content(strict_stocks, normal_stocks):
    html = """<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <title>【統合版】銘柄底値圏ダッシュボード</title>
    <style>
        body { font-family: 'Helvetica Neue', Arial, 'Hiragino Kaku Gothic ProN', Meiryo, sans-serif; background-color: #f3f4f6; color: #333; margin: 0; padding: 30px; }
        h1 { text-align: center; color: #111827; margin-bottom: 5px; }
        h2 { text-align: center; color: #1f2937; margin-top: 40px; margin-bottom: 10px; border-bottom: 2px solid #ccc; padding-bottom: 10px; }
        .subtitle { text-align: center; color: #4b5563; margin-bottom: 20px; font-weight: bold; }
        .grid-container { display: grid; grid-template-columns: repeat(2, 1fr); gap: 25px; max-width: 1200px; margin: 0 auto; }
        
        /* 超厳選スタイル (赤ベース) */
        .card-strict { background: #ffffff; border-radius: 12px; box-shadow: 0 10px 15px -3px rgba(0,0,0,0.1); padding: 20px; border: 2px solid #ef4444; display: flex; flex-direction: column; align-items: center; }
        .title-strict { font-size: 1.3rem; font-weight: bold; margin: 0 0 10px 0; color: #dc2626; }
        .badge-6m { background-color: #fee2e2; color: #991b1b; padding: 4px 8px; border-radius: 4px; font-size: 0.85rem; font-weight: bold; border: 1px solid #fca5a5; }
        .badge-1m { background-color: #eff6ff; color: #1e40af; padding: 4px 8px; border-radius: 4px; font-size: 0.85rem; font-weight: bold; border: 1px solid #93c5fd; }
        
        /* 通常スタイル (青ベース) */
        .card-normal { background: #ffffff; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.05); padding: 20px; border: 1px solid #e0e0e0; display: flex; flex-direction: column; align-items: center; }
        .title-normal { font-size: 1.3rem; font-weight: bold; margin: 0 0 10px 0; color: #1a73e8; }
        .badge-3m { display: inline-block; background-color: #fce8e6; color: #c5221f; padding: 4px 8px; border-radius: 4px; font-size: 0.9rem; font-weight: bold; }
        
        .badge-container { margin-top: 8px; display: flex; gap: 8px; }
        .chart-img { max-width: 100%; height: auto; border-radius: 6px; margin-top: 10px; }
        .no-data { text-align: center; grid-column: span 2; font-size: 1.1rem; color: #666; padding: 4px 0; background: white; border-radius: 12px; }
        .section-box { max-width: 1200px; margin: 0 auto 50px auto; }
    </style>
</head>
<body>
    <h1>📊 銘柄底値圏ダッシュボード</h1>
    
    <!-- 上段：超厳選セクション -->
    <div class="section-box">
        <h2>🚨 1. 【超厳選】ダブル条件クリア・大バーゲン株</h2>
        <div class="subtitle">条件：6ヶ月前比 -15%以上 ＆ 1ヶ月前比 -7%以上（両方合致）</div>
        <div class="grid-container">
"""
    if strict_stocks:
        for stock in strict_stocks:
            html += f"""
            <div class="card-strict">
                <div style="width: 100%; margin-bottom: 15px;">
                    <div class="title-strict">【{stock['code']}】 {stock['name']}</div>
                    <div>現在株価: <strong>{stock['current_price']}円</strong></div>
                    <div class="badge-container">
                        <span class="badge-6m">6ヶ月前比: -{stock['drop_6m']}%</span>
                        <span class="badge-1m">1ヶ月前比: -{stock['drop_1m']}%</span>
                    </div>
                </div>
                <img class="chart-img" src="{stock['image']}" alt="株価チャート">
            </div>
            """
    else:
        html += '<div class="no-data">該当する【超厳選】銘柄は現在ありません。</div>'

    html += """
        </div>
    </div>

    <!-- 下段：通常セクション -->
    <div class="section-box">
        <h2>📈 2. 主要優良株 3ヶ月前比 -10%以上 カタログ</h2>
        <div class="subtitle">条件：3ヶ月前比 -10%以上</div>
        <div class="grid-container">
"""
    if normal_stocks:
        for stock in normal_stocks:
            html += f"""
            <div class="card-normal">
                <div style="width: 100%; margin-bottom: 15px;">
                    <div class="title-normal">【{stock['code']}】 {stock['name']}</div>
                    <div>現在株価: <strong>{stock['current_price']}円</strong></div>
                    <div style="margin-top: 5px;">
                        <span class="badge-3m">3ヶ月前比: -{stock['drop_3m']}%</span>
                    </div>
                </div>
                <img class="chart-img" src="{stock['image']}" alt="株価チャート">
            </div>
            """
    else:
        html += '<div class="no-data">該当する【通常】銘柄は現在ありません。</div>'

    html += """
        </div>
    </div>
</body>
</html>
"""
    return html

print(f"\n🚀 統合スキャンを開始します... (合計: {total_count} 銘柄 / 3秒に1回)")
print("----------------------------------------------------------------")

strict_picks = []
normal_picks = []

# 初回の空HTMLを作成
with open(html_filename, "w", encoding="utf-8") as f:
    f.write(generate_html_content([], []))

for index, ticker_code in enumerate(ALL_TICKERS, start=1):
    pure_code = ticker_code.replace(".T", "")
    company_name = CORE_MARKET_STOCKS.get(pure_code, "不明な銘柄")
    
    progress = (index / total_count) * 100
    print(f"[{index}/{total_count}] ({progress:.1f}%) {pure_code}: {company_name} ... ", end="", flush=True)
    
    try:
        # 計算用データを取得（6ヶ月分あれば全条件が計算可能）
        df = yf.Ticker(ticker_code).history(period="6mo")
        
        if df.empty or len(df) < 25:
            print("データ不足スキップ")
            time.sleep(3)
            continue
            
        current_price = df['Close'].iloc[-1]
        
        # 1ヶ月前(20営業日前)、3ヶ月前(60営業日前)、6ヶ月前(先頭)の価格
        price_1mo_ago = df['Close'].iloc[-20]
        price_3mo_ago = df['Close'].iloc[-60] if len(df) >= 60 else df['Close'].iloc[0]
        price_6mo_ago = df['Close'].iloc[0]
        
        # 下落率計算
        drop_1m = ((price_1mo_ago - current_price) / price_1mo_ago) * 100
        drop_3m = ((price_3mo_ago - current_price) / price_3mo_ago) * 100
        drop_6m = ((price_6mo_ago - current_price) / price_6mo_ago) * 100
        
        is_strict = (drop_6m >= 15 and drop_1m >= 7)
        is_normal = (drop_3m >= 10)
        
        hit_tags = []
        
        # --- 1. 超厳選判定 (app_strict) ---
        if is_strict:
            hit_tags.append("超厳選Hit!")
            plt.figure(figsize=(6, 3.5))
            plt.plot(df.index, df['Close'], color='#dc2626', linewidth=2)
            plt.title(f"{pure_code}: {company_name} (6-Month)", fontsize=12, fontweight='bold', fontname='MS Gothic')
            plt.grid(True, linestyle='--', alpha=0.5)
            plt.xticks(rotation=15)
            plt.tight_layout()
            
            strict_img_path = f"{IMAGE_DIR_STRICT}/{pure_code}.png"
            plt.savefig(strict_img_path, dpi=150)
            plt.close()
            
            strict_picks.append({
                "code": pure_code,
                "name": company_name,
                "current_price": round(current_price, 1),
                "drop_6m": round(drop_6m, 1),
                "drop_1m": round(drop_1m, 1),
                "image": strict_img_path
            })
            
        # --- 2. 通常判定 (app) ---
        if is_normal:
            hit_tags.append("通常Hit!")
            df_3m = df.tail(60) # チャート表示用（直近3ヶ月）
            plt.figure(figsize=(6, 3.5))
            plt.plot(df_3m.index, df_3m['Close'], color='#1a73e8', linewidth=2)
            plt.title(f"{pure_code}: {company_name}", fontsize=12, fontweight='bold', fontname='MS Gothic')
            plt.grid(True, linestyle='--', alpha=0.5)
            plt.xticks(rotation=15)
            plt.tight_layout()
            
            normal_img_path = f"{IMAGE_DIR_NORMAL}/{pure_code}.png"
            plt.savefig(normal_img_path, dpi=150)
            plt.close()
            
            normal_picks.append({
                "code": pure_code,
                "name": company_name,
                "current_price": round(current_price, 1),
                "drop_3m": round(drop_3m, 1),
                "image": normal_img_path
            })
            
        if hit_tags:
            print(f"🔥 {' & '.join(hit_tags)}")
            # 条件に一致するたびにHTMLを更新
            with open(html_filename, "w", encoding="utf-8") as f:
                f.write(generate_html_content(strict_picks, normal_picks))
        else:
            print("スルー")
            
    except Exception as e:
        print(f"エラー回避: {e}")
    
    time.sleep(3)

print("\n✨ すべての統合スキャンが完了しました！")
html_path = os.path.abspath(html_filename)
webbrowser.open(f"file://{html_path}")