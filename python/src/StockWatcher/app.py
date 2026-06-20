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

JSON_FILE = Path("stocks.json")

# --------------------------
# JSON
# --------------------------

def load_stocks():

    if not JSON_FILE.exists():

        return []

    with open(
        JSON_FILE,
        "r",
        encoding="utf-8"
    ) as f:

        return json.load(f)


def save_stocks(stocks):

    with open(
        JSON_FILE,
        "w",
        encoding="utf-8"
    ) as f:

        json.dump(
            stocks,
            f,
            ensure_ascii=False,
            indent=4
        )


# --------------------------
# Yahoo Finance
# --------------------------

def get_current_price(code):

    try:

        ticker = yf.Ticker(
            f"{code}.T"
        )

        price = ticker.fast_info.get(
            "lastPrice"
        )

        if price:

            return float(price)

        hist = ticker.history(
            period="1d"
        )

        if not hist.empty:

            return float(
                hist["Close"].iloc[-1]
            )

    except Exception as ex:

        logging.exception(ex)

    return None


# --------------------------
# 通知
# --------------------------

def notify(
        title,
        message
):

    if toaster is None:
        return

    try:

        toaster.show_toast(
            title,
            message,
            duration=10,
            threaded=True
        )

    except Exception:

        logging.exception(
            "通知失敗"
        )


# --------------------------
# 保有日数
# --------------------------

def calc_holding_days(
        buy_date
):

    try:

        dt = datetime.strptime(
            buy_date,
            "%Y-%m-%d"
        )

        return (
            datetime.now() - dt
        ).days

    except Exception:

        return 0


# --------------------------
# 損益率
# --------------------------

def calc_profit_rate(
        buy_price,
        current_price
):

    if current_price is None:

        return None

    try:

        return round(
            (
                (
                    current_price
                    - buy_price
                )
                / buy_price
            ) * 100,
            2
        )

    except Exception:

        return None


# --------------------------
# JSONバックアップ
# --------------------------

def create_backup():

    timestamp = datetime.now().strftime(
        "%Y%m%d%H%M%S"
    )

    backup_file = (
        Path("backup")
        /
        f"stocks_{timestamp}.json"
    )

    shutil.copy(
        JSON_FILE,
        backup_file
    )

    logging.info(
        f"バックアップ作成:{backup_file}"
    )

    return str(backup_file)


# --------------------------
# AI相談ファイル
# --------------------------

def create_ai_prompt_file():

    stocks = load_stocks()

    timestamp = datetime.now().strftime(
        "%Y%m%d%H%M%S"
    )

    file_path = (
        Path("ai_support")
        /
        f"ai_support_{timestamp}.txt"
    )

    lines = []

    lines.append(
        "あなたは個人投資家向けのアドバイザーです。"
    )

    lines.append("")

    lines.append("【投資家プロフィール】")
    lines.append("")
    lines.append("・株式投資初心者")
    lines.append("・楽天証券のかぶピタを利用")
    lines.append("・NISA成長投資枠を利用")
    lines.append("・投資額は1銘柄あたり1,000円～10,000円程度")
    lines.append("・投資収益で生活する予定はない")
    lines.append("・目的はお小遣い程度の利益を得ること")
    lines.append("・投資期間は1～3年程度")
    lines.append("・配当より株価上昇を重視")
    lines.append("・利益が出たら売却したい")
    lines.append("・損切りは基本的に行わない")
    lines.append("・最大損失が1万円程度なら許容")
    lines.append("・専門用語はできるだけ避けること")
    lines.append("・株の知識は初心者レベル")
    lines.append("・短期売買より数か月～数年保有")
    lines.append("・大きな利益より堅実なお小遣い稼ぎを重視")
    lines.append("・売却タイミングを知りたい")
    lines.append("・1銘柄あたりの投資額は少額")
    lines.append("・大きなリターンより小さな利益を積み重ねたい")
    lines.append("・投資判断の勉強も兼ねている")
    lines.append("・初心者なので売却タイミングの提案を重視する")
    lines.append("")

    lines.append("【分析時に重視する項目】")
    lines.append("")
    lines.append("・アナリスト予想")
    lines.append("・今後1～3年の成長見込み")
    lines.append("・現在の株価水準")
    lines.append("・初心者にも分かる説明")
    lines.append("")

    lines.append("【評価基準】")
    lines.append("")
    lines.append("以下を5段階評価してください。")
    lines.append("")
    lines.append("・売却推奨度")
    lines.append("・保有継続推奨度")
    lines.append("・買い増し推奨度")
    lines.append("")
    
    lines.append("【重要】")
    lines.append("利益最大化だけを目的にせず、")
    lines.append("初心者がお小遣いを増やすという観点で提案してください。")
    lines.append("")
    
    for stock in stocks:

        current_price = get_current_price(
            stock["code"]
        )

        profit_rate = calc_profit_rate(
            stock["avg_buy_price"],
            current_price
        )

        holding_days = calc_holding_days(
            stock["buy_date"]
        )

        lines.append(
            f"銘柄:{stock['name']}"
        )

        lines.append(
            f"コード:{stock['code']}"
        )

        lines.append(
            f"購入日:{stock['buy_date']}"
        )

        lines.append(
            f"保有日数:{holding_days}"
        )

        lines.append(
            f"平均取得価格:{stock['avg_buy_price']}"
        )

        lines.append(
            f"現在価格:{current_price if current_price is not None else '取得失敗'}"
        )
        
        lines.append(
            f"損益率:{profit_rate if profit_rate is not None else '計算不可'}"
        )
        
        if current_price is not None:

            buy_price = stock["avg_buy_price"]

            target_10 = round(
                buy_price * 1.10,
                2
            )

            target_20 = round(
                buy_price * 1.20,
                2
            )

            target_30 = round(
                buy_price * 1.30,
                2
            )

            lines.append(
                f"参考価格(+10%):{target_10}"
            )

            lines.append(
                f"参考価格(+20%):{target_20}"
            )

            lines.append(
                f"参考価格(+30%):{target_30}"
            )

        lines.append(
            f"メモ:{stock.get('memo','')}"
        )

        lines.append("")

    lines.append("")
    lines.append("【依頼内容】")
    lines.append("")

    lines.append(
        "各銘柄について以下の形式で回答してください。"
    )

    lines.append("")

    lines.append("売却推奨度：★★★★★")
    lines.append("保有継続推奨度：★★★★★")
    lines.append("買い増し推奨度：★★★★★")
    lines.append("")

    lines.append("理由：")
    lines.append("初心者向けに分かりやすく説明してください。")
    lines.append("")

    lines.append("注意点：")
    lines.append("初心者が気を付けるべきことを説明してください。")
    lines.append("")

    lines.append("期待度：")
    lines.append("低・中・高")
    lines.append("")

    lines.append(
       "また、現在価格から見て"
    )

    lines.append(
        "+10%、+20%、+30% のどこで利益確定を検討すると良いか提案してください。"
    )

    lines.append(
        "その理由も説明してください。"
    )

    text = "\n".join(lines)

    with open(
        file_path,
        "w",
        encoding="utf-8"
    ) as f:

        f.write(text)

    if pyperclip:

        try:

            pyperclip.copy(text)

        except Exception:

            logging.exception(
                "クリップボードコピー失敗"
            )

    return str(file_path)
def build_stock_view_data():

    stocks = load_stocks()

    for stock in stocks:

        current_price = get_current_price(
            stock["code"]
        )

        stock["current_price"] = current_price

        stock["profit_rate"] = calc_profit_rate(
            stock["avg_buy_price"],
            current_price
        )

        stock["holding_days"] = calc_holding_days(
            stock["buy_date"]
        )

        if stock["profit_rate"] is not None:

            if stock["profit_rate"] >= stock["notify_plus"]:

                notify(
                    "株価通知",
                    f"{stock['name']} +{stock['profit_rate']}%"
                )

            if stock["profit_rate"] <= -stock["notify_minus"]:

                notify(
                    "株価通知",
                    f"{stock['name']} {stock['profit_rate']}%"
                )

    return stocks
def build_summary_data():

    stocks = build_stock_view_data()

    result = {}

    for stock in stocks:

        code = stock["code"]

        if code not in result:

            result[code] = {
                "code": code,
                "name": stock["name"],
                "count": 0,
                "avg_buy_price": 0,
                "current_price": stock["current_price"],
                "profit_rate": 0
            }

        result[code]["count"] += 1

        result[code]["avg_buy_price"] += (
            stock["avg_buy_price"]
        )

        if stock["profit_rate"] is not None:

            result[code]["profit_rate"] += (
                stock["profit_rate"]
            )

    rows = []

    for row in result.values():

        row["avg_buy_price"] = round(
            row["avg_buy_price"] / row["count"],
            2
        )

        row["profit_rate"] = round(
            row["profit_rate"] / row["count"],
            2
        )

        rows.append(row)

    return rows
@app.route("/")
def index():

    mode = request.args.get(
        "mode",
        "detail"
    )

    if mode == "summary":

        stocks = build_summary_data()

    else:

        stocks = build_stock_view_data()

    return render_template(
        "index.html",
        stocks=stocks,
        mode=mode
    )
@app.route(
    "/add",
    methods=["POST"]
)
def add_stock():

    stocks = load_stocks()

    stocks.append({

        "code":
            request.form["code"],

        "name":
            request.form["name"],

        "buy_date":
            request.form["buy_date"],

        "avg_buy_price":
            float(
                request.form[
                    "avg_buy_price"
                ]
            ),

        "notify_plus":
            float(
                request.form[
                    "notify_plus"
                ]
            ),

        "notify_minus":
            float(
                request.form[
                    "notify_minus"
                ]
            ),

        "memo":
            request.form["memo"]
    })

    save_stocks(stocks)

    logging.info(
        f"追加:{request.form['code']}"
    )

    return redirect("/")
@app.route("/delete/<int:index>")
def delete_stock(index):

    stocks = load_stocks()

    if 0 <= index < len(stocks):

        del stocks[index]

        save_stocks(stocks)

    return redirect("/")
@app.route(
    "/backup",
    methods=["POST"]
)
def backup():

    create_backup()

    return redirect("/")
@app.route(
    "/generate_ai_prompt",
    methods=["POST"]
)
def generate_ai_prompt():

    create_ai_prompt_file()

    return redirect("/")
if __name__ == "__main__":

    app.run(
        debug=True
    )
