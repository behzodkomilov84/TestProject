// "Admin obunalari" — foydalanuvchi so'rovi, 2026-09-06: /users
// sahifasidan ajratilgan alohida sahifa (avval hammasi bitta sahifada,
// jadval juda ko'p ustunli bo'lib qolgani uchun bo'lindi).
const ROLE = document.body.dataset.role;

if (ROLE !== "ROLE_OWNER") {
    showAlertModal("⛔ Доступ запрещён");
    location.href = "/login";
}

document.addEventListener("DOMContentLoaded", () => {
    loadUsersForSelect();
    loadPendingSubscriptions();
    loadMinAmount();
});

function loadUsersForSelect() {
    fetch("/api/users")
        .then(r => r.ok ? r.json() : [])
        .then(populateManualUserSelect)
        .catch(err => console.error(err));
}

function populateManualUserSelect(users) {
    const select = document.getElementById("manualUserSelect");
    if (!select) return;

    select.innerHTML = users
        .map(u => `<option value="${u.id}">${u.username}</option>`)
        .join("");
}

function loadPendingSubscriptions() {
    fetch("/api/subscriptions?status=PENDING")
        .then(r => r.ok ? r.json() : [])
        .then(renderPendingSubscriptions)
        .catch(err => console.error(err));
}

function renderPendingSubscriptions(subscriptions) {
    const tbody = document.getElementById("pendingTableBody");
    if (!tbody) return;

    if (!subscriptions.length) {
        tbody.innerHTML = `<tr><td colspan="5" class="empty-row">Kutilayotgan so'rov yo'q</td></tr>`;
        return;
    }

    tbody.innerHTML = subscriptions.map(s => `
        <tr>
            <td>${s.username}</td>
            <td>${s.amount} so'm</td>
            <td>${s.source}</td>
            <td>${new Date(s.createdAt).toLocaleString("uz-UZ")}</td>
            <td>
                <button class="action-btn" onclick="confirmSubscription(${s.id})" title="Tasdiqlash">✅</button>
                <button class="action-btn" onclick="cancelSubscription(${s.id})" title="Rad etish">❌</button>
            </td>
        </tr>
    `).join("");
}

async function confirmSubscription(id) {
    const months = await showPromptModal("ADMIN huquqi necha oyga beriladi?", "1");
    if (months === null) return;

    try {
        const res = await fetch(`/api/subscriptions/${id}/confirm`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ durationMonths: Number(months) || 1 })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }

        showAlertModal("✅ Tasdiqlandi, ADMIN huquqi berildi.");
        loadPendingSubscriptions();
    } catch (err) {
        console.error(err);
        showAlertModal("Network error");
    }
}

async function cancelSubscription(id) {
    if (!await showConfirmModal("So'rovni rad etmoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/subscriptions/${id}/cancel`, { method: "POST" });
        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }

        loadPendingSubscriptions();
    } catch (err) {
        console.error(err);
        showAlertModal("Network error");
    }
}

async function createManualSubscription() {
    const userId = Number(document.getElementById("manualUserSelect").value);
    const amount = Number(document.getElementById("manualAmount").value);
    const durationMonths = Number(document.getElementById("manualDuration").value) || 1;
    const note = document.getElementById("manualNote").value.trim();

    if (!userId || !amount || amount <= 0) {
        showAlertModal("❌ Foydalanuvchi va to'g'ri summani kiriting");
        return;
    }

    try {
        const res = await fetch("/api/subscriptions", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ userId, amount, durationMonths, note })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }

        showAlertModal("✅ To'lov qayd qilindi, ADMIN huquqi berildi.");
        document.getElementById("manualAmount").value = "";
        document.getElementById("manualNote").value = "";
    } catch (err) {
        console.error(err);
        showAlertModal("Network error");
    }
}

function loadMinAmount() {
    const input = document.getElementById("minAmountInput");
    if (!input) return;

    fetch("/api/payments/min-amount")
        .then(r => r.ok ? r.json() : null)
        .then(data => {
            if (data) input.value = data.minAmountSom;
        })
        .catch(err => console.error(err));
}

async function saveMinAmount() {
    const value = Number(document.getElementById("minAmountInput").value);

    if (!value || value <= 0) {
        showAlertModal("❌ To'g'ri summa kiriting");
        return;
    }

    try {
        const res = await fetch("/api/payments/min-amount", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ minAmountSom: value })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }

        showAlertModal("✅ Minimal summa saqlandi: " + data.minAmountSom + " so'm");
    } catch (err) {
        console.error(err);
        showAlertModal("Network error");
    }
}
