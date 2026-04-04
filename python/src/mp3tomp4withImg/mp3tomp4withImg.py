import tkinter as tk
from tkinter import filedialog, messagebox, ttk
import os
import threading
from moviepy import ImageClip, AudioFileClip

class VideoCreatorApp:
    def __init__(self):
        self.root = tk.Tk()
        self.root.title("画像+音声 動画作成ツール")
        self.root.geometry("400x200")
        
        # メイン画面の構築
        self.label = tk.Label(self.root, text="下のボタンを押して作成を開始してください", pady=20)
        self.label.pack()
        
        self.start_button = tk.Button(self.root, text="ファイルを選択して動画作成", 
                                      command=self.start_process, 
                                      width=30, height=2, bg="#4CAF50", fg="white")
        self.start_button.pack(pady=10)

    def start_process(self):
        # 1. 画像選択
        image_path = filedialog.askopenfilename(
            title="画像ファイルを選択してください",
            filetypes=[("Image files", "*.jpg *.jpeg *.png *.bmp")]
        )
        if not image_path: return

        # 2. 音声選択
        audio_path = filedialog.askopenfilename(
            title="音声ファイルを選択してください",
            filetypes=[("Audio files", "*.mp3 *.m4a *.wav")]
        )
        if not audio_path: return

        # 3. 保存先選択（音声ファイル名をデフォルトにする）
        base_name = os.path.splitext(os.path.basename(audio_path))[0]
        default_filename = f"{base_name}.mp4"
        output_path = filedialog.asksaveasfilename(
            title="保存先を指定してください",
            initialfile=default_filename,
            defaultextension=".mp4",
            filetypes=[("Video files", "*.mp4")]
        )
        if not output_path: return

        # 4. 進捗ウィンドウの表示
        self.show_progress_window(image_path, audio_path, output_path)

    def show_progress_window(self, image_path, audio_path, output_path):
        # ボタンを無効化
        self.start_button.config(state=tk.DISABLED)
        
        # サブウィンドウ作成
        self.prog_win = tk.Toplevel(self.root)
        self.prog_win.title("処理中")
        self.prog_win.geometry("300x120")
        self.prog_win.transient(self.root) # 親ウィンドウの上に固定
        
        tk.Label(self.prog_win, text="動画を作成しています...", pady=10).pack()
        
        # 左右に動き続けるプログレスバー
        self.pb = ttk.Progressbar(self.prog_win, mode='indeterminate', length=200)
        self.pb.pack(pady=10)
        self.pb.start(10)
        
        # 書き出し処理を別スレッドで実行（画面フリーズ防止）
        thread = threading.Thread(target=self.create_video, args=(image_path, audio_path, output_path))
        thread.start()

    def create_video(self, image_path, audio_path, output_path):
        try:
            # MoviePy処理
            audio = AudioFileClip(audio_path)
            video = ImageClip(image_path).with_duration(audio.duration)
            video = video.with_audio(audio)
            
            # 書き出し（logger=Noneでエラー回避）
            video.write_videofile(output_path, fps=24, codec="libx264", audio_codec="aac", logger=None)
            
            # 成功時：メインスレッドでUI更新
            self.root.after(0, lambda: self.finish_process("完了", "動画の作成が完了しました！"))
            
        except Exception as e:
            # 失敗時：メインスレッドでエラー表示
            self.root.after(0, lambda: self.finish_process("エラー", f"エラーが発生しました:\n{e}"))

    def finish_process(self, title, message):
        # インジケーター停止とウィンドウ削除
        self.pb.stop()
        self.prog_win.destroy()
        self.start_button.config(state=tk.NORMAL)
        
        if title == "完了":
            messagebox.showinfo(title, message)
        else:
            messagebox.showerror(title, message)

if __name__ == "__main__":
    app = VideoCreatorApp()
    app.root.mainloop()