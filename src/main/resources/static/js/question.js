const params = new URLSearchParams(window.location.search);
const topicId = params.get("topicId");

let editingRow = null; // хранит текущую редактируемую строку

let modalState = {
    questionId: null,
    answerId: null,
    originalText: ""
};

if (!topicId) {
    document.querySelector("#questionsTable tbody").innerHTML =
        "<tr><td colspan='9'>❌ topicId yuborilmagan</td></tr>";
} else {
    loadQuestions(topicId);
}

async function loadQuestions(topicId) {
    try {
        const res = await fetch(`/api/question?topicId=${topicId}`);
        if (!res.ok) throw new Error("Ошибка загрузки тестов");

        const questions = await res.json();
        renderQuestionsTable(questions);

    } catch (e) {
        document.querySelector("#questionsTable tbody").innerHTML =
            `<tr><td colspan="9">❌ ${e.message}</td></tr>`;
    }
}

function renderQuestionsTable(questions) {
    const tbody = document.querySelector("#questionsTable tbody");
    tbody.innerHTML = "";

    const letters = ["A", "B", "C", "D"];

    questions.forEach((q, index) => {
        const answers = q.answers.slice(0, 4);
        const correctIndex = answers.findIndex(a => a.isTrue);
        const correctLetter = correctIndex !== -1 ? letters[correctIndex] : "-";
        const correctAnswer = answers.find(a => a.isTrue);

        const row = document.createElement("tr");
        row.dataset.questionId = q.id;  // <-- это ключевое
        row.innerHTML = `
            <td>${index + 1}</td>
            <td data-editable>${q.questionText}</td>
            ${answers.map(a => `

            <td data-editable data-answer-id="${a.id}" class="answer-cell ${a.isTrue ? "correct" : ""}">${a.answerText}</td>

            `).join("")}
            <td class="correct-letter"><b>${correctLetter}</b></td>
            
            <td class="comment-col hidden">
                    <input class="comment-input" type="text">
            </td>
            
            <td class="actions-cell">
                <div class="view-actions">
                    
                    <button class="action-btn comment" 
                        data-question-id="${q.id}"
                        data-answer-id="${correctAnswer?.id ?? ''}"
                        data-comment="${encodeURIComponent(correctAnswer?.commentary ?? '')}"
    
                        onclick="openCommentModal(this)" 
                        title="Izoh ko‘rsatish">
                    💬
                    </button>
                    
                    <button class="action-btn edit" 
                    onclick="enableInlineEdit(this, ${q.id})"
                     title="Tahrirlash">✏️</button>
                     
                </div>
                <div class="edit-actions" style="display:none;">
                    <button class="action-btn save" 
                    onclick="saveInlineEdit(this, ${q.id})" 
                    title="Saqlash">💾</button>
                    
                    <button class="action-btn cancel" 
                    style="color: orangered; font-weight: bold;" 
                    onclick="cancelInlineEdit(this)" 
                    title="Bekor qilish">&#8634;</button>
                    
                    <button class="action-btn delete" 
                    onclick="deleteQuestion(${q.id})" 
                    title="O‘chirish">❌</button>
                    
                </div>
            </td>
        `;
        tbody.appendChild(row);
    });
}

function enableInlineEdit(btn) {
    const row = btn.closest("tr");

    document.querySelectorAll(".comment-col")
        .forEach(c => c.classList.remove("hidden"));

    // запрет на редактирование, если уже редактируется другая строка
    if (editingRow && editingRow !== row) {
        alert("Avval tahrirlanayotgan satrni yakuniga yetkazing!");
        return;
    }

    editingRow = row; // помечаем эту строку как редактируемую

    row.classList.add("editing");

    toggleButtons(row, true);

    // 🔹 ВОПРОС
    const questionCell = row.querySelector("td[data-editable]");
    const qText = questionCell.innerText;

    questionCell.innerHTML = `
    <input type="text"
           class="inline-input question-input"
           value="${qText}">
`;


    const answerCells = row.querySelectorAll(".answer-cell");

    answerCells.forEach((cell, index) => {
        const text = cell.innerText;
        const id = cell.dataset.answerId;
        const isCorrect = cell.classList.contains("correct");

        cell.innerHTML = `
            <label style="display:flex; gap:6px; align-items:center;">
                <input type="radio"
                       name="correct-${row.rowIndex}"
                       class="correct-radio"
                       ${isCorrect ? "checked" : ""}>
                <input type="text"
                       class="inline-input"
                       data-answer-id="${id}"
                       value="${text}">
            </label>
        `;
    });
//обработчик radio (КЛЮЧЕВОЕ)
    const radios = row.querySelectorAll(".correct-radio");
    const correctLetterCell = row.querySelector(".correct-letter b");
    const letters = ["A", "B", "C", "D"];

    radios.forEach((radio, index) => {
        radio.addEventListener("change", () => {
            correctLetterCell.innerText = letters[index];
        });
    });


    // комментарий
    // ===== комментарий (ТОЛЬКО из data-comment кнопки) =====
    const commentBtn = row.querySelector(".action-btn.comment");
    const commentText = commentBtn
        ? decodeURIComponent(commentBtn.dataset.comment || "")
        : "";

    const commentCol = row.querySelector(".comment-col");

    commentCol.innerHTML = `
    <input type="text"
           class="comment-input"
           placeholder="To'g'ri javob uchun izoh"
           value="${commentText}">
`;

}

function cancelInlineEdit(btn) {
    editingRow = null; // снимаем флаг редактирования

    document.querySelectorAll(".comment-col")
        .forEach(c => c.classList.add("hidden"));//скрываем коммент столбцу

    loadQuestions(topicId);
}

function saveInlineEdit(btn, questionId) {
    const row = btn.closest("tr");

    const questionText = row.querySelector('td[data-editable] input').value;

    const answerRows = row.querySelectorAll(".answer-cell");

    const answers = [];
    let correctIndex = -1;

    answerRows.forEach((cell, i) => {
        const input = cell.querySelector(".inline-input");
        const radio = cell.querySelector(".correct-radio");

        if (radio.checked) correctIndex = i;

        answers.push({
            id: Number(input.dataset.answerId),
            answerText: input.value,
            isTrue: radio.checked,
            commentary: ""
        });
    });

    // комментарий — ТОЛЬКО правильному
    const comment = row.querySelector(".comment-input")?.value ?? "";
    if (correctIndex !== -1) {
        answers[correctIndex].commentary = comment;
    }

    const payload = {
        id: questionId,
        questionText,
        answers
    };

    fetch("/api/question/update", {
        method: "PUT",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(payload)
    })
        .then(async r => {

            const data = await r.json();

            if (!r.ok) {
                showAlert(data.error || "Saqlashda xatolik yuz berdi.")
                return;
            }

            showAlert("✅ Muvaffaqiyatli saqlandi.", "success");

            editingRow = null; // снимаем флаг после сохранения

            document.querySelectorAll(".comment-col")
                .forEach(c => c.classList.add("hidden"));//скрываем коммент столбцу

            loadQuestions(topicId);
        })
        .catch(e => alert(e.message));
}

function toggleButtons(row, isEditing) {

    row.querySelector(".view-actions").style.display = isEditing ? "none" : "flex";
    row.querySelector(".edit-actions").style.display = isEditing ? "flex" : "none";

    row.querySelector(".edit").style.display = isEditing ? "none" : "inline-block";
    row.querySelector(".comment").style.display = isEditing ? "none" : "inline-block";

    row.querySelector(".save").style.display = isEditing ? "inline-block" : "none";
    row.querySelector(".cancel").style.display = isEditing ? "inline-block" : "none";
    row.querySelector(".delete").style.display = isEditing ? "inline-block" : "none";
}

function hideCommentColumn() {
    document.querySelectorAll(".comment-col")
        .forEach(c => c.classList.add("hidden"));
}

async function deleteQuestion(questionId) {

    if (!confirm("Rostdan ham savolni o‘chirmoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/question/${questionId}`, {method: "DELETE"});
        if (!res.ok) throw new Error("O‘chirishda xatolik");

        // Сброс флага редактирования, если удаляем текущую редактируемую строку
        if (editingRow && editingRow.dataset.questionId == questionId) {
            editingRow = null;
        }

        // 🔑 ВАЖНО: скрываем колонку комментариев (th + td)
        hideCommentColumn();

        await loadQuestions(topicId);
    } catch (e) {
        alert(e.message);
    }
}

function goBack() {
    history.back();
}

function createTest() {
    window.location.href = `/question/${topicId}/create-test-form`;
}

function showAlert(message, type = "error") {
    const box = document.getElementById("alertBox");
    box.textContent = message;
    box.className = `alert ${type}`;
    box.classList.remove("hidden");

    setTimeout(() => {
        box.classList.add("hidden");
    }, 4000);
}


//=============================================================================
//                      MODAL commentary
//=============================================================================
// Открытие модала
const modal = document.getElementById("commentModal");
const textarea = document.getElementById("modalComment");
const editBtn = document.getElementById("modalEdit");
const saveBtn = document.getElementById("modalSaveBtn");
const closeBtn = modal.querySelector("button[onclick*='closeCommentModal']");

let currentAnswerId = null;
let currentQuestionId = null;
let originalText = "";

function openCommentModal(btn) {
    const modal = document.getElementById("commentModal");
    const textarea = document.getElementById("modalComment");
    const saveBtn = document.getElementById("modalSaveBtn");

    if (!modal || !textarea || !saveBtn) {
        console.error("Modal, textarea или saveBtn не найдены!");
        return;
    }

    const answerId = btn.dataset.answerId;
    const questionId = btn.dataset.questionId;
    const commentary = decodeURIComponent(btn.dataset.comment || "");

    if (!answerId) {
        alert("❌ Правильный ответ не найден, комментарий отсутствует.");
        return;
    }

    currentAnswerId = answerId;
    currentQuestionId = questionId;
    originalText = commentary;

    textarea.value = commentary;
    textarea.readOnly = true;
    saveBtn.disabled = true;

    modal.classList.add("show");
}


document.addEventListener("DOMContentLoaded", () => {
    // Закрытие модала
    window.closeCommentModal = function() {
        modal.classList.remove("show");
        textarea.value = "";
        textarea.readOnly = true;
        saveBtn.disabled = true;
    };

    // Режим редактирования
    if (editBtn) {
        editBtn.onclick = () => {
            textarea.readOnly = false;
            textarea.focus();
        };
    }

    // Включение Save при изменении текста
    if (textarea) {
        textarea.addEventListener("input", () => {
            saveBtn.disabled = textarea.value === originalText;
        });
    }

    // Сохранение комментария
    if (saveBtn) {
        saveBtn.onclick = async () => {
            const newComment = textarea.value;

            const payload = {
                questionId: Number(currentQuestionId),
                trueAnswer: {
                    id: Number(currentAnswerId),
                    commentary: newComment,
                    isTrue: true
                }
            };

            try {
                const res = await fetch("/api/question/updateComment", {
                    method: "PATCH",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify(payload)
                });

                const data = await res.json();
                if (!res.ok) {
                    alert(data.error || "Ошибка сохранения");
                    return;
                }
                showAlert(data.message, "success");

                originalText = newComment;
                textarea.readOnly = true;
                saveBtn.disabled = true;
                closeCommentModal();

                // Обновляем таблицу
                const params = new URLSearchParams(window.location.search);
                const topicId = params.get("topicId");
                if (topicId) await loadQuestions(topicId);

            } catch (e) {
                alert("Ошибка сети");
            }
        };
    }

    // Кнопка закрытия
    if (closeBtn) {
        closeBtn.onclick = () => closeCommentModal();
    }

});