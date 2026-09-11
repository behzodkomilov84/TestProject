// Получаем роль из data-role
const ROLE = document.body.dataset.role;

if (ROLE !== "ROLE_OWNER") {
    showAlertModal("⛔ Доступ запрещён");
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

async function emailReport() {
    try {
        const res = await fetch("/api/subscriptions/stats/email", { method: "POST" });
        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            showAlertModal(data.error || "❌ Xatolik yuz berdi");
            return;
        }

        showAlertModal(data.message || "✅ Yuborildi");
    } catch (err) {
        console.error(err);
        showAlertModal("❌ Tarmoq xatoligi");
    }
}

function formatSum(amount) {
    return Number(amount).toLocaleString("uz-UZ") + " so'm";
}

// "dd.mm.yyyy hh.mm.ss" — ANIQ shu formatda, brauzer/OS tili sozlamasiga
// qarab o'zgarib turadigan toLocaleString()'dan farqli (foydalanuvchi
// so'rovi, 2026-09-12: "sanalarni dd.mm.yyyy hh.mm.ss formatida qil").
function formatDateTime(dateStr) {
    if (!dateStr) return "—";
    const d = new Date(dateStr);
    if (isNaN(d.getTime())) return "—";
    const p = n => String(n).padStart(2, "0");
    return `${p(d.getDate())}.${p(d.getMonth() + 1)}.${d.getFullYear()} ${p(d.getHours())}.${p(d.getMinutes())}.${p(d.getSeconds())}`;
}

// "2026-08" -> "Avgust 2026"
function formatMonth(monthKey) {
    const [year, month] = monthKey.split("-").map(Number);
    return MONTH_NAMES_UZ[month - 1] + " " + year;
}

// HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-09: "/payments
// ma'lumotlari noto'g'ri" — Click'ning o'z panelida 151 000 so'm ko'rinsa-
// da, bu sahifa faqat 50 000 so'mni ko'rsatardi) — sahifa FAQAT umumiy
// ADMIN-rol obunalarini (/api/subscriptions/stats) hisoblardi, kurs
// obunalari uchun to'lovlar (/api/course-subscriptions/stats, masalan
// "Bakteriologiya" kursiga 2 oylik 100 000 so'm) butunlay tushib qolgan
// edi. Endi ikkalasi ham olinib, BIRLASHTIRILADI.
function loadStats() {
    Promise.all([
        fetch("/api/subscriptions/stats").then(r => {
            if (!r.ok) throw new Error("403 or not authorized");
            return r.json();
        }),
        fetch("/api/course-subscriptions/stats").then(r => r.ok ? r.json() : null)
    ])
        .then(([adminStats, courseStats]) => renderStats(mergeStats(adminStats, courseStats)))
        .catch(err => {
            console.error(err);
            showAlertModal("Statistikani yuklashda xatolik");
        });
}

// Ikkala manbadan (ADMIN-rol + kurs) kelgan bir xil shakldagi
// statistikani bitta umumiy ko'rsatkichga birlashtiradi — "Oylar
// bo'yicha tushum" jadvali ham oy kaliti bo'yicha qo'shiladi.
function mergeStats(adminStats, courseStats) {
    if (!courseStats) return adminStats;

    const monthlyByKey = {};
    [...adminStats.monthlyBreakdown, ...courseStats.monthlyBreakdown].forEach(m => {
        if (!monthlyByKey[m.month]) monthlyByKey[m.month] = { month: m.month, amount: 0, count: 0 };
        monthlyByKey[m.month].amount += Number(m.amount);
        monthlyByKey[m.month].count += m.count;
    });

    return {
        totalRevenue: Number(adminStats.totalRevenue) + Number(courseStats.totalRevenue),
        thisMonthRevenue: Number(adminStats.thisMonthRevenue) + Number(courseStats.thisMonthRevenue),
        totalConfirmedCount: adminStats.totalConfirmedCount + courseStats.totalConfirmedCount,
        activeSubscribersCount: adminStats.activeSubscribersCount + courseStats.activeSubscribersCount,
        pendingCount: adminStats.pendingCount + courseStats.pendingCount,
        monthlyBreakdown: Object.values(monthlyByKey).sort((a, b) => a.month.localeCompare(b.month))
    };
}

function renderStats(stats) {
    document.getElementById("statTotalRevenue").textContent = formatSum(stats.totalRevenue);
    document.getElementById("statThisMonth").textContent = formatSum(stats.thisMonthRevenue);
    document.getElementById("statActiveSubscribers").textContent = stats.activeSubscribersCount;
    document.getElementById("statTotalCount").textContent = stats.totalConfirmedCount;
    // "Tasdiq kutmoqda" katakchasi olib tashlandi (foydalanuvchi so'rovi,
    // 2026-09-12) — qo'lda tasdiqlanadigan PENDING to'lov so'rovi
    // mexanizmi butunlay olib tashlangan edi, shu sabab stats.pendingCount
    // endi ko'rsatilmaydi.

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

// ADMIN-rol obunalari VA kurs obunalari — ikkalasi ham olib, bitta
// jadvalda (createdAt bo'yicha eng so'nggisi tepada) ko'rsatiladi
// (foydalanuvchi so'rovi, 2026-09-09: "/payments ma'lumotlari noto'g'ri").
function loadHistory() {
    Promise.all([
        fetch("/api/subscriptions").then(r => r.ok ? r.json() : []),
        fetch("/api/course-subscriptions").then(r => r.ok ? r.json() : [])
    ])
        .then(([adminSubs, courseSubs]) => {
            const tagged = [
                ...adminSubs.map(s => ({ ...s, service: "🎓 ADMIN huquqi" })),
                ...courseSubs.map(s => ({ ...s, service: "📚 " + s.courseTitle }))
            ].sort((a, b) => new Date(b.createdAt) - new Date(a.createdAt));

            renderHistory(tagged);
        })
        .catch(err => console.error(err));
}

const STATUS_LABELS_UZ = {
    CONFIRMED: "Tasdiqlangan",
    PENDING: "Kutmoqda",
    CANCELLED: "Bekor qilingan",
    EXPIRED: "Muddati tugagan"
};

const SOURCE_LABELS_UZ = {
    MANUAL: "✋ Qo'lda berilgan",
    ONLINE: "💳 Onlayn to'lov (Click)",
    TELEGRAM: "🤖 Telegram bot orqali",
    TRIAL: "🎁 Bepul sinov",
    REQUESTED: "📩 So'rov"
};

function renderHistory(subscriptions) {
    const tbody = document.getElementById("historyTableBody");
    if (!tbody) return;

    if (!subscriptions.length) {
        tbody.innerHTML = `<tr><td colspan="8" class="empty-row">Hali to'lov yo'q</td></tr>`;
        return;
    }

    tbody.innerHTML = subscriptions.map(s => `
        <tr>
            <td>${s.username}</td>
            <td>${s.service}</td>
            <td>${formatSum(s.amount)}</td>
            <td>${SOURCE_LABELS_UZ[s.source] || s.source}</td>
            <td><span class="status-badge ${s.status}">${STATUS_LABELS_UZ[s.status] || s.status}</span></td>
            <td>${formatDateTime(s.createdAt)}</td>
            <td>${formatDateTime(s.endDate)}</td>
            <td>${s.note || "—"}</td>
        </tr>
    `).join("");
}
