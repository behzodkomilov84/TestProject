document.addEventListener("DOMContentLoaded", () => {
    fetch("/api/profile")
        .then(r => r.json())
        .then(data => {
            document.getElementById("username").innerText = data.username;
            document.getElementById("role").innerText = data.role;
        });

    fetch("/api/profile/stats")
        .then(r => r.json())
        .then(data => {
            document.getElementById("total-tests").innerText = data.totalTests;
            document.getElementById("avg-percent").innerText = data.avgPercent;
            document.getElementById("best-percent").innerText = data.bestPercent;
            document.getElementById("worst-percent").innerText = data.worstPercent;
            document.getElementById("total-duration").innerText = data.totalDurationSec;
        });

    fetch("/api/profile/history")
        .then(r => r.json())
        .then(data => {
            const tbody = document.getElementById("history-body");
            tbody.innerHTML = "";
            data.forEach(test => {
                const tr = document.createElement("tr");
                tr.innerHTML = `
                    <td>${test.testSessionId}</td>
                    <td>${new Date(test.startedAt).toLocaleString()}</td>
                    <td>${test.finishedAt ? new Date(test.finishedAt).toLocaleString() : "—"}</td>
                    <td>${test.percent}</td>
                    <td><button onclick="viewTest(${test.testSessionId})">Ko'rish</button></td>
                `;
                tbody.appendChild(tr);
            });
        });
});

function viewTest(id) {
    window.location.href = `/profile/test/${id}`; // или открытие модалки
}
