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
    } catch (err) {
        console.error(err);
        document.getElementById("statsLoadingMessage").textContent = "❌ Tarmoq xatoligi.";
    }
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
            <td>${escapeHtml(r.username)}</td>
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
