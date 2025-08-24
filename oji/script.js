document.addEventListener('DOMContentLoaded', () => {
    const titleScreen = document.getElementById('title-screen');
    const stageScreen = document.getElementById('stage-screen');
    const gameOverScreen = document.getElementById('game-over-screen');
    const gameClearScreen = document.getElementById('game-clear-screen');

    const startButton = document.getElementById('start-button');
    const submitButton = document.getElementById('submit-button');
    const restartButtonGameover = document.getElementById('restart-button-gameover');
    const restartButtonClear = document.getElementById('restart-button-clear');

    const operationCheckboxes = document.querySelectorAll('input[name="operation"]');
    const digitsSelect = document.getElementById('digits');
    const questionCountInput = document.getElementById('question-count-input');
    const unclePositionInput = document.getElementById('uncle-position-input');

    const uncleElement = document.getElementById('uncle');
    const questionElement = document.getElementById('question');
    const answerInput = document.getElementById('answer');
    const messageElement = document.getElementById('message');
    const questionCountElement = document.getElementById('question-count');
    const totalQuestionsElement = document.getElementById('total-questions');
    const itemStopButton = document.getElementById('item-stop');
    const itemBackButton = document.getElementById('item-back');

    let unclePosition;
    let initialUnclePosition;
    let consecutiveCorrectAnswers = 0;
    let questionNumber = 1;
    let totalQuestions;
    let stopItemCount = 0;
    let backItemCount = 0;
    let currentQuestion = {};
    let stopItemUsed = false;

    // --- Screen Transition Logic ---
    function showScreen(screenToShow) {
        [titleScreen, stageScreen, gameOverScreen, gameClearScreen].forEach(screen => {
            screen.classList.add('hidden');
        });
        screenToShow.classList.remove('hidden');
    }

    // ゲーム開始
    startButton.addEventListener('click', () => {
        showScreen(stageScreen);
        totalQuestions = parseInt(questionCountInput.value);
        totalQuestionsElement.textContent = totalQuestions;
        resetGame();
        nextQuestion();
    });

    // 回答提出
    submitButton.addEventListener('click', () => {
        checkAnswer();
    });

    // Enterキーで回答
    answerInput.addEventListener('keypress', (e) => {
        if (e.key === 'Enter') {
            checkAnswer();
        }
    });

    // 足止めアイテム使用
    itemStopButton.addEventListener('click', () => {
        if (stopItemCount > 0) {
            stopItemCount--;
            stopItemUsed = true;
            updateItemButtons();
            messageElement.textContent = '足止めアイテムを使用！おじさんは次のターン動きません。';
        }
    });

    // 後退アイテム使用
    itemBackButton.addEventListener('click', () => {
        if (backItemCount > 0) {
            backItemCount--;
            updateItemButtons();
            if (unclePosition < initialUnclePosition) {
                unclePosition++;
                updateUnclePosition();
            }
            messageElement.textContent = '後退アイテムを使用！おじさんが1歩下がった。';
        }
    });

    // ゲームオーバー後のリスタート
    restartButtonGameover.addEventListener('click', () => {
        showScreen(titleScreen);
    });

    // ゲームクリア後のリスタート
    restartButtonClear.addEventListener('click', () => {
        showScreen(titleScreen);
    });

    // ゲームのリセット
    function resetGame() {
        initialUnclePosition = parseInt(unclePositionInput.value);
        unclePosition = initialUnclePosition;
        consecutiveCorrectAnswers = 0;
        questionNumber = 1;
        stopItemCount = 0;
        backItemCount = 0;
        updateUnclePosition();
        updateItemButtons();
        questionCountElement.textContent = questionNumber;
        messageElement.textContent = '';
    }

    // 次の問題へ
    function nextQuestion() {
        if (questionNumber > totalQuestions) {
            gameClear();
            return;
        }
        currentQuestion = generateQuestion();
        questionElement.textContent = `${currentQuestion.num1} ${currentQuestion.op} ${currentQuestion.num2} = ?`;
        answerInput.value = '';
        answerInput.focus();
        questionCountElement.textContent = questionNumber;
    }

    // 問題を生成
    function generateQuestion() {
        const selectedOps = Array.from(operationCheckboxes).filter(cb => cb.checked).map(cb => cb.value);
        if (selectedOps.length === 0) {
            // 念のため、選択がない場合は足し算をデフォルトにする
            selectedOps.push('addition');
        }
        const digits = parseInt(digitsSelect.value);
        const maxAnswer = Math.pow(10, digits) - 1;
        const minAnswer = Math.pow(10, digits - 1);

        let num1, num2, op, answer, opSymbol;

        while (true) {
            op = selectedOps[Math.floor(Math.random() * selectedOps.length)];

            if (op === 'addition') {
                opSymbol = '+';
                answer = Math.floor(Math.random() * (maxAnswer - minAnswer + 1)) + minAnswer;
                num1 = Math.floor(Math.random() * (answer - 1)) + 1;
                num2 = answer - num1;
            } else if (op === 'subtraction') {
                opSymbol = '-';
                answer = Math.floor(Math.random() * (maxAnswer - minAnswer + 1)) + minAnswer;
                num2 = Math.floor(Math.random() * (maxAnswer - answer)) + 1;
                num1 = answer + num2;
            } else if (op === 'multiplication') {
                opSymbol = '×';
                answer = Math.floor(Math.random() * (maxAnswer - minAnswer + 1)) + minAnswer;
                const divisors = getDivisors(answer);
                num1 = divisors[Math.floor(Math.random() * divisors.length)];
                num2 = answer / num1;
            } else if (op === 'division') {
                opSymbol = '÷';
                num2 = Math.floor(Math.random() * (maxAnswer - 1)) + 2; // 1で割っても意味がないので2から
                const maxQuotient = Math.floor(maxAnswer / num2);
                answer = Math.floor(Math.random() * maxQuotient) + 1;
                num1 = answer * num2;
            }

            if (num1 > 0 && num2 > 0 && Number.isInteger(num1) && Number.isInteger(num2) && answer >= minAnswer && answer <= maxAnswer && Number.isInteger(answer)) {
                break;
            }
        }
        return { num1, num2, op: opSymbol, answer };
    }

    function getDivisors(n) {
        const divisors = [];
        for (let i = 1; i <= Math.sqrt(n); i++) {
            if (n % i === 0) {
                divisors.push(i);
                if (i * i !== n) {
                    divisors.push(n / i);
                }
            }
        }
        return divisors;
    }

    // 回答をチェック
    function checkAnswer() {
        const userAnswer = parseInt(answerInput.value);
        if (isNaN(userAnswer)) {
            messageElement.textContent = '数値を入力してください。';
            return;
        }

        if (userAnswer === currentQuestion.answer) {
            messageElement.textContent = '正解！';
            consecutiveCorrectAnswers++;
            if (consecutiveCorrectAnswers === 3) {
                stopItemCount++;
                updateItemButtons();
                messageElement.textContent = '3回連続正解！足止めアイテムをゲット！';
            } else if (consecutiveCorrectAnswers >= 5 && (consecutiveCorrectAnswers - 5) % 2 === 0) {
                backItemCount++;
                updateItemButtons();
                messageElement.textContent = '連続正解！後退アイテムをゲット！';
            }
        } else {
            messageElement.textContent = `不正解！正解は ${currentQuestion.answer}`;
            consecutiveCorrectAnswers = 0;
            if (stopItemUsed) {
                stopItemUsed = false;
                messageElement.textContent += ' (足止め使用中)';
            } else {
                unclePosition--;
                updateUnclePosition();
            }
            if (unclePosition <= 0) {
                gameOver();
                return;
            }
        }
        questionNumber++;
        nextQuestion();
    }

    // おじさんの位置を更新
    function updateUnclePosition() {
        const minScale = 1.0;
        const maxScale = 6.0; // より迫力が出るように調整

        if (initialUnclePosition > 1) {
            const progress = (initialUnclePosition - unclePosition) / (initialUnclePosition - 1);
            const scale = minScale + (maxScale - minScale) * progress;
            uncleElement.style.transform = `scale(${scale})`
        } else {
            uncleElement.style.transform = `scale(${minScale})`;
        }
    }

    // アイテムボタンを更新
    function updateItemButtons() {
        itemStopButton.textContent = `足止め (${stopItemCount})`;
        itemBackButton.textContent = `後退 (${backItemCount})`;
        itemStopButton.disabled = stopItemCount === 0;
        itemBackButton.disabled = backItemCount === 0;
    }

    // ゲームオーバー
    function gameOver() {
        showScreen(gameOverScreen);
    }

    // ゲームクリア
    function gameClear() {
        showScreen(gameClearScreen);
        createFireworks();
    }

    // 花火を生成
    function createFireworks() {
        const fireworksContainer = document.querySelector('.fireworks');
        if (!fireworksContainer) return;
        fireworksContainer.innerHTML = ''; // 前の花火をクリア
        for (let i = 0; i < 30; i++) {
            const firework = document.createElement('div');
            firework.className = 'firework';
            firework.style.left = `${Math.random() * 100}%`;
            firework.style.top = `${Math.random() * 100}%`;
            firework.style.backgroundColor = `hsl(${Math.random() * 360}, 100%, 50%)`;
            firework.style.animationDelay = `${Math.random() * 1.5}s`;
            fireworksContainer.appendChild(firework);
        }
    }
});