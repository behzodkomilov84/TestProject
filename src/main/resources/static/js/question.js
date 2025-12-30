
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
        block.className = "question-block" ;

        if (index === 0) {
            block.classList.add("active") //показываем первый
        }

        block.innerHTML = `
        <h3>${index + 1}. ${q.questionText}</h3>
        <ul>
            ${q.answers.map(a => `
                <li>
                    <label>
                        <input type="radio" name="q-${q.id}">
                        <span>${a.answerText}</span>
                    </label>
                </li>
            `).join("")}
        </ul>
    `;

        container.appendChild(block);
    });
    showQuestionsStepByStep(); // запускаем пошаговое отображение
}


function showQuestionsStepByStep() {
    const questions = document.querySelectorAll('.question-block');
    questions.forEach((question, index) => {
        const inputs = question.querySelectorAll('input[type="radio"]');
        inputs.forEach(input => {
            input.addEventListener('change', () => {
                goToNextQuestion(index);
            });
        });
    });

    function goToNextQuestion(index) {
        questions[index].classList.remove('active'); // скрыть текущий
        if (questions[index + 1]) {
            questions[index + 1].classList.add('active'); // показать следующий
        } else {
            showFinish();
        }
    }

    function showFinish() {
        alert('Test tugadi!');
        // можно отправить результат на backend
    }
}

function goBack() {
    history.back();
}

function createTest(){
    window.location.href = `/question/${topicId}/create-test-form`;
}