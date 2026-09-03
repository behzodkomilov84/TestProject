// "/teacher/all-sets" — FAQAT ROLE_OWNER. Har bir o'qituvchi/admin
// yaratgan savollar to'plamini, o'qituvchi bo'yicha guruhlab, bitta
// ro'yxatda ko'rsatadi (backend ham ROLE_OWNER'dan boshqasini 403 bilan
// rad etadi — TeacherController.getAllQuestionSetsForOwner).

document.addEventListener("DOMContentLoaded", () => {
    loadAllSets();
});

function escapeHtml(s) {
    const div = document.createElement("div");
    div.textContent = s == null ? "" : String(s);
    return div.innerHTML;
}

async function loadAllSets() {
    const container = document.getElementById("setsList");
    try {
        const res = await fetch("/api/teacher/questionsets/all");
        if (res.status === 403) {
            container.innerHTML = `<div class="teacher-empty">⛔ Bu sahifa faqat OWNER uchun.</div>`;
            return;
        }
        if (!res.ok) throw new Error();

        const list = await res.json();
        renderGroupedByTeacher(list);
    } catch (err) {
        console.error("Yuklashda xatolik:", err);
        container.innerHTML = `<div class="teacher-empty">Yuklashda xatolik</div>`;
    }
}

function renderGroupedByTeacher(list) {
    const container = document.getElementById("setsList");
    if (!list.length) {
        container.innerHTML = `<div class="teacher-empty">Hali hech kim to'plam yaratmagan</div>`;
        return;
    }

    // Backend allaqachon o'qituvchi bo'yicha saralab beradi — shu tartibda
    // ketma-ket kelgan qatorlarni guruhlaymiz (qayta saralash shart emas).
    const groups = [];
    let current = null;
    list.forEach(s => {
        if (!current || current.teacherUsername !== s.teacherUsername) {
            current = { teacherUsername: s.teacherUsername, sets: [] };
            groups.push(current);
        }
        current.sets.push(s);
    });

    container.innerHTML = groups.map(g => {
        const rows = g.sets.map(s => `
            <div class="teacher-set-row">
                <span class="teacher-set-name">${escapeHtml(s.name)}</span>
                <span class="teacher-set-count">${s.questionCount} ta savol</span>
            </div>
        `).join("");

        return `
        <div class="teacher-group-card expanded">
            <div class="teacher-group-header" style="cursor:default;">
                <span class="teacher-group-name">👤 ${escapeHtml(g.teacherUsername)}</span>
                <span class="teacher-set-count">(${g.sets.length} ta to'plam)</span>
            </div>
            <div class="teacher-group-body" style="display:block;">${rows}</div>
        </div>`;
    }).join("");
}
