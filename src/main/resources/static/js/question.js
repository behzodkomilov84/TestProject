const params = new URLSearchParams(window.location.search);
const topicId = params.get("topicId");

if (!topicId) {
    document.getElementById("questions").innerHTML =
        "<p class='empty'>❌ topicId yuborilmagan</p>";
} else {
    loadQuestions(topicId);
}

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
                    <button class="delete-btn hidden" onclick="deleteQuestion(${q.id})">O'CHIRISH</button>
                </div>
                <h3>
                    <span class="question-text">${index + 1}. ${q.questionText}</span>
                </h3>

            </div>

            <ul>
                ${q.answers.map(a => `
                    <li>
                        <label>
                            <input type="radio" name="q-${q.id}" ${a.isTrue ? "checked" : ""}>
                            <span class="answer-text" data-answer-id="${a.id}">${a.answerText}</span>
                        </label>
                    </li>
                `).join("")}
            </ul>
            
             <div class="actions-bottom">
                    <button class="previous-btn" onclick="goToPreviousQuestion()">AVVALGI</button>
                    <button class="next-btn" onclick="goToNextQuestion()">KEYINGI</button>
                </div>
        `;

        container.appendChild(block);
    });
}

function editQuestion(button) {
    const block = button.closest('.question-block');
    toggleButtons(block, true);

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
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(payload)
    });

    if (!res.ok) {
        alert("Ошибка сохранения");
        return;
    }

    location.reload();
}



function cancelEdit(button) {
    location.reload(); // возвращаем исходное состояние
}

async function deleteQuestion(questionId) {
    if (!confirm("Удалить вопрос?")) return;

    await fetch(`/api/question/${questionId}`, {
        method: "DELETE"
    });

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

function goToNextQuestion() {
    const questions = document.querySelectorAll('.question-block');
    const current = document.querySelector('.question-block.active');

    if (!current) return;

    let next = current.nextElementSibling;

    current.classList.remove('active');

    if (next && next.classList.contains('question-block')) {
        next.classList.add('active');
    } else {
        // если дошли до конца — возвращаемся к первому
        questions[0].classList.add('active');
    }
}

function goToPreviousQuestion() {
    const questions = document.querySelectorAll('.question-block');
    const current = document.querySelector('.question-block.active');

    if (!current) return;

    let prev = current.previousElementSibling;

    current.classList.remove('active');

    if (prev && prev.classList.contains('question-block')) {
        prev.classList.add('active');
    } else {
        // если первый — идём в конец
        questions[questions.length - 1].classList.add('active');
    }
}

