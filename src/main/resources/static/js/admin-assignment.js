const CURRENT_USER_ID = Number(document.body.dataset.userId);

let currentChatAssignment = null;
let chatModalInstance = null;

document.addEventListener("DOMContentLoaded", () => {

    void loadAssignments();

    const modalElement = document.getElementById("chatModal");
    chatModalInstance = new bootstrap.Modal(modalElement);

});

//--------------------------------------------------------
const chatModalEl = document.getElementById("chatModal");
const chatInput = document.getElementById("chatInput");

chatModalEl.addEventListener("shown.bs.modal", () => {
    chatInput.focus();

    //Чтобы курсор ставился в конец текста (если поле не пустое)
    chatInput.setSelectionRange(chatInput.value.length, chatInput.value.length);
});
//--------------------------------------------------------

async function loadAssignments() {

    const res = await fetch("/api/admin/assignments");
    const data = await res.json();

    const tbody = document.getElementById("assignmentTable");
    tbody.innerHTML = "";

    data.forEach(a => {

        tbody.innerHTML += `
            <tr>
                <td><input type="checkbox" class="bulkCheck" value="${a.id}"></td>
                <td>${a.id}</td>
                <td>${a.questionSetName}</td>
                <td>${a.groupName}</td>
                <td>${formatDate(a.assignedAt)}</td>
                <td>${formatDate(a.dueDate)}</td>
                <td>${a.totalStudents}</td>
                <td>${a.finished}/${a.totalStudents}</td>
                <td>${a.avgPercent.toFixed(1)}%</td>
                <td>
                    <button class="btn btn-sm btn-info me-1" onclick="openDetails(${a.id})">Batafsil...</button>
                    <button class="btn btn-sm btn-secondary" onclick="openChat(${a.id})">Chat</button>
                </td>
            </tr>
        `;
    });
}

function toggleAll(masterCheckbox) {

    const tbody = document.getElementById("assignmentTable");

    if (!tbody) return;

    const checkboxes = tbody.querySelectorAll("input[type='checkbox']");

    checkboxes.forEach(cb => {
        cb.checked = masterCheckbox.checked;
    });
}

async function openDetails(id) {

    const res = await fetch(`/api/admin/assignments/${id}/details`);
    const data = await res.json();

    const tbody = document.getElementById("detailTable");
    tbody.innerHTML = "";

    data.forEach(s => {

        tbody.innerHTML += `
            <tr>
                <td>${s.pupilId}</td>
                <td>${s.pupilName}</td>
                <td>${statusFormat(s.status)}</td>
                <td>${s.percent ?? 0}%</td>
                <td>${s.durationSec ?? 0}</td>
                <td>${formatDate(s.lastActivity)}</td>
            </tr>
        `;
    });

    new bootstrap.Modal(document.getElementById('detailModal')).show();
}
function statusFormat(status){
    if (status === "NEW") {return colorStatus("YANGI");}
    if (status === "IN_PROGRESS") {return colorStatus("BOSHLANGAN");}
    if (status === "FINISHED") {return colorStatus("TUGAGAN");}
}

function colorStatus(status) {

    switch (status) {

        case "YANGI":
            return `<span class="badge bg-secondary">YANGI</span>`;

        case "BOSHLANGAN":
            return `<span class="badge bg-warning text-dark">BOSHLANGAN</span>`;

        case "TUGAGAN":
            return `<span class="badge bg-success">TUGAGAN</span>`;

        case "KECHIKKAN":
            return `<span class="badge bg-danger">KECHIKKAN</span>`;

        default:
            return `<span class="badge bg-light text-dark">${status}</span>`;
    }
}
function formatDate(d) {
    if (!d) return "-";
    return new Date(d).toLocaleString();
}

function getSelectedIds() {
    return Array.from(document.querySelectorAll(".bulkCheck:checked"))
        .map(cb => cb.value);
}

async function bulkReassign() {

    const ids = getSelectedIds();

    if (ids.length === 0) {
        showAlertModal("Avval topshiriqni belgilang!!!");
        return;
    }

    await fetch("/api/admin/assignments/bulk-reassign", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(ids)
    });

    void loadAssignments();
}

async function bulkExtend() {

    const ids = getSelectedIds();

    if (ids.length === 0) {
        showAlertModal("Avval topshiriqni belgilang!!!");
        return;
    }

    if (ids.length > 1) {
        showAlertModal("Bir vaqtda bittadan ortiq topshiriqni muddatini o'zgartira olmaysiz.");
        return;
    }

    const newDate = await showPromptModal("New due date (YYYY-MM-DD HH:mm)");
    if (!newDate) return;

    await fetch("/api/admin/assignments/bulk-extend", {
        method: "PUT",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
            ids: ids,
            dueDate: newDate
        })
    });

    void loadAssignments();
}

async function openChat(id) {

    console.log("CURRENT_USER_ID: ", CURRENT_USER_ID);

    currentChatAssignment = id;

    const container = document.getElementById("chatContainer");
    if (!container) {
        console.error("chatContainer not found in DOM");
        return;
    }

    const res = await fetch(`/api/admin/assignments/${id}/chat`);
    const data = await res.json();

    const wasAtBottom = isUserNearBottom(container);

    container.innerHTML = "";

    data.forEach(msg => {

        const isMine = Number(msg.senderId) === CURRENT_USER_ID;
        const senderName = isMine ? "Men" : msg.senderName;
        const roleLabel =
            msg.role === "ROLE_ADMIN" ? "O'qituvchi" : "O'quvchi";

        container.innerHTML += `
            <div class="chat-row ${isMine ? "mine" : "other"}">
                <div class="chat-bubble">
                    <div class="chat-header">
                        <span class="chat-name" style="margin-right: 25px; font-weight: 600;">${senderName}</span>
                        <span class="chat-role">${roleLabel}</span>
                    </div>
                        <hr>
                    <div class="chat-text">${msg.message}</div>
                    <div class="chat-time">
                        ${formatDate(msg.createdAt)}
                    </div>
                </div>
            </div>
        `;
    });

    if (wasAtBottom) {
        scrollToBottom(container);
    }

    container.scrollTop = container.scrollHeight;

    chatModalInstance.show();

    setTimeout(() => {
        document.getElementById("chatInput")?.focus();
    }, 200);
}
async function sendMessage() {

    const input = document.getElementById("chatInput");
    const text = input.value.trim();

    if (!text) return; // пустые сообщения не отправляем

    await fetch(`/api/admin/assignments/${currentChatAssignment}/chat`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({text})
    });

    input.value = "";              // 🔥 очищаем поле
    input.style.height = "auto";   // 🔥 сброс высоты после отправки
    input.focus();                 // удобно — курсор обратно в поле

    await openChat(currentChatAssignment);

    const container = document.getElementById("chatContainer");
    scrollToBottom(container);
}

//-----------------------------------------------------------------------
let sending = false;

document.getElementById("chatInput")
    .addEventListener("keydown", async function (e) {

        if (e.key === "Enter" && e.shiftKey) return;

        if (e.key === "Enter") {
            e.preventDefault();

            if (sending) return;

            sending = true;
            try {
                await sendMessage();
            } finally {
                sending = false;
            }
        }
    });

//auto-grow как Telegram
chatInput.addEventListener("input", function () {
    this.style.height = "auto";
    this.style.height = this.scrollHeight + "px";
});
//-----------------------------------------------------------------------

async function deleteAssignment() {

    const ids = getSelectedIds();

    if (ids.length === 0) {
        showAlertModal("Avval topshiriqni tanlang!!!");
        return;
    }

    if (!await showConfirmModal(`Haqiqatan ham ${ids.length} ta topshiriqni o‘chirmoqchimisiz?`, { danger: true }))
        return;

    try {

        const res = await fetch("/api/admin/assignments/bulk-delete", {
            method: "DELETE",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(ids)
        });

        if (!res.ok) {
            const text = await res.text();
            throw new Error(text || "Topshiriqni o'chirishda xatolik yuz berdi.");
        }

        void loadAssignments();

    } catch (e) {

        console.error(e);
        showAlertModal("O‘chirishda xatolik yuz berdi");
    }
}

function toggleChatFullscreen() {
    const modalDialog = document.querySelector("#chatModal .modal-dialog");
    const btn = document.getElementById("chatFullscreenBtn");

    modalDialog.classList.toggle("modal-fullscreen");

    if (modalDialog.classList.contains("modal-fullscreen")) {
        btn.textContent = "🗗"; // уменьшить
    } else {
        btn.textContent = "🔲"; // во весь экран
    }
}

function isUserNearBottom(container) {
    const threshold = 80; // px
    return container.scrollHeight - container.scrollTop - container.clientHeight < threshold;
}

function scrollToBottom(container) {
    container.scrollTop = container.scrollHeight;
}