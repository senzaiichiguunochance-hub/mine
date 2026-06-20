import json
import logging
from pathlib import Path
from datetime import datetime

import yfinance as yf
from flask import Flask, render_template, request, redirect

try:
    from win10toast import ToastNotifier
    toaster = ToastNotifier()
except Exception:
    toaster = None

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s"
)

app = Flask(__name__)

JSON_FILE = Path("stocks.json")


def load_stocks():
    if not JSON_FILE.exists():
        return []

    with open(JSON_FILE, "r", encoding="utf-8") as f:
        return json.load(f)


def save_stocks(stocks):
    with open(JSON_FILE, "w", encoding="utf-8") as f:
        json.dump(
            stocks,
            f,
            ensure_ascii=False,
            indent=4
        )


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


def notify(title, message):
    if toaster:
        try:
            toaster.show_toast(
                title,
                message,
                duration=10,
                threaded=True
            )
        except Exception:
            logging.exception("通知失敗")


@app.route("/")
def index():

    stocks = load_stocks()

    for stock in stocks:

        current_price = get_current_price(stock["code"])

        stock["current_price"] = current_price

        if current_price is not None:

            avg_buy_price = float(
                stock["avg_buy_price"]
            )

            stock["profit_rate"] = round(
                ((current_price - avg_buy_price)
                 / avg_buy_price) * 100,
                2
            )

            if stock["profit_rate"] >= stock["notify_plus"]:
                notify(
                    "株価通知",
                    f'{stock["name"]} が +{stock["profit_rate"]}%'
                )

            if stock["profit_rate"] <= -stock["notify_minus"]:
                notify(
                    "株価通知",
                    f'{stock["name"]} が {stock["profit_rate"]}%'
                )

        else:
            stock["profit_rate"] = None

        buy_date = datetime.strptime(
            stock["buy_date"],
            "%Y-%m-%d"
        )

        stock["holding_days"] = (
            datetime.now() - buy_date
        ).days

    return render_template(
        "index.html",
        stocks=stocks
    )


@app.route("/add", methods=["POST"])
def add_stock():

    stocks = load_stocks()

    stocks.append({
        "code": request.form["code"],
        "name": request.form["name"],
        "buy_date": request.form["buy_date"],
        "avg_buy_price": float(
            request.form["avg_buy_price"]
        ),
        "notify_plus": float(
            request.form["notify_plus"]
        ),
        "notify_minus": float(
            request.form["notify_minus"]
        ),
        "memo": request.form["memo"]
    })

    save_stocks(stocks)

    return redirect("/")


@app.route("/delete/<code>")
def delete_stock(code):

    stocks = load_stocks()

    stocks = [
        s for s in stocks
        if s["code"] != code
    ]

    save_stocks(stocks)

    return redirect("/")


if __name__ == "__main__":
    app.run(debug=True)