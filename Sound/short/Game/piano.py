kinter as tk
import winsound
import time
import random

# 音のマッピング (周波数, ms) と音名
WHITE_KEYS = {
    '_': (247, 300, 'B'), 'z': (262, 300, 'ド'), 'x': (294, 300, 'レ'), 'c': (330, 300, 'ミ'), 'v': (349, 300, 'ファ'), 'b': (392, 300, 'ソ'), 'n': (440, 300, 'ラ'), 'm': (494, 300, 'シ'),
    'a': (523, 300, 'ド'), 's': (587, 300, 'レ'), 'd': (659, 300, 'ミ'), 'f': (698, 300, 'ファ'), 'g': (784, 300, 'ソ'), 'h': (880, 300, 'ラ'), 'j': (988, 300, 'シ'),
    'k': (1047, 300, 'ド'), 'l': (1175, 300, 'レ'), ';': (1319, 300, 'ミ'), "'": (1397, 300, 'ファ'),
    'q': (1568, 300, 'ソ'), 'w': (1760, 300, 'ラ'), 'e': (1976, 300, 'シ'), 'r': (2093, 300, 'ド'), 't': (2349, 300, 'レ'), 'y': (2637, 300, 'ミ'), 'u': (2794, 300, 'ファ')
}

BLACK_KEYS = {
    'A': (277, 300, 'ド#'), 'S': (311, 300, 'レ#'), 'F': (370, 300, 'ファ#'), 'G': (415, 300, 'ソ#'), 'H': (466, 300, 'ラ#'),
    'K': (554, 300, 'ド#'), 'L': (622, 300, 'レ#'), 'P': (740, 300, 'ファ#'),
    'Q': (831, 300, 'ソ#'), 'W': (932, 300, 'ラ#'), 'E': (1109, 300, 'ド#'), 'R': (1245, 300, 'レ#'), 'T': (1480, 300, 'ミ#'), 'Y': (1661, 300, 'ファ#'), 'U': (1865, 300, 'ソ#')
}  # Shift+キー

sequence = []  # 演奏履歴

def play_sound(frequency, duration, note, index):
    """ 音と背景色を同期して再生 """
    # 背景色を変更
    listbox.itemconfig(index, {'bg': random.choice(['lightblue', 'lightgreen', 'lightyellow', 'lightpink', 'lightgray'])})
    
    # 音を鳴らす
    winsound.Beep(frequency, duration)
    
    # 音のデータをsequenceに追加
    sequence.append((frequency, duration, note))
    
    # 画面描画を更新
    root.update_idletasks()
    time.sleep(duration / 1000)  # 音が鳴っている間待機

def on_key_press(event):
    """ キーが押されたときの処理 """
    key = event.keysym
    if key in WHITE_KEYS:
        # アイテムを追加してからインデックスを取得
        note_name = WHITE_KEYS[key][2]
        index = listbox.size()
        listbox.insert(tk.END, f"{key}: {note_name}")  # アイテムを追加
        play_sound(*WHITE_KEYS[key], index)  # 修正：indexを渡す
    elif key in BLACK_KEYS and event.state & 0x1:  # Shiftキー判定
        # アイテムを追加してからインデックスを取得
        note_name = BLACK_KEYS[key][2]
        index = listbox.size()
        listbox.insert(tk.END, f"{key}: {note_name}")  # アイテムを追加
        play_sound(*BLACK_KEYS[key], index)  # 修正：indexを渡す

def replay_sequence():
    """ 再生ボタンが押されたときの処理 """
    # すべての行の背景色をリセット（白以外）
    for idx in range(listbox.size()):
        listbox.itemconfig(idx, {'bg': 'white'})
    
    def play_sequence(idx=0):
        """ 再生するシーケンスを処理 """
        if idx < len(sequence):
            freq, dur, note = sequence[idx]
            # 背景色変更（白は除外したランダムな色）
            listbox.itemconfig(idx, {'bg': random.choice(['lightblue', 'lightgreen', 'lightyellow', 'lightpink', 'lightgray'])})
            # 音の再生
            winsound.Beep(freq, dur)
            root.update_idletasks()  # 描画更新
            time.sleep(dur / 1000)  # 音が鳴っている間待機
            root.after(int(dur), play_sequence, idx + 1)  # 次の音を再生

    # 再生を開始
    play_sequence()

def clear_listbox():
    """ リストボックスをクリアする """
    listbox.delete(0, tk.END)
    sequence.clear()

# GUI作成
root = tk.Tk()

# 画面中央に配置し、ウィンドウサイズ調整
root.title("キーボードピアノ")
root.geometry("400x500+{}+{}".format((root.winfo_screenwidth() - 400) // 2, (root.winfo_screenheight() - 500) // 2))

label = tk.Label(root, text="Press a key", font=("Arial", 24))
label.pack()

# リストボックスをもっと縦長に
listbox = tk.Listbox(root, height=20)  # 高さを増加
listbox.pack(expand=True, fill=tk.BOTH)

# 再現ボタン
replay_button = tk.Button(root, text="再現", command=replay_sequence)
replay_button.pack()

# クリアボタン
clear_button = tk.Button(root, text="リストをクリア", command=clear_listbox)
clear_button.pack()

root.bind("<KeyPress>", on_key_press)
root.mainloop()







