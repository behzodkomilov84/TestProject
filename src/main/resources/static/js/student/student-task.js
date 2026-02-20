
let currentQuestionIndex = 0;

//Barcha tasklarni shu yerga zagruzka qilinadi.
const taskStore = {
    list: [],
    byId: new Map()
};

//Bitta taskdagi ma'lumotlar shu yerga yuklanadi.
let currentTask = {
    meta: null,

    id: null,
    attemptId: null,
    questions: [],
    totalQuestions: 0,
    correctAnswers: 0,
    percent: 0,
    startedAt: null,
    finishedAt: null,

    started: false,
    syncTimer: null,
    finishing: false,

    timerInterval: null,
    durationSec: 0,

    timerBaseTimestamp:null

};

//Taskni bajarishga real qancha vaqt sarflaganini bilish uchun
let heartbeatTimer = null;

async function loadTasks() {

    setTitle("Mening vazifalarim");

    try {
        const list = await apiFetch(`/api/student/tasks`);

        // ✅ сохраняем состояние
        taskStore.list = list;
        taskStore.byId.clear();

        list.forEach(task => {
            taskStore.byId.set(task.id, task);
        });

        console.log("TASK STORE:", taskStore);

        renderTasks(list);

    } catch (e) {
        showError(e.message);
    }
}//DONE

function renderTasks(list) {
    let html = `
        <div class="table-box">
        <table class="table">
        <thead>
        <tr>
            <th>№</th>
            <th>Guruh nomi</th>
            <th>Topshiriq beruvchi nomi</th>
            <th>Topshiriq id'si </th>
            <th>Savollar paketi id'si </th>
            <th>Savollar paketi nomi </th>
            <th>Topshiriq berilgan vaqt </th>
            <th>Topshiriq muddati </th>
            <th>Amallar</th>
            <th>Status</th>
        </tr>
        </thead><tbody>`;

    list.forEach((t, index) => {

        html += `
            <tr>
                <td>${index + 1}</td>
                <td>${t.groupName}</td>
                <td>${t.assignerName}</td>
                <td>${t.id}</td>
                <td>${t.questionSetId}</td>
                <td>${t.questionSetName}</td>
                <td>${formatDateTime(t.assignedAt)}</td>
                <td>${formatDateTime(t.dueDate)}</td>
                <td>
                    <button class="btn btn-primary btn-sm"
                        onclick="showCurrentTask(${t.id})">
                        Topshiriqni ko'rish
                    </button>
                </td>
                <td></td>
            </tr>`;
    });

    html += "</tbody></table></div>";

    render(html);
}//DONE

async function showCurrentTask(taskId) {

    resetCurrentTask();

    currentTask.id = taskId;
    const task = taskStore.byId.get(taskId);
    if (!task) return;

    currentTask.meta = task;

    // 1️⃣ Открываем модаль СРАЗУ
    openTaskModal();
    updateTaskHeader();

    const container =
        document.getElementById("taskQuestionsBody");

    if (!container) return;

    // Пытаемся загрузить attempt
    try {

        await loadAttempt(taskId);

        console.log(currentTask);

        renderTaskPlaceholder(container, currentTask);

    } catch (e) {
        console.log("Attempt not found → new session");
    }
}

async function loadAttempt(taskId){
    const response = await apiFetch(
        `/api/student/attempt/getattempt/${taskId}`,
        {method: "GET"}
    );

    // Если attempt есть — сохраняем данные
    currentTask.attemptId = response.attemptId;
    currentTask.totalQuestions = response.totalQuestions;
    currentTask.correctAnswers = response.correctAnswers;
    currentTask.percent = response.percent;
    currentTask.durationSec = response.durationSec;
    currentTask.startedAt = response.startedAt;
    currentTask.finishedAt = response.finishedAt;
    currentTask.lastSync = response.lastSync;

    return response;
}

function renderTaskPlaceholder(container, currentTask) {
    if (!container) return;

    if (currentTask.attemptId === null) {
        container.innerHTML = `
        <div class="text-center p-5">
            <h4 class="mb-3">Test boshlashga tayyormisiz?</h4>
            <p class="text-muted">
                Boshlash tugmasini bosganingizdan so'ng test sessiyasi ishga tushadi.
            </p>
            <button class="btn btn-success mt-3" onclick="startTaskSession(currentTask.id)">
                Boshlash
            </button>
        </div>
    `;
    } else if (currentTask.finishedAt !== null) {
        container.innerHTML = `
        <div class="text-center p-5">
            <h4 class="mb-3">Bu topshiriq allaqachon topshirilgan.</h4>
            <p class="text-muted">
                "Natijani ko'rish" tugmasini bosganingizdan so'ng test natijalarini ko'rishingiz mumkin.
            </p>
            <button class="btn btn-success mt-3" onclick="showTaskResult(currentTask.id)">
                Natijani ko'rish
            </button>
            <button class="btn btn-success mt-3" onclick="reStartTaskSession(currentTask.id)">
                Qayta urinish
            </button>
        </div>
    `;
    } else if (currentTask.startedAt !== null) {
        container.innerHTML = `
        <div class="text-center p-5">
            <h4 class="mb-3">Bu test boshlangan, lekin yakunlanmagan.</h4>
            <p class="text-muted">
                "Davom etish" tugmasini bosganingizdan so'ng test sessiyasi boshlanadi.
            </p>
            <button class="btn btn-success mt-3" onclick="continueTaskSession(currentTask.id)">
                Davom etish
            </button>
            
        </div>
    `;
    }


}//TODO

function openTaskModal() {

    const finishBtn = document.getElementById("finishBtn");
    const syncBtn = document.getElementById("syncBtn");

    if (currentTask.viewMode) {
        finishBtn.style.display = "none";
        syncBtn.style.display = "none";
    } else {
        finishBtn.style.display = "";
        syncBtn.style.display = "";
    }


    startHeartbeat();

    const modal =
        new bootstrap.Modal(
            document.getElementById("taskModal")
        );

    modal.show();
    setTimeout(() => {
        updateProgress();
    }, 50);
}//TODO

function updateTaskHeader() {

    const badge = document.getElementById("taskSetName");
    if (!badge || !currentTask.meta) return;

    badge.textContent = currentTask.meta.questionSetName;
}

async function startTaskSession(taskId) {

    if (!currentTask.meta) return;

    const res = await apiFetch(
        `/api/student/attempt/start/${taskId}`,
        {method: "POST"});

    currentTask.attemptId = res.attemptId;
    currentTask.correctAnswers = res.correctAnswers;
    currentTask.durationSec = res.durationSec;
    currentTask.finishedAt = res.finishedAt;
    currentTask.lastSync = res.lastSync;
    currentTask.percent = res.percent;
    currentTask.startedAt = res.startedAt;
    currentTask.totalQuestions = res.totalQuestions;

    currentTask.started = true;

    startDisplayTimer();
    startHeartbeat(); //TODO
    startAutoSync();

    await loadCurrentTaskQuestions(taskId);

    console.log("currentTask: ", currentTask);
    startHeartbeat();

    startAutoSync();

    // 🔹 сразу показываем первый вопрос
    renderTaskQuestions();
}

async function loadCurrentTaskQuestions(taskId) {

    try {
        const task = taskStore.byId.get(taskId);
        if (!task) {
            alert("Topshiriq topilmadi");
            return;
        }

        const questionSet = await apiFetch(`/api/student/question-set/${task.questionSetId}`);

        currentTask.questions = questionSet.questions.map(q => {

            return {
                questionId: q.id,
                questionText: q.text,

                answers: q.answers.map(a => ({
                    id: a.id,
                    text: a.text,
                    isCorrect: a.isTrue
                }))
            };
        });

    } catch (err) {
        console.error(err);
        alert("Topshiriqni yuklashda xatolik yuz berdi.");
    }
}

function resetCurrentTask() {

    currentTask.meta = null;
    currentTask.attemptId = null;
    currentTask.questions = [];
    currentTask.startedAt = null;

    currentTask.totalQuestions = 0;
    currentTask.correctAnswers = 0;
    currentTask.percent = 0;
    currentTask.finishedAt = null;
    currentTask.started = false;
    currentTask.finishing = false;
    currentTask.timerInterval = null;
    currentTask.durationSec = 0;

    currentTask.viewMode = false;
    currentTask.attemptedQuestions = [];

    clearInterval(currentTask.syncTimer);
    currentTask.syncTimer = null;

    currentQuestionIndex = 0;

    const container = document.getElementById("taskQuestionsBody");
    if (container) container.innerHTML = "";
}

//-------------------------------------------------------------
//              ACTIVE TIMER
//-------------------------------------------------------------
document.addEventListener("visibilitychange", () => {

    if (document.hidden) {

        stopHeartbeat();

    } else {

        startHeartbeat();
    }
});

function startHeartbeat() {

    stopHeartbeat();

    if (!currentTask.started ||
        currentTask.finishedAt !== null ||
        !currentTask.attemptId ||
        currentTask.questions.length === 0) return;

    heartbeatTimer = setInterval(() => {

        apiFetch(
            `/api/student/attempt/heartbeat/${currentTask.attemptId}`,
            {method: "POST"}
        );

    }, 5000);
}

function stopHeartbeat() {

    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
}

//-------------------------------------------------------------

function renderTaskQuestions() {

    const container =
        document.getElementById("taskQuestionsBody");

    if (!container) return;

    if (!currentTask.questions.length) {

        container.innerHTML = `
            <div class="text-danger p-4 text-center">
                Savollar topilmadi
            </div>`;
        return;
    }

    const q =
        currentTask.questions[currentQuestionIndex];

    let html = `
        <div class="exam-card">
            <div class="exam-header">
                <div class="exam-counter">
                    Savol ${currentQuestionIndex + 1}
                    / ${currentTask.questions.length}
                </div>
            </div>

            <div class="exam-question">
                ${q.questionText}
            </div>

            <div class="exam-answers">
    `;

    q.answers.forEach(a => {

        const selected =
            q.selectedAnswerId === a.id;

        html += `
            <label class="exam-answer ${selected ? "selected" : ""}">
                <input type="radio"
                    name="q_${q.questionId}"
                    ${selected ? "checked" : ""}
                    onchange="selectAnswer(${q.questionId}, ${a.id})">

                ${a.text}
            </label>
        `;
    });

    html += `</div></div>`;

    container.innerHTML = html;

    updateProgress();
}

function nextQuestion() {

    if (!currentTask.started) return;

    if (currentQuestionIndex <
        currentTask.questions.length - 1) {

        currentQuestionIndex++;
        renderTaskQuestions();
    }
}

function prevQuestion() {

    if (!currentTask.started) return;

    if (currentQuestionIndex > 0) {

        currentQuestionIndex--;
        renderTaskQuestions();
    }
}

function startAutoSync() {

    if (!currentTask.started) return;

    clearInterval(currentTask.syncTimer);

    currentTask.syncTimer = setInterval(() => {

        void syncAttempt();

    }, 5000);

}

function stopAutoSync() {

    if (currentTask.syncTimer) {

        clearInterval(currentTask.syncTimer);
        currentTask.syncTimer = null;

        console.log("AUTOSYNC stopped");
    }
}

async function syncAttempt() {

    // 🔒 защита
    if (!currentTask.started || !currentTask.attemptId) {
        console.log("SYNC пропущен");
        return;
    }

    // ✅ только изменённые ответы
    const dirtyAnswers = currentTask.questions
        .filter(q => q.dirty && q.selectedAnswerId !== null);

    if (!dirtyAnswers.length) {
        console.log("SYNC — изменений нет");
        return;
    }

    const payload = {
        attemptId: currentTask.attemptId,
        answers: dirtyAnswers.map(q => ({
            questionId: q.questionId,
            selectedAnswerId: q.selectedAnswerId
        }))
    };

    try {

        console.log("SYNC отправка:", payload);

        await apiFetch("/api/student/attempt/sync", {
            method: "POST",
            body: JSON.stringify(payload)
        });

        // ✅ помечаем как синкнутые
        dirtyAnswers.forEach(q => q.dirty = false);

        console.log("SYNC успешно");

    } catch (e) {

        console.error("SYNC ошибка:", e);

        // ❗ dirty НЕ сбрасываем — повторим позже
    }
}

function closeTaskModal() {

    const modalEl =
        document.getElementById("taskModal");
    if (modalEl) {

        modalEl.addEventListener("hidden.bs.modal", () => {

            console.log("Modal closed → stopping timers");

            stopHeartbeat();
            stopAutoSync();
        });
    }
    const modal =
        bootstrap.Modal.getInstance(modalEl);

    modal?.hide();
}

function selectAnswer(questionId, answerId) {

    const q = currentTask.questions.find(
        x => x.questionId === questionId
    );

    if (!q) return;

    q.selectedAnswerId = answerId;
    q.answered = true;

    // 🔥 ключевая строка
    q.dirty = true;

    saveTaskState();
    updateProgress();
}

function saveTaskState() {

    if (!currentTask.meta) return;

    const safeState = {
        attemptId: currentTask.attemptId,
        questions: currentTask.questions,
        started: currentTask.started,
        startedAt: currentTask.startedAt
    };

    localStorage.setItem(
        "task_" + currentTask.meta.id,
        JSON.stringify(safeState)
    );
}

function updateProgress() {
    if (!currentTask.questions.length) return;

    const answered = currentTask.questions
        .filter(q => q.answered).length;

    const percent =
        (answered / currentTask.questions.length) * 100;

    document.getElementById("taskProgress")
        .style.width = percent + "%";
}

async function finishTaskSession() {

    stopDisplayTimer();
    stopHeartbeat();

    if (!currentTask.started || !currentTask.attemptId) {
        return;
    }

    if (currentTask.finishing) return;
    currentTask.finishing = true;

    try {

        // 👉 финальный sync перед закрытием
        await syncAttempt();

        // 👉 backend finish
        await apiFetch(
            `/api/student/attempt/${currentTask.attemptId}/finish`,
            { method: "POST" }
        );

        // === остановка autosync ===
        stopAutoSync();

        // === UI состояние ===
        currentTask.started = false;

        // можно показать результат
        alert("Test yakunlandi");

        // обновить прогресс / задачи
        updateProgress();

        // закрыть модалку (если нужно)
        closeTaskModal();

    } catch (err) {

        console.error("FINISH FAILED", err);
        alert("Testni yakunlab bo‘lmadi");

    } finally {

        currentTask.finishing = false;
    }
}

function formatDuration(sec) {

    const h = Math.floor(sec / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = sec % 60;

    if (h > 0)
        return `${h}:${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`;

    return `${m}:${s.toString().padStart(2, "0")}`;
}

function startDisplayTimer() {

    stopDisplayTimer();

    updateTimerUI();

    // если finished — просто показываем время
    if (currentTask.finishedAt !== null) return;

    currentTask.timerInterval = setInterval(() => {

        currentTask.durationSec++;
        updateTimerUI();

    }, 1000);
}

function stopDisplayTimer() {

    clearInterval(currentTask.timerInterval);
    currentTask.timerInterval = null;
}

function updateTimerUI() {

    const el =
        document.getElementById("attemptTimer");

    if (!el) return;

    el.textContent =
        "⏱ " + formatDuration(currentTask.durationSec);
}

async function showTaskResult(taskId) {
    try {
        const res = await apiFetch(
            `/api/student/attempt/get-full-attempt/${taskId}`,
            { method: "GET" }
        );

        // сохраняем общие данные attempt
        currentTask.attemptId = res.attemptId;
        currentTask.totalQuestions = res.totalQuestions;
        currentTask.correctAnswers = res.correctAnswers;
        currentTask.percent = res.percent;
        currentTask.durationSec = res.durationSec;
        currentTask.startedAt = res.startedAt;
        currentTask.finishedAt = res.finishedAt;
        currentTask.lastSync = res.lastSync;

        currentTask.viewMode = true; // 🔹 режим просмотра

        // --- сохраняем attemptedQuestions для быстрого доступа
        currentTask.attemptedQuestions = res.attemptedQuestions || [];

        // --- объединяем вопросы с ответами
        const answersMap = new Map();
        currentTask.attemptedQuestions.forEach(a => {
            answersMap.set(a.questionId, a.selectedAnswerId);
        });

        currentTask.questions = res.questions.map(q => ({
            questionId: q.id,
            questionText: q.text,
            selectedAnswerId: answersMap.get(q.id) || null,
            answers: q.answers.map(a => ({
                id: a.id,
                text: a.text,
                isCorrect: a.isTrue
            }))
        }));

        // открываем модальное окно
        stopHeartbeat();
        stopAutoSync();
        openTaskModal();

        // рендерим результаты
        renderTaskResult();

    } catch (e) {
        console.error(e);
        alert("Natijani yuklashda xatolik yuz berdi.");
    }
}

let wrongQuestionIndexes = []; // массив индексов неправильных вопросов
let currentResultIndex = 0;

function renderTaskResult() {
    const container = document.getElementById("taskQuestionsBody");
    if (!container) return;

    // --- собираем индексы неправильных вопросов
    wrongQuestionIndexes = [];
    currentTask.questions.forEach((q, idx) => {
        if (q.selectedAnswerId !== q.answers.find(a => a.isCorrect)?.id) {
            wrongQuestionIndexes.push(idx);
        }
    });

    // --- блок статистики и навигации по ошибкам
    let html = `
        <div class="alert alert-info mb-4">
            <strong>Natija:</strong><br>
            To'g'ri javoblar: ${currentTask.correctAnswers} / ${currentTask.totalQuestions}<br>
            Foiz: ${currentTask.percent}%<br>
            Sarflangan vaqt: ${formatDuration(currentTask.durationSec)}
        </div>

        <div id="resultNav" class="mb-3">
            ${wrongQuestionIndexes.length > 0
        ? `<strong>Xatolar:</strong> ${wrongQuestionIndexes.map(idx =>
            `<button class="btn btn-sm btn-outline-danger mx-1" onclick="showResultQuestion(${idx})">${idx + 1}</button>`
        ).join("")}`
        : `<span>Barcha javoblar to'g'ri ✅</span>`
    }
        </div>
    `;

    container.innerHTML = html;

    // показываем первый вопрос сразу
    showResultQuestion(0);
}

function showResultQuestion(index) {
    const container = document.getElementById("taskQuestionsBody");
    if (!container) return;

    const q = currentTask.questions[index];
    const selectedId = q.selectedAnswerId;
    const correctId = q.answers.find(a => a.isCorrect)?.id;

    let html = `
        <div class="exam-card mb-4 question-card">
            <div class="exam-header">
                <div class="exam-counter">Savol ${index + 1} / ${currentTask.questions.length}</div>
            </div>

            <div class="exam-question mb-2">${q.questionText}</div>
            <div class="exam-answers">
    `;

    q.answers.forEach(a => {
        let css = "exam-answer";
        if (a.id === correctId) css += " correct-answer";             // правильный
        if (a.id === selectedId && selectedId !== correctId) css += " wrong-answer"; // выбранный неверный
        html += `<div class="${css}">${a.text}</div>`;
    });

    html += `</div></div>`;

    // кнопки навигации
    html += `
        <div class="d-flex justify-content-between mt-3">
            <button class="btn btn-outline-secondary" ${index === 0 ? "disabled" : ""} onclick="showResultQuestion(${index - 1})">
                ◀ Oldingi
            </button>
            <button class="btn btn-outline-secondary" ${index === currentTask.questions.length - 1 ? "disabled" : ""} onclick="showResultQuestion(${index + 1})">
                Keyingi ▶
            </button>
        </div>
    `;

    // сохраняем текущий индекс
    currentResultIndex = index;

    // рендерим
    container.innerHTML = container.innerHTML.split('<div class="exam-card')[0] + html;
}

// переход к вопросу с ошибкой
function showWrongQuestion(idx) {
    showResultQuestion(idx);
}

