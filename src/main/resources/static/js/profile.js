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

    document.getElementById("edit").addEventListener("click", enableEditUsername);
    document.getElementById("save-username").addEventListener("click", saveUsername);
    document.getElementById("cancel-username").addEventListener("click", cancelUsernameEdit);
});

function enableEditUsername() {
    const current = document.getElementById("username").innerText;

    document.getElementById("username-input").value = current;

    document.getElementById("username-view").style.display = "none";
    document.getElementById("username-edit").style.display = "inline";
    document.getElementById("edit").style.display = "none";
}

function cancelUsernameEdit() {
    document.getElementById("username-edit").style.display = "none";
    document.getElementById("username-view").style.display = "inline";
    document.getElementById("edit").style.display = "inline";
}



function viewTest(id) {
    window.location.href = `/profile/test/${id}`; // или открытие модалки
}

function saveUsername() {
    const newUsername = document.getElementById("username-input").value.trim();

    if (newUsername.length < 3) {
        alert("Username juda qisqa");
        return;
    }

    fetch("/api/profile/username", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ newUsername })
    })
        .then(r => {
            if (!r.ok) throw new Error();
            return r;
        })
        .then(() => {
            document.getElementById("username").innerText = newUsername;

            document.getElementById("username-edit").style.display = "none";
            document.getElementById("username-view").style.display = "inline";
            document.getElementById("edit").style.display = "inline";

            alert("Username o'zgartirildi");
        })
        .catch(() => {
            alert("Bu username band yoki xatolik");
        });
}

/*ОТКРЫТЬ / ЗАКРЫТЬ МОДАЛКУ*/
document.getElementById("open-password-modal")
    .addEventListener("click", () => {
        document.getElementById("password-modal").classList.remove("hidden");
    });

document.getElementById("close-password-modal")
    .addEventListener("click", closePasswordModal);

function closePasswordModal() {
    document.getElementById("password-modal").classList.add("hidden");
    document.getElementById("currentPassword").value = "";
    document.getElementById("newPassword").value = "";
}

/*СОХРАНЕНИЕ ПАРОЛЯ (API)*/
document.getElementById("save-password")
    .addEventListener("click", changePassword);

function changePassword() {
    const currentPassword = document.getElementById("currentPassword").value;
    const newPassword = document.getElementById("newPassword").value;

    if (!currentPassword || !newPassword) {
        alert("Barcha maydonlarni to‘ldiring");
        return;
    }

    if (newPassword.length < 6) {
        alert("Parol kamida 6 belgidan iborat bo‘lishi kerak");
        return;
    }

    fetch("/api/profile/password", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            currentPassword,
            newPassword
        })
    })
        .then(r => {
            if (!r.ok) throw new Error();
            alert("Parol o‘zgartirildi. Qayta kiring.");
            location.href = "/logout";
        })
        .catch(() => {
            alert("Hozirgi parol noto‘g‘ri");
        });
}
//=========================================================