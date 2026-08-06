let currentPage = 0;
const pageSize = 4;

document.addEventListener("DOMContentLoaded", () => {
    fetch("/api/profile")
        .then(r => r.json())
        .then(data => {
            document.getElementById("username").innerText = data.username;
            document.getElementById("email").innerText = data.email || "— (kiritilmagan)";
            // Dual-role: foydalanuvchida bir nechta rol bo'lishi mumkin
            // (masalan, ham o'qituvchi, ham o'quvchi) — barchasi ko'rsatiladi.
            document.getElementById("role").innerText =
                (data.roles || []).map(r => r.replace("ROLE_", "")).join(", ");

            loadPaymentConfig(data.roles || []);
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

    loadHistory(0);

    document.getElementById("edit").addEventListener("click", enableEditUsername);
    document.getElementById("save-username").addEventListener("click", saveUsername);
    document.getElementById("cancel-username").addEventListener("click", cancelUsernameEdit);

    document.getElementById("edit-email").addEventListener("click", enableEditEmail);
    document.getElementById("save-email").addEventListener("click", saveEmail);
    document.getElementById("cancel-email").addEventListener("click", cancelEmailEdit);
});

function loadHistory(page) {

    currentPage = safePage(page);

    fetch(`/api/profile/history?page=${currentPage}&size=${pageSize}`)
        .then(r => r.json())
        .then(data => {

            const tbody = document.getElementById("history-body");
            tbody.innerHTML = "";

            data.content.forEach(test => {
                const tr = document.createElement("tr");
                tr.innerHTML = `
                    <td>${test.testSessionId}</td>
                    <td>${new Date(test.startedAt).toLocaleString()}</td>
                    <td>${test.finishedAt ? new Date(test.finishedAt).toLocaleString() : "—"}</td>
                    <td>${test.percent}</td>
                    <td>
                        <button onclick="viewTest(${test.testSessionId})">
                            Ko'rish
                        </button>
                    </td>
                `;
                tbody.appendChild(tr);
            });

            renderPagination(data);
        });
}

function renderPagination(pageData) {
    const pagination = document.getElementById("pagination");
    pagination.innerHTML = "";

    // Previous
    const prev = document.createElement("li");
    prev.className = "page-item " + (pageData.first ? "disabled" : "");
    prev.innerHTML = `<a class="page-link" href="#">Previous</a>`;
    prev.onclick = () => !pageData.first && loadHistory(safePage(pageData.number) - 1);
    pagination.appendChild(prev);

    // Pages
    for (let i = 0; i < pageData.totalPages; i++) {
        const li = document.createElement("li");
        li.className = "page-item " + (i === pageData.number ? "active" : "");
        li.innerHTML = `<a class="page-link" href="#">${i + 1}</a>`;
        li.onclick = () => loadHistory(safePage(i));
        pagination.appendChild(li);
    }

    // Next
    const next = document.createElement("li");
    next.className = "page-item " + (pageData.last ? "disabled" : "");
    next.innerHTML = `<a class="page-link" href="#">Next</a>`;
    next.onclick = () => !pageData.last && loadHistory(safePage(pageData.number) + 1);
    pagination.appendChild(next);
}


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



function enableEditEmail() {
    const current = document.getElementById("email").innerText;
    const input = document.getElementById("email-input");

    input.value = current.startsWith("—") ? "" : current;

    document.getElementById("email-view").style.display = "none";
    document.getElementById("email-edit").style.display = "inline";
    document.getElementById("edit-email").style.display = "none";
}

function cancelEmailEdit() {
    document.getElementById("email-edit").style.display = "none";
    document.getElementById("email-view").style.display = "inline";
    document.getElementById("edit-email").style.display = "inline";
}

function saveEmail() {
    const newEmail = document.getElementById("email-input").value.trim();

    if (!newEmail || !newEmail.includes("@")) {
        alert("To'g'ri email kiriting");
        return;
    }

    fetch("/api/profile/email", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ newEmail })
    })
        .then(async r => {
            if (!r.ok) {
                const data = await r.json().catch(() => ({}));
                throw new Error(data.error || "Xatolik yuz berdi");
            }
        })
        .then(() => {
            document.getElementById("email").innerText = newEmail;

            document.getElementById("email-edit").style.display = "none";
            document.getElementById("email-view").style.display = "inline";
            document.getElementById("edit-email").style.display = "inline";

            alert("✅ Email saqlandi. Endi parolni tiklashda zaxira kanal sifatida ishlatiladi.");
        })
        .catch(err => {
            alert(err.message || "Bu email band yoki xatolik");
        });
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

function safePage(page) {
    const n = Number(page);
    return Number.isFinite(n) && n >= 0 ? n : 0;
}

/* ===== Onlayn to'lov (Payme/Click) — ROLE_ADMIN obunasini o'zi sotib olish ===== */

function loadPaymentConfig(roles) {
    if (roles.includes("ROLE_OWNER")) return; // OWNER'ga kerak emas

    fetch("/api/payments/config")
        .then(r => r.ok ? r.json() : null)
        .then(config => {
            if (!config || (!config.paymeEnabled && !config.clickEnabled)) return;

            document.getElementById("onlinePaymentSection").style.display = "block";
            document.getElementById("pricePerMonthText").textContent =
                `1 oy = ${Number(config.pricePerMonthSom).toLocaleString("uz-UZ")} so'm`;

            if (config.paymeEnabled) document.getElementById("payWithPaymeBtn").style.display = "inline-block";
            if (config.clickEnabled) document.getElementById("payWithClickBtn").style.display = "inline-block";
        })
        .catch(err => console.error(err));
}

async function startPayment(provider) {
    const durationMonths = Number(document.getElementById("paymentMonths").value) || 1;

    try {
        const res = await fetch("/api/payments/orders", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ durationMonths, provider })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            alert(data.error || "Xatolik yuz berdi");
            return;
        }

        location.href = data.checkoutUrl;
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}
