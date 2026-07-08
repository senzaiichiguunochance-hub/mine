import os
import time
import webbrowser
import yfinance as yf
import matplotlib.pyplot as plt

# --- 1. エラー回避：東証プライムの主要・超優良企業100社のマスターデータ ---
# 日本を代表する、潰れる心配のない大型優良株のリストです。お小遣い稼ぎに最適。
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
    "9022": "ＪＲ東海", "9101": "日本郵船", "9104": "商船三井", "9107": "川崎汽船", 
    "9201": "日本航空", "9202": "ＡＮＡHD", "9432": "日本電信電話", "9433": "ＫＤＤＩ", 
    "9434": "ソフトバンク", "9613": "ＮＴＴデータ", "9735": "セコム", "9983": "ファーストリテイリング", 
    "9984": "ソフトバンクG", "3397": "トリドールHD"
}

ALL_TICKERS = [f"{code}.T" for code in CORE_MARKET_STOCKS.keys()]
total_count = len(ALL_TICKERS)

IMAGE_DIR = "static_charts"
os.makedirs(IMAGE_DIR, exist_ok=True)

picked_stocks = []
html_filename = "index.html"

def generate_html_content(stocks):
    html = """<!DOCTYPE html>
<html lang="ja">
<head>
    <meta charset="UTF-8">
    <title>【リアルタイム更新】主要優良株・底値圏カタログ</title>
    <style>
        body { font-family: 'Helvetica Neue', Arial, 'Hiragino Kaku Gothic ProN', Meiryo, sans-serif; background-color: #f8f9fa; color: #333; margin: 0; padding: 30px; }
        h1 { text-align: center; color: #202124; margin-bottom: 5px; }
        .subtitle { text-align: center; color: #666; margin-bottom: 30px; }
        .grid-container { display: grid; grid-template-columns: repeat(2, 1fr); gap: 25px; max-width: 1200px; margin: 0 auto; }
        .stock-card { background: #ffffff; border-radius: 12px; box-shadow: 0 4px 6px rgba(0,0,0,0.05); padding: 20px; border: 1px solid #e0e0e0; display: flex; flex-direction: column; align-items: center; }
        .stock-info { width: 100%; margin-bottom: 15px; }
        .stock-title { font-size: 1.3rem; font-weight: bold; margin: 0 0 10px 0; color: #1a73e8; }
        .status-badge { display: inline-block; background-color: #fce8e6; color: #c5221f; padding: 4px 8px; border-radius: 4px; font-size: 0.9rem; font-weight: bold; }
        .chart-img { max-width: 100%; height: auto; border-radius: 6px; }
        .no-data { text-align: center; grid-column: span 2; font-size: 1.2rem; color: #666; padding: 50px; }
    </style>
</head>
<body>
    <h1>📊 主要優良株 3ヶ月前比 -10%以上の底値圏カタログ</h1>
    <div class="subtitle">※プログラム実行中もリアルタイムでここに追加されていきます</div>
    <div class="grid-container">
"""
    if stocks:
        for stock in stocks:
            html += f"""
            <div class="stock-card">
                <div class="stock-info">
                    <div class="stock-title">【{stock['code']}】 {stock['name']}</div>
                    <div>現在株価: <strong>{stock['current_price']}円</strong></div>
                    <div style="margin-top: 5px;">
                        <span class="status-badge">3ヶ月前比: -{stock['drop_rate']}%</span>
                    </div>
                </div>
                <img class="chart-img" src="{stock['image']}" alt="株価チャート">
            </div>
            """
    else:
        html += '<div class="no-data">現在スキャン中... 条件に合う銘柄が見つかり次第、ここに表示されます。</div>'
        
    html += "</div></body></html>"
    return html

print(f"\n🚀 主要優良企業のスキャンを開始します... (合計: {total_count} 銘柄 / 3秒に1回)")
print("※途中で止めても、そこまでのデータは index.html にリアルタイム保存されます。")
print("----------------------------------------------------------------")

# 初回の空のHTMLを作っておく
with open(html_filename, "w", encoding="utf-8") as f:
    f.write(generate_html_content([]))

for index, ticker_code in enumerate(ALL_TICKERS, start=1):
    pure_code = ticker_code.replace(".T", "")
    company_name = CORE_MARKET_STOCKS.get(pure_code, "不明な銘柄")
    
    progress = (index / total_count) * 100
    print(f"[{index}/{total_count}] ({progress:.1f}%) {pure_code}: {company_name} ... ", end="", flush=True)
    
    try:
        df = yf.Ticker(ticker_code).history(period="3mo")
        
        if df.empty or len(df) < 2:
            print("データなしスキップ")
            time.sleep(3)
            continue
            
        price_3mo_ago = df['Close'].iloc[0]
        current_price = df['Close'].iloc[-1]
        drop_rate = ((price_3mo_ago - current_price) / price_3mo_ago) * 100
        
        if drop_rate >= 10:
            print(f"🔥 Hit! (-{drop_rate:.1f}%)")
            
            # --- グラフの生成 ---
            plt.figure(figsize=(6, 3.5))
            plt.plot(df.index, df['Close'], color='#1a73e8', linewidth=2)
            plt.title(f"{pure_code}: {company_name}", fontsize=12, fontweight='bold', fontname='MS Gothic')
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
                "drop_rate": round(drop_rate, 1),
                "image": image_path
            })
            
            # ヒットのたびにHTMLを即座に上書き
            with open(html_filename, "w", encoding="utf-8") as f:
                f.write(generate_html_content(picked_stocks))
        else:
            print("スルー")
            
    except Exception as e:
        print(f"エラー回避: {e}")
    
    time.sleep(3)

print("\n✨ すべてのスキャンが完了しました！")
html_path = os.path.abspath(html_filename)
webbrowser.open(f"file://{html_path}")