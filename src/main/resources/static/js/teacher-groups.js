// "/teacher/groups" — guruhlarni yaratish/tahrirlash/o'chirish, a'zolarni
// ko'rish (accordion — bosilganda ochiladi) va taklif qilish. Avvalgi
// bitta "/teacher" sahifasining chap sidebar qismi shu yerga ko'chirildi,
// endi to'liq kenglikda, alohida sahifa sifatida.

let currentGroupId = null;
// Qaysi guruh a'zolari hozir ochiq (accordion) — key: groupId (string).
const expandedGroups = new Set();

document.addEventListener("DOMContentLoaded", () => {
    loadGroups();
});

//=======================================================================
//              GURUHLAR RO'YXATI
//=======================================================================
function loadGroups() {
    fetch("/api/teacher/get-groups")
        .then(r => r.json())
        .then(list => renderGroups(list))
        .catch(err => console.error(err));
}

function renderGroups(list) {
    const container = document.getElementById("groupList");

    if (!list.length) {
        container.innerHTML = `<div class="teacher-empty">Hali guruh yo'q — yuqoridagi maydondan yarating.</div>`;
        return;
    }

    container.innerHTML = list.map(g => {
        const id = g.teacherGroupId;
        const isExpanded = expandedGroups.has(String(id));
        return `
        <div class="teacher-group-card ${isExpanded ? "expanded" : ""}" data-group-id="${id}">
            <div class="teacher-group-header" onclick="toggleGroupMembers(${id})">
                <span class="teacher-group-chevron">▸</span>
                <span class="teacher-group-name" data-id="${id}">${escapeHtml(g.groupName)}</span>
                <div class="teacher-group-actions" onclick="event.stopPropagation()">
                    <button class="teacher-icon-btn" onclick="startInlineEdit(${id})" title="Nomini tahrirlash">✏️</button>
                    <button class="teacher-icon-btn" onclick="openAddStudentModal(${id})" title="O'quvchi taklif qilish">➕</button>
                    <button class="teacher-icon-btn teacher-btn-danger" onclick="deleteGroup(${id})" title="O'chirish">🗑</button>
                </div>
            </div>
            <div class="teacher-group-body" id="members-${id}">
                <div class="teacher-empty">Yuklanmoqda...</div>
            </div>
        </div>`;
    }).join("");
}

function escapeHtml(s) {
    const div = document.createElement("div");
    div.textContent = s == null ? "" : String(s);
    return div.innerHTML;
}

// Guruh sarlavhasiga bosilganda a'zolar ro'yxati ochiladi/yopiladi —
// avval alohida o'ng sidebar'da ko'rinardi, endi shu yerning o'zida.
function toggleGroupMembers(groupId) {
    const key = String(groupId);
    const card = document.querySelector(`.teacher-group-card[data-group-id="${groupId}"]`);
    if (!card) return;

    if (expandedGroups.has(key)) {
        expandedGroups.delete(key);
        card.classList.remove("expanded");
        return;
    }

    expandedGroups.add(key);
    card.classList.add("expanded");
    loadGroupMembers(groupId);
}

async function loadGroupMembers(groupId) {
    const body = document.getElementById(`members-${groupId}`);
    if (!body) return;

    try {
        const res = await fetch(`/api/teacher/group/${groupId}/students`);
        if (!res.ok) throw new Error("Yuklashda xatolik");
        const list = await res.json();

        if (!list.length) {
            body.innerHTML = `<div class="teacher-empty">Bu guruhga a'zo o'quvchi yo'q</div>`;
            return;
        }

        body.innerHTML = list.map(s => `
            <div class="teacher-member-row">
                <span>${escapeHtml(s.username)}</span>
                <span class="teacher-member-status ${s.status === "ACCEPTED" ? "accepted" : "pending"}">${s.status}</span>
            </div>
        `).join("");
    } catch (err) {
        console.error(err);
        body.innerHTML = `<div class="teacher-empty">Yuklashda xatolik</div>`;
    }
}

function createGroup() {
    const nameInput = document.getElementById("groupName");
    const name = nameInput.value.trim();
    if (!name) return alert("Guruh nomini kiriting");

    fetch("/api/teacher/create-group", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name })
    })
        .then(res => {
            if (!res.ok) throw new Error("Guruh yaratishda xatolik");
            loadGroups();
            nameInput.value = "";
        })
        .catch(err => {
            console.error(err);
            alert("Guruh yaratishda xatolik yuz berdi.");
        });
}

function deleteGroup(id) {
    if (!confirm("Bu guruhni o'chirmoqchimisiz?")) return;
    fetch(`/api/teacher/groups/${id}`, { method: "DELETE" })
        .then(() => loadGroups());
}

//=======================================================================
//              GURUHNI TAHRIRLASH (nomini o'zgartirish)
//=======================================================================
function startInlineEdit(groupId) {
    const span = document.querySelector(`.teacher-group-name[data-id="${groupId}"]`);
    if (!span) return;

    const oldValue = span.innerText;
    const input = document.createElement("input");
    input.type = "text";
    input.value = oldValue;
    input.dataset.saved = "false";
    input.onclick = e => e.stopPropagation();

    span.replaceWith(input);
    input.focus();
    input.select();

    input.addEventListener("keydown", e => {
        if (e.key === "Enter") {
            e.preventDefault();
            saveInlineEdit(groupId, input, oldValue);
        }
        if (e.key === "Escape") {
            cancelInlineEdit(input, oldValue, groupId);
        }
    });
    input.addEventListener("blur", () => cancelInlineEdit(input, oldValue, groupId));
}

function saveInlineEdit(groupId, input, oldValue) {
    if (input.dataset.saved === "true") return;
    input.dataset.saved = "true";

    const newName = input.value.trim();
    if (!newName || newName === oldValue) {
        replaceWithSpan(input, groupId, oldValue);
        return;
    }

    fetch(`/api/teacher/groups/${groupId}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: newName })
    })
        .then(r => {
            if (!r.ok) throw new Error();
            replaceWithSpan(input, groupId, newName);
        })
        .catch(() => {
            alert("Saqlashda xatolik");
            replaceWithSpan(input, groupId, oldValue);
        });
}

function cancelInlineEdit(input, value, groupId) {
    if (input.dataset.saved === "true") return;
    input.dataset.saved = "true";
    replaceWithSpan(input, groupId, value);
}

function replaceWithSpan(input, groupId, text) {
    const span = document.createElement("span");
    span.className = "teacher-group-name";
    span.dataset.id = groupId;
    span.innerText = text;
    input.replaceWith(span);
}

//=======================================================================
//              O'QUVCHI TAKLIF QILISH
//=======================================================================
function openAddStudentModal(groupId) {
    currentGroupId = groupId;

    fetch("/api/teacher/group/students")
        .then(r => {
            if (!r.ok) throw new Error("Forbidden or server error");
            return r.json();
        })
        .then(list => {
            const table = document.getElementById("inviteTable");
            table.innerHTML = list.map(u => `
                <tr>
                    <td>${escapeHtml(u.username)}</td>
                    <td><button class="btn btn-sm btn-primary" onclick="inviteStudent(${u.id})">Taklif qilish</button></td>
                </tr>
            `).join("");

            new bootstrap.Modal(document.getElementById("inviteModal")).show();
        })
        .catch(err => console.error("O'quvchilarni yuklashda xatolik:", err));
}

function inviteStudent(pupilId) {
    fetch(`/api/teacher/group/${currentGroupId}/invite`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ pupilId })
    })
        .then(r => {
            if (!r.ok) throw new Error("Taklifda xatolik");
            if (expandedGroups.has(String(currentGroupId))) {
                loadGroupMembers(currentGroupId);
            }
            alert("Taklif yuborildi");
        })
        .catch(() => alert("Taklifda xatolik"));
}
