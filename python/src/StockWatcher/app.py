#python app.py
#http://127.0.0.1:5000

import json
import logging
import shutil
from pathlib import Path
from datetime import datetime

import yfinance as yf

from flask import (
    Flask,
    render_template,
    request,
    redirect,
    url_for
)

try:
    from win10toast import ToastNotifier
    toaster = ToastNotifier()
except Exception:
    toaster = None

try:
    import pyperclip
except Exception:
    pyperclip = None


# --------------------------
# ディレクトリ作成
# --------------------------
Path("logs").mkdir(exist_ok=True)
Path("backup").mkdir(exist_ok=True)
Path("ai_support").mkdir(exist_ok=True)

# --------------------------
# ログ設定
# --------------------------
logging.basicConfig(
    filename="logs/app.log",
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
    encoding="utf-8"
)

# --------------------------
# Flask
# --------------------------
app = Flask(__name__)

STOCKS_JSON_FILE = Path("stocks.json")
PAST_JSON_FILE = Path("past.json")

# --------------------------
# JSON汎用関数
# --------------------------
def load_json(file_path):
    if not file_path.exists():
        return []
    with open(file_path, "r", encoding="utf-8") as f:
        return json.load(f)

def save_json(file_path, data):
    # 保存前にcode順でソート（文字列として比較）
    sorted_data = sorted(data, key=lambda x: str(x.get("code", "")))
    with open(file_path, "w", encoding="utf-8") as f:
        json.dump(sorted_data, f, ensure_ascii=False, indent=4)


# --------------------------
# Yahoo Finance
# --------------------------
def get_current_price(code):
    try:
        ticker = yf.Ticker(f"{code}.T")
        price = ticker.fast_info.get("lastPrice")
        if price:
            return float(price)

        hist = ticker.history(period="1d")
        if not hist.empty:
            return float(hist["Close"].iloc[-1])
    except Exception as ex:
        logging.exception(ex)
    return None


# --------------------------
# 通知
# --------------------------
def notify(title, message):
    if toaster is None:
        return
    try:
        toaster.show_toast(title, message, duration=10, threaded=True)
    except Exception:
        logging.exception("通知失敗")


# --------------------------
# 保有日数
# --------------------------
def calc_holding_days(buy_date):
    try:
        dt = datetime.strptime(buy_date, "%Y-%m-%d")
        return (datetime.now() - dt).days
    except Exception:
        return 0


# --------------------------
# 損益率
# --------------------------
def calc_profit_rate(buy_price, current_price):
    if current_price is None:
        return None
    try:
        return round(((current_price - buy_price) / buy_price) * 100, 2)
    except Exception:
        return None


# --------------------------
# JSONバックアップ（両ファイル対応）
# --------------------------
def create_backup():
    timestamp = datetime.now().strftime("%Y%m%d%H%M%S")
    
    # stocks.jsonのバックアップ
    if STOCKS_JSON_FILE.exists():
        stocks_backup = Path("backup") / f"stocks_{timestamp}.json"
        shutil.copy(STOCKS_JSON_FILE, stocks_backup)
        logging.info(f"バックアップ作成:{stocks_backup}")

    # past.jsonのバックアップ
    if PAST_JSON_FILE.exists():
        past_backup = Path("backup") / f"past_{timestamp}.json"
        shutil.copy(PAST_JSON_FILE, past_backup)
        logging.info(f"バックアップ作成:{past_backup}")


# --------------------------
# AI相談ファイル（現在・過去両方の銘柄を含める仕様）
# --------------------------
def create_ai_prompt_file():
    stocks = load_json(STOCKS_JSON_FILE)
    past_stocks = load_json(PAST_JSON_FILE)

    timestamp = datetime.now().strftime("%Y%m%d%H%M%S")
    file_path = Path("ai_support") / f"ai_support_{timestamp}.txt"

    lines = [
        "あなたは個人投資家向けのアドバイザーです。",
        "",
        "【投資家プロフィール】",
        "・株式投資初心者",
        "・楽天証券のかぶピタを利用",
        "・NISA成長投資枠を利用",
        "・投資額は1銘柄あたり1,000円～10,000円程度",
        "・投資収益で生活する予定はない",
        "・目的はお小遣い程度の利益を得ること",
        "・投資期間は1～3年程度",
        "・配当より株価上昇を重視",
        "・利益が出たら売却したい",
        "・損切りは基本的に行わない",
        "・最大損失が1万円程度なら許容",
        "・専門用語はできるだけ避けること",
        "・株の知識は初心者レベル",
        "・短期売買より数か月～数年保有",
        "・大きな利益より堅実なお小遣い稼ぎを重視",
        "・売却タイミングを知りたい",
        "・1銘柄あたりの投資額は少額",
        "・大きなリターンより小さな利益を積み重ねたい",
        "・投資判断の勉強も兼ねている",
        "・初心者なので売却タイミングの提案を重視する",
        "",
        "【分析時に重視する項目】",
        "・アナリスト予想",
        "・今後1～3年の成長見込み",
        "・現在の株価水準",
        "・初心者にも分かる説明",
        "",
        "【評価基準】",
        "以下を5段階評価してください。",
        "・売却推奨度",
        "・保有継続推奨度",
        "・買い増し推奨度",
        "",
        "【重要】",
        "利益最大化だけを目的にせず、",
        "初心者がお小遣いを増やすという観点で提案してください。",
        ""
    ]

    def append_stock_lines(stock_list, label):
        for stock in stock_list:
            current_price = get_current_price(stock["code"])
            profit_rate = calc_profit_rate(stock["avg_buy_price"], current_price)
            holding_days = calc_holding_days(stock["buy_date"])

            lines.append(f"状態:{label}")
            lines.append(f"銘柄:{stock['name']}")
            lines.append(f"コード:{stock['code']}")
            lines.append(f"購入日:{stock['buy_date']}")
            lines.append(f"保有日数:{holding_days}")
            lines.append(f"平均取得価格:{stock['avg_buy_price']}")
            lines.append(f"現在価格:{current_price if current_price is not None else '取得失敗'}")
            lines.append(f"損益率:{profit_rate if profit_rate is not None else '計算不可'}")
            
            if current_price is not None:
                buy_price = stock["avg_buy_price"]
                lines.append(f"参考価格(+10%):{round(buy_price * 1.10, 2)}")
                lines.append(f"参考価格(+20%):{round(buy_price * 1.20, 2)}")
                lines.append(f"参考価格(+30%):{round(buy_price * 1.30, 2)}")

            lines.append(f"メモ:{stock.get('memo','')}")
            lines.append("")

    if stocks:
        lines.append("【現在保有中の銘柄】")
        append_stock_lines(stocks, "保有中")
        
    if past_stocks:
        lines.append("【過去に保有していた銘柄】")
        append_stock_lines(past_stocks, "過去保有")

    lines.extend([
        "【依頼内容】",
        "各銘柄について以下の形式で回答してください。",
        "",
        "売却推奨度：★★★★★",
        "保有継続推奨度：★★★★★",
        "買い増し推奨度：★★★★★",
        "",
        "理由：",
        "初心者向けに分かりやすく説明してください。",
        "",
        "注意点：",
        "初心者が気を付けるべきことを説明してください。",
        "",
        "期待度：",
        "低・中・高",
        "",
        "また、現在価格から見て",
        "+10%、+20%、+30% のどこで利益確定を検討すると良いか提案してください。",
        "その理由も説明してください。"
    ])

    text = "\n".join(lines)

    with open(file_path, "w", encoding="utf-8") as f:
        f.write(text)

    if pyperclip:
        try:
            pyperclip.copy(text)
        except Exception:
            logging.exception("クリップボードコピー失敗")

    return str(file_path)


# --------------------------
# 表示データ生成
# --------------------------
def build_stock_view_data(file_path, do_notify=False):
    stocks = load_json(file_path)

    for stock in stocks:
        current_price = get_current_price(stock["code"])
        stock["current_price"] = current_price
        stock["profit_rate"] = calc_profit_rate(stock["avg_buy_price"], current_price)
        stock["holding_days"] = calc_holding_days(stock["buy_date"])

        # 通知は現在保有銘柄（stocks.json）の更新時のみ走るよう制御可能に
        if do_notify and stock["profit_rate"] is not None:
            if stock["profit_rate"] >= stock.get("notify_plus", 999):
                notify("株価通知", f"{stock['name']} +{stock['profit_rate']}%")
            if stock["profit_rate"] <= -stock.get("notify_minus", 999):
                notify("株価通知", f"{stock['name']} {stock['profit_rate']}%")

    return stocks


# --------------------------
# ルーティング
# --------------------------
@app.route("/")
def index():
    # 現在銘柄と過去銘柄をそれぞれ詳細形式で取得
    stocks = build_stock_view_data(STOCKS_JSON_FILE, do_notify=True)
    past_stocks = build_stock_view_data(PAST_JSON_FILE, do_notify=False)

    return render_template(
        "index.html",
        stocks=stocks,
        past_stocks=past_stocks
    )


@app.route("/add", methods=["POST"])
def add_stock():
    target = request.form.get("target_json", "stocks")
    file_path = PAST_JSON_FILE if target == "past" else STOCKS_JSON_FILE
    
    stocks = load_json(file_path)
    stocks.append({
        "code": request.form["code"],
        "name": request.form["name"],
        "buy_date": request.form["buy_date"],
        "avg_buy_price": float(request.form["avg_buy_price"]),
        "notify_plus": float(request.form.get("notify_plus", 10)),
        "notify_minus": float(request.form.get("notify_minus", 10)),
        "memo": request.form["memo"]
    })

    # save_jsonの内部で自動的にソートされます
    save_json(file_path, stocks)
    logging.info(f"{target}に追加:{request.form['code']}")
    return redirect("/")


@app.route("/sell/<int:index>")
def sell_stock(index):
    stocks = load_json(STOCKS_JSON_FILE)
    past_stocks = load_json(PAST_JSON_FILE)

    if 0 <= index < len(stocks):
        # 現在保有からポップして過去保有へ追加
        sold_stock = stocks.pop(index)
        past_stocks.append(sold_stock)

        # 両方のファイルをソートして保存
        save_json(STOCKS_JSON_FILE, stocks)
        save_json(PAST_JSON_FILE, past_stocks)
        logging.info(f"売却(過去保有へ移動):{sold_stock['code']}")

    return redirect("/")


@app.route("/delete/<string:target>/<int:index>")
def delete_stock(target, index):
    file_path = PAST_JSON_FILE if target == "past" else STOCKS_JSON_FILE
    stocks = load_json(file_path)

    if 0 <= index < len(stocks):
        del stocks[index]
        save_json(file_path, stocks)

    return redirect("/")


@app.route("/backup", methods=["POST"])
def backup():
    create_backup()
    return redirect("/")


@app.route("/generate_ai_prompt", methods=["POST"])
def generate_ai_prompt():
    create_ai_prompt_file()
    return redirect("/")


if __name__ == "__main__":
    app.run(debug=True)