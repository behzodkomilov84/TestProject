// Javob variantlari uchun A/B/C/D/E belgilari — question.js'dagi admin
// jadvali bilan bir xil ko'rinish (savol yaratishda ham shu tartibda).
const ANSWER_LETTERS = ["A", "B", "C", "D", "E"];

//==============================================================
//            Состояние теста
//==============================================================
const testState = {
    mode: sessionStorage.getItem("testMode"),

    topicIds: JSON.parse(sessionStorage.getItem("topicIds") || "[]"),
    limit: Number(sessionStorage.getItem("limit") || 10),
    time: Number(sessionStorage.getItem("time") || 10),

    testSessionId: null,
    allQuestions: [],
    questions: [],
    currentIndex: 0,
    answers: new Map(),
    startedAt: null,
    finishedAt: null,
    finished: false
};

//==============================================================
//                DOMContentLoaded
//==============================================================
document.addEventListener("DOMContentLoaded", () => {

    if (testState.mode !== "practice") {
        startTimer(testState.time);
    } else {
        document.getElementById("timer").style.display = "none";
    }

    if (testState.topicIds.length === 0) {
        alert("Нет выбранных тем. Вернитесь на предыдущую страницу.");
        window.location.href = "/testConfigPage";
        return;
    }

    setupModeLabel();

    // Запрашиваем тест сразу после загрузки страницы
    fetch("/api/test-session/start", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
            topicIds: testState.topicIds,
            limit: testState.limit,
            mode: testState.mode
        })
    })
        .then(r => r.json())
        .then(data => {
            if (!data.questions || data.questions.length === 0) {
                document.getElementById("questions").innerHTML = "<p class='empty'>❌ Вопросы не пришли с сервера</p>";
                return;
            }
            testState.testSessionId = data.testSessionId;
            testState.allQuestions = data.questions;
            testState.questions = data.questions;
            testState.startedAt = Date.now();

            console.log("TestSession ID:", testState.testSessionId);

            startTest();
        })
        .catch(err => {
            console.error(err);
            alert(err);
            document.getElementById("questions").innerHTML = "<p class='empty'>❌ Ошибка загрузки теста</p>";
        });

});

function setupModeLabel() {

    const label = document.getElementById("modeLabel");

    const modeNames = {
        practice: "📝 PRACTICE MODE",
        exam: "⏱ EXAM MODE",
        hard: "🔥 HARD MODE"
    };

    const mode = testState.mode;

    label.innerText = modeNames[mode] || mode.toUpperCase();

    // ключевая строка — режим в body для CSS
    document.body.dataset.mode = mode;
}

//==============================================================
//                   Таймер
//==============================================================
let time;
let timerInterval = null;

function startTimer(min) {
    time = min * 60;

    // 🔴 защита от повторного запуска
    if (timerInterval !== null) {
        clearInterval(timerInterval);
    }

    timerInterval = setInterval(() => {
        const m = Math.floor(time / 60);
        const s = time % 60;

        document.getElementById("timer").innerText =
            `${m}:${s < 10 ? '0' : ''}${s}`;

        time--;

        if (time < 0) {
            stopTimer();  // ✅ правильно
            finishTest();
        }
    }, 1000);
}

function stopTimer() {
    if (timerInterval !== null) {
        clearInterval(timerInterval);
        timerInterval = null;
    }
}
//Отрисовка вопросов
function renderQuestions(questions) {
    const container = document.getElementById("questions");
    container.innerHTML = "";

    questions.forEach((q, index) => {

        const correctAnswer = q.answers.find(a => a.isTrue);

        const block = document.createElement("div");
        block.className = "question-block";
        if (index === 0) block.classList.add("active");
        block.dataset.questionId = q.id;

        block.innerHTML = `
            <h3>${index + 1}. ${q.questionText}</h3>
            ${q.imageUrl ? `<img class="question-image" src="${q.imageUrl}" alt="Savol rasmi">` : ""}
            <ul>
                ${q.answers.map((a, i) => `
                    <li>
                        <label>
                            <input type="radio" name="q-${q.id}" data-answer-id="${a.id}">
                            <b>${ANSWER_LETTERS[i] || ""}) </b>${a.answerText}
                            ${a.imageUrl ? `<br><img class="answer-image" src="${a.imageUrl}" alt="Javob rasmi">` : ""}
                        </label>
                    </li>
                `).join("")}
            </ul>
            <div class="actions-bottom">
                ${testState.mode === "practice" ? `
                <button class="action-btn comment"
                    data-comment="${encodeURIComponent(correctAnswer?.commentary || '')}"
                    data-comment-image="${encodeURIComponent(correctAnswer?.commentaryImageUrl || '')}"
                    data-comment-video="${encodeURIComponent(correctAnswer?.commentaryVideoUrl || '')}"
                    onclick="openCommentModal(this)">
                💬
                </button>` : ""}

                <button onclick="goToPreviousQuestion()">AVVALGI</button>
                <button onclick="goToNextQuestion()">KEYINGI</button>
                <button onclick="finishTest()">Test Natijasi</button>
            </div>
        `;
        container.appendChild(block);
    });
    showQuestion(0);
}

function getQuestions() {
    return document.querySelectorAll('.question-block');
}

function getActiveQuestion() {
    return document.querySelector('.question-block.active');
}

function getActiveIndex() {
    const questions = getQuestions();
    return [...questions].findIndex(q => q.classList.contains("active"));
}

function showQuestion(index) {
    const questions = getQuestions();
    if (!questions.length) return;

    document.querySelectorAll('.question-block').forEach(q => {
        q.classList.remove('active');
        q.style.display = 'none';
    });

    if (index < 0) index = questions.length - 1;
    if (index >= questions.length) index = 0;


    const active = document.querySelectorAll('.question-block')[index];
    active.classList.add('active');
    active.style.display = 'block';

    // 👇 гарантируем, что вопрос виден под progress-bar
    active.scrollIntoView({
        behavior: 'smooth',
        block: 'start'
    });

    focusFirstAnswer();
}

function goToNextQuestion() {
    showQuestion(getActiveIndex() + 1);
}

function goToPreviousQuestion() {
    showQuestion(getActiveIndex() - 1);
}
//Выбор ответа
document.addEventListener("change", (e) => {
    if (e.target.type !== "radio") return;

    const block = e.target.closest('.question-block');
    const questionId = Number(block.dataset.questionId);
    const answerId = Number(e.target.dataset.answerId);

    const wasAnsweredBefore = testState.answers.has(questionId);

    testState.answers.set(questionId, answerId);

    // ✅ обновляем прогресс ТОЛЬКО если вопрос был без ответа
    if (!wasAnsweredBefore) {
        updateProgress();
    }
});

function startTest() {
    testState.startedAt = Date.now();
    testState.currentIndex = 0;
    testState.answers.clear();

    // ✅ ПОКАЗЫВАЕМ progress + timer
    document.getElementById("progress").style.width = "0%";
    document.getElementById("progressWrapper").classList.remove("hidden");
    document.body.classList.remove("no-progress");

    const timerEl = document.getElementById("timer");

    if (testState.mode === "practice") {
        timerEl.style.display = "none";
    } else {
        timerEl.style.display = "flex";
        startTimer(testState.time);
    }


    renderQuestions(testState.questions);

    document.body.classList.add("test-started");
}

function finishTest() {

    testState.finished = true;

    stopTimer(); // 🔴 ВАЖНО

    // 👻 Скрыть таймер
    const timerEl = document.getElementById("timer");
    if (timerEl) {
        timerEl.style.display = "none";
    }

    const unanswered = testState.questions.filter(q => !testState.answers.has(q.id));
    if (unanswered.length > 0) {
        alert(`❗ Barcha savollarga javob bering, (${unanswered.length} ta qoldi)`);
        return;
    }

    // ✅ СКРЫВАЕМ progress + timer
    document.getElementById("progressWrapper")
        .classList.add("hidden");

    // ✅ корректируем отступы
    document.body.classList.add("no-progress");

    testState.finishedAt = Date.now();
    calculateResult();

    saveTestResult();
}

function saveTestResult() {

    const payload = {
        testSessionId: testState.testSessionId,
        startedAt: testState.startedAt,
        finishedAt: testState.finishedAt,
        // Ajratilgan (mavjud) savollar soni — javob berilganlar emas.
        // Vaqt tugab avtomatik yakunlanganda ba'zi savollarga ulgurilmagan
        // bo'lishi mumkin, natija ("X/Y") shu haqiqiy sonlarga nisbatan
        // hisoblanishi uchun.
        totalQuestions: testState.allQuestions.length,
        answers: Array.from(testState.answers.entries()).map(
            ([questionId, answerId]) => ({
                questionId,
                answerId
            })
        )
    };

    fetch("/api/test-session/finish", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(payload)
    })
        .then(r => {
            if (!r.ok) throw new Error("Ошибка сохранения теста");
        })
        .then(() => {
            console.log("✅ Test sessiyasi saqlandi");
        });
}

function calculateResult() {
    let correct = 0;
    testState.questions.forEach(q => {
        const selectedId = testState.answers.get(q.id);
        const correctAnswer = q.answers.find(a => a.isTrue);
        if (correctAnswer && correctAnswer.id === selectedId) correct++;
    });

    const result = {
        total: testState.questions.length,
        correct,
        percent: Math.round((correct / testState.questions.length) * 100),
        durationSec: Math.floor((testState.finishedAt - testState.startedAt) / 1000)
    };

    showResult(result);
}

function showResult(result) {
    document.getElementById("questions").innerHTML = `
        <div class="result-card">
            <h2>📊 Natija</h2>

            <p>Jami savollar: <b>${result.total}</b></p>
            <p>To‘g‘ri javoblar: <b>${result.correct}</b></p>
            <p>Xato javoblar: <b>${result.total - result.correct}</b></p>

            <p>Foiz: <b>${result.percent}%</b></p>
            <p>Vaqt: <b>${result.durationSec} soniya</b></p>

            <div class="result-actions">
                <button onclick="restartTest()">🔄 Qayta boshlash</button>
                <button onclick="goBack()">⬅ Qayta sozlash</button>
                
                ${testState.mode === "practice" 
                ? `<button onclick="showWrongAnswers()">❌ Xatolarni ko‘rish</button>`
                : ""}

            </div>
        </div>
    `;
}

function restartTest() {
    testState.questions = testState.allQuestions;
    startTest();
}

function goBack() {
    history.back();
}

function showWrongAnswers() {

    document.getElementById("progressWrapper")
        .classList.add("hidden");

    document.body.classList.add("no-progress");


    // ⛔ Остановить таймер
    stopTimer();

    // 👻 Скрыть таймер
    const timerEl = document.getElementById("timer");
    if (timerEl) {
        timerEl.style.display = "none";
    }

    const container = document.getElementById("questions");
    container.innerHTML = "";

    let hasErrors = false;

    testState.questions.forEach((q, index) => {

        const selectedAnswerId = Number(testState.answers.get(q.id));
        const correctAnswer = q.answers.find(a => a.isTrue);

        // если ответ верный — пропускаем
        if (!correctAnswer || Number(correctAnswer.id) === Number(selectedAnswerId)) {
            return;
        }

        hasErrors = true;

        const selectedAnswer = q.answers.find(a => a.id === selectedAnswerId);

        const block = document.createElement("div");
        block.className = "wrong-question-card";

        block.innerHTML = `
            <h3>❓ ${index + 1}. ${q.questionText}</h3>
            ${q.imageUrl ? `<img class="question-image" src="${q.imageUrl}" alt="Savol rasmi">` : ""}

            <ul class="answers-review">
                <li class="wrong-answer">
                    ❌ Siz tanlagan javob:
                    <div>${selectedAnswer?.answerText ?? "Javob tanlanmagan"}</div>
                </li>

                <li class="correct-answer">
                    ✅ To‘g‘ri javob:
                    <div>${correctAnswer.answerText}</div>
                </li>
            </ul>

            ${correctAnswer.commentary ? `<div class="commentary-box">💬 Izoh: ${correctAnswer.commentary}</div>` : ""}
            ${correctAnswer.commentaryImageUrl ? `<img class="comment-image" src="${correctAnswer.commentaryImageUrl}" alt="Izoh rasmi">` : ""}
            ${correctAnswer.commentaryVideoUrl ? `<video class="comment-video" src="${correctAnswer.commentaryVideoUrl}" controls></video>` : ""}
        `;

        container.appendChild(block);
    });

    if (!hasErrors) {
        container.innerHTML = `
            <div class="result-card">
                <h2>🎉 Tabriklaymiz!</h2>
                <p>Sizda xato javoblar yo‘q.</p>
                <div class="result-actions">
                <button onclick="restartTest()">🔄 Testni qayta boshlash</button>
                <button onclick="goBack()">⬅ Qayta sozlash</button>
                </div>
                
            </div>
        `;
        return;
    }
    /* === КНОПКИ ПОСЛЕ СПИСКА ОШИБОК === */
    const actions = document.createElement("div");
    actions.className = "result-actions";

    actions.innerHTML = `
    <button onclick="restartTest()">🔄 Testni qayta boshlash</button>
    <button onclick="repeatWrongOnly()">🧪 Faqat xatolar bilan test</button>
    <button onclick="goBack()">⬅ Qayta sozlash</button>
`;

    container.appendChild(actions);
}

function repeatWrongOnly() {
    const wrongQuestions = getWrongQuestions();
    if (wrongQuestions.length === 0) {
        alert("🎉 Xato savollar yo‘q");
        return;
    }

    testState.questions = wrongQuestions;
    testState.answers.clear();
    testState.currentIndex = 0;
    testState.startedAt = Date.now();
    testState.finishedAt = null;

    const container = document.getElementById("questions");
    container.innerHTML = "";
    renderQuestions(wrongQuestions);
    showQuestion(0);
    focusFirstAnswer();
}

function focusFirstAnswer() {

    // берём активный вопрос
    const activeQuestion = getActiveQuestion();

    if (!activeQuestion) return;

    // ищем первый radio
    const firstRadio = activeQuestion.querySelector(
        'input[type="radio"]'
    );

    if (!firstRadio) return;

    // небольшой defer — чтобы гарантировать готовность DOM
    requestAnimationFrame(() => {
        firstRadio.focus();
    });
}

function getWrongQuestions() {
    return testState.questions.filter(q => {
        const selectedAnswerId = testState.answers.get(q.id);
        const correctAnswer = q.answers.find(a => a.isTrue);
        return !correctAnswer || Number(correctAnswer.id) !== Number(selectedAnswerId);
    });
}

document.addEventListener("keydown", (e) => {

    const activeQuestion = getActiveQuestion();

    if (!activeQuestion) return;

    const tag = e.target.tagName;

    // ⛔ ПОЛНОСТЬЮ БЛОКИРУЕМ стандартную навигацию radio
    if (tag === "INPUT") {
        e.preventDefault();
    }

    switch (e.key) {
        case "ArrowRight":
            e.preventDefault();
            goToNextQuestion();
            break;

        case "ArrowLeft":
            e.preventDefault();
            goToPreviousQuestion();
            break;

        case "ArrowUp":
        case "ArrowDown":
            e.preventDefault();
            moveAnswerCursor(e.key === "ArrowDown" ? 1 : -1);
            break;

        case "Enter":
            e.preventDefault();
            selectAnswerAndNext();
            break;

        case " ":
        case "Spacebar": //для старых браузеров
            e.preventDefault();
            selectAnswerOnly();
            break;
    }
});

function moveAnswerCursor(direction) {
    const question = getActiveQuestion();
    const radios = [...question.querySelectorAll('input[type="radio"]')];

    if (!radios.length) return;

    const index = radios.findIndex(r => r === document.activeElement);
    let nextIndex = index + direction;

    if (nextIndex < 0) nextIndex = radios.length - 1;
    if (nextIndex >= radios.length) nextIndex = 0;

    radios[nextIndex].focus();
}

function selectAnswerAndNext() {
    const focused = document.activeElement;

    // фокус должен быть на radio
    if (!focused || focused.type !== "radio") return;

    focused.checked = true;
    focused.dispatchEvent(new Event("change", {bubbles: true}));

    // перейти к следующему вопросу
    setTimeout(() => {
        goToNextQuestion();
    }, 1000);
}

function selectAnswerOnly() {
    const focused = document.activeElement;

    if (!focused || focused.type !== "radio") return;

    focused.checked = true;

    // 🔑 ЯВНО вызываем change для прохождения теста
    focused.dispatchEvent(new Event("change", {bubbles: true}));
}

function updateProgress() {
    const answered = testState.answers.size;
    const total = testState.questions.length;

    const percent = Math.round((answered / total) * 100);

    const bar = document.getElementById("progress");
    if (bar) {
        bar.style.width = percent + "%";
    }
}

function openCommentModal(button) {
    const comment = decodeURIComponent(button.dataset.comment || "");
    const imageUrl = decodeURIComponent(button.dataset.commentImage || "");
    const videoUrl = decodeURIComponent(button.dataset.commentVideo || "");

    if (!comment.trim() && !imageUrl && !videoUrl) {
        alert("Izoh mavjud emas");
        return;
    }

    // Izohda matn, rasm va video birga bo'lishi mumkin.
    const body = document.getElementById("commentModalBody");
    body.innerHTML = `
        ${comment ? `<p>${comment}</p>` : ""}
        ${imageUrl ? `<img class="comment-image" src="${imageUrl}" alt="Izoh rasmi">` : ""}
        ${videoUrl ? `<video class="comment-video" src="${videoUrl}" controls></video>` : ""}
    `;

    document.getElementById("commentModal").classList.remove("hidden");
}

function closeCommentModal() {
    document.getElementById("commentModal").classList.add("hidden");
}

window.addEventListener("beforeunload", () => {

    if (!testState.finished && testState.testSessionId) {

        fetch("/api/test-session/cancel", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                testSessionId: testState.testSessionId
            }),
            keepalive: true
        });
    }
});





