ygame
import random
import tkinter as tk
from tkinter import messagebox

# 初期設定
pygame.init()
WIDTH, HEIGHT = 600, 600
GRID_SIZE = 20
COLS = WIDTH // GRID_SIZE
ROWS = HEIGHT // GRID_SIZE
screen = pygame.display.set_mode((WIDTH, HEIGHT))
pygame.display.set_caption("迷路ゲーム")

# 色
WHITE = (255, 255, 255)
BLACK = (0, 0, 0)
RED = (255, 0, 0)
GREEN = (0, 255, 0)  # ゴールの色

# 迷路生成関数（深さ優先探索法）
def generate_maze():
    maze = [[1] * COLS for _ in range(ROWS)]  # 1は壁、0は通路
    stack = [(random.randrange(1, COLS, 2), random.randrange(1, ROWS, 2))]
    maze[stack[0][1]][stack[0][0]] = 0

    while stack:
        x, y = stack[-1]
        neighbors = []
        for nx, ny in [(x + 2, y), (x - 2, y), (x, y + 2), (x, y - 2)]:
            if 0 < nx < COLS - 1 and 0 < ny < ROWS - 1 and maze[ny][nx] == 1:
                wall_x = (x + nx) // 2
                wall_y = (y + ny) // 2
                neighbors.append((nx, ny, wall_x, wall_y))
        
        if neighbors:
            nx, ny, wx, wy = random.choice(neighbors)
            maze[ny][nx] = 0
            maze[wy][wx] = 0
            stack.append((nx, ny))
        else:
            stack.pop()

    return maze

# スタートとゴールをランダムに決める
def set_start_and_goal():
    start_pos = [1, 1]  # スタート地点（左上）
    goal_pos = [random.randrange(1, COLS - 1), random.randrange(1, ROWS - 1)]  # ランダムなゴール
    return start_pos, goal_pos

# ゴールの周りに触れるか判定
def check_goal_reached(start_pos, goal_pos):
    gx, gy = goal_pos
    sx, sy = start_pos
    
    # ゴールの周りに触れる判定（左、右、上、下）
    if (sx == gx - 1 and sy == gy) or (sx == gx + 1 and sy == gy) or (sx == gx and sy == gy - 1) or (sx == gx and sy == gy + 1):
        return True
    return False

# 迷路を描画
def draw_maze(maze):
    for y in range(ROWS):
        for x in range(COLS):
            color = WHITE if maze[y][x] == 0 else BLACK
            pygame.draw.rect(screen, color, pygame.Rect(x * GRID_SIZE, y * GRID_SIZE, GRID_SIZE, GRID_SIZE))

# ゴールを表示
def draw_goal(goal_pos):
    pygame.draw.rect(screen, GREEN, pygame.Rect(goal_pos[0] * GRID_SIZE, goal_pos[1] * GRID_SIZE, GRID_SIZE, GRID_SIZE))

# メッセージボックスを表示
def show_message():
    root = tk.Tk()
    root.withdraw()  # メインウィンドウを隠す
    messagebox.showinfo("ゲーム終了", "ゴールに到達しました！")
    root.quit()

# メインループ
def main():
    maze = generate_maze()
    start_pos, goal_pos = set_start_and_goal()
    
    clock = pygame.time.Clock()

    while True:
        screen.fill(BLACK)
        draw_maze(maze)
        draw_goal(goal_pos)

        # プレイヤーの移動
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                pygame.quit()
                return

        keys = pygame.key.get_pressed()

        # 1回のキー入力で1マスだけ移動
        if keys[pygame.K_LEFT] and start_pos[0] > 0 and maze[start_pos[1]][start_pos[0] - 1] == 0:
            start_pos[0] -= 1
        if keys[pygame.K_RIGHT] and start_pos[0] < COLS - 1 and maze[start_pos[1]][start_pos[0] + 1] == 0:
            start_pos[0] += 1
        if keys[pygame.K_UP] and start_pos[1] > 0 and maze[start_pos[1] - 1][start_pos[0]] == 0:
            start_pos[1] -= 1
        if keys[pygame.K_DOWN] and start_pos[1] < ROWS - 1 and maze[start_pos[1] + 1][start_pos[0]] == 0:
            start_pos[1] += 1
        
        # プレイヤーを描画
        pygame.draw.rect(screen, RED, pygame.Rect(start_pos[0] * GRID_SIZE, start_pos[1] * GRID_SIZE, GRID_SIZE, GRID_SIZE))
        
        # ゴールに到達した場合の判定
        if check_goal_reached(start_pos, goal_pos):
            show_message()  # ゴールに到達したらメッセージ表示
            pygame.quit()   # ゲーム終了
            return
        
        pygame.display.flip()
        clock.tick(10)

if __name__ == "__main__":
    main()







