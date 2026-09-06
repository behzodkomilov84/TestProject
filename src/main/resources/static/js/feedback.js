// "💬 Fikr va takliflar" — sayt bo'yicha (global) ochiq fikr-taklif
// taxtasi (foydalanuvchi so'rovi, 2026-09-06). courseForum.js bilan bir
// xil andoza (accordion-javoblar), farqi — courseId yo'q va har bir
// yozuvda status chip/select bor. Kirish huquqi BUTUNLAY API darajasida
// (FeedbackService) tekshiriladi.

const CURRENT_USER_ID = Number(document.body.dataset.userId);

// OWNER yoki ADMIN — BOSHQA foydalanuvchilarning yozuvlarini ham o'chira
// oladi va statusini o'zgartira oladi (moderatsiya), backend
// FeedbackThreadsResponseDto#canManage orqali keladi (feedback.js o'zi
// hisoblamaydi — xavfsizlik server tomonda).
let canManage = false;

const STATUS_LABELS = {
    OPEN: "🆕 Yangi",
    IN_PROGRESS: "🔧 Ko'rib chiqilmoqda",
    DONE: "✅ Amalga oshirildi",
    REJECTED: "❌ Rad etildi"
};

document.addEventListener("DOMContentLoaded", () => {
    loadThreads();

    document.getElementById("fbNewThreadBtn").addEventListener("click", submitNewThread);
});

async function loadThreads() {
    const container = document.getElementById("fbThreadsList");

    try {
        const res = await fetch(`/api/feedback/threads`);
        if (!res.ok) throw new Error("Server xatoligi: " + res.status);

        const data = await res.json();
        canManage = data.canManage;
        renderThreads(data.threads);
    } catch (e) {
        console.error(e);
        container.innerHTML = `<p class="fb-error">❌ Fikr-takliflarni yuklashda xatolik yuz berdi.</p>`;
    }
}

function renderThreads(threads) {
    const container = document.getElementById("fbThreadsList");

    if (threads.length === 0) {
        container.innerHTML = `<p class="fb-empty">Hali fikr-takliflar yo'q. Birinchi bo'lib yozing!</p>`;
        return;
    }

    container.innerHTML = threads.map(t => buildThreadCard(t)).join("");
}

function buildThreadCard(t) {
    const canDelete = canManage || t.authorId === CURRENT_USER_ID;
    const statusClass = `fb-status-${t.status.toLowerCase().replace(/_/g, "-")}`;

    const statusEl = canManage
        ? `<select class="fb-status-select ${statusClass}" onchange="updateStatus(${t.id}, this.value)">
                ${Object.entries(STATUS_LABELS).map(([value, label]) =>
                    `<option value="${value}" ${value === t.status ? "selected" : ""}>${label}</option>`).join("")}
           </select>`
        : `<span class="fb-status-chip ${statusClass}">${STATUS_LABELS[t.status] ?? t.status}</span>`;

    return `
        <div class="fb-thread" id="fbThread-${t.id}">
            <div class="fb-thread-header">
                <div class="fb-author-row">
                    <span class="fb-author-name">${escapeHtml(t.authorName)}</span>
                    ${t.authorIsStaff ? `<span class="fb-badge">🛡️ Jamoa</span>` : ""}
                    <span class="fb-date">${formatDate(t.createdAt)}</span>
                </div>
                ${canDelete ? `<button type="button" class="fb-delete-btn" onclick="deleteThread(${t.id})" title="O'chirish">🗑️</button>` : ""}
            </div>
            <p class="fb-thread-text">${escapeHtml(t.feedbackText)}</p>
            <div class="fb-thread-footer">
                ${statusEl}
                <button type="button" class="fb-toggle-replies-btn" onclick="toggleReplies(${t.id})">
                    💬 <span id="fbReplyCount-${t.id}">${t.replyCount}</span> ta javob
                    <span class="fb-chevron" id="fbChevron-${t.id}">▶</span>
                </button>
            </div>
            <div class="fb-replies-body hidden" id="fbReplies-${t.id}"></div>
        </div>
    `;
}

async function toggleReplies(threadId) {
    const body = document.getElementById(`fbReplies-${threadId}`);
    const chevron = document.getElementById(`fbChevron-${threadId}`);
    const isHidden = body.classList.contains("hidden");

    if (isHidden) {
        body.classList.remove("hidden");
        chevron.textContent = "▼";
        if (!body.dataset.loaded) {
            await loadReplies(threadId);
        }
    } else {
        body.classList.add("hidden");
        chevron.textContent = "▶";
    }
}

async function loadReplies(threadId) {
    const body = document.getElementById(`fbReplies-${threadId}`);
    body.innerHTML = `<p class="fb-loading">⏳ Yuklanmoqda...</p>`;

    try {
        const res = await fetch(`/api/feedback/threads/${threadId}/replies`);
        if (!res.ok) throw new Error("Server xatoligi");

        const replies = await res.json();
        body.dataset.loaded = "1";
        renderReplies(threadId, replies);
    } catch (e) {
        console.error(e);
        body.innerHTML = `<p class="fb-error">❌ Javoblarni yuklashda xatolik yuz berdi.</p>`;
    }
}

function renderReplies(threadId, replies) {
    const body = document.getElementById(`fbReplies-${threadId}`);

    const list = replies.length === 0
        ? `<p class="fb-empty-replies">Hali javob yo'q — birinchi bo'lib javob bering.</p>`
        : replies.map(r => buildReplyRow(threadId, r)).join("");

    body.innerHTML = `
        ${list}
        <div class="fb-reply-form">
            <textarea id="fbReplyInput-${threadId}" class="fb-reply-input" rows="2" placeholder="Javob yozing..."></textarea>
            <button type="button" class="fb-reply-submit-btn" onclick="submitReply(${threadId})">Yuborish</button>
        </div>
    `;
}

function buildReplyRow(threadId, r) {
    const canDelete = canManage || r.authorId === CURRENT_USER_ID;

    return `
        <div class="fb-reply" id="fbReply-${r.id}">
            <div class="fb-author-row">
                <span class="fb-author-name">${escapeHtml(r.authorName)}</span>
                ${r.authorIsStaff ? `<span class="fb-badge">🛡️ Jamoa</span>` : ""}
                <span class="fb-date">${formatDate(r.createdAt)}</span>
                ${canDelete ? `<button type="button" class="fb-delete-btn" onclick="deleteReply(${threadId}, ${r.id})" title="O'chirish">🗑️</button>` : ""}
            </div>
            <p class="fb-reply-text">${escapeHtml(r.replyText)}</p>
        </div>
    `;
}

async function submitNewThread() {
    const input = document.getElementById("fbNewThreadInput");
    const text = input.value.trim();
    if (!text) return;

    const btn = document.getElementById("fbNewThreadBtn");
    btn.disabled = true;

    try {
        const res = await fetch(`/api/feedback/threads`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({feedbackText: text})
        });

        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }

        input.value = "";
        await loadThreads();
    } catch (e) {
        console.error(e);
        showAlertModal("Tarmoq xatoligi");
    } finally {
        btn.disabled = false;
    }
}

async function submitReply(threadId) {
    const input = document.getElementById(`fbReplyInput-${threadId}`);
    const text = input.value.trim();
    if (!text) return;

    try {
        const res = await fetch(`/api/feedback/threads/${threadId}/replies`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({replyText: text})
        });

        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }

        input.value = "";
        await loadReplies(threadId);

        const countEl = document.getElementById(`fbReplyCount-${threadId}`);
        if (countEl) countEl.textContent = String(Number(countEl.textContent) + 1);
    } catch (e) {
        console.error(e);
        showAlertModal("Tarmoq xatoligi");
    }
}

async function updateStatus(threadId, status) {
    try {
        const res = await fetch(`/api/feedback/threads/${threadId}/status`, {
            method: "PATCH",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({status})
        });

        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Xatolik yuz berdi");
            await loadThreads();
            return;
        }

        const select = document.querySelector(`#fbThread-${threadId} .fb-status-select`);
        if (select) {
            select.className = `fb-status-select fb-status-${status.toLowerCase().replace(/_/g, "-")}`;
        }
    } catch (e) {
        console.error(e);
        showAlertModal("Tarmoq xatoligi");
    }
}

async function deleteThread(threadId) {
    if (!await showConfirmModal("Bu fikr-taklifni (va uning barcha javoblarini) o'chirmoqchimisiz?", {danger: true})) return;

    try {
        const res = await fetch(`/api/feedback/threads/${threadId}`, {method: "DELETE"});
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }
        await loadThreads();
    } catch (e) {
        console.error(e);
        showAlertModal("Tarmoq xatoligi");
    }
}

async function deleteReply(threadId, replyId) {
    if (!await showConfirmModal("Bu javobni o'chirmoqchimisiz?", {danger: true})) return;

    try {
        const res = await fetch(`/api/feedback/threads/${threadId}/replies/${replyId}`, {method: "DELETE"});
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }
        await loadReplies(threadId);

        const countEl = document.getElementById(`fbReplyCount-${threadId}`);
        if (countEl) countEl.textContent = String(Math.max(0, Number(countEl.textContent) - 1));
    } catch (e) {
        console.error(e);
        showAlertModal("Tarmoq xatoligi");
    }
}

function formatDate(iso) {
    const d = new Date(iso);
    return d.toLocaleString("uz-UZ", {day: "2-digit", month: "2-digit", year: "numeric", hour: "2-digit", minute: "2-digit"});
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text ?? "";
    return div.innerHTML;
}
