// アプリケーションの基本設定
const IMAGE_WIDTH = 800; // 画像の幅 (CSSの#morph-containerのwidthと合わせる)
const IMAGE_HEIGHT = 600; // 画像の高さ (CSSの#morph-containerのheightと合わせる)

// 画像ファイルのリスト
const imageUrls = ['1.png', '2.png', '3.png', '4.png'];
let currentImageIndex = 0;

// アニメーション中かどうかを管理するフラグ
let isAnimating = false;

// HTML要素の取得
const container = document.getElementById('morph-container');
const nextButton = document.getElementById('next-button');

// --- PixiJSの初期設定 ---
const app = new PIXI.Application();
// アプリケーションを初期化 (async/awaitを使うため、関数内で実行)
async function initPixi() {
    await app.init({
        width: IMAGE_WIDTH,
        height: IMAGE_HEIGHT,
        backgroundColor: 0x000000,
    });
    // 作成されたcanvasをHTMLのコンテナに追加
    container.appendChild(app.view);
    
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
    nextSprite.alpha = 0; // 透明にしておく
    app.stage.addChild(nextSprite);
    
    // --- ディスプレイスメント（歪み）フィルターの作成 ---
    // 歪みの元となるノイズ画像を作成（外部ファイル不要）
    const displacementTexture = await createDisplacementTexture();
    const displacementFilter = new PIXI.DisplacementFilter(
        new PIXI.Sprite(displacementTexture)
    );
    // 最初は歪みを0にしておく
    displacementFilter.scale.x = 0;
    displacementFilter.scale.y = 0;

    // フィルターをステージ（描画領域全体）に適用
    app.stage.filters = [displacementFilter];


    // --- ボタンクリック時の処理 ---
    nextButton.addEventListener('click', async () => {
        // アニメーション中はボタンを無効にする
        if (isAnimating) return;
        isAnimating = true;
        nextButton.disabled = true;
        nextButton.textContent = "Animating...";

        // 次の画像のインデックスを計算
        const nextImageIndex = (currentImageIndex + 1) % imageUrls.length;

        // 次のスプライトに新しいテクスチャを設定し、表示状態にする
        nextSprite.texture = textures[nextImageIndex];
        nextSprite.alpha = 1;

        // GSAPを使ったアニメーション
        const tl = gsap.timeline({
            onComplete: () => {
                // アニメーション完了後の処理
                currentSprite.texture = textures[nextImageIndex]; // 現在のスプライトを更新
                nextSprite.alpha = 0; // 次のスプライトを非表示に戻す
                currentImageIndex = nextImageIndex; // インデックスを更新
                
                isAnimating = false;
                nextButton.disabled = false;
                nextButton.textContent = "次の画像へ (Next)";
            }
        });

        // タイムラインにアニメーションを追加
        tl.to(displacementFilter.scale, {
            duration: 1, // 1秒かけて
            x: 200,      // 横方向の歪みを200まで増加
            y: 200,      // 縦方向の歪みを200まで増加
            ease: "power2.in"
        })
        .to(currentSprite, { // 歪みが最大のタイミングで
            duration: 0.01,
            alpha: 0 // 現在の画像を瞬時に透明にする
        }, "-=0.5") // 0.5秒早く開始
        .to(displacementFilter.scale, {
            duration: 1, // 1秒かけて
            x: 0,        // 歪みを0に戻す
            y: 0,
            ease: "power2.out"
        });
    });
}

// 歪み用のノイズテクスチャを動的に生成する関数
async function createDisplacementTexture() {
    const graphics = new PIXI.Graphics();
    // 512x512の領域にランダムなノイズを描画
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
    // グラフィックスからテクスチャを生成
    return app.renderer.generateTexture(graphics);
}

// 初期化関数を実行
initPixi();