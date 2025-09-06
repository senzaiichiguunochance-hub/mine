import streamlit as st
import pandas as pd
import numpy as np
import matplotlib.pyplot as plt

# ==== データ読み込み ====
csv_url = "https://loto6.thekyo.jp/data/loto6.csv"
df = pd.read_csv(csv_url, encoding="shift_jis", header=0)
number_cols = ['第1数字', '第2数字', '第3数字', '第4数字', '第5数字', '第6数字']
df_numbers = df[number_cols].copy()

# ==== 統計的特徴 ====
last100 = df_numbers.tail(100).values.flatten()
freq = np.bincount(last100, minlength=44)[1:]
freq_norm = freq / freq.sum()

last_seen = {n: len(df_numbers) - df_numbers.apply(lambda row: n in row.values, axis=1).values[::-1].argmax()
             for n in range(1, 44)}
max_gap = max(last_seen.values())
gap_score = np.array([last_seen[n]/max_gap for n in range(1,44)])

# ==== 仮のAIスコア（本来は学習済みモデルから予測） ====
#np.random.seed(42)
ai_score = np.random.rand(43)  # ダミー

# ==== ハイブリッドスコア ====
w_ai, w_freq, w_gap = 0.5, 0.3, 0.2
final_score = w_ai * ai_score + w_freq * freq_norm + w_gap * gap_score

# ==== 予測番号 ====
# 上位6個を選んでソートして表示
pred_ai = np.sort(np.argsort(ai_score)[-6:] + 1)
pred_freq = np.sort(np.argsort(freq_norm)[-6:] + 1)
pred_hybrid = np.sort(np.argsort(final_score)[-6:] + 1)

# 共通の描画関数
def show_numbers(title, numbers, color="#4CAF50"):
    st.markdown(f"### {title}")
    cols = st.columns(len(numbers))
    for col, num in zip(cols, numbers):
        col.markdown(
            f"""
            <div style='
                background-color: {color};
                border-radius: 50%;
                width: 60px; height: 60px;
                display: flex; align-items: center; justify-content: center;
                color: white; font-size: 20px; font-weight: bold;
                margin: auto; box-shadow: 2px 2px 6px rgba(0,0,0,0.3);
            '>{num}</div>
            """,
            unsafe_allow_html=True
        )

st.title("🎯 Loto6 予想アプリ")

show_numbers("🔮 AI予想", pred_ai, "#2196F3")       # 青
show_numbers("📊 統計予想", pred_freq, "#9C27B0")   # 紫
show_numbers("⚡ ハイブリッド予想", pred_hybrid, "#FF5722") # オレンジ

st.header("📊 出現頻度（直近100回）")
fig, ax = plt.subplots(figsize=(10,4))
ax.bar(range(1,44), freq)
ax.set_xlabel("番号")
ax.set_ylabel("出現回数")
st.pyplot(fig)

st.header("⏳ 冷遇数字ランキング")
cold_numbers = sorted(last_seen.items(), key=lambda x: -x[1])[:10]
st.table(pd.DataFrame(cold_numbers, columns=["番号","未出現回数"]))

#実行コマンド
#streamlit run loto6.py