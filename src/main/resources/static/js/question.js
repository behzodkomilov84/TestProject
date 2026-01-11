const params = new URLSearchParams(window.location.search);
const topicId = params.get("topicId");

if (!topicId) {
    document.getElementById("questions").innerHTML =
        "<p class='empty'>❌ topicId yuborilmagan</p>";
} else {
    loadQuestions(topicId);
}
//Состояние теста
const testState = {
    topicId: null,

    allQuestions: [], //Все вопросы (НИКОГДА не меняем)
    questions: [], //Текущий набор (all / wrong only)

    currentIndex: 0,
    answers: new Map(), // questionId -> answerId
    startedAt: Date.now(),
    finishedAt: null
};

//===============================================================================
/*async function loadQuestions(topicId) {
    try {
        const res = await fetch(`/api/question?topicId=${topicId}`);

        if (!res.ok) {
            throw new Error("Ошибка загрузки тестов");
        }

        const questions = await res.json();

        // 🔑 ИНИЦИАЛИЗАЦИЯ ТЕСТА
        testState.topicId = Number(topicId);
        testState.allQuestions = questions; // Оригинал
        testState.questions = questions; // Текущие
        testState.answers.clear();
        testState.startedAt = Date.now();



        // ⚠️ ВАЖНО: НЕ рендерим здесь
        document.getElementById("questions").classList.add("hidden");
        document.getElementById("start-screen").classList.remove("hidden");

         renderQuestions(questions);

    } catch (e) {
        document.getElementById("questions").innerHTML =
            `<p class="empty">❌ ${e.message}</p>`;
    }
}*/

async function loadQuestions(topicId) {
    try {
        const res = await fetch(`/api/question?topicId=${topicId}`);
        if (!res.ok) throw new Error("Ошибка загрузки тестов");

        const questions = await res.json();

        // инициализация
        testState.topicId = Number(topicId);
        testState.allQuestions = questions;
        testState.questions = questions;
        testState.answers.clear();

        // UI
        document.getElementById("questions").classList.add("hidden");
        document.getElementById("start-screen").classList.remove("hidden");

        // ❌ ВАЖНО: тут НЕ должно быть renderQuestions
        // renderQuestions(questions); ← УДАЛИТЬ

    } catch (e) {
        document.getElementById("questions").innerHTML =
            `<p class="empty">❌ ${e.message}</p>`;
    }
}


function renderQuestions(questions) {
    const container = document.getElementById("questions");
    container.innerHTML = "";

    questions.forEach((q, index) => {
        const block = document.createElement("div");
        block.className = "question-block";
        block.dataset.questionId = q.id;

        if (index === 0) {
            block.classList.add("active");
        }

        block.innerHTML = `
            <div class="question-header">
                <div class="actions">
                    <button class="edit-btn" onclick="editQuestion(this)">TAHRIRLASH</button>

                    <button class="save-btn hidden" onclick="saveQuestion(this)">SAQLASH</button>
                    <button class="cancel-btn hidden" onclick="cancelEdit(this)">BEKOR QILISH</button>
                    <button class="delete-btn hidden" onclick="deleteQuestion(${q.id})" title="Delete tugmasi bilan ham o‘chiriladi">O'CHIRISH</button>
                </div>
                <h3>
                    <span class="question-text">${index + 1}. ${q.questionText}</span>
                </h3>

            </div>

          
            <ul>
    ${q.answers.map(a => {
            return `
                <li 
                    data-is-true="${a.isTrue}"
                    data-commentary="${a.commentary ?? ""}">
                    <label>
                        <input 
                            type="radio"
                            name="q-${q.id}">
                        <span class="answer-text" data-answer-id="${a.id}">
                            ${a.answerText}
                        </span>
                    </label>
                    
                    <div class="comment-block">
                    <button class="comment-btn hidden" onclick="addCommentary(this)">IZOH QO'SHISH</button>
                    <textarea class="commentary hidden"></textarea>
                    </div>
                </li>
            `;
        }).join("")}
</ul>

            
             <div class="actions-bottom">
                    <button class="previous-btn" onclick="goToPreviousQuestion()">AVVALGI</button>
                    <button class="next-btn" onclick="goToNextQuestion()">KEYINGI</button>
                    <button class="endTest-btn" onclick="finishTest()">Test Natijasi</button>
                </div>
        `;
        container.appendChild(block);
        focusFirstAnswer();
    });
}

function addCommentary(btn) {
    const li = btn.closest("li");
    const textarea = li.querySelector(".commentary");

    textarea.classList.toggle("hidden");
    textarea.focus();
}

function editQuestion(button) {
    const block = button.closest('.question-block');
    toggleButtons(block, true);
    block.classList.add("editing");

    // ===== вопрос =====
    const questionSpan = block.querySelector('.question-text');
    const text = questionSpan.textContent.replace(/^\d+\.\s*/, '');
    questionSpan.innerHTML =
        `<input type="text" class="edit-question-input" value="${text}">`;

    // ===== ответы =====
    block.querySelectorAll("li").forEach(li => {
        const span = li.querySelector('.answer-text');
        const radio = li.querySelector('input[type="radio"]');

        const answerId = span.dataset.answerId;
        const answerText = span.textContent;
        const isTrue = li.dataset.isTrue === "true";
        const commentary = li.dataset.commentary || "";

        // текст → input
        span.innerHTML = `
            <input type="text"
                   class="edit-answer-input"
                   data-answer-id="${answerId}"
                   value="${answerText.trim()}">
        `;

        // 🔑 ТОЛЬКО В EDIT MODE
        radio.checked = isTrue;

        if (isTrue) {
            li.querySelector('.comment-btn').classList.remove("hidden");

            if (commentary) {
                const textarea = li.querySelector('.commentary');
                textarea.classList.remove("hidden");
                textarea.value = commentary;
            }
        }
    });

    block.querySelector('.edit-question-input').focus();
}

async function saveQuestion(button) {
    const block = button.closest('.question-block');

    const payload = {
        id: Number(block.dataset.questionId),
        questionText: block.querySelector('.edit-question-input').value.trim(),
        answers: []
    };

    block.querySelectorAll('li').forEach(li => {
        const textInput = li.querySelector('.edit-answer-input');
        const radio = li.querySelector('input[type="radio"]');
        const commentaryElement = li.querySelector('.commentary');

        payload.answers.push({
            id: Number(textInput.dataset.answerId),
            answerText: textInput.value.trim(),
            isTrue: radio.checked,
            commentary: radio.checked ? commentaryElement?.value.trim() || null : null
        });
    });

    console.log("UPDATE PAYLOAD:", payload);

    const res = await fetch("/api/question/update", {
        method: "PUT",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(payload)
    });

    if (!res.ok) {
        alert("Ошибка сохранения");
        return;
    }

    location.reload();
}

function cancelEdit(buttonOrBlock) {
    const block = buttonOrBlock.closest
        ? buttonOrBlock.closest('.question-block')
        : buttonOrBlock;

    block.classList.remove("editing");
    location.reload(); // у тебя уже используется — допустимо
}

async function deleteQuestion(questionId) {
    if (!confirm("❗ Savolni o‘chirishni xohlaysizmi?")) return;

    const response = await fetch(`/api/question/${questionId}`, {
        method: "DELETE"
    });

    if (!response.ok) {
        alert("❌ O‘chirishda xatolik");
        return;
    }

    location.reload();
}

function toggleButtons(block, isEdit) {
    block.querySelector('.edit-btn').classList.toggle('hidden', isEdit);
    block.querySelector('.save-btn').classList.toggle('hidden', !isEdit);
    block.querySelector('.cancel-btn').classList.toggle('hidden', !isEdit);
    block.querySelector('.delete-btn').classList.toggle('hidden', !isEdit);
} //Переключение кнопок

function goBack() {
    history.back();
}

function createTest() {
    window.location.href = `/question/${topicId}/create-test-form`;
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

    questions.forEach(q => q.classList.remove("active"));

    // зацикливание
    if (index < 0) index = questions.length - 1;
    if (index >= questions.length) index = 0;

    questions[index].classList.add("active");
    questions[index].scrollIntoView({behavior: "smooth", block: "center"});
}

function goToNextQuestion() {
    const index = getActiveIndex();
    showQuestion(index + 1);
    focusFirstAnswer();
}

function goToPreviousQuestion() {
    const index = getActiveIndex();
    showQuestion(index - 1);
    focusFirstAnswer();
}

function focusFirstAnswer() {
    const question = getActiveQuestion();
    const firstRadio = question.querySelector('input[type="radio"]');
    if (firstRadio) firstRadio.focus();
}

document.addEventListener("keydown", (e) => {

    const editingBlock = document.querySelector(".question-block.editing");
    const activeQuestion = getActiveQuestion();

    if (!activeQuestion) return;

    const tag = e.target.tagName;
    /* ================= EDIT MODE ================= */

    if (editingBlock) {

        // ⛔ разрешаем ввод текста
        if (tag === "INPUT" || tag === "TEXTAREA") {
            if (["Escape", "Enter", "Delete"].includes(e.key)) {
                e.preventDefault();
            } else {
                return;
            }
        }

        switch (e.key) {
            case "Escape":
                cancelEdit(editingBlock);
                break;

            case "Enter":
                const saveBtn = editingBlock.querySelector('.save-btn');
                if (saveBtn) {
                    saveQuestion(saveBtn);
                }
                break;

            //DELETE -> Delete question
            case "Delete":
                const questionId = Number(editingBlock.dataset.questionId);
                deleteQuestion(questionId);
                break;


        }
        return;
    }

    /* ================= VIEW MODE ================= */

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

        case "Escape":
            goBack();
            break;

        case "+":
            createTest();
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

        case "F2":
            e.preventDefault();
            editActiveQuestionByKey();
            break;
    }
});

document.addEventListener("change", (e) => {
    if (e.target.type !== "radio") {
        return;
    }

    const block = e.target.closest('.question-block');

    // ❌ если не в режиме редактирования — выходим
    if (!block?.classList.contains("editing")) return;

    const li = e.target.closest("li");
    const list = li.parentElement.querySelectorAll("li");

    // скрываем всё
    list.forEach(item => {
        item.querySelector('.comment-btn')?.classList.add("hidden");
        item.querySelector('.commentary')?.classList.add("hidden");
    });

    // показываем только у выбранного
    li.querySelector('.comment-btn')?.classList.remove("hidden");
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

function editActiveQuestionByKey() {
    const block = getActiveQuestion();
    if (!block) return;

    // если уже в режиме редактирования — не делаем ничего
    if (block.classList.contains("editing")) return;

    const editBtn = block.querySelector(".edit-btn");
    if (editBtn) {
        editQuestion(editBtn);
    }
}

//==============================================================
//                     Модель прохождение теста
//==============================================================
//                      Start test
/*function startTest() {
    /!* document.querySelectorAll(".edit-btn").forEach(btn => {
         btn.disabled = true;
     });*!///Блокируем редактирование после старта (ОЧЕНЬ желательно)

    initTest();

    document.getElementById("start-screen").classList.add("hidden");
    document.getElementById("questions").classList.remove("hidden");

    testState.startedAt = Date.now();
    testState.currentIndex = 0;
    testState.answers.clear();

    showQuestion(0);
    focusFirstAnswer();
}*/
function startTest() {

    initTest();

    document.getElementById("start-screen").classList.add("hidden");
    document.getElementById("questions").classList.remove("hidden");

    // 🔑 ВОТ ТУТ нужно отрисовать вопросы
    renderQuestions(testState.questions);

    testState.startedAt = Date.now();
    testState.currentIndex = 0;
    testState.answers.clear();

    showQuestion(0);
    focusFirstAnswer();
}


//==============================================================
//              Логика прохождение теста
//                      Выбор ответа
document.addEventListener("change", (e) => {
    if (e.target.type !== "radio") return;

    const block = e.target.closest('.question-block');
    const questionId = Number(block.dataset.questionId);
    const answerId = Number(
        e.target.closest('li').querySelector('.answer-text').dataset.answerId
    );

    testState.answers.set(questionId, answerId);
});
//==============================================================
//           Завершение теста
//        Проверка перед завершением
//==============================================================
function finishTest() {
    const unanswered = testState.questions.filter(
        q => !testState.answers.has(q.id)
    );

    if (unanswered.length > 0) {
        alert(`❗ Barcha savollarga javob bering (${unanswered.length} ta qoldi)`);
        return;
    }

    testState.finishedAt = Date.now();
    calculateResult();
}

//==============================================================
//                   Расчёт результата
//          Локальный расчёт (быстро, без сервера)
//==============================================================
function calculateResult() {
    let correct = 0;

    testState.questions.forEach(q => {
        const selectedAnswerId = testState.answers.get(q.id);
        const correctAnswer = q.answers.find(a => a.isTrue);

        if (correctAnswer && correctAnswer.id === selectedAnswerId) {
            correct++;
        }
    });

    const result = {
        total: testState.questions.length,
        correct,
        percent: Math.round((correct / testState.questions.length) * 100),
        durationSec: Math.floor((testState.finishedAt - testState.startedAt) / 1000)
    };

    showResult(result);
}

//==============================================================
//                  Отображение результатов (UI)
//                          Экран результата
//==============================================================
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
                <button onclick="goBack()">⬅ Mavzuga qaytish</button>
                <button onclick="showWrongAnswers()">❌ Xatolarni ko‘rish</button>
            </div>
        </div>
    `;
}

//==============================================================
//              Показ правильных ответов и комментариев
//                      После завершения теста
//==============================================================
function showWrongAnswers() {

    const container = document.getElementById("questions");
    container.innerHTML = "";

    let hasErrors = false;

    testState.questions.forEach((q, index) => {

        const selectedAnswerId = testState.answers.get(q.id);
        const correctAnswer = q.answers.find(a => a.isTrue);

        // если ответ верный — пропускаем
        if (!correctAnswer || correctAnswer.id === selectedAnswerId) {
            return;
        }

        hasErrors = true;

        const selectedAnswer = q.answers.find(a => a.id === selectedAnswerId);

        const block = document.createElement("div");
        block.className = "wrong-question-card";

        block.innerHTML = `
            <h3>❓ ${index + 1}. ${q.questionText}</h3>

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

            ${correctAnswer.commentary ? `<div class="commentary-box">💬 Izoh: ${correctAnswer.commentary}</div>` : ""

        }
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
                <button onclick="goBack()">⬅ Mavzuga qaytish</button>
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
    <button onclick="goBack()">⬅ Mavzuga qaytish</button>
`;

    container.appendChild(actions);
}

function initTest() {

    // 1. Сброс состояния теста
    testState.answers.clear();
    testState.currentIndex = 0;
    testState.startedAt = Date.now();
    testState.finishedAt = null;

    // 2. Очистить все radio
    document.querySelectorAll('input[type="radio"]').forEach(radio => {
        radio.checked = false;
    });

    // 3. Убрать подсветку результатов (если была)
    document.querySelectorAll('li.correct, li.wrong').forEach(li => {
        li.classList.remove("correct", "wrong");
    });

}

function restartTest() {

    testState.questions = testState.allQuestions; //Возврат ко всем
    initTest();

/*    // 4. Показать вопросы обратно (если был экран результатов)
    const container = document.getElementById("questions");
    container.innerHTML = "";*/
    renderQuestions(testState.questions);

    // 5. Активировать первый вопрос
    setTimeout(() => {
        showQuestion(0);
        focusFirstAnswer();
    }, 0);
}

function showTests(){
const questions = testState.allQuestions;
    document.getElementById("start-screen").classList.add("hidden");
    document.getElementById("questions").classList.remove("hidden");

renderQuestions(questions);
}

function getWrongQuestions() {
    return testState.questions.filter(q => {
        const selectedAnswerId = testState.answers.get(q.id);
        const correctAnswer = q.answers.find(a => a.isTrue);
        return !correctAnswer || correctAnswer.id !== selectedAnswerId;
    });
}


function repeatWrongOnly() {

    const wrongQuestions = getWrongQuestions();

    if (wrongQuestions.length === 0) {
        alert("🎉 Xato savollar yo‘q");
        return;
    }

    // 🔑 Новый тест
    testState.questions = wrongQuestions;
    testState.answers.clear();            // ❗ ОБЯЗАТЕЛЬНО
    testState.currentIndex = 0;
    testState.startedAt = Date.now();
    testState.finishedAt = null;

    // очистить DOM
    const container = document.getElementById("questions");
    container.innerHTML = "";

    // перерисовать ТОЛЬКО ошибочные
    renderQuestions(wrongQuestions);

    // активировать первый
    showQuestion(0);
    focusFirstAnswer();
}




