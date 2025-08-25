// --- 基本設定 ---
const IMAGE_WIDTH = 800; // 画像の幅 (CSSと合わせる)
const IMAGE_HEIGHT = 600; // 画像の高さ (CSSと合わせる)
const imageUrls = ['1.png', '2.png', '3.png', '4.png'];
let currentImageIndex = 0;
let isAnimating = false;

// --- HTML要素の取得 ---
const container = document.getElementById('morph-container');
const nextButton = document.getElementById('next-button');

// --- PixiJSアプリケーションの作成（エラー修正箇所） ---
// app.init()を使わず、設定を直接コンストラクタに渡す確実な方法に変更
const app = new PIXI.Application({
    width: IMAGE_WIDTH,
    height: IMAGE_HEIGHT,
    backgroundColor: 0x000000,
});

// 作成されたcanvasをHTMLのコンテナに追加
container.appendChild(app.view);

// --- メイン処理（非同期関数として定義） ---
async function main() {
    // --- 画像とフィルターの準備 ---
    // 4枚の画像をテクスチャとして読み込み
    const textures = await Promise.all(imageUrls.map(url => PIXI.Assets.load(url)));

    // 現在表示中の画像スプライト
    const currentSprite = new PIXI.Sprite(textures[currentImageIndex]);
    currentSprite.width = IMAGE_WIDTH;
    currentSprite.height = IMAGE_HEIGHT;
    app.stage.addChild(currentSprite);

    // 次に表示する画像スプライト（最初は非表示）
    const nextSprite = new PIXI.Sprite(textures[currentImageIndex]);
    nextSprite.width = IMAGE_WIDTH;
    nextSprite.height = IMAGE_HEIGHT;
    nextSprite.alpha = 0;
    app.stage.addChild(nextSprite);
    
    // --- ディスプレイスメント（歪み）フィルターの作成 ---
    const displacementTexture = await createDisplacementTexture();
    const displacementSprite = new PIXI.Sprite(displacementTexture);
    const displacementFilter = new PIXI.DisplacementFilter(displacementSprite);
    
    // フィルターをステージ（描画領域全体）に適用
    app.stage.filters = [displacementFilter];
    // 最初は歪みを0にしておく
    displacementFilter.scale.x = 0;
    displacementFilter.scale.y = 0;

    // --- ボタンクリック時の処理 ---
    nextButton.addEventListener('click', () => {
        if (isAnimating) return;
        isAnimating = true;
        nextButton.disabled = true;
        nextButton.textContent = "Animating...";

        const nextImageIndex = (currentImageIndex + 1) % imageUrls.length;
        nextSprite.texture = textures[nextImageIndex];
        nextSprite.alpha = 1;

        const tl = gsap.timeline({
            onComplete: () => {
                currentSprite.texture = textures[nextImageIndex];
                nextSprite.alpha = 0;
                currentImageIndex = nextImageIndex;
                isAnimating = false;
                nextButton.disabled = false;
                nextButton.textContent = "次の画像へ (Next)";
            }
        });

        tl.to(displacementFilter.scale, { duration: 1, x: 200, y: 200, ease: "power2.in" })
          .to(currentSprite, { duration: 0.01, alpha: 0 }, "-=0.5")
          .to(displacementFilter.scale, { duration: 1, x: 0, y: 0, ease: "power2.out" });
    });
}

// 歪み用のノイズテクスチャを動的に生成する関数
async function createDisplacementTexture() {
    const graphics = new PIXI.Graphics();
    for (let i = 0; i < 5000; i++) {
        graphics.fill({
            color: Math.random() * 0xFFFFFF,
            alpha: Math.random()
        }).circle(
            Math.random() * 512,
            Math.random() * 512,
            Math.random() * 15
        );
    }
    return app.renderer.generateTexture(graphics);
}

// メイン処理を実行
main();