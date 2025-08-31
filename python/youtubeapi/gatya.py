import random
import streamlit as st
from googleapiclient.discovery import build

# 🔑 SecretsからAPIキーを読み込む
api_key = st.secrets["api_key"]
youtube = build("youtube", "v3", developerKey=api_key)

st.title("🎲 YouTube ガチャ")
st.write("ランダムキーワードで動画を探して遊ぼう！")

if st.button("ガチャを回す！"):
    keywords = ["カオス", "失敗", "宇宙", "爆笑", "🐸", "料理", "深夜"]
    word = random.choice(keywords)

    request = youtube.search().list(
        part="snippet",
        q=word,
        type="video",
        maxResults=10
    )
    response = request.execute()

    video = random.choice(response["items"])
    video_id = video["id"]["videoId"]
    video_title = video["snippet"]["title"]
    url = f"https://www.youtube.com/watch?v={video_id}"

    st.subheader(f"🎲 今日のガチャワード: {word}")
    st.write(f"🎬 当たり動画: {video_title}")
    st.markdown(f"[👉 動画を見る]({url})", unsafe_allow_html=True)
