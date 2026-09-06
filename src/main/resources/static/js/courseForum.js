// "💬 Forum" — kurs foydalanuvchisi kurs muallifiga (yoki umuman kursga)
// savol berishi, XOHLAGAN boshqa foydalanuvchi esa shu savolga javob
// yozishi mumkin bo'lgan sahifa (foydalanuvchi so'rovi, 2026-09-06).
// Kirish huquqi BUTUNLAY API darajasida (CourseForumService
// #requireViewableCourse) tekshiriladi.

const CURRENT_USER_ID = Number(document.body.dataset.userId);

// OWNER yoki shu kursni yaratgan ADMIN — BOSHQA foydalanuvchilarning
// yozuvlarini ham o'chira oladi (moderatsiya), backend
// ForumThreadsResponseDto#canManage orqali keladi (courseForum.js
// o'zi hisoblamaydi — xavfsizlik server tomonda).
let canManage = false;

document.addEventListener("DOMContentLoaded", () => {
    loadThreads();

    document.getElementById("cfNewQuestionBtn").addEventListener("click", submitNewThread);
});

async function loadThreads() {
    const container = document.getElementById("cfThreadsList");

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/forum/threads`);

        if (res.status === 404) {
            container.innerHTML = `<p class="cf-error">❌ Kurs topilmadi.</p>`;
            return;
        }
        if (!res.ok) throw new Error("Server xatoligi: " + res.status);

        const data = await res.json();
        canManage = data.canManage;
        renderThreads(data.threads);
    } catch (e) {
        console.error(e);
        container.innerHTML = `<p class="cf-error">❌ Savollarni yuklashda xatolik yuz berdi.</p>`;
    }
}

function renderThreads(threads) {
    const container = document.getElementById("cfThreadsList");

    if (threads.length === 0) {
        container.innerHTML = `<p class="cf-empty">Hali savollar yo'q. Birinchi bo'lib savol bering!</p>`;
        return;
    }

    container.innerHTML = threads.map(t => buildThreadCard(t)).join("");
}

function buildThreadCard(t) {
    const canDelete = canManage || t.authorId === CURRENT_USER_ID;

    return `
        <div class="cf-thread" id="cfThread-${t.id}">
            <div class="cf-thread-header">
                <div class="cf-author-row">
                    <span class="cf-author-name">${escapeHtml(t.authorName)}</span>
                    ${t.authorIsCreator ? `<span class="cf-badge">🎓 Muallif</span>` : ""}
                    <span class="cf-date">${formatDate(t.createdAt)}</span>
                </div>
                ${canDelete ? `<button type="button" class="cf-delete-btn" onclick="deleteThread(${t.id})" title="O'chirish">🗑️</button>` : ""}
            </div>
            <p class="cf-question-text">${escapeHtml(t.questionText)}</p>
            <button type="button" class="cf-toggle-replies-btn" onclick="toggleReplies(${t.id})">
                💬 <span id="cfReplyCount-${t.id}">${t.replyCount}</span> ta javob
                <span class="cf-chevron" id="cfChevron-${t.id}">▶</span>
            </button>
            <div class="cf-replies-body hidden" id="cfReplies-${t.id}"></div>
        </div>
    `;
}

async function toggleReplies(threadId) {
    const body = document.getElementById(`cfReplies-${threadId}`);
    const chevron = document.getElementById(`cfChevron-${threadId}`);
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
    const body = document.getElementById(`cfReplies-${threadId}`);
    body.innerHTML = `<p class="cf-loading">⏳ Yuklanmoqda...</p>`;

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/forum/threads/${threadId}/replies`);
        if (!res.ok) throw new Error("Server xatoligi");

        const replies = await res.json();
        body.dataset.loaded = "1";
        renderReplies(threadId, replies);
    } catch (e) {
        console.error(e);
        body.innerHTML = `<p class="cf-error">❌ Javoblarni yuklashda xatolik yuz berdi.</p>`;
    }
}

function renderReplies(threadId, replies) {
    const body = document.getElementById(`cfReplies-${threadId}`);

    const list = replies.length === 0
        ? `<p class="cf-empty-replies">Hali javob yo'q — birinchi bo'lib javob bering.</p>`
        : replies.map(r => buildReplyRow(threadId, r)).join("");

    body.innerHTML = `
        ${list}
        <div class="cf-reply-form">
            <textarea id="cfReplyInput-${threadId}" class="cf-reply-input" rows="2" placeholder="Javob yozing..."></textarea>
            <button type="button" class="cf-reply-submit-btn" onclick="submitReply(${threadId})">Yuborish</button>
        </div>
    `;
}

function buildReplyRow(threadId, r) {
    const canDelete = canManage || r.authorId === CURRENT_USER_ID;

    return `
        <div class="cf-reply" id="cfReply-${r.id}">
            <div class="cf-author-row">
                <span class="cf-author-name">${escapeHtml(r.authorName)}</span>
                ${r.authorIsCreator ? `<span class="cf-badge">🎓 Muallif</span>` : ""}
                <span class="cf-date">${formatDate(r.createdAt)}</span>
                ${canDelete ? `<button type="button" class="cf-delete-btn" onclick="deleteReply(${threadId}, ${r.id})" title="O'chirish">🗑️</button>` : ""}
            </div>
            <p class="cf-reply-text">${escapeHtml(r.replyText)}</p>
        </div>
    `;
}

async function submitNewThread() {
    const input = document.getElementById("cfNewQuestionInput");
    const text = input.value.trim();
    if (!text) return;

    const btn = document.getElementById("cfNewQuestionBtn");
    btn.disabled = true;

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/forum/threads`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({questionText: text})
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
    const input = document.getElementById(`cfReplyInput-${threadId}`);
    const text = input.value.trim();
    if (!text) return;

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/forum/threads/${threadId}/replies`, {
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

        const countEl = document.getElementById(`cfReplyCount-${threadId}`);
        if (countEl) countEl.textContent = String(Number(countEl.textContent) + 1);
    } catch (e) {
        console.error(e);
        showAlertModal("Tarmoq xatoligi");
    }
}

async function deleteThread(threadId) {
    if (!await showConfirmModal("Bu savolni (va uning barcha javoblarini) o'chirmoqchimisiz?", {danger: true})) return;

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/forum/threads/${threadId}`, {method: "DELETE"});
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
        const res = await fetch(`/api/courses/${COURSE_ID}/forum/threads/${threadId}/replies/${replyId}`, {method: "DELETE"});
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }
        await loadReplies(threadId);

        const countEl = document.getElementById(`cfReplyCount-${threadId}`);
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
