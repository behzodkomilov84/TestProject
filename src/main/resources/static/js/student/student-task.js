const CURRENT_USER_ID = Number(document.body.dataset.userId);

let currentChatAssignment = null;
let chatModalInstance = null;

document.addEventListener("DOMContentLoaded", () => {

    const modalElement = document.getElementById("chatModal");
    chatModalInstance = new bootstrap.Modal(modalElement);

});

//--------------------------------------------------------
const chatModalEl = document.getElementById("chatModal");
const chatInput = document.getElementById("chatInput");

chatModalEl.addEventListener("shown.bs.modal", () => {
    chatInput.focus();

    //Чтобы курсор ставился в конец текста (если поле не пустое)
    chatInput.setSelectionRange(chatInput.value.length, chatInput.value.length);
});
//--------------------------------------------------------

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
    dueDate: null,

    started: false,
    syncTimer: null,
    finishing: false,
    saving: false,

    timerInterval: null,
    durationSec: 0,

};

//Taskni bajarishga real qancha vaqt sarflaganini bilish uchun
let heartbeatTimer = null;

async function loadTasks() {

    setTitle("Mening vazifalarim");

    try {
        const list = await apiFetch(`/api/student/attempt/tasks`);

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

        const status = resolveTaskStatus(t);

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
                    <div class="d-flex gap-1">
                        <button class="btn btn-primary btn-sm"
                            onclick="showCurrentTask(${t.id})">
                            OCHISH
                        </button>
                        <button class="btn btn-sm btn-secondary"
                            onclick="openChat(${t.id})">
                            Chat
                        </button>
                    </div>                </td>
                <td>
                ${renderStatusBadge(t.taskStatus)}
                </td>
            </tr>`;
    });

    html += "</tbody></table></div>";

    render(html);
}//DONE

async function openChat(id) {

    console.log("CURRENT_USER_ID: ", CURRENT_USER_ID);

    currentChatAssignment = id;

    const container = document.getElementById("chatContainer");
    if (!container) {
        console.error("chatContainer not found in DOM");
        return;
    }

    const res = await fetch(`/api/assignments/${id}/chat`);
    const data = await res.json();

    const wasAtBottom = isUserNearBottom(container);

    container.innerHTML = "";

    data.forEach(msg => {

        const isMine = Number(msg.senderId) === CURRENT_USER_ID;
        const senderName = isMine ? "Men" : msg.senderName;
        const roleLabel =
            msg.role === "ROLE_ADMIN" ? "O'qituvchi" : "O'quvchi";

        container.innerHTML += `
            <div class="chat-row ${isMine ? "mine" : "other"}">
                <div class="chat-bubble">
                    <div class="chat-header">
                        <span class="chat-name" style="margin-right: 25px; font-weight: 600;">${senderName}</span>
                        <span class="chat-role">${roleLabel}</span>
                    </div>
                        <hr>
                    <div class="chat-text">${msg.message}</div>
                    <div class="chat-time">
                        ${formatDate(msg.createdAt)}
                    </div>
                </div>
            </div>
        `;
    });

    if (wasAtBottom) {
        scrollToBottom(container);
    }

    container.scrollTop = container.scrollHeight;

    chatModalInstance.show();

    setTimeout(() => {
        document.getElementById("chatInput")?.focus();
    }, 200);
}

async function sendMessage() {

    const input = document.getElementById("chatInput");
    const text = input.value.trim();

    if (!text) return; // пустые сообщения не отправляем

    await fetch(`/api/assignments/${currentChatAssignment}/chat`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({text})
    });

    input.value = "";              // 🔥 очищаем поле
    input.style.height = "auto";   // 🔥 сброс высоты после отправки
    input.focus();                 // удобно — курсор обратно в поле

    await openChat(currentChatAssignment);

    const container = document.getElementById("chatContainer");
    scrollToBottom(container);
}

//-----------------------------------------------------------------------
let sending = false;

document.getElementById("chatInput")
    .addEventListener("keydown", async function (e) {

        if (e.key === "Enter" && e.shiftKey) return;

        if (e.key === "Enter") {
            e.preventDefault();

            if (sending) return;

            sending = true;
            try {
                await sendMessage();
            } finally {
                sending = false;
            }
        }
    });

//auto-grow как Telegram
chatInput.addEventListener("input", function () {
    this.style.height = "auto";
    this.style.height = this.scrollHeight + "px";
});

function formatDate(d) {
    if (!d) return "-";
    return new Date(d).toLocaleString();
}

function resolveTaskStatus(t) {
    const now = new Date();

    if (!t.attemptId) {
        return { label: "NEW", class: "secondary" };
    }

    if (t.finishedAt) {
        return { label: "FINISHED", class: "success" };
    }

    if (t.dueDate && new Date(t.dueDate) < now) {
        return { label: "OVERDUE", class: "danger" };
    }

    return { label: "IN PROGRESS", class: "warning" };
}

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

async function loadAttempt(taskId) {
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

    const taskProgressbar = document.getElementById("taskProgressbar");
    const taskTimer = document.getElementById("taskTimer");
    const finishBtn = document.getElementById("finishBtn");
    const syncBtn = document.getElementById("syncBtn");
    const prevBtn = document.getElementById("prevBtn");
    const nextBtn = document.getElementById("nextBtn");

    if (taskProgressbar) taskProgressbar.style.display = "none";
    if (taskTimer) taskTimer.style.display = "none";
    if (finishBtn) finishBtn.style.display = "none";
    if (syncBtn) syncBtn.style.display = "none";
    if (prevBtn) prevBtn.style.display = "none";
    if (nextBtn) nextBtn.style.display = "none";

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

    const summary = document.getElementById("resultSummary");
    if (summary) summary.style.display = "none";

    const progressBarWrapper = document.getElementById("taskProgress")?.parentElement;
    if (progressBarWrapper) progressBarWrapper.style.display = "";

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

    startServerTimerSync();
    startHeartbeat();
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
        stopDisplayTimer();
        stopHeartbeat();

    } else {
        startDisplayTimer();
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

    }, 60000);
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

    document.getElementById("taskProgressbar").style.display = "";
    document.getElementById("taskTimer").style.display = "";
    document.getElementById("finishBtn").style.display = "";
    document.getElementById("syncBtn").style.display = "";
    document.getElementById("prevBtn").style.display = "";
    document.getElementById("nextBtn").style.display = "";

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
    updateFinishButtonState();
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
            stopDisplayTimer();
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
    updateFinishButtonState();
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

    // 🔒 базовая защита
    if (!currentTask.started || !currentTask.attemptId) return;
    if (currentTask.finishing) return;

    currentTask.finishing = true;

    const btn = document.getElementById("finishBtn");
    if (btn) btn.disabled = true;   // 🔒 БЛОКИРУЕМ СРАЗУ

    try {

        // 1️⃣ Останавливаем UI таймер
        stopServerTimerSync();

        // 2️⃣ Останавливаем фоновые процессы СРАЗУ
        stopAutoSync();
        stopHeartbeat();

        // 3️⃣ Финальный sync (если есть грязные ответы)
        await syncAttempt();

        // 4️⃣ Finish на backend
        const finishRes = await apiFetch(
            `/api/student/attempt/${currentTask.attemptId}/finish`,
            {method: "POST"}
        );

        // 5️⃣ Берём финальные данные из backend (источник истины)
        currentTask.durationSec = finishRes.durationSec;
        currentTask.finishedAt = finishRes.finishedAt;
        currentTask.correctAnswers = finishRes.correctAnswers;
        currentTask.percent = finishRes.percent;

        // 6️⃣ Блокируем состояние
        currentTask.started = false;

        // 7️⃣ Обновляем UI таймера финальным значением
        updateTimerUI(currentTask.durationSec);


        // 8️⃣ Показываем результат (по желанию)
        await showTaskResult(currentTask.id);

    } catch (err) {

        console.error("FINISH FAILED:", err);

        alert("Testni yakunlab bo‘lmadi. Qayta urinib ko‘ring.");

        // ❗ если finish не удался — разрешаем повторную попытку
        currentTask.finishing = false;
        return;

    }

    currentTask.finishing = false;
}

function formatDuration(sec) {

    const h = Math.floor(sec / 3600);
    const m = Math.floor((sec % 3600) / 60);
    const s = sec % 60;

    if (h > 0)
        return `${h}:${m.toString().padStart(2, "0")}:${s.toString().padStart(2, "0")}`;

    return `${m}:${s.toString().padStart(2, "0")}`;
}

function updateTimerUI() {

    const el =
        document.getElementById("taskTimer");

    if (!el) return;

    el.textContent =
        "⏱ " + formatDuration(currentTask.durationSec);
}

async function getFullAttemptInfo(taskId){
    return await apiFetch(
        `/api/student/attempt/get-full-attempt/${taskId}`,
        {method: "GET"}
    );
}

async function showTaskResult(taskId) {
    try {
        const res = await apiFetch(
            `/api/student/attempt/get-full-attempt/${taskId}`,
            {method: "GET"}
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
        updateTimerUI();

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

    container.innerHTML = "";
    const modalFooter = document.getElementById("modal-footer");
    if (modalFooter) {modalFooter.innerHTML = "";}

    // Скрываем прогрессбар
    const progressBar = document.getElementById("taskProgress")?.parentElement;
    if (progressBar) progressBar.style.display = "none";

// Показ summary
    const summary = document.getElementById("resultSummary");
    if (summary) {
        summary.style.display = "block";
        summary.innerHTML = `
        <strong style="color: #C62828">Natija:</strong>
<!--        To'g'ri javoblar: ${currentTask.correctAnswers} / ${currentTask.totalQuestions} |-->
        <strong style="color: #1b5e20">Foiz:</strong> ${currentTask.percent}% |
        <strong style="color: #1b5e20">Sarflangan vaqt:</strong> ${formatDuration(currentTask.durationSec)}
    `;
    }

    // --- собираем индексы неправильных вопросов
    wrongQuestionIndexes = [];
    currentTask.questions.forEach((q, idx) => {
        if (q.selectedAnswerId !== q.answers.find(a => a.isCorrect)?.id) {
            wrongQuestionIndexes.push(idx);
        }
    });

    // --- блок статистики и навигации по ошибкам
    let html = `
       
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

async function continueTaskSession(taskId) {

    if (!taskId) return;

    try {

        // 1️⃣ получаем актуальный attempt с backend
        const res = await getFullAttemptInfo(taskId);

        currentTask.started = true;
        currentTask.viewMode = false;

        // 3️⃣ загружаем вопросы
        await loadCurrentTaskQuestions(taskId);

        // 4️⃣ загружаем уже выбранные ответы
        if (res.attemptedQuestions) {

            const answerMap = new Map();

            res.attemptedQuestions.forEach(a => {
                answerMap.set(a.questionId, a.selectedAnswerId);
            });

            currentTask.questions.forEach(q => {
                q.selectedAnswerId = answerMap.get(q.questionId) || null;
                q.answered = !!q.selectedAnswerId;
                q.dirty = false;
            });
        }

        // 5️⃣ запускаем таймер (корректный способ)
        startServerTimerSync();

        // 6️⃣ запускаем heartbeat
        startHeartbeat();

        // 7️⃣ запускаем autosync
        startAutoSync();

        // 8️⃣ показываем первый вопрос
        renderTaskQuestions();
        updateFinishButtonState();

    } catch (err) {

        console.error("CONTINUE FAILED:", err);
        alert("Testni davom ettirib bo‘lmadi");

    }
}

async function apiFetch(url, options = {}) {

    options.headers = {
        "Content-Type": "application/json",
        ...(options.headers || {})
    };

    const res = await fetch(url,{
        credentials: "include",   // 🔥 обязательно
        ...options
    });

    if (!res.ok) {
        const text = await res.text();
        throw new Error(text);
    }

    return res.json().catch(() => ({}));
}

function renderStatusBadge(status) {

    const map = {
        NEW: { label: "Yangi", class: "secondary" },
        IN_PROGRESS: { label: "Jarayonda", class: "warning" },
        FINISHED: { label: "Tugallangan", class: "success" },
        OVERDUE: { label: "Muddati o'tgan", class: "danger" }
    };

    const s = map[status] || map.NEW;

    return `<span class="badge bg-${s.class}">${s.label}</span>`;
}

function updateFinishButtonState() {

    const btn = document.getElementById("finishBtn");
    if (!btn) return;

    // если режим просмотра — всегда выключена
    if (currentTask.viewMode) {
        btn.disabled = true;
        return;
    }

    // если тест не запущен
    if (!currentTask.started) {
        btn.disabled = true;
        return;
    }

    // есть ли хотя бы один неотвеченный вопрос
    const hasUnanswered = currentTask.questions.some(q => !q.selectedAnswerId);

    btn.disabled = hasUnanswered;
}

async function manualSaveAttempt() {

    // 🔒 базовая защита
    if (!currentTask.started || !currentTask.attemptId) return;
    if (currentTask.saving) return;

    const btn = document.getElementById("syncBtn");
    if (!btn) return;

    // Проверяем есть ли изменения
    const dirtyAnswers = currentTask.questions
        .filter(q => q.dirty && q.selectedAnswerId !== null);

    if (!dirtyAnswers.length) {
        showSaveState("O'zgarish yo'q", "secondary");
        return;
    }

    try {

        currentTask.saving = true;

        btn.disabled = true;
        btn.innerHTML = "Saqlanmoqda...";

        await syncAttempt(); // используем твой существующий метод

        showSaveState("Saqlandi ✓", "success");

    } catch (e) {

        console.error("MANUAL SAVE ERROR:", e);
        showSaveState("Xatolik!", "danger");

    } finally {

        currentTask.saving = false;
        btn.disabled = false;
        btn.innerHTML = "Holatni saqlash";
    }
}

function showSaveState(text, type) {

    const btn = document.getElementById("syncBtn");
    if (!btn) return;

    const original = btn.innerHTML;

    btn.classList.remove(
        "btn-warning",
        "btn-success",
        "btn-danger",
        "btn-secondary"
    );

    btn.classList.add("btn-" + type);
    btn.innerHTML = text;

    setTimeout(() => {
        btn.classList.remove("btn-" + type);
        btn.classList.add("btn-warning");
        btn.innerHTML = "Holatni saqlash";
    }, 1500);
}

function startServerTimerSync() {

    stopServerTimerSync();

    currentTask.timerInterval = setInterval(async () => {

        if (!currentTask.attemptId) return;

        try {
            const res = await apiFetch(
                `/api/student/attempt/${currentTask.attemptId}/time`
            );

            currentTask.durationSec = res.durationSec;
            updateTimerUI();

        } catch (e) {
            console.error("Timer sync error", e);
        }

    }, 5000);
}

function stopServerTimerSync() {

    if (currentTask.timerInterval !== null) {
        clearInterval(currentTask.timerInterval);
        currentTask.timerInterval = null;
    }
}

document.getElementById("taskModal")
    .addEventListener("hidden.bs.modal", async () => {

        if (currentTask.started && currentTask.attemptId) {

            await apiFetch(
                `/api/student/attempt/heartbeat/${currentTask.attemptId}`,
                { method: "POST" }
            );

            stopServerTimerSync();
        }
    });

function toggleChatFullscreen() {
    const modalDialog = document.querySelector("#chatModal .modal-dialog");
    const btn = document.getElementById("chatFullscreenBtn");

    modalDialog.classList.toggle("modal-fullscreen");

    if (modalDialog.classList.contains("modal-fullscreen")) {
        btn.textContent = "🗗"; // уменьшить
    } else {
        btn.textContent = "🔲"; // во весь экран
    }
}

function isUserNearBottom(container) {
    const threshold = 80; // px
    return container.scrollHeight - container.scrollTop - container.clientHeight < threshold;
}

function scrollToBottom(container) {
    container.scrollTop = container.scrollHeight;
}