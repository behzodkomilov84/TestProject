document.addEventListener("DOMContentLoaded", loadHistory);

function loadHistory() {
    fetch("/api/test-session/history")
        .then(r => r.json())
        .then(data => {
            const tbody = document.querySelector("#testsTable tbody");
            tbody.innerHTML = "";

            data.content.forEach(test => {
                const tr = document.createElement("tr");

                tr.innerHTML = `
                    <td>${formatDate(test.finishedAt)}</td>
                    <td>${test.totalQuestions}</td>
                    <td>${test.correctAnswers}</td>
                    <td>${test.percent}%</td>
                    <td>${test.durationSec} sec.</td>
                    <td>
                        <button onclick="loadDetails(${test.id})">
                            Batafsil...
                        </button>
                    </td>
                `;
                tbody.appendChild(tr);
            });
        });
}

function loadDetails(testId) {
    fetch(`/api/test-session/${testId}`)
        .then(r => r.json())
        .then(data => {
            const container = document.getElementById("detailsContent");
            container.innerHTML = "";

            data.forEach(q => {
                const div = document.createElement("div");
                div.className = "question-card " + (q.correct ? "correct" : "wrong");

                div.innerHTML = `
                    <b>${q.questionText}</b><br>
                    <i>Sizning javobingiz:</i> ${q.selectedAnswer}<br>
                    <i>To'g'ri javob:</i> ${q.correctAnswer}
                `;
                container.appendChild(div);
            });

            document.getElementById("details").classList.remove("hidden");
        });
}

function closeDetails() {
    document.getElementById("details").classList.add("hidden");
}

function formatDate(dateStr) {
    return new Date(dateStr).toLocaleString();
}
