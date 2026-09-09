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
    loadAllSubscriptions();
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
        loadAllSubscriptions();
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
        loadAllSubscriptions();
    } catch (err) {
        console.error(err);
        showAlertModal("Network error");
    }
}

// "📋 Barcha obunalar" — PENDING/CONFIRMED/EXPIRED/CANCELLED barchasi
// (foydalanuvchi so'rovi, 2026-09-09: "Bekor qilinganlar ro'yxati
// qayerda saqlanadi?"). /courses/subscriptions'dagi bilan bir xil g'oya.
let allAdminSubs = [];

function loadAllSubscriptions() {
    fetch("/api/subscriptions")
        .then(r => r.ok ? r.json() : [])
        .then(subs => {
            allAdminSubs = subs;
            renderAllSubscriptions();
        })
        .catch(err => console.error(err));
}

const ADMIN_SUB_STATUS_LABELS = {
    CONFIRMED: "✅ Faol",
    PENDING: "⏳ Kutilmoqda",
    EXPIRED: "⌛ Muddati tugagan",
    CANCELLED: "❌ Bekor qilingan"
};

function renderAllSubscriptions() {
    const tbody = document.getElementById("allSubsTableBody");
    if (!tbody) return;

    const filter = (document.getElementById("allSubsFilter").value || "").trim().toLowerCase();
    const subs = filter
        ? allAdminSubs.filter(s => s.username.toLowerCase().includes(filter))
        : allAdminSubs;

    if (!subs.length) {
        tbody.innerHTML = `<tr><td colspan="7" class="empty-row">Hali obuna yo'q</td></tr>`;
        return;
    }

    tbody.innerHTML = subs.map(s => {
        const statusClass = s.status === "CONFIRMED" ? "sub-status-active" : "sub-status-inactive";
        const muddat = s.endDate ? new Date(s.endDate).toLocaleDateString("uz-UZ") : "—";
        // PENDING'ning o'z tasdiqlash/rad etish tugmalari yuqoridagi
        // "⏳ Tasdiq kutilayotgan" jadvalida bor — bu yerda takrorlanmaydi.
        // "✏️ Tahrirlash" — PENDING'dan tashqari barcha holatlarda (eskirgan/
        // bekor qilinganni ham qayta faollashtiradi), "🗑️ O'chirish" — HAR
        // DOIM (foydalanuvchi so'rovi, 2026-09-09: "тахрирлаш, ўчиришни
        // ҳам қўш" — /courses/subscriptions'dagi bilan bir xil g'oya).
        let action = "";
        if (s.status !== "PENDING") {
            action += `<button class="sub-action-btn sub-action-edit" onclick="editAdminSubscription(${s.id})">✏️ Tahrirlash</button>`;
        }
        if (s.status === "CONFIRMED") {
            action += `<button class="sub-action-btn sub-action-cancel" onclick="cancelActiveSubscription(${s.id})">❌ Bekor qilish</button>`;
        }
        action += `<button class="sub-action-btn sub-action-delete" onclick="deleteAdminSubscriptionPermanently(${s.id})">🗑️ O'chirish</button>`;

        return `
            <tr>
                <td>${escapeHtmlAdmin(s.username)}</td>
                <td>${Number(s.amount).toLocaleString("uz-UZ")} so'm</td>
                <td>${escapeHtmlAdmin(s.source)}</td>
                <td><span class="sub-status-badge ${statusClass}">${ADMIN_SUB_STATUS_LABELS[s.status] || s.status}</span></td>
                <td>${muddat}</td>
                <td>${new Date(s.createdAt).toLocaleDateString("uz-UZ")}</td>
                <td>${action}</td>
            </tr>
        `;
    }).join("");
}

function escapeHtmlAdmin(text) {
    const div = document.createElement("div");
    div.textContent = text ?? "";
    return div.innerHTML;
}

async function cancelActiveSubscription(id) {
    if (!await showConfirmModal(
        "Ushbu obunani bekor qilmoqchimisiz? Agar bu ADMIN huquqini bergan yagona faol obuna bo'lsa, huquq DARHOL olib tashlanadi."))
        return;

    try {
        const res = await fetch(`/api/subscriptions/${id}/cancel`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }
        loadAllSubscriptions();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "✏️ Tahrirlash" — mavjud ADMIN-rol obunasining summasi/muddatini
// o'zgartiradi (foydalanuvchi so'rovi, 2026-09-09). Eskirgan/bekor
// qilingan obunani tahrirlash uni qayta FAOLlashtirib, ROLE_ADMIN'ni
// qayta beradi (serverda — SubscriptionService.updateSubscription).
async function editAdminSubscription(id) {
    const sub = allAdminSubs.find(s => s.id === id);
    if (!sub) return;

    const amountStr = await showPromptModal(
        `"${sub.username}" uchun yangi summa (so'm):`,
        String(Math.round(Number(sub.amount) || 0)));
    if (amountStr === null) return;

    const amount = Number(amountStr);
    if (isNaN(amount) || amount <= 0) {
        showAlertModal("❌ Noto'g'ri summa");
        return;
    }

    const durationStr = await showPromptModal("Yangi muddat (necha oy, boshlanish sanasidan):", "1");
    if (durationStr === null) return;

    const durationMonths = Number(durationStr);
    if (!durationMonths || durationMonths <= 0) {
        showAlertModal("❌ Noto'g'ri muddat");
        return;
    }

    try {
        const res = await fetch(`/api/subscriptions/${id}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ amount, durationMonths })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }

        showAlertModal("✅ Obuna yangilandi");
        loadAllSubscriptions();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "🗑️ O'chirish" — "Bekor qilish"dan (holatni CANCELLED qilib saqlaydi)
// farqli, yozuvni BUTUNLAY o'chiradi (foydalanuvchi so'rovi, 2026-09-09).
async function deleteAdminSubscriptionPermanently(id) {
    if (!await showConfirmModal(
        "Bu obuna yozuvini BUTUNLAY o'chirmoqchimisiz? Agar hali faol bo'lsa, ADMIN huquqi ham darhol olib tashlanadi. Bu amalni ortga qaytarib bo'lmaydi."))
        return;

    try {
        const res = await fetch(`/api/subscriptions/${id}`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }
        loadAllSubscriptions();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
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
        loadAllSubscriptions();
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
