// Получаем роль из data-role
const ROLE = document.body.dataset.role;

if (ROLE !== "ROLE_OWNER") {
    alert("⛔ Доступ запрещён");
    location.href = "/login";
}

const MONTH_NAMES_UZ = [
    "Yanvar", "Fevral", "Mart", "Aprel", "May", "Iyun",
    "Iyul", "Avgust", "Sentabr", "Oktabr", "Noyabr", "Dekabr"
];

document.addEventListener("DOMContentLoaded", () => {
    loadStats();
    loadHistory();
});

function formatSum(amount) {
    return Number(amount).toLocaleString("uz-UZ") + " so'm";
}

// "2026-08" -> "Avgust 2026"
function formatMonth(monthKey) {
    const [year, month] = monthKey.split("-").map(Number);
    return MONTH_NAMES_UZ[month - 1] + " " + year;
}

function loadStats() {
    fetch("/api/subscriptions/stats")
        .then(r => {
            if (!r.ok) throw new Error("403 or not authorized");
            return r.json();
        })
        .then(renderStats)
        .catch(err => {
            console.error(err);
            alert("Statistikani yuklashda xatolik");
        });
}

function renderStats(stats) {
    document.getElementById("statTotalRevenue").textContent = formatSum(stats.totalRevenue);
    document.getElementById("statThisMonth").textContent = formatSum(stats.thisMonthRevenue);
    document.getElementById("statActiveSubscribers").textContent = stats.activeSubscribersCount;
    document.getElementById("statTotalCount").textContent = stats.totalConfirmedCount;
    document.getElementById("statPending").textContent = stats.pendingCount;

    renderMonthlyBreakdown(stats.monthlyBreakdown);
}

function renderMonthlyBreakdown(months) {
    const tbody = document.getElementById("monthlyTableBody");
    if (!tbody) return;

    if (!months.length) {
        tbody.innerHTML = `<tr><td colspan="3" class="empty-row">Hali to'lov yo'q</td></tr>`;
        return;
    }

    // Eng so'nggi oy tepada ko'rinishi uchun teskari tartibda chiqaramiz.
    tbody.innerHTML = [...months].reverse().map(m => `
        <tr>
            <td>${formatMonth(m.month)}</td>
            <td>${formatSum(m.amount)}</td>
            <td>${m.count}</td>
        </tr>
    `).join("");
}

function loadHistory() {
    fetch("/api/subscriptions")
        .then(r => r.ok ? r.json() : [])
        .then(renderHistory)
        .catch(err => console.error(err));
}

const STATUS_LABELS_UZ = {
    CONFIRMED: "Tasdiqlangan",
    PENDING: "Kutmoqda",
    CANCELLED: "Bekor qilingan",
    EXPIRED: "Muddati tugagan"
};

function renderHistory(subscriptions) {
    const tbody = document.getElementById("historyTableBody");
    if (!tbody) return;

    if (!subscriptions.length) {
        tbody.innerHTML = `<tr><td colspan="7" class="empty-row">Hali to'lov yo'q</td></tr>`;
        return;
    }

    tbody.innerHTML = subscriptions.map(s => `
        <tr>
            <td>${s.username}</td>
            <td>${formatSum(s.amount)}</td>
            <td>${s.source}</td>
            <td><span class="status-badge ${s.status}">${STATUS_LABELS_UZ[s.status] || s.status}</span></td>
            <td>${new Date(s.createdAt).toLocaleString("uz-UZ")}</td>
            <td>${s.endDate ? new Date(s.endDate).toLocaleDateString("uz-UZ") : "—"}</td>
            <td>${s.note || "—"}</td>
        </tr>
    `).join("");
}
