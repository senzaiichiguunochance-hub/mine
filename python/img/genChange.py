import streamlit as st
from PIL import Image
import torch
from diffusers import StableDiffusionPipeline

st.title("性別変換アプリ (顔画像)")

uploaded_file = st.file_uploader("人物画像を選択", type=["png","jpg","jpeg"])
target_gender = st.radio("変換先の性別", ["男性", "女性"])

if uploaded_file:
    original_image = Image.open(uploaded_file).convert("RGB")
    st.image(original_image, caption="元の画像", use_column_width=True)

    if st.button("変換する"):
        st.info("変換中…少々お待ちください")

        # モデル読み込み
        device = "cuda" if torch.cuda.is_available() else "cpu"
        model_id = "runwayml/stable-diffusion-v1-5"  # 学習済みSD
        pipe = StableDiffusionPipeline.from_pretrained(model_id, torch_dtype=torch.float16 if device=="cuda" else torch.float32)
        pipe = pipe.to(device)

        # プロンプト設定
        prompt = f"A photo of a {target_gender.lower()} person, realistic, high quality"

        # 生成
        result = pipe(prompt, init_image=original_image, strength=0.6, guidance_scale=7.5)
        output_image = result.images[0]

        st.image(output_image, caption="変換後画像", use_column_width=True)
        output_image.save("output.png")
        st.download_button("ダウンロード", data=open("output.png","rb"), file_name="output.png", mime="image/png")
