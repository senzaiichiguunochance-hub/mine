import tkinter as tk
from tkinter import filedialog, messagebox, ttk # ttkを追加
import os
from moviepy import ImageClip, AudioFileClip

def select_files_and_create_video():
    root = tk.Tk()
    root.withdraw()

    image_path = filedialog.askopenfilename(
        title="画像ファイルを選択してください",
        filetypes=[("Image files", "*.jpg *.jpeg *.png *.bmp")]
    )
    if not image_path: return

    audio_path = filedialog.askopenfilename(
        title="音声ファイルを選択してください",
        filetypes=[("Audio files", "*.mp3 *.m4a *.wav")]
    )
    if not audio_path: return

    base_name = os.path.splitext(os.path.basename(audio_path))[0]
    default_filename = f"{base_name}.mp4"

    output_path = filedialog.asksaveasfilename(
        title="保存先を指定してください",
        initialfile=default_filename,  # デフォルトのファイル名をセット
        defaultextension=".mp4",
        filetypes=[("Video files", "*.mp4")]
    )
    if not output_path: return

    try:
        print("動画を作成中...")
        # 音声の読み込み
        audio = AudioFileClip(audio_path)
        
        # 【重要】MoviePy 2.0での変更点
        # set_duration → with_duration
        # set_audio → with_audio
        video = ImageClip(image_path).with_duration(audio.duration)
        video = video.with_audio(audio)
        
        # 書き出し
        video.write_videofile(output_path, fps=24, codec="libx264", audio_codec="aac", logger=None)
        
        messagebox.showinfo("完了", f"動画の作成が完了しました！")
    
    except Exception as e:
        messagebox.showerror("エラー", f"エラーが発生しました:\n{e}")

if __name__ == "__main__":
    select_files_and_create_video()