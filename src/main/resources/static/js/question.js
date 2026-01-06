const params = new URLSearchParams(window.location.search);
const topicId = params.get("topicId");

if (!topicId) {
    document.getElementById("questions").innerHTML =
        "<p class='empty'>❌ topicId yuborilmagan</p>";
} else {
    loadQuestions(topicId);
}

//===============================================================================
async function loadQuestions(topicId) {
    try {
        const res = await fetch(`/api/question?topicId=${topicId}`);

        if (!res.ok) {
            throw new Error("Ошибка загрузки тестов");
        }

        const questions = await res.json();

        renderQuestions(questions);

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
                ${q.answers.map(a => `
                    <li>
                        <label>
                            <input type="radio" name="q-${q.id}" }> 
                            <span class="answer-text" data-answer-id="${a.id}">${a.answerText}</span>
                        </label>
                    </li>
                `).join("")}
            </ul>
            
             <div class="actions-bottom">
                    <button class="previous-btn" onclick="goToPreviousQuestion()">AVVALGI</button>
                    <button class="next-btn" onclick="goToNextQuestion()">KEYINGI</button>
                </div>
        `;//${a.isTrue ? "checked" : "" -> buni <input type="radio" name="q-${q.id}" }> ni ichidan oldim.

        container.appendChild(block);
        focusFirstAnswer();
    });
}

function editQuestion(button) {
    const block = button.closest('.question-block');
    toggleButtons(block, true);

    block.classList.add("editing"); // 🔑 маркер режима

    // вопрос
    const questionSpan = block.querySelector('.question-text');
    const text = questionSpan.textContent.replace(/^\d+\.\s*/, '');

    questionSpan.innerHTML =
        `<input type="text" class="edit-question-input" value="${text}">`;

    // ответы
    block.querySelectorAll('.answer-text').forEach(span => {
        const value = span.textContent;
        const answerId = span.dataset.answerId;

        span.innerHTML = `
            <input 
                type="text"
                class="edit-answer-input"
                data-answer-id="${answerId}"
                value="${value}">
        `;
    });

    // сразу фокус на вопрос
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

        payload.answers.push({
            id: Number(textInput.dataset.answerId),
            answerText: textInput.value.trim(),
            isTrue: radio.checked
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

    // перейти к следующему вопросу
    setTimeout(() => {
        goToNextQuestion();
    }, 1000);
}

function selectAnswerOnly() {
    const focused = document.activeElement;

    if (!focused || focused.type !== "radio") return;

    focused.checked = true;
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




