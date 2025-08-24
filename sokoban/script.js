document.addEventListener('DOMContentLoaded', () => {
    const gameBoard = document.getElementById('game-board');
    const resetButton = document.getElementById('reset-button');

    const TILE = { FLOOR: 0, WALL: 1, PLAYER: 2, BOX: 3, GOAL: 4, BOX_ON_GOAL: 5, PLAYER_ON_GOAL: 6 };
    const TILE_CLASS = { [TILE.FLOOR]: 'floor', [TILE.WALL]: 'wall', [TILE.PLAYER]: 'player', [TILE.BOX]: 'box', [TILE.GOAL]: 'goal', [TILE.BOX_ON_GOAL]: 'box-on-goal', [TILE.PLAYER_ON_GOAL]: 'player' };

    let level, originalLevel, playerPos;

    // --- ステージ生成 --- //
    function generateRandomLevel(width, height, numBoxes) {
        let newLevel;
        let attempts = 0;
        while (attempts < 1000) { // 無限ループ防止
            newLevel = Array.from({ length: height }, () => Array(width).fill(TILE.WALL));
            let emptyCells = [];

            // 床を生成
            for (let y = 1; y < height - 1; y++) {
                for (let x = 1; x < width - 1; x++) {
                    if (Math.random() > 0.3) {
                        newLevel[y][x] = TILE.FLOOR;
                        emptyCells.push({ x, y });
                    }
                }
            }

            if (emptyCells.length < numBoxes * 2 + 1) continue; // 十分なスペースがない場合は再試行

            // ゴール、箱、プレイヤーを配置
            const placements = sample(emptyCells, numBoxes * 2 + 1);
            for (let i = 0; i < numBoxes; i++) newLevel[placements[i].y][placements[i].x] = TILE.GOAL;
            for (let i = 0; i < numBoxes; i++) newLevel[placements[numBoxes + i].y][placements[numBoxes + i].x] = TILE.BOX;
            const playerStart = placements[numBoxes * 2];
            newLevel[playerStart.y][playerStart.x] = TILE.PLAYER;

            // クリア可能か検証
            if (isSolvable(newLevel)) {
                return newLevel;
            }
            attempts++;
        }
        console.error("クリア可能なステージを生成できませんでした。設定を調整してください。");
        return null; // 生成失敗
    }

    function sample(arr, size) {
        const shuffled = arr.slice(0); let i = arr.length; let temp; let index;
        while (i--) {
            index = Math.floor((i + 1) * Math.random());
            temp = shuffled[index];
            shuffled[index] = shuffled[i];
            shuffled[i] = temp;
        }
        return shuffled.slice(0, size);
    }

    // --- クリア可能性の検証 (ソルバー) --- //
    function isSolvable(testLevel) {
        const height = testLevel.length;
        const width = testLevel[0].length;
        let startState = { board: testLevel.map(row => row.slice()), player: null, boxes: [] };
        let goals = [];

        for (let y = 0; y < height; y++) {
            for (let x = 0; x < width; x++) {
                const tile = testLevel[y][x];
                if (tile === TILE.PLAYER) startState.player = { x, y };
                if (tile === TILE.BOX) startState.boxes.push({ x, y });
                if (tile === TILE.GOAL || tile === TILE.BOX_ON_GOAL) goals.push({ x, y });
            }
        }

        // 盤面の状態をユニークなキーに変換
        const getStateKey = (state) => {
            let key = `${state.player.x},${state.player.y}`;
            state.boxes.sort((a, b) => a.x - b.x || a.y - b.y).forEach(b => key += `|${b.x},${b.y}`);
            return key;
        };

        const queue = [startState];
        const visited = new Set([getStateKey(startState)]);

        while (queue.length > 0) {
            const currentState = queue.shift();

            // クリア判定
            if (currentState.boxes.every(box => goals.some(g => g.x === box.x && g.y === box.y))) {
                return true;
            }

            // プレイヤーの移動
            const moves = [[0, 1], [0, -1], [1, 0], [-1, 0]];
            for (const [dx, dy] of moves) {
                const newPlayerPos = { x: currentState.player.x + dx, y: currentState.player.y + dy };
                const nextTile = currentState.board[newPlayerPos.y][newPlayerPos.x];

                if (nextTile === TILE.WALL) continue;

                const boxIndex = currentState.boxes.findIndex(b => b.x === newPlayerPos.x && b.y === newPlayerPos.y);
                // 箱を押す場合
                if (boxIndex > -1) {
                    const newBoxPos = { x: newPlayerPos.x + dx, y: newPlayerPos.y + dy };
                    const afterBoxTile = currentState.board[newBoxPos.y][newBoxPos.x];

                    if (afterBoxTile === TILE.WALL || currentState.boxes.some(b => b.x === newBoxPos.x && b.y === newBoxPos.y)) continue;
                    
                    const newBoxes = currentState.boxes.map((b, i) => i === boxIndex ? newBoxPos : b);
                    const newState = { board: currentState.board, player: newPlayerPos, boxes: newBoxes };
                    const key = getStateKey(newState);
                    if (!visited.has(key)) {
                        visited.add(key);
                        queue.push(newState);
                    }
                } else { // ただ移動するだけ
                    const newState = { board: currentState.board, player: newPlayerPos, boxes: currentState.boxes };
                    const key = getStateKey(newState);
                    if (!visited.has(key)) {
                        visited.add(key);
                        queue.push(newState);
                    }
                }
            }
        }
        return false; // クリア不可
    }

    // --- ゲームロジック --- //
    function drawBoard() {
        gameBoard.innerHTML = '';
        gameBoard.style.gridTemplateColumns = `repeat(${level[0].length}, 40px)`;
        level.forEach((row, y) => {
            row.forEach((tile, x) => {
                const cell = document.createElement('div');
                let currentTile = tile;
                // ゴール地点のタイルを描画
                if (originalLevel[y][x] === TILE.GOAL) {
                    if (tile === TILE.FLOOR) currentTile = TILE.GOAL;
                    if (tile === TILE.BOX) currentTile = TILE.BOX_ON_GOAL;
                    if (tile === TILE.PLAYER) currentTile = TILE.PLAYER_ON_GOAL;
                }
                cell.className = `cell ${TILE_CLASS[currentTile] || 'floor'}`;
                gameBoard.appendChild(cell);
            });
        });
    }

    function movePlayer(dx, dy) {
        const newPos = { x: playerPos.x + dx, y: playerPos.y + dy };
        const nextTile = level[newPos.y][newPos.x];

        if (nextTile === TILE.WALL) return;

        if (nextTile === TILE.BOX) {
            const afterBoxPos = { x: newPos.x + dx, y: newPos.y + dy };
            const afterBoxTile = level[afterBoxPos.y][afterBoxPos.x];
            if (afterBoxTile === TILE.WALL || afterBoxTile === TILE.BOX) return;
            level[afterBoxPos.y][afterBoxPos.x] = TILE.BOX;
        }

        level[playerPos.y][playerPos.x] = TILE.FLOOR;
        level[newPos.y][newPos.x] = TILE.PLAYER;
        playerPos = newPos;

        drawBoard();
        checkWin();
    }

    function checkWin() {
        const boxes = [];
        const goals = [];
        originalLevel.forEach((row, y) => {
            row.forEach((tile, x) => {
                if (tile === TILE.GOAL) goals.push({x, y});
            });
        });
        level.forEach((row, y) => {
            row.forEach((tile, x) => {
                if (tile === TILE.BOX) boxes.push({x, y});
            });
        });

        if (boxes.every(box => goals.some(goal => goal.x === box.x && goal.y === box.y))) {
            setTimeout(() => {
                const messages = ["完璧です！", "素晴らしい頭脳！", "見事なクリア！", "あなたは倉庫番マスターだ！"];
                alert(messages[Math.floor(Math.random() * messages.length)]);
                if (confirm("新しいステージに挑戦しますか？")) {
                    startNewGame();
                } 
            }, 100);
        }
    }

    function findInitialPlayerPos(board) {
        for (let y = 0; y < board.length; y++) {
            for (let x = 0; x < board[y].length; x++) {
                if (board[y][x] === TILE.PLAYER) return { x, y };
            }
        }
        return null;
    }

    function startNewGame() {
        gameBoard.innerHTML = '<p>クリア可能なステージを生成中...</p>';
        setTimeout(() => {
            const newLevel = generateRandomLevel(10, 8, 3);
            if (newLevel) {
                originalLevel = newLevel.map(row => row.slice()); // 元の配置（特にゴール）を記憶
                level = newLevel.map(row => row.slice());
                playerPos = findInitialPlayerPos(level);
                drawBoard();
            } else {
                gameBoard.innerHTML = '<p>ステージ生成に失敗しました。リセットしてください。</p>';
            }
        }, 50); // 描画更新のためのタイムアウト
    }

    // --- イベントリスナー --- //
    resetButton.addEventListener('click', startNewGame);
    document.addEventListener('keydown', (e) => {
        if (!level) return;
        switch (e.key) {
            case 'ArrowUp': movePlayer(0, -1); break;
            case 'ArrowDown': movePlayer(0, 1); break;
            case 'ArrowLeft': movePlayer(-1, 0); break;
            case 'ArrowRight': movePlayer(1, 0); break;
        }
    });

    // --- 初期化 --- //
    startNewGame();
});
