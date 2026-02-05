document.addEventListener("DOMContentLoaded", () => loadHistory(0));

const pageSize = 7;

function loadHistory(page) {
    fetch(`/api/test-session/history?page=${page}&size=${pageSize}`)
        .then(r => {
            if (!r.ok) {
                throw new Error("HTTP error: " + r.status);
            }

            const contentType = r.headers.get("content-type") || "";
            if (!contentType.includes("application/json")) {
                throw new Error("Response is not JSON");
            }

            return r.json();
        })
        .then(data => {
            console.log("History response:", data); // 🔍 DEBUG

            if (!data || !Array.isArray(data.content)) {
                throw new Error("Invalid history format");
            }

            const tbody = document.querySelector("#testsTable tbody");
            tbody.innerHTML = "";

            data.content.forEach(test => {
                const tr = document.createElement("tr");

                tr.innerHTML = `
                    <td>${formatDate(test.finishedAt)}</td>
                    <td>${test.scienceName}</td>
                    <td>${test.totalQuestions}</td>
                    <td>${test.correctAnswers}</td>
                    <td>${test.percent}%</td>
                    <td>${test.durationSec} sec.</td>
                    <td>
                        <button class="btn btn-sm btn-primary" onclick="loadDetails(${test.testSessionId})">
                            Batafsil...
                        </button>
                    </td>
                `;

                tbody.appendChild(tr);
            });

            renderPagination(data);
        })
        .catch(err => {
            console.error("❌ History load error:", err);
            alert("Test tarixi yuklanmadi. Qayta urinib ko‘ring.");
        });
}

function renderPagination(data) {
    const pagination = document.getElementById("pagination");

    if (!pagination) {
        console.warn("⚠️ Pagination container not found");
        return;
    }

    pagination.innerHTML = "";

    // Previous
    const prev = document.createElement("li");
    prev.className = "page-item " + (data.first ? "disabled" : "");
    prev.innerHTML = `<a class="page-link" href="#">Previous</a>`;
    prev.onclick = () => !data.first && loadHistory(data.number - 1);
    pagination.appendChild(prev);

    for (let i = 0; i < data.totalPages; i++) {
        const li = document.createElement("li");
        li.className = "page-item " + (i === data.number ? "active" : "");
        li.innerHTML = `<a class="page-link" href="#">${i + 1}</a>`;
        li.onclick = () => loadHistory(i);
        pagination.appendChild(li);
    }

    // Next
    const next = document.createElement("li");
    next.className = "page-item " + (data.last ? "disabled" : "");
    next.innerHTML = `<a class="page-link" href="#">Next</a>`;
    next.onclick = () => !data.last && loadHistory(data.number + 1);
    pagination.appendChild(next);
}

function loadDetails(testId) {
    fetch(`/api/test-session/${testId}`)
        .then(r => {
            if (!r.ok) throw new Error("Failed to load details");
            return r.json();
        })
        .then(data => {
            const container = document.getElementById("detailsContent");
            container.innerHTML = "";

            data.forEach((q, index) => {
                const div = document.createElement("div");
                div.className =
                    "question-card mb-3 p-3 border rounded " +
                    (q.correct ? "border-success" : "border-danger");

                div.innerHTML = `
                    <div><b>${index + 1}. ${q.questionText}</b></div>
                    <div><i>Sizning javobingiz:</i> ${q.selectedAnswer}</div>
                    <div><i>To'g'ri javob:</i> ${q.correctAnswer}</div>
                `;

                container.appendChild(div);
            });

            // 🔥 Открываем Bootstrap modal
            const modal = new bootstrap.Modal(
                document.getElementById("detailsModal")
            );
            modal.show();
        })
        .catch(err => {
            console.error("❌ Details load error:", err);
            alert("Test tafsilotlari yuklanmadi");
        });
}



function formatDate(dateStr) {
    return new Date(dateStr).toLocaleString();
}
