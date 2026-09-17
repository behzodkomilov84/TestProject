// "📊 Statistika" -> "👤 Foydalanuvchilar bo'yicha test statistikasi"
// (foydalanuvchi so'rovi, 2026-09-13: "foydalanuvchilar kesimida test
// sessiyasi bo'yicha statistika yaratib Statistika menyusiga qo'sh.
// Sahifa mukammal bo'lsin. Qidiruv, saralash ... funksiyalari bo'lsin").
// Foydalanuvchilar soni odatda yuzlab bilan cheklangani uchun — BARCHA
// ma'lumot bir martada yuklanadi, qidiruv/saralash/filtr FAQAT
// frontendda (server darajasidagi sahifalash keraksiz murakkablik bo'lardi).

let statsAllRows = [];
let statsSearchQuery = "";
let statsOnlyActive = false;
let statsSortKey = "sessionCount";
let statsSortDir = "desc"; // "asc" | "desc"

document.addEventListener("DOMContentLoaded", loadUserSessionStats);

async function loadUserSessionStats() {
    try {
        const res = await fetch("/api/statistics/user-sessions");
        if (!res.ok) {
            document.getElementById("statsLoadingMessage").textContent =
                res.status === 403
                    ? "⛔ Bu sahifani ko'rish huquqingiz yo'q."
                    : "❌ Ma'lumotlarni yuklashda xatolik yuz berdi.";
            return;
        }
        const data = await res.json();

        // Backend "firstName"/"lastName" alohida qaytaradi — qidiruv/
        // ko'rsatishda qulay bo'lishi uchun bitta "fullName"ga
        // birlashtiriladi (ikkalasi ham bo'sh bo'lsa — "—").
        statsAllRows = data.map(row => ({
            ...row,
            fullName: [row.firstName, row.lastName].filter(Boolean).join(" ").trim() || "—",
            groupName: row.groupName || "—"
        }));

        document.getElementById("statsLoadingMessage").classList.add("hidden");
        renderSummary();
        renderTable();
        initStatsScrollSync();
    } catch (err) {
        console.error(err);
        document.getElementById("statsLoadingMessage").textContent = "❌ Tarmoq xatoligi.";
    }
}

// ===== Ekranga qotirilgan (fixed) gorizontal scroll (foydalanuvchi
// so'rovi, 2026-09-18: "horizontal scroll qo'sh. Ekranga fixed bo'lsin")
// — /users sahifasidagi (.table-scroll-fixed) bilan bir xil andoza: sahifa
// pastga aylantirilganda, jadvalning o'z (native) scrollbar'i ekrandan
// tashqarida qolsa, uning nusxasi ekranning pastiga qotirib ko'rsatiladi. =====
let statsScrollSyncInitialized = false;

function initStatsScrollSync() {
    positionFixedScrollBar();
    buildStatsFixedHeader();
    positionStatsFixedHeader();

    if (statsScrollSyncInitialized) return;
    statsScrollSyncInitialized = true;

    const bottomScroll = document.getElementById("statsTableScroll");
    const fixedScroll = document.getElementById("statsTableScrollFixed");
    const bars = [bottomScroll, fixedScroll];
    let syncing = false;

    bars.forEach(bar => {
        bar.addEventListener("scroll", () => {
            if (syncing) return;
            syncing = true;
            bars.forEach(other => {
                if (other !== bar) other.scrollLeft = bar.scrollLeft;
            });
            // Qotirilgan sarlavhaning aylanadigan qismini (muzlatilgan
            // "Username"dan tashqari ustunlar) ham xuddi shu gorizontal
            // siljishga sinxronlaydi.
            const headerInner = document.getElementById("statsTableHeaderFixedInner");
            if (headerInner) headerInner.style.transform = `translateX(-${bottomScroll.scrollLeft}px)`;
            syncing = false;
        });
    });

    window.addEventListener("resize", () => {
        positionFixedScrollBar();
        buildStatsFixedHeader();
        positionStatsFixedHeader();
    });
    window.addEventListener("scroll", () => {
        updateFixedScrollBarVisibility();
        updateStatsFixedHeaderVisibility();
    }, { passive: true });
}

// ===== Sarlavhani tepaga qotirish (foydalanuvchi so'rovi, 2026-09-18:
// "jadval sarlavhasini ... fixed qil") — /users va /payments
// sahifalaridagi bilan bir xil ISHONCHLI andoza: ASL <thead> oddiy
// (static) qoldirilib, uning nusxasi position:fixed <div>lar bilan
// alohida quriladi (jonli tekshiruvda topilgan bug sabab —
// position:sticky jadval katakchalarida ishlatib bo'lmaydi, qarang:
// userSessionStatistics.css). "Username" — muzlatilgan (frozen) ustun,
// gorizontal aylantirishdan mustasno. =====
function buildStatsFixedHeader() {
    const ths = [...document.querySelectorAll("#statsTable thead th")];
    const frozenContainer = document.getElementById("statsTableHeaderFixedFrozen");
    const scrollContainer = document.getElementById("statsTableHeaderFixedInner");
    if (!ths.length || !frozenContainer || !scrollContainer) return;

    frozenContainer.innerHTML = "";
    scrollContainer.innerHTML = "";

    ths.forEach((th, i) => {
        const div = document.createElement("div");
        div.className = "fx-cell" + (th.classList.contains("stats-col-num") ? " stats-col-num" : "");
        div.textContent = th.textContent.trim();
        // Haqiqiy chizilgan kenglikni o'qib, aynan shu qiymatni qattiq
        // belgilaymiz — nusxa asl ustunlar bilan pixel-aniq tekislanadi.
        div.style.width = th.getBoundingClientRect().width + "px";

        const sortKey = th.dataset.sort;
        if (sortKey) {
            div.addEventListener("click", () => onSortHeaderClick(sortKey));
        }
        (i === 0 ? frozenContainer : scrollContainer).appendChild(div);
    });
}

function positionStatsFixedHeader() {
    const scrollEl = document.getElementById("statsTableScroll");
    const fixedHeader = document.getElementById("statsTableHeaderFixed");
    if (!scrollEl || !fixedHeader) return;

    const rect = scrollEl.getBoundingClientRect();
    fixedHeader.style.left = rect.left + "px";
    fixedHeader.style.width = rect.width + "px";

    const headerInner = document.getElementById("statsTableHeaderFixedInner");
    if (headerInner) headerInner.style.transform = `translateX(-${scrollEl.scrollLeft}px)`;

    updateStatsFixedHeaderVisibility();
}

// Nusxa FAQAT asl <thead> ekranning (navbar ostidagi, 72px) tepasidan
// chiqib ketganda ko'rinadi — aks holda ikkita sarlavha bir vaqtda
// ko'rinib, ortiqcha g'ijimlanish hosil qilardi.
function updateStatsFixedHeaderVisibility() {
    const realThead = document.querySelector("#statsTable thead");
    const fixedHeader = document.getElementById("statsTableHeaderFixed");
    if (!realThead || !fixedHeader) return;

    const rect = realThead.getBoundingClientRect();
    fixedHeader.hidden = !(rect.top < 72);
}

function positionFixedScrollBar() {
    const bottomScroll = document.getElementById("statsTableScroll");
    const fixedScroll = document.getElementById("statsTableScrollFixed");
    const fixedInner = document.getElementById("statsTableScrollFixedInner");
    const table = document.getElementById("statsTable");
    if (!bottomScroll || !fixedScroll || !fixedInner || !table) return;

    const rect = bottomScroll.getBoundingClientRect();
    fixedScroll.style.left = rect.left + "px";
    fixedScroll.style.width = rect.width + "px";
    fixedInner.style.width = table.scrollWidth + "px";

    updateFixedScrollBarVisibility();
}

// Qotirilgan scrollbar FAQAT jadval haqiqatan gorizontal aylantirilishi
// kerak bo'lganda VA jadvalning o'z (native) pastki scrollbar'i hozir
// ekrandan tashqarida (ko'rinmayotgan) bo'lsa ko'rsatiladi — aks holda
// ikkita scrollbar bir vaqtda ko'rinib, ortiqcha g'ijimlanish hosil qilardi.
function updateFixedScrollBarVisibility() {
    const bottomScroll = document.getElementById("statsTableScroll");
    const fixedScroll = document.getElementById("statsTableScrollFixed");
    if (!bottomScroll || !fixedScroll) return;

    const rect = bottomScroll.getBoundingClientRect();
    const isScrollable = bottomScroll.scrollWidth > bottomScroll.clientWidth + 1;
    const nativeScrollbarOffscreen = rect.bottom > window.innerHeight;
    const tableStillVisible = rect.top < window.innerHeight;

    fixedScroll.hidden = !(isScrollable && nativeScrollbarOffscreen && tableStillVisible);
}

function renderSummary() {
    const total = statsAllRows.length;
    const active = statsAllRows.filter(r => r.sessionCount > 0);
    const totalSessions = statsAllRows.reduce((sum, r) => sum + r.sessionCount, 0);
    const totalQuestions = statsAllRows.reduce((sum, r) => sum + r.totalQuestions, 0);
    const avgOfActive = active.length
        ? active.reduce((sum, r) => sum + r.avgPercent, 0) / active.length
        : 0;

    document.getElementById("summaryTotalUsers").textContent = total;
    document.getElementById("summaryActiveUsers").textContent = active.length;
    document.getElementById("summaryTotalSessions").textContent = totalSessions;
    document.getElementById("summaryTotalQuestions").textContent = totalQuestions;
    document.getElementById("summaryAvgPercent").textContent = active.length ? avgOfActive.toFixed(1) + "%" : "—";
}

function onStatsSearchInput() {
    statsSearchQuery = document.getElementById("statsSearchInput").value.trim().toLowerCase();
    renderTable();
}

function onStatsFilterChange() {
    statsOnlyActive = document.getElementById("statsOnlyActiveCheckbox").checked;
    renderTable();
}

// Ustun sarlavhasiga bosilganda — xuddi shu ustun bo'yicha SARALANGAN
// bo'lsa yo'nalishi teskarisiga o'giriladi, aks holda YANGI ustun
// bo'yicha (standart yo'nalish bilan) saralanadi.
function onSortHeaderClick(key) {
    const defaultDirDesc = new Set(["sessionCount", "totalQuestions", "totalCorrect", "avgPercent", "bestPercent", "totalDurationSec", "lastSessionAt"]);
    if (statsSortKey === key) {
        statsSortDir = statsSortDir === "asc" ? "desc" : "asc";
    } else {
        statsSortKey = key;
        statsSortDir = defaultDirDesc.has(key) ? "desc" : "asc";
    }
    renderTable();
}

function getFilteredSortedRows() {
    let rows = statsAllRows;

    if (statsOnlyActive) {
        rows = rows.filter(r => r.sessionCount > 0);
    }

    if (statsSearchQuery) {
        rows = rows.filter(r =>
            (r.username || "").toLowerCase().includes(statsSearchQuery) ||
            (r.fullName || "").toLowerCase().includes(statsSearchQuery) ||
            (r.groupName || "").toLowerCase().includes(statsSearchQuery)
        );
    }

    const dir = statsSortDir === "asc" ? 1 : -1;
    const key = statsSortKey;
    rows = [...rows].sort((a, b) => {
        let va = a[key];
        let vb = b[key];
        // "lastSessionAt" — hech qachon test yechmagan (null) qatorlar
        // saralashda HAR DOIM oxiriga tushadi (yo'nalishidan qat'iy nazar).
        if (key === "lastSessionAt") {
            if (!va && !vb) return 0;
            if (!va) return 1;
            if (!vb) return -1;
            return dir * (new Date(va) - new Date(vb));
        }
        if (typeof va === "string" || typeof vb === "string") {
            va = (va || "").toString().toLowerCase();
            vb = (vb || "").toString().toLowerCase();
            return dir * va.localeCompare(vb, "uz");
        }
        return dir * ((va || 0) - (vb || 0));
    });

    return rows;
}

function renderTable() {
    const rows = getFilteredSortedRows();
    const tbody = document.getElementById("statsTableBody");
    const emptyMsg = document.getElementById("statsEmptyMessage");

    document.querySelectorAll(".sort-arrow").forEach(el => el.textContent = "");
    const activeArrow = document.getElementById(`sortArrow-${statsSortKey}`);
    if (activeArrow) activeArrow.textContent = statsSortDir === "asc" ? "▲" : "▼";
    buildStatsFixedHeader();
    positionStatsFixedHeader();

    if (!rows.length) {
        tbody.innerHTML = "";
        emptyMsg.classList.remove("hidden");
        document.getElementById("statsVisibleCount").textContent = "";
        return;
    }
    emptyMsg.classList.add("hidden");

    document.getElementById("statsVisibleCount").textContent =
        rows.length === statsAllRows.length
            ? `${rows.length} ta foydalanuvchi`
            : `${rows.length} / ${statsAllRows.length} ta foydalanuvchi`;

    tbody.innerHTML = rows.map(r => `
        <tr>
            <td class="stats-sticky-col">${escapeHtml(r.username)}</td>
            <td>${escapeHtml(r.fullName)}</td>
            <td>${escapeHtml(r.groupName)}</td>
            <td class="stats-col-num">${r.sessionCount}</td>
            <td class="stats-col-num">${r.totalQuestions}</td>
            <td class="stats-col-num">${r.totalCorrect}</td>
            <td class="stats-col-num">${r.sessionCount > 0 ? r.avgPercent.toFixed(1) + "%" : "—"}</td>
            <td class="stats-col-num">${r.sessionCount > 0 ? r.bestPercent + "%" : "—"}</td>
            <td class="stats-col-num">${formatDuration(r.totalDurationSec)}</td>
            <td>${formatDate(r.lastSessionAt)}</td>
        </tr>
    `).join("");

    positionFixedScrollBar();
}

function formatDuration(totalSec) {
    if (!totalSec) return "—";
    const h = Math.floor(totalSec / 3600);
    const m = Math.floor((totalSec % 3600) / 60);
    if (h > 0) return `${h} soat ${m} daq`;
    if (m > 0) return `${m} daq`;
    return `${totalSec} son`;
}

function formatDate(isoString) {
    if (!isoString) return "—";
    const d = new Date(isoString);
    if (isNaN(d.getTime())) return "—";
    return d.toLocaleString("uz-UZ", { day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit" });
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text == null ? "" : String(text);
    return div.innerHTML;
}
