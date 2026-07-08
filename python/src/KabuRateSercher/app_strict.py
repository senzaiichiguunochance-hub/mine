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
    "7309": "シマノ", "7733": "オリンパス", "7741": "ＨＯＹＡ", "7751": "キヤノン", 
    "7974": "任天堂", "8001": "伊藤忠商事", "8002": "丸紅", "8031": "三井物産", 
    "8053": "住友商事", "8058": "三菱商事", "8113": "ユニ・チャーム", "8267": "イオン", 
    "8306": "三菱ＵＦＪフィナンシャルG", "8316": "三井住友フィナンシャルG", "8411": "みずほフィナンシャルG", 
    "8591": "オリックス", "8604": "野村HD", "8725": "ＭＳ＆ＡＤ", "8766": "東京海上HD", 
    "8801": "三井不動産", "8802": "三菱地所", "9020": "ＪＲ東日本", "9021": "ＪＲ西日本", 
    "9022": "ＪＲ東海", "9101": "日本郵線", "9104": "商船三井", "9107": "川崎汽船", 
    "9201": "日本航空", "9202": "ＡＮＡHD", "9432": "日本電信電話", "9433": "ＫＤＤＩ", 
    "9434": "ソフトバンク", "9613": "ＮＴＴデータ", "9735": "セコム", "9983": "ファーストリテイリング", 
    "9984": "ソフトバンクG", "3397": "トリドールHD"
}

ALL_TICKERS = [f"{code}.T" for code in CORE_MARKET_STOCKS.keys()]
total_count = len(ALL_TICKERS)

IMAGE_DIR = "strict_charts"  # 既存の画像フォルダと混ざらないよう別フォルダに
os.makedirs(IMAGE_DIR, exist_ok=True)

# 今回は別の結果画面として出力
html_filename = "index_strict.html"

def generate_html_content(stocks):
    html = """<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <title>【超厳選】大バーゲン底値圏カタログ</title>
    <style>
        body { font-family: 'Helvetica Neue', Arial, 'Hiragino Kaku Gothic ProN', Meiryo, sans-serif; background-color: #f3f4f6; color: #333; margin: 0; padding: 30px; }
        h1 { text-align: center; color: #111827; margin-bottom: 5px; }
        .subtitle { text-align: center; color: #4b5563; margin-bottom: 30px; font-weight: bold; }
        .grid-container { display: grid; grid-template-columns: repeat(2, 1fr); gap: 25px; max-width: 1200px; margin: 0 auto; }
        .stock-card { background: #ffffff; border-radius: 12px; box-shadow: 0 10px 15px -3px rgba(0,0,0,0.1); padding: 20px; border: 2px solid #ef4444; display: flex; flex-direction: column; align-items: center; }
        .stock-info { width: 100%; margin-bottom: 15px; }
        .stock-title { font-size: 1.3rem; font-weight: bold; margin: 0 0 10px 0; color: #dc2626; }
        .badge-container { margin-top: 8px; display: flex; gap: 8px; }
        .status-badge-6m { background-color: #fee2e2; color: #991b1b; padding: 4px 8px; border-radius: 4px; font-size: 0.85rem; font-weight: bold; border: 1px solid #fca5a5; }
        .status-badge-1m { background-color: #eff6ff; color: #1e40af; padding: 4px 8px; border-radius: 4px; font-size: 0.85rem; font-weight: bold; border: 1px solid #93c5fd; }
        .chart-img { max-width: 100%; height: auto; border-radius: 6px; margin-top: 10px; }
        .no-data { text-align: center; grid-column: span 2; font-size: 1.2rem; color: #4b5563; padding: 50px; background: white; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.05); }
    </style>
</head>
<body>
    <h1>🚨 【超厳選】ダブル条件クリア・大バーゲン株カタログ</h1>
    <div class="subtitle">条件：6ヶ月前比 -15%以上 ＆ 1ヶ月前比 -7%以上（両方合致）</div>
    <div class="grid-container">
"""
    if stocks:
        for stock in stocks:
            html += f"""
            <div class="stock-card">
                <div class="stock-info">
                    <div class="stock-title">【{stock['code']}】 {stock['name']}</div>
                    <div>現在株価: <strong>{stock['current_price']}円</strong></div>
                    <div class="badge-container">
                        <span class="status-badge-6m">6ヶ月前比: -{stock['drop_6m']}%</span>
                        <span class="status-badge-1m">1ヶ月前比: -{stock['drop_1m']}%</span>
                    </div>
                </div>
                <img class="chart-img" src="{stock['image']}" alt="株価チャート">
            </div>
            """
    else:
        html += '<div class="no-data">現在スキャン中、または条件を満たす歴史的大バーゲン企業はありません。</div>'
        
    html += "</div></body></html>"
    return html

print(f"\n🚀 【超厳選】ダブル条件スキャンを開始します... (合計: {total_count} 銘柄 / 3秒に1回)")
print("条件: [6ヶ月前比 -15%以上] かつ [1ヶ月前比 -7%以上]")
print("----------------------------------------------------------------")

with open(html_filename, "w", encoding="utf-8") as f:
    f.write(generate_html_content([]))

picked_stocks = []

for index, ticker_code in enumerate(ALL_TICKERS, start=1):
    pure_code = ticker_code.replace(".T", "")
    company_name = CORE_MARKET_STOCKS.get(pure_code, "不明な銘柄")
    
    progress = (index / total_count) * 100
    print(f"[{index}/{total_count}] ({progress:.1f}%) {pure_code}: {company_name} ... ", end="", flush=True)
    
    try:
        # 計算のために6ヶ月分のデータを取得
        df = yf.Ticker(ticker_code).history(period="6mo")
        
        # 1ヶ月前（営業日ベースで約20日前）のインデックス位置を計算
        if df.empty or len(df) < 25:
            print("データ不足スキップ")
            time.sleep(3)
            continue
            
        current_price = df['Close'].iloc[-1]
        price_6mo_ago = df['Close'].iloc[0]
        price_1mo_ago = df['Close'].iloc[-20] # 約1ヶ月前（20営業日前）の終値
        
        # 下落率の計算
        drop_6m = ((price_6mo_ago - current_price) / price_6mo_ago) * 100
        drop_1m = ((price_1mo_ago - current_price) / price_1mo_ago) * 100
        
        # 【判定】6ヶ月前比-15%以上 かつ 1ヶ月前比-7%以上
        if drop_6m >= 15 and drop_1m >= 7:
            print(f"🔥 【ダブルHit!】 (6ヶ月: -{drop_6m:.1f}% / 1ヶ月: -{drop_1m:.1f}%)")
            
            # --- グラフの生成（直近6ヶ月のトレンドを表示） ---
            plt.figure(figsize=(6, 3.5))
            plt.plot(df.index, df['Close'], color='#dc2626', linewidth=2) # 厳選用に赤ライン
            plt.title(f"{pure_code}: {company_name} (6-Month)", fontsize=12, fontweight='bold', fontname='MS Gothic')
            plt.grid(True, linestyle='--', alpha=0.5)
            plt.xticks(rotation=15)
            plt.tight_layout()
            
            image_path = f"{IMAGE_DIR}/{pure_code}.png"
            plt.savefig(image_path, dpi=150)
            plt.close()
            
            picked_stocks.append({
                "code": pure_code,
                "name": company_name,
                "current_price": round(current_price, 1),
                "drop_6m": round(drop_6m, 1),
                "drop_1m": round(drop_1m, 1),
                "image": image_path
            })
            
            with open(html_filename, "w", encoding="utf-8") as f:
                f.write(generate_html_content(picked_stocks))
        else:
            print("スルー")
            
    except Exception as e:
        print(f"エラー回避: {e}")
    
    time.sleep(3)

print("\n✨ すべての厳選スキャンが完了しました！")
html_path = os.path.abspath(html_filename)
webbrowser.open(f"file://{html_path}")