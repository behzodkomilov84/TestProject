// "/teacher/assign" — guruh va o'quvchilarni tanlab, savollar to'plami +
// muddat bilan topshiriq berish. O'quvchilar ro'yxati endi jadval EMAS,
// to'liq kenglikdagi karta ro'yxati (mobilda ham qulay).

document.addEventListener("DOMContentLoaded", () => {
    loadGroupSelect();
    loadSets();

    document.getElementById("groupSelect").addEventListener("change", e => {
        loadGroupStudents(e.target.value || null);
    });

    document.getElementById("setSelect").addEventListener("change", updateAssignButtonState);
    document.getElementById("dueDate").addEventListener("input", updateAssignButtonState);

    document.getElementById("selectAllStudents").addEventListener("change", e => {
        // ":not(:disabled)" — PENDING (hali qabul qilmagan) o'quvchilar
        // "Hammasini belgilash" bilan ham belgilanib qolmasin.
        document.querySelectorAll(".student-checkbox:not(:disabled)").forEach(cb => cb.checked = e.target.checked);
        updateAssignButtonState();
    });

    document.addEventListener("change", e => {
        if (e.target.classList.contains("student-checkbox")) updateAssignButtonState();
    });
});

function escapeHtml(s) {
    const div = document.createElement("div");
    div.textContent = s == null ? "" : String(s);
    return div.innerHTML;
}

async function loadGroupSelect() {
    try {
        const res = await fetch("/api/teacher/groups/select");
        if (!res.ok) throw new Error();
        const groups = await res.json();

        const select = document.getElementById("groupSelect");
        select.innerHTML = `<option value="">--Guruhni tanlang--</option>` +
            groups.map(g => `<option value="${g.id}">${escapeHtml(g.name)}</option>`).join("");
    } catch (err) {
        console.error("Guruhlarni yuklashda xatolik:", err);
    }
}

function loadSets() {
    fetch("/api/teacher/questionsets")
        .then(r => r.json())
        .then(list => {
            const select = document.getElementById("setSelect");
            select.innerHTML = `<option value="">--Savollar to'plamini tanlang--</option>` +
                list.map(s => `<option value="${s.id}">${escapeHtml(s.name)} (${s.questionIds.length} ta)</option>`).join("");
        })
        .catch(err => console.error("To'plamlarni yuklashda xatolik:", err));
}

async function loadGroupStudents(groupId) {
    const section = document.getElementById("studentsSection");
    const list = document.getElementById("studentsList");

    if (!groupId) {
        section.style.display = "none";
        list.innerHTML = "";
        updateAssignButtonState();
        return;
    }

    section.style.display = "block";
    document.getElementById("selectAllStudents").checked = false;

    try {
        const res = await fetch(`/api/teacher/group/${groupId}/students`);
        if (!res.ok) throw new Error();
        const students = await res.json();

        if (!students.length) {
            list.innerHTML = `<div class="teacher-empty">Bu guruhga a'zo o'quvchi yo'q</div>`;
            updateAssignButtonState();
            return;
        }

        // FAQAT taklifni QABUL QILGAN (ACCEPTED) o'quvchilarga topshiriq
        // berish mumkin — backend shuni tekshiradi (TeacherGroup.pupils —
        // faqat qabul qilinganda GroupMember yaratiladi). Avval PENDING
        // o'quvchi ham belgilanardi, "Topshiriq berish" bosilgandagina
        // (oxirida) inglizcha "Some students not in this group" xatosi
        // chiqardi — endi PENDING o'quvchi checkbox'i boshidanoq
        // belgilanmaydigan (disabled), tushunarli izoh bilan.
        list.innerHTML = students.map(s => {
            const isAccepted = s.status === "ACCEPTED";
            return `
            <label class="teacher-student-card ${isAccepted ? "" : "teacher-student-disabled"}"
                   title="${isAccepted ? "" : "Bu o'quvchi hali taklifni qabul qilmagan — topshiriq berib bo'lmaydi"}">
                <input type="checkbox" class="student-checkbox" data-id="${s.pupilId}" ${isAccepted ? "" : "disabled"}>
                <span class="teacher-student-name">${escapeHtml(s.username)}</span>
                <span class="teacher-member-status ${isAccepted ? "accepted" : "pending"}">${s.status}</span>
            </label>`;
        }).join("");
        updateAssignButtonState();
    } catch (err) {
        console.error("O'quvchilarni yuklashda xatolik:", err);
        list.innerHTML = `<div class="teacher-empty">Yuklashda xatolik</div>`;
    }
}

function updateAssignButtonState() {
    const assignBtn = document.getElementById("assignBtn");
    const studentsOk = Array.from(document.querySelectorAll(".student-checkbox")).some(cb => cb.checked);
    const setOk = document.getElementById("setSelect").value !== "";
    const dateOk = document.getElementById("dueDate").value !== "";
    assignBtn.disabled = !(studentsOk && setOk && dateOk);
}

async function assignTest() {
    try {
        const groupId = Number(document.getElementById("groupSelect").value);
        if (!groupId) return alert("Guruhni tanlang");

        const setId = Number(document.getElementById("setSelect").value);
        if (!setId) return alert("Savollar to'plamini tanlang");

        const dueDateInput = document.getElementById("dueDate").value;
        const dueDate = dueDateInput ? new Date(dueDateInput).toISOString() : null;

        const studentIds = Array.from(document.querySelectorAll(".student-checkbox:checked"))
            .map(cb => Number(cb.dataset.id))
            .filter(Boolean);

        if (studentIds.length === 0) return alert("Kamida bitta o'quvchini tanlang");

        const res = await fetch("/api/teacher/assign", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ groupId, setId, dueDate, studentIds })
        });

        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            throw new Error(data.error || "Topshiriq berishda xatolik");
        }

        alert("Test topshirig'i muvaffaqiyatli jo'natildi!");
        document.querySelectorAll(".student-checkbox:checked").forEach(cb => cb.checked = false);
        document.getElementById("selectAllStudents").checked = false;
        updateAssignButtonState();
    } catch (err) {
        console.error(err);
        alert("Topshiriq berishda xatolik: " + (err.message || err));
    }
}
