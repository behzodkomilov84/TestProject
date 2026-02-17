let currentQuestionIndex = 0;

//Barcha tasklarni shu yerga zagruzka qilinadi.
const taskStore = {
    list: [],
    byId: new Map()
};

//Bitta taskdagi ma'lumotlar shu yerga yuklanadi.
let currentTask = {
    meta: null,
    attemptId: null,
    questions: [],
    started: false,
    startedAt: null,
    syncTimer: null
};

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

        console.log("TASK STORE:", taskStore);//TODO

        renderTasks(list);

    } catch (e) {
        showError(e.message);
    }
}

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
                        onclick="loadCurrentTask(${t.id})">
                        Topshiriqni ko'rish
                    </button>
                </td>
                <td></td>
            </tr>`;
    });

    html += "</tbody></table></div>";

    render(html);
}

function resetCurrentTask() {

    currentTask.meta = null;
    currentTask.attemptId = null;
    currentTask.questions = [];
    currentTask.started = false;
    currentTask.startedAt = null;

    clearInterval(currentTask.syncTimer);
    currentTask.syncTimer = null;

    currentQuestionIndex = 0;

    const container = document.getElementById("taskQuestionsBody");
    if (container) container.innerHTML = "";
}

async function loadCurrentTask(taskId) {

    resetCurrentTask();

    console.log("taskId:", taskId);//TODO

    try {

        // 1️⃣ берём задачу из store
        const task = taskStore.byId.get(taskId);

        if (!task) {
            alert("Topshiriq topilmadi.");
            return;
        }

        // 2️⃣ сохраняем meta
        currentTask.meta = task;

        console.log("currentTask.meta", currentTask.meta);

            // 4️⃣ загружаем с сервера
            const response = await apiFetch(
                `/api/student/question-set/${task.questionSetId}`, {method: "GET"}
            );

            if (!response || !response.questions)
                throw new Error("Backend javobi noto‘g‘ri");

            const questions = Array.isArray(response.questions)
                ? response.questions
                : [];

            currentTask.questions = questions.map(q => ({
                questionId: q.id,
                questionText: q.text,

                answers: q.answers.map(a => ({
                    id: a.id,
                    text: a.text
                })),

                selectedAnswerId: null,
                answered: false
            }));

            currentTask.started = false;
            currentTask.startedAt = null;

            console.log("Loaded from backend");


        // 5️⃣ открываем UI
        openTaskModal();
        updateTaskHeader();

        // 6️⃣ рендер
        renderTaskQuestions();
        updateProgress();

        console.log("currentTask.started: ", currentTask.started);
        console.log("currentTask.attemptId: ", currentTask.attemptId);

        if (currentTask.started && currentTask.attemptId) {

            console.log("AutoSync resumed");

            setTimeout(() => {
                startAutoSync();
            }, 200);
        }
    } catch (err) {

        console.error("loadCurrentTask error:", err);
        alert("Vazifani yuklashda xatolik yuz berdi");
    }

}

function renderTaskPlaceholder(container) {
    if (!container) return;

    container.innerHTML = `
        <div class="text-center p-5">
            <h4 class="mb-3">Test boshlashga tayyormisiz?</h4>
            <p class="text-muted">
                Boshlash tugmasini bosganingizdan so'ng test sessiyasi ishga tushadi.
            </p>
            <button class="btn btn-success mt-3" onclick="startTaskSession()">
                Boshlash
            </button>
        </div>
    `;
}

function openTaskModal() {

    const modal =
        new bootstrap.Modal(
            document.getElementById("taskModal")
        );

    modal.show();
    setTimeout(() => {
        updateProgress();
    }, 50);
}

function showQuestion(index) {

    const rows = document.querySelectorAll("#taskQuestionsBody tr");

    rows.forEach((r, i) => {
        r.style.display = i === index ? "table-row" : "none";
    });

    currentQuestionIndex = index;
}


function renderTaskQuestions() {

    const container =
        document.getElementById("taskQuestionsBody");

    if (!container) return;

    // placeholder
    if (!currentTask.started) {
        renderTaskPlaceholder(container);
        return;
    }

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

async function startTaskSession() {
    if (!currentTask.meta) return;

    const res = await apiFetch(`/api/student/attempt/start/${currentTask.meta.id}`, {method: "POST"});

    currentTask.attemptId = res.attemptId;
    currentTask.started = true;
    currentTask.startedAt = Date.now();
    currentQuestionIndex = 0;

    startAutoSync();
    saveTaskState();

    // 🔹 сразу показываем первый вопрос
    renderTaskQuestions();
}

function startAutoSync() {

    if (!currentTask.attemptId) return;

    clearInterval(currentTask.syncTimer);

    currentTask.syncTimer = setInterval(() => {

        void syncAttempt();

    }, 30000);

    console.log("AutoSync started", currentTask.syncTimer);
}


async function syncAttempt() {

    if (!currentTask.started || !currentTask.attemptId) {
        console.log("SYNC skipped");
        return;
    }

    console.log("SYNC sending...");

    const payload = {
        attemptId: currentTask.attemptId,
        answers: currentTask.questions
            .filter(q => q.selectedAnswerId !== null)
            .map(q => ({
            questionId: q.questionId,
            selectedAnswerId: q.selectedAnswerId
        }))
    };

    try {

        await apiFetch("/api/student/attempt/sync", {
            method: "POST",
            body: JSON.stringify(payload)
        });

        console.log("SYNC OK");

    } catch (e) {

        console.error("SYNC FAILED", e);
    }
}

async function finishTaskSession() {

    await syncAttempt();

    await apiFetch(
        `/api/student/attempt/finish/${currentTask.attemptId}`,
        {method: "POST"}
    );

    clearInterval(currentTask.syncTimer);

    localStorage.removeItem(
        "task_" + currentTask.meta.id
    );

    alert("Test yakunlandi");
}

function selectAnswer(questionId, answerId) {

    const q = currentTask.questions.find(
        x => x.questionId === questionId
    );

    if (!q) return;

    q.selectedAnswerId = answerId;
    q.answered = true;

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


function updateTaskHeader() {

    const badge = document.getElementById("taskSetName");
    if (!badge || !currentTask.meta) return;

    badge.textContent = currentTask.meta.questionSetName;
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