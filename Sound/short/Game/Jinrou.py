kinter as tk
from tkinter import messagebox, simpledialog
import random

# 役職リスト
roles = ["村人", "村人", "村人", "占い師", "人狼"]
random.shuffle(roles)
player_role = roles[0]
players = ["あなた", "A", "B", "C", "D"]
eliminated = []
player_alive = True
game_over = False

def show_rules():
    rules = (
        "【人狼ゲームのルール】\n"
        "・プレイヤーは一人で操作します。\n"
        "・5人の村に、1人の人狼が紛れています。\n"
        "・1人は占い師で、毎晩1人の正体を占えます。\n"
        "・人狼は毎晩1人を襲撃できます。\n"
        "・昼に投票で1人を追放し、人狼を排除すれば勝利です。\n"
        "・人狼が村人の数以上になれば人狼の勝ちです。\n"
        "・追放されたプレイヤーは観戦のみ可能です。\n"
    )
    messagebox.showinfo("ルール説明", rules)

def show_role():
    messagebox.showinfo("あなたの役職", f"あなたの役職は『{player_role}』です。")

def night_phase():
    global player_alive, game_over
    if game_over:
        return
    if player_alive and player_role == "占い師":
        target = simpledialog.askstring("占い", "誰を占いますか？ (A, B, C, D)を入力")
        if target in players and target not in eliminated:
            index = players.index(target)
            messagebox.showinfo("占い結果", f"{target}の正体は『{roles[index]}』です。")
    
    # 人狼の襲撃
    remaining_players = [p for p in players if p not in eliminated]
    wolves = [p for p in remaining_players if roles[players.index(p)] == "人狼"]
    if wolves:
        target = random.choice([p for p in remaining_players if p not in wolves])
        eliminated.append(target)
        messagebox.showinfo("夜の襲撃", f"{target}が人狼に襲撃されました！")
        if target == "あなた":
            messagebox.showinfo("ゲーム継続", "あなたは襲撃されました。以降は観戦モードになります。")
            player_alive = False
    
    if check_game_over():
        return
    
    messagebox.showinfo("夜の時間", "夜が明けました。昼の投票を開始します。")

def day_phase():
    global player_alive, game_over
    if game_over:
        return
    if player_alive:
        valid_choices = [p for p in players if p not in eliminated]
        target = simpledialog.askstring("投票", f"誰を追放しますか？ {valid_choices} から選択")
        if target in valid_choices:
            index = players.index(target)
            eliminated.append(target)
            messagebox.showinfo("追放", f"{target}が追放されました。役職: {roles[index]}")
            if target == "あなた":
                messagebox.showinfo("ゲーム継続", "あなたは追放されました。以降は観戦モードになります。")
                player_alive = False
            if check_game_over():
                return
    
    # 村人の投票（ランダム）
    remaining_players = [p for p in players if p not in eliminated]
    if remaining_players:
        vote_target = random.choice(remaining_players)
        eliminated.append(vote_target)
        messagebox.showinfo("投票", f"村人たちの投票により{vote_target}が追放されました！")
        if check_game_over():
            return
    
    night_phase()

def check_game_over():
    global game_over
    if game_over:
        return True
    remaining_roles = [roles[players.index(p)] for p in players if p not in eliminated]
    if "人狼" not in remaining_roles:
        game_over = True
        messagebox.showinfo("ゲーム終了", "村人の勝利！")
        root.quit()
        return True
    elif remaining_roles.count("人狼") >= remaining_roles.count("村人"):
        game_over = True
        messagebox.showinfo("ゲーム終了", "人狼の勝利！")
        root.quit()
        return True
    return False

def start_game():
    show_rules()
    show_role()
    night_phase()
    while not game_over:
        if check_game_over():
            break
        day_phase()

# Tkinter GUI のセットアップ
root = tk.Tk()
root.withdraw()  # メインウィンドウを表示しない
start_game()







