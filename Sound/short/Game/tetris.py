ygame
import random
import time
import tkinter as tk
from tkinter import messagebox

# ウィンドウサイズとグリッド設定
WINDOW_WIDTH, WINDOW_HEIGHT = 300, 600
GRID_SIZE = 30
COLUMNS, ROWS = WINDOW_WIDTH // GRID_SIZE, WINDOW_HEIGHT // GRID_SIZE
FPS = 60

# テトリスのブロック定義
SHAPES = [
    [[1, 1, 1, 1]],  # I型（横長）
    [[1], [1], [1], [1]],  # I型（縦長）
    [[1, 1], [1, 1]],  # O
    [[0, 1, 0], [1, 1, 1]],  # T
    [[1, 1, 0], [0, 1, 1]],  # S
    [[0, 1, 1], [1, 1, 0]],  # Z
    [[1, 0, 0], [1, 1, 1]],  # J
    [[0, 0, 1], [1, 1, 1]]  # L
]

COLORS = [(0, 255, 255), (0, 255, 255), (255, 255, 0), (128, 0, 128), (0, 255, 0),
          (255, 0, 0), (0, 0, 255), (255, 165, 0), (255, 20, 147), (75, 0, 130)]  # I型をカラフルに変更

def create_new_piece():
    index = random.randint(0, len(SHAPES) - 1)
    return {'shape': SHAPES[index], 'color': COLORS[index], 'x': COLUMNS // 2 - 1, 'y': 0, 'hold': False}

def check_collision(grid, piece, dx=0, dy=0, rotated_shape=None):
    shape = rotated_shape if rotated_shape else piece['shape']
    for y, row in enumerate(shape):
        for x, cell in enumerate(row):
            if cell:
                new_x, new_y = piece['x'] + x + dx, piece['y'] + y + dy
                if new_x < 0 or new_x >= COLUMNS or new_y >= ROWS or (new_y >= 0 and grid[new_y][new_x] != (0, 0, 0)):
                    return True
    return False

def merge_piece(grid, piece):
    for y, row in enumerate(piece['shape']):
        for x, cell in enumerate(row):
            if cell:
                grid[piece['y'] + y][piece['x'] + x] = piece['color']

def clear_lines(grid):
    new_grid = [row for row in grid if any(cell == (0, 0, 0) for cell in row)]
    full_rows = ROWS - len(new_grid)
    for _ in range(full_rows):
        new_grid.insert(0, [(0, 0, 0)] * COLUMNS)
    return new_grid, full_rows

def draw_grid(surface, grid):
    for y, row in enumerate(grid):
        for x, cell in enumerate(row):
            pygame.draw.rect(surface, cell, pygame.Rect(x * GRID_SIZE, y * GRID_SIZE, GRID_SIZE, GRID_SIZE))
            pygame.draw.rect(surface, (50, 50, 50), pygame.Rect(x * GRID_SIZE, y * GRID_SIZE, GRID_SIZE, GRID_SIZE), 1)

def draw_gradient(surface, rect, color1, color2, vertical=True):
    """グラデーションを描画する関数"""
    if vertical:
        for y in range(rect.height):
            r = int(color1[0] + (color2[0] - color1[0]) * y / rect.height)
            g = int(color1[1] + (color2[1] - color1[1]) * y / rect.height)
            b = int(color1[2] + (color2[2] - color1[2]) * y / rect.height)
            pygame.draw.line(surface, (r, g, b), (rect.left, rect.top + y), (rect.right, rect.top + y))
    else:
        for x in range(rect.width):
            r = int(color1[0] + (color2[0] - color1[0]) * x / rect.width)
            g = int(color1[1] + (color2[1] - color1[1]) * x / rect.width)
            b = int(color1[2] + (color2[2] - color1[2]) * x / rect.width)
            pygame.draw.line(surface, (r, g, b), (rect.left + x, rect.top), (rect.left + x, rect.bottom))

def draw_piece(surface, piece):
    for y, row in enumerate(piece['shape']):
        for x, cell in enumerate(row):
            if cell:
                # ブロックを描く
                rect = pygame.Rect((piece['x'] + x) * GRID_SIZE, (piece['y'] + y) * GRID_SIZE, GRID_SIZE, GRID_SIZE)
                
                is_i_shape = (piece['shape'] == [[1, 1, 1, 1]] or piece['shape'] == [[1], [1], [1], [1]])

                #I型かチェックしてグラデーションの色を変更
                if is_i_shape:
                    #I型用のグラデーション色
                    color1 = (100, 255, 255)
                    color2 = piece['color']
                    draw_gradient(surface, rect, color1, color2)
                else:
                    #その他用のグラデーション色
                    color1 = (255, 255, 255)
                    color2 = piece['color']
                    draw_gradient(surface, rect, color1, color2)
                
                # I型かどうかのチェック
                if is_i_shape:
                    if piece['shape'] == [[1, 1, 1, 1]]:
                        # 横長の光沢
                        highlight_rect = pygame.Rect((piece['x'] + x) * GRID_SIZE + 5, (piece['y'] + y) * GRID_SIZE + 5, GRID_SIZE - 10, GRID_SIZE //2)
                    else:
                        # 縦長の光沢
                        highlight_rect = pygame.Rect((piece['x'] + x) * GRID_SIZE + 5, (piece['y'] + y) * GRID_SIZE + 5, GRID_SIZE //2, GRID_SIZE - 10)

                    # ハイライトに透明度を適用
                    s = pygame.Surface((highlight_rect.width, highlight_rect.height), pygame.SRCALPHA)
                    s.fill((255, 255, 255, 100))
                    surface.blit(s, highlight_rect)
                else:
                    # それ以外のブロックの光沢
                    highlight_rect = pygame.Rect((piece['x'] + x) * GRID_SIZE + 5, (piece['y'] + y) * GRID_SIZE + 5, GRID_SIZE - 10, GRID_SIZE - 10)
                    pygame.draw.rect(surface, (255, 255, 255), highlight_rect, 0)

def hard_drop(grid, piece):
    while not check_collision(grid, piece, dy=1):
        piece['y'] += 1
    merge_piece(grid, piece)
    return create_new_piece()

def is_game_over(grid):
    return any(cell != (0, 0, 0) for cell in grid[0])

def show_game_over_message(lines_cleared, game_time):
    root = tk.Tk()
    root.withdraw()  # Tkinterウィンドウを非表示にする
    messagebox.showinfo("Game Over", f"ゲームオーバー！\n消した行数: {lines_cleared}\nゲーム時間: {game_time:.2f}秒")

def main():
    pygame.init()
    screen = pygame.display.set_mode((WINDOW_WIDTH, WINDOW_HEIGHT))
    clock = pygame.time.Clock()
    running = True
    grid = [[(0, 0, 0) for _ in range(COLUMNS)] for _ in range(ROWS)]
    piece = create_new_piece()
    hold_piece = None
    fall_speed = 30  # フレーム単位で落下速度管理
    fall_counter = 0
    start_time = time.time()  # ゲーム開始時刻
    lines_cleared = 0  # 消した行数

    while running:
        screen.fill((0, 0, 0))
        draw_grid(screen, grid)
        draw_piece(screen, piece)

        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                running = False
            elif event.type == pygame.KEYDOWN:
                if event.key == pygame.K_LEFT and not check_collision(grid, piece, dx=-1):
                    piece['x'] -= 1
                elif event.key == pygame.K_RIGHT and not check_collision(grid, piece, dx=1):
                    piece['x'] += 1
                elif event.key == pygame.K_DOWN and not check_collision(grid, piece, dy=1):
                    piece['y'] += 1
                elif event.key == pygame.K_UP:
                    rotated = list(zip(*reversed(piece['shape'])))
                    if not check_collision(grid, piece, rotated_shape=rotated):
                        piece['shape'] = rotated
                elif event.key == pygame.K_SPACE:
                    piece = hard_drop(grid, piece)
                    grid, cleared = clear_lines(grid)
                    lines_cleared += cleared
                elif event.key == pygame.K_c and not piece['hold']:
                    if hold_piece:
                        piece, hold_piece = hold_piece, piece
                        piece['x'], piece['y'] = COLUMNS // 2 - 1, 0
                    else:
                        hold_piece = piece
                        piece = create_new_piece()
                    piece['hold'] = True

        fall_counter += 1
        if fall_counter >= fall_speed:
            if not check_collision(grid, piece, dy=1):
                piece['y'] += 1
            else:
                merge_piece(grid, piece)
                grid, cleared = clear_lines(grid)
                lines_cleared += cleared
                if is_game_over(grid):
                    game_time = time.time() - start_time
                    show_game_over_message(lines_cleared, game_time)
                    running = False
                piece = create_new_piece()
            fall_counter = 0

        pygame.display.flip()
        clock.tick(FPS)

    pygame.quit()
    print("Game Over")

if __name__ == "__main__":
    main()







