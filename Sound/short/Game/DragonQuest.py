ygame
import random
import time

# 初期化
pygame.init()

# 画面設定
WIDTH, HEIGHT = 800, 600
screen = pygame.display.set_mode((WIDTH, HEIGHT))
pygame.display.set_caption("ドラクエ風RPG")

# 色
WHITE = (255, 255, 255)
BLACK = (0, 0, 0)
GREEN = (0, 255, 0)
RED = (255, 0, 0)

# フォント
font = pygame.font.SysFont("meiryo", 30)

# プレイヤーのクラス
class Player(pygame.sprite.Sprite):
    def __init__(self):
        super().__init__()
        self.image = pygame.Surface((40, 40))  # プレイヤーの見た目を■で設定
        self.image.fill(WHITE)
        self.rect = self.image.get_rect()
        self.rect.center = (WIDTH // 2, HEIGHT // 2)
        self.hp = 30
        self.attack = 5
        self.defense = 2
        self.x = WIDTH // 2
        self.y = HEIGHT // 2
        self.level = 1
        self.experience = 0
        self.has_key = False  # 最初は鍵を持っていない

    def move(self, dx, dy):
        self.x += dx
        self.y += dy
        self.rect.center = (self.x, self.y)

# 王様との会話イベント
def talk_to_king(player):
    return "王様: '勇者よ、城の外には危険な魔物がいます。だが、この鍵を持って行きなさい。'" 

# ゲームのメインループ
def main():
    player = Player()
    all_sprites = pygame.sprite.Group()
    all_sprites.add(player)

    clock = pygame.time.Clock()
    running = True
    game_over = False
    in_castle = True  # 最初は城の中
    message = "王様に話しかけてください。"

    # メインゲームループ
    while running:
        screen.fill(GREEN if not in_castle else BLACK)  # 城内の背景は黒、外の背景は緑

        # 王様に話しかける処理
        if in_castle:
            message_text = font.render(message, True, WHITE)
            screen.blit(message_text, (WIDTH // 2 - message_text.get_width() // 2, HEIGHT // 2))

        # プレイヤーの移動
        keys = pygame.key.get_pressed()
        if keys[pygame.K_LEFT]:
            player.move(-5, 0)
        if keys[pygame.K_RIGHT]:
            player.move(5, 0)
        if keys[pygame.K_UP]:
            player.move(0, -5)
        if keys[pygame.K_DOWN]:
            player.move(0, 5)

        # 王様に話しかける処理（城内でプレイヤーが王様の位置に到達したら）
        if in_castle and player.rect.colliderect(pygame.Rect(WIDTH // 2 - 40, HEIGHT // 2 - 40, 40, 40)):
            message = talk_to_king(player)
            player.has_key = True  # 鍵をもらう
            in_castle = False  # 城を出る準備が整った

        # 鍵を取った後、城を出る
        if not in_castle and not player.rect.colliderect(pygame.Rect(WIDTH // 2 - 40, HEIGHT // 2 - 40, 40, 40)):
            message = "鍵を使って城を出ました。外の世界へ！"
            # ここで外の探索が始まる

        pygame.display.flip()

        # イベント処理
        for event in pygame.event.get():
            if event.type == pygame.QUIT:
                running = False

        clock.tick(60)

    pygame.quit()

if __name__ == "__main__":
    main()







