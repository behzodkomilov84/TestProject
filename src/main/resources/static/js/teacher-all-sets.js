// "/teacher/all-sets" — FAQAT ROLE_OWNER. Har bir o'qituvchi/admin
// yaratgan savollar to'plamini, o'qituvchi bo'yicha guruhlab, bitta
// ro'yxatda ko'rsatadi (backend ham ROLE_OWNER'dan boshqasini 403 bilan
// rad etadi — TeacherController.getAllQuestionSetsForOwner). Har bir
// to'plam — kim/qachon yaratgani bilan birga, bosilganda ICHIDAGI
// savollar (matni bilan) ochiladi (TeacherController.getQuestionSetDetail
// — OWNER uchun bu yerda "faqat egasi" cheklovi yo'q).

document.addEventListener("DOMContentLoaded", () => {
    loadAllSets();
});

function escapeHtml(s) {
    const div = document.createElement("div");
    div.textContent = s == null ? "" : String(s);
    return div.innerHTML;
}

function formatDate(iso) {
    if (!iso) return "";
    const d = new Date(iso);
    if (isNaN(d.getTime())) return "";
    return d.toLocaleString("uz-UZ", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
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
            <div class="teacher-group-card" data-set-id="${s.id}" style="margin-bottom:8px;">
                <div class="teacher-group-header" onclick="toggleSetContents(this, ${s.id})">
                    <span class="teacher-group-chevron">▸</span>
                    <span class="teacher-group-name">${escapeHtml(s.name)}</span>
                    <span class="teacher-set-count">${s.questionCount} ta savol · ${formatDate(s.createdAt)}</span>
                </div>
                <div class="teacher-group-body"></div>
            </div>
        `).join("");

        return `
        <div style="margin-bottom:20px;">
            <div style="font-weight:700;margin-bottom:8px;color:#334155;">
                👤 ${escapeHtml(g.teacherUsername)}
                <span class="teacher-set-count">(${g.sets.length} ta to'plam)</span>
            </div>
            ${rows}
        </div>`;
    }).join("");
}

// To'plam sarlavhasi bosilganda — ICHIDAGI savollar (matni bilan) ochiladi
// (birinchi ochilishda yuklanadi, keyingi ochish/yopishlarda qayta so'rov
// yuborilmaydi — "loaded" belgisi bilan keshlanadi).
async function toggleSetContents(headerEl, setId) {
    const card = headerEl.closest(".teacher-group-card");
    const body = card.querySelector(".teacher-group-body");

    if (card.classList.contains("expanded")) {
        card.classList.remove("expanded");
        return;
    }
    card.classList.add("expanded");

    if (body.dataset.loaded === "1") return;

    body.innerHTML = `<div class="teacher-empty">Yuklanmoqda...</div>`;
    try {
        const res = await fetch(`/api/teacher/questionsets/${setId}`);
        if (!res.ok) throw new Error();
        const detail = await res.json();

        body.innerHTML = detail.questions.length
            ? detail.questions.map((q, i) => `
                <div class="teacher-question-item" style="cursor:default;">
                    <span>${i + 1}. ${escapeHtml(q.questionText)}</span>
                </div>
              `).join("")
            : `<div class="teacher-empty">Bu to'plamda savol yo'q</div>`;
        body.dataset.loaded = "1";
    } catch (err) {
        console.error(err);
        body.innerHTML = `<div class="teacher-empty">Yuklashda xatolik</div>`;
    }
}
