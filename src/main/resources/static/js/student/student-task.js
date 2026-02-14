let currentQuestionIndex = 0;

//Barcha tasklarni shu yerga zagruzka qilinadi.
const taskStore = {
    list: [],
    byId: new Map()
};

//Bitta taskdagi ma'lumotlar shu yerga yuklanadi.
let currentTask = {
    meta: null,
    questions: [],
    started: false,
    startedAt: null
};

async function loadTasks() {

    setTitle("Mening vazifalarim");

    try {
        const list = await apiFetch(`/api/student/tasks`);

        // ✅ сохраняем состояние
        taskStore.list = list;
        taskStore.byId.clear();

        list.forEach(task  => {
            taskStore.byId.set(task.id, task );
        });

        console.log("TASK STORE:", taskStore);//TODO

        renderTasks(list);

    } catch (e) {
        showError(e.message);
    }
}

function renderTasks(list){
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

async function loadCurrentTask(taskId) {

    currentTask.questions = [];
    currentTask.started = false;
    currentTask.startedAt = null;

    console.log("taskId:", taskId);

    try {

        // 1️⃣ берём задачу из store
        const task = taskStore.byId.get(taskId);

        if (!task) {
            alert("Задача не найдена");
            return;
        }

        // 2️⃣ сохраняем meta
        currentTask.meta = task;

        // 3️⃣ пробуем восстановить прогресс
        const saved = localStorage.getItem("task_" + taskId);

        if (saved) {

            const parsed = JSON.parse(saved);

            currentTask.questions = parsed.questions || [];
            currentTask.started = !!parsed.started;
            currentTask.startedAt = parsed.startedAt || null;

            console.log("Restored from localStorage");

        } else {

            // 4️⃣ загружаем с сервера
            const response = await apiFetch(
                `/api/student/question-set/${task.questionSetId}`
            );

            if (!response || !response.questions)
                throw new Error("Invalid backend response");

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
        }

        // 5️⃣ открываем UI
        openTaskModal();

        // 6️⃣ рендер
        renderTaskQuestions();

        updateTaskHeader();

    } catch (err) {

        console.error("loadCurrentTask error:", err);
        alert("Ошибка загрузки задачи");
    }
}

function openTaskModal() {

    const modal =
        new bootstrap.Modal(
            document.getElementById("taskModal")
        );

    modal.show();
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

    const q = currentTask.questions[currentQuestionIndex];
    if (!q) return;

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

        const selected = q.selectedAnswerId === a.id;

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

    html += `
        </div>
    </div>
    `;

    container.innerHTML = html;

}

function nextQuestion() {

    if (currentQuestionIndex <
        currentTask.questions.length - 1) {

        currentQuestionIndex++;
        renderTaskQuestions();
    }
}

function prevQuestion() {

    if (currentQuestionIndex > 0) {

        currentQuestionIndex--;
        renderTaskQuestions();
    }
}

function startTaskSession() {

    if (!currentTask.meta) return;

    currentTask.started = true;

    alert("Сессия началась!");

    // тут можешь:
    // → открыть testSession.html
    // → переключить UI
} //TODO

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

    localStorage.setItem(
        "task_" + currentTask.meta.id,
        JSON.stringify(currentTask)
    );
}

function updateTaskHeader() {

    const badge = document.getElementById("taskSetName");
    if (!badge || !currentTask.meta) return;

    badge.textContent = currentTask.meta.questionSetName;
}

function updateProgress() {

    const answered = currentTask.questions
        .filter(q => q.answered).length;

    const percent =
        (answered / currentTask.questions.length) * 100;

    document.getElementById("taskProgress")
        .style.width = percent + "%";
}