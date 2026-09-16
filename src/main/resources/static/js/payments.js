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
// Har bir qatorga aniq "type" belgisi qo'yiladi ("admin"/"course") —
// "✏️ Tahrirlash" tugmasi qaysi API'ga (/api/subscriptions yoki
// /api/course-subscriptions) murojaat qilishini shu orqali aniqlaydi,
// courseTitle borligini taxmin qilish o'rniga (foydalanuvchi so'rovi,
// 2026-09-16: "шу жадвал устунларига саралаш қўш, таҳрирлаш action ҳам қўш").
let lastHistoryList = [];

function loadHistory() {
    Promise.all([
        fetch("/api/subscriptions").then(r => r.ok ? r.json() : []),
        fetch("/api/course-subscriptions").then(r => r.ok ? r.json() : [])
    ])
        .then(([adminSubs, courseSubs]) => {
            lastHistoryList = [
                ...adminSubs.map(s => ({ ...s, service: "🎓 ADMIN huquqi", type: "admin" })),
                ...courseSubs.map(s => ({ ...s, service: "📚 " + s.courseTitle, type: "course" }))
            ];
            renderHistory(getSortedHistory(lastHistoryList));
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

// "To'liq to'lov tarixi" jadvali uchun saralash — /users va
// /statistics/user-sessions sahifalaridagi bilan bir xil andoza
// (data-sort ustun sarlavhasi bosilganda, xuddi shu ustun qayta
// bosilsa yo'nalish teskari bo'ladi).
let historySortKey = "createdAt";
let historySortDir = "desc";
const HISTORY_DEFAULT_DESC = new Set(["createdAt", "endDate", "amount"]);

function onHistorySortHeaderClick(key) {
    if (historySortKey === key) {
        historySortDir = historySortDir === "asc" ? "desc" : "asc";
    } else {
        historySortKey = key;
        historySortDir = HISTORY_DEFAULT_DESC.has(key) ? "desc" : "asc";
    }
    renderHistory(getSortedHistory(lastHistoryList));
}

function getSortedHistory(list) {
    const key = historySortKey;
    const dir = historySortDir === "asc" ? 1 : -1;

    return [...list].sort((a, b) => {
        let va = a[key];
        let vb = b[key];

        if (key === "createdAt" || key === "endDate") {
            va = va ? new Date(va).getTime() : null;
            vb = vb ? new Date(vb).getTime() : null;
        } else if (key === "amount") {
            va = Number(va);
            vb = Number(vb);
        } else if (key === "status") {
            va = STATUS_LABELS_UZ[va] || va || "";
            vb = STATUS_LABELS_UZ[vb] || vb || "";
        } else if (key === "source") {
            va = SOURCE_LABELS_UZ[va] || va || "";
            vb = SOURCE_LABELS_UZ[vb] || vb || "";
        } else {
            va = (va ?? "").toString().toLowerCase();
            vb = (vb ?? "").toString().toLowerCase();
        }

        if (va === null || va === undefined || va === "") return vb === null || vb === undefined || vb === "" ? 0 : 1;
        if (vb === null || vb === undefined || vb === "") return -1;

        if (va < vb) return -1 * dir;
        if (va > vb) return 1 * dir;
        return 0;
    });
}

function updateHistorySortArrows() {
    document.querySelectorAll("#historyTable .sort-arrow").forEach(el => el.textContent = "");
    const arrow = document.getElementById("historySortArrow-" + historySortKey);
    if (arrow) arrow.textContent = historySortDir === "asc" ? "▲" : "▼";
}

function escapeHtmlHistory(text) {
    const div = document.createElement("div");
    div.textContent = text ?? "";
    return div.innerHTML;
}

function renderHistory(subscriptions) {
    const tbody = document.getElementById("historyTableBody");
    if (!tbody) return;

    if (!subscriptions.length) {
        tbody.innerHTML = `<tr><td colspan="9" class="empty-row">Hali to'lov yo'q</td></tr>`;
        updateHistorySortArrows();
        return;
    }

    tbody.innerHTML = subscriptions.map(s => {
        // "🗑️ O'chirish" — HAR DOIM (statusdan qat'iy nazar), "✏️
        // Tahrirlash"dan farqli (adminSubscriptions.js/courseSubscriptions.js
        // bilan bir xil g'oya — foydalanuvchi so'rovi, 2026-09-16).
        let actions = s.status !== "PENDING"
            ? `<button class="sub-action-btn sub-action-edit" onclick="editHistorySubscription(${s.id}, '${s.type}')">✏️ Tahrirlash</button>`
            : "";
        actions += `<button class="sub-action-btn sub-action-delete" onclick="deleteHistorySubscription(${s.id}, '${s.type}')">🗑️ O'chirish</button>`;

        return `
        <tr>
            <td>${escapeHtmlHistory(s.username)}</td>
            <td>${escapeHtmlHistory(s.service)}</td>
            <td>${formatSum(s.amount)}</td>
            <td>${SOURCE_LABELS_UZ[s.source] || s.source}</td>
            <td><span class="status-badge ${s.status}">${STATUS_LABELS_UZ[s.status] || s.status}</span></td>
            <td>${formatDateTime(s.createdAt)}</td>
            <td>${formatDateTime(s.endDate)}</td>
            <td>${escapeHtmlHistory(s.note) || "—"}</td>
            <td>${actions}</td>
        </tr>
    `;
    }).join("");

    updateHistorySortArrows();
    buildHistoryFixedHeader();
    positionHistoryFixedHeader();
    initHistoryScrollSync();
}

// ===== Sarlavhani tepaga qotirish (foydalanuvchi so'rovi, 2026-09-16:
// "jadvaldagi th ni va chapdan username'gacha fixed qil") — /users
// sahifasidagi buildFixedHeader()/positionFixedHeader() bilan bir xil
// ISHONCHLI andoza (users.js), faqat 1ta muzlatilgan ustun (username)
// bilan va bitta manba scrollbar (bu yerda users.js'dagi kabi alohida
// tepa/fixed mirror scrollbar shart emas — jadval ancha ingichka). =====
function buildHistoryFixedHeader() {
    const ths = [...document.querySelectorAll("#historyTable thead th")];
    const frozenContainer = document.getElementById("historyTableHeaderFixedFrozen");
    const scrollContainer = document.getElementById("historyTableHeaderFixedInner");
    if (!ths.length || !frozenContainer || !scrollContainer) return;

    frozenContainer.innerHTML = "";
    scrollContainer.innerHTML = "";

    ths.forEach((th, i) => {
        const div = document.createElement("div");
        div.className = "fx-cell";
        div.textContent = th.textContent.trim();
        // Haqiqiy chizilgan kenglikni o'qib, aynan shu qiymatni qattiq
        // belgilaymiz — nusxa asl ustunlar bilan pixel-aniq tekislanadi.
        div.style.width = th.getBoundingClientRect().width + "px";

        const sortKey = th.dataset.sort;
        if (sortKey) {
            div.dataset.sort = sortKey;
            div.addEventListener("click", () => onHistorySortHeaderClick(sortKey));
        }
        (i === 0 ? frozenContainer : scrollContainer).appendChild(div);
    });
}

function positionHistoryFixedHeader() {
    const scrollEl = document.getElementById("historyTableScroll");
    const fixedHeader = document.getElementById("historyTableHeaderFixed");
    if (!scrollEl || !fixedHeader) return;

    const rect = scrollEl.getBoundingClientRect();
    fixedHeader.style.left = rect.left + "px";
    fixedHeader.style.width = rect.width + "px";

    const headerInner = document.getElementById("historyTableHeaderFixedInner");
    if (headerInner) headerInner.style.transform = `translateX(-${scrollEl.scrollLeft}px)`;

    updateHistoryFixedHeaderVisibility();
}

// Nusxa FAQAT asl <thead> ekranning (navbar ostidagi, 72px) tepasidan
// chiqib ketganda ko'rinadi — aks holda ikkita sarlavha bir vaqtda
// ko'rinib, ortiqcha g'ijimlanish hosil qilardi.
function updateHistoryFixedHeaderVisibility() {
    const realThead = document.querySelector("#historyTable thead");
    const fixedHeader = document.getElementById("historyTableHeaderFixed");
    if (!realThead || !fixedHeader) return;

    const rect = realThead.getBoundingClientRect();
    fixedHeader.hidden = !(rect.top < 72);
}

let historyScrollSyncInitialized = false;

function initHistoryScrollSync() {
    if (historyScrollSyncInitialized) return;
    historyScrollSyncInitialized = true;

    const scrollEl = document.getElementById("historyTableScroll");
    if (scrollEl) scrollEl.addEventListener("scroll", positionHistoryFixedHeader);

    window.addEventListener("resize", () => {
        buildHistoryFixedHeader();
        positionHistoryFixedHeader();
    });
    window.addEventListener("scroll", updateHistoryFixedHeaderVisibility, { passive: true });
}

// "✏️ Tahrirlash" — qator turiga ("admin" yoki "course") qarab to'g'ri
// API'ga yo'naltiradi: ADMIN-rol obunasi /api/subscriptions/{id} (amount,
// durationMonths, source), kurs obunasi /api/course-subscriptions/{id}
// (amount, durationMonths — source'siz). adminSubscriptions.js'dagi
// editAdminSubscription() / courseSubscriptions.js'dagi editSubscription()
// bilan bir xil showPromptModal andozasi (foydalanuvchi so'rovi, 2026-09-16).
async function editHistorySubscription(id, type) {
    const sub = lastHistoryList.find(s => s.id === id && s.type === type);
    if (!sub) return;

    const amountStr = await showPromptModal(
        `"${sub.username}" — "${sub.service}": yangi summa (so'm):`,
        String(Math.round(Number(sub.amount) || 0)));
    if (amountStr === null) return;

    const amount = Number(amountStr);
    if (isNaN(amount) || amount < 0) {
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

    const body = { amount, durationMonths };

    if (type === "admin") {
        const sourceStr = await showPromptModal(
            "Manba (MANUAL, ONLINE yoki TELEGRAM — bo'sh qoldirsangiz o'zgarmaydi):",
            sub.source || "");
        if (sourceStr === null) return;

        const trimmedSource = sourceStr.trim().toUpperCase();
        if (trimmedSource && !["MANUAL", "ONLINE", "TELEGRAM"].includes(trimmedSource)) {
            showAlertModal("❌ Manba noto'g'ri (MANUAL, ONLINE yoki TELEGRAM bo'lishi kerak)");
            return;
        }
        body.source = trimmedSource || null;
    }

    const url = type === "admin" ? `/api/subscriptions/${id}` : `/api/course-subscriptions/${id}`;

    try {
        const res = await fetch(url, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(body)
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }

        showAlertModal("✅ Obuna yangilandi");
        loadHistory();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "🗑️ O'chirish" — "Bekor qilish"dan farqli, yozuvni BUTUNLAY o'chiradi
// (adminSubscriptions.js#deleteAdminSubscriptionPermanently /
// courseSubscriptions.js#deleteSubscriptionPermanently bilan bir xil,
// foydalanuvchi so'rovi, 2026-09-16: "satrlarni delete qilishni ham qo'sh").
async function deleteHistorySubscription(id, type) {
    const warning = type === "admin"
        ? "Bu obuna yozuvini BUTUNLAY o'chirmoqchimisiz? Agar hali faol bo'lsa, ADMIN huquqi ham darhol olib tashlanadi. Bu amalni ortga qaytarib bo'lmaydi."
        : "Bu obuna yozuvini BUTUNLAY o'chirmoqchimisiz? Bu amalni ortga qaytarib bo'lmaydi.";
    if (!await showConfirmModal(warning, { danger: true })) return;

    const url = type === "admin" ? `/api/subscriptions/${id}` : `/api/course-subscriptions/${id}`;

    try {
        const res = await fetch(url, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }
        loadHistory();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}
