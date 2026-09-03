// "/teacher/builder" — Fan → Mavzu → Savol tanlab, yangi "Savollar
// to'plami" (question set) yaratish, va mavjud to'plamlarni ko'rish/
// nomini o'zgartirish/o'chirish.

const selectedMap = new Map();

// Joriy tanlangan Fan uchun TO'LIQ mavzular ro'yxati (Bo'lim ma'lumoti
// bilan birga) — sectionSelect/topicSelect ikkalasi ham shundan
// (qayta so'rovsiz) to'ldiriladi.
let cachedTopics = [];

document.addEventListener("DOMContentLoaded", () => {
    loadSciences();
    loadSets();

    document.getElementById("scienceSelect").addEventListener("change", e => {
        if (e.target.value) {
            loadTopics(e.target.value);
        } else {
            resetSectionAndBelow();
        }
    });
});

function escapeHtml(s) {
    const div = document.createElement("div");
    div.textContent = s == null ? "" : String(s);
    return div.innerHTML;
}

//--------------------------------------------------------
//          FAN → BO'LIM → MAVZU → SAVOL (ketma-ket tanlash)
//--------------------------------------------------------
function loadSciences() {
    const select = document.getElementById("scienceSelect");
    fetch("/api/teacher/sciences")
        .then(r => r.ok ? r.json() : [])
        .then(list => {
            select.innerHTML = `<option value="">--Fanni tanlang--</option>` +
                list.map(s => `<option value="${s.id}">${escapeHtml(s.name)}</option>`).join("");
        })
        .catch(err => console.error(err));
}

// Fan tanlangach — mavzularni (Bo'lim ma'lumoti bilan) bir yo'la
// yuklab, keyin "Bo'lim" select'ini to'ldiradi (Mavzu select'i FAQAT
// Bo'lim tanlangandan keyin to'ldiriladi — foydalanuvchi ANIQ shu
// ketma-ketlikni so'radi: Fan → Bo'lim → Mavzu).
function loadTopics(scienceId) {
    fetch(`/api/teacher/topics/${scienceId}`)
        .then(r => r.json())
        .then(list => {
            cachedTopics = list;
            populateSectionSelect();
        })
        .catch(err => console.error(err));
}

function populateSectionSelect() {
    const sectionSelect = document.getElementById("sectionSelect");

    const sectionsById = new Map();
    let hasUnlinked = false;
    cachedTopics.forEach(t => {
        if (t.sectionId != null) {
            if (!sectionsById.has(t.sectionId)) {
                sectionsById.set(t.sectionId, { id: t.sectionId, name: t.sectionName, orderIndex: t.sectionOrderIndex });
            }
        } else {
            hasUnlinked = true;
        }
    });
    const sections = [...sectionsById.values()].sort((a, b) => a.orderIndex - b.orderIndex);

    let options = `<option value="">--Bo'limni tanlang--</option>`;
    options += sections.map(s => `<option value="${s.id}">${escapeHtml(s.name)}</option>`).join("");
    if (hasUnlinked) {
        options += `<option value="none">— Bo'limsiz mavzular —</option>`;
    }
    sectionSelect.innerHTML = options;
    sectionSelect.onchange = () => populateTopicSelect(sectionSelect.value);

    resetTopicAndBelow();
}

function populateTopicSelect(sectionValue) {
    const topicSelect = document.getElementById("topicSelect");

    if (!sectionValue) {
        resetTopicAndBelow();
        return;
    }

    const filtered = sectionValue === "none"
        ? cachedTopics.filter(t => t.sectionId == null)
        : cachedTopics.filter(t => String(t.sectionId) === sectionValue);

    const totalQuestions = filtered.reduce((sum, t) => sum + Number(t.questionCount || 0), 0);
    topicSelect.innerHTML = `<option value="">--Mavzuni tanlang-- (jami ${totalQuestions} ta test)</option>` +
        filtered.map(t => `<option value="${t.id}">${escapeHtml(t.name)} (${t.questionCount} ta)</option>`).join("");
    topicSelect.onchange = () => loadQuestions(topicSelect.value);

    loadQuestions("");
}

function resetSectionAndBelow() {
    cachedTopics = [];
    document.getElementById("sectionSelect").innerHTML = `<option value="">--Avval fanni tanlang--</option>`;
    resetTopicAndBelow();
}

function resetTopicAndBelow() {
    document.getElementById("topicSelect").innerHTML = `<option value="">--Avval bo'limni tanlang--</option>`;
    loadQuestions("");
}

function loadQuestions(topicId) {
    const box = document.getElementById("questions");
    if (!topicId) {
        box.innerHTML = `<div class="teacher-empty">Avval fan, bo'lim va mavzuni tanlang</div>`;
        return;
    }

    fetch(`/api/teacher/questions/topic/${topicId}`)
        .then(r => r.json())
        .then(list => {
            if (!list.length) {
                box.innerHTML = `<div class="teacher-empty">Bu mavzuda savol yo'q</div>`;
                return;
            }
            box.innerHTML = list.map((q, i) => `
                <label class="teacher-question-item">
                    <input type="checkbox" ${selectedMap.has(q.id) ? "checked" : ""}
                           onchange="toggleQuestion(${q.id}, this, \`${escapeHtml(q.questionText).replace(/`/g, "'")}\`)">
                    <span>${i + 1}. ${escapeHtml(q.questionText)}</span>
                </label>
            `).join("");
        });
}

function toggleQuestion(id, checkbox, text) {
    if (checkbox.checked) {
        selectedMap.set(id, { id, text });
        addSelectedUI(id, text);
    } else {
        selectedMap.delete(id);
        removeSelectedUI(id);
    }
    updateCounter();
}

function addSelectedUI(id, text) {
    if (document.getElementById("sel-" + id)) return;
    const list = document.getElementById("selectedList");
    list.insertAdjacentHTML("beforeend", `
        <div class="teacher-question-item" id="sel-${id}" style="cursor:default;">
            <span style="flex:1">${text}</span>
            <span onclick="removeSelectedQuestion(${id})" style="cursor:pointer;color:#dc2626;font-weight:bold;">✖</span>
        </div>
    `);
}

function removeSelectedQuestion(id) {
    selectedMap.delete(id);
    removeSelectedUI(id);
    const checkbox = document.querySelector(`#questions input[onchange*="toggleQuestion(${id},"]`);
    if (checkbox) checkbox.checked = false;
    updateCounter();
}

function removeSelectedUI(id) {
    const el = document.getElementById("sel-" + id);
    if (el) el.remove();
}

function updateCounter() {
    document.getElementById("counter").innerText = String(selectedMap.size);
}

function resetBuilder() {
    selectedMap.clear();
    document.getElementById("selectedList").innerHTML = "";
    updateCounter();
    document.getElementById("setName").value = "";
    document.querySelectorAll("#questions input").forEach(cb => cb.checked = false);
}

//--------------------------------------------------------
//          TO'PLAMNI SAQLASH / RO'YXAT / TAHRIRLASH
//--------------------------------------------------------
function saveSet() {
    const name = document.getElementById("setName").value.trim();
    if (!name || selectedMap.size === 0) {
        alert("To'plam nomini kiriting va kamida bitta savolni tanlang.");
        return;
    }

    fetch("/api/teacher/questionset", {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, questionIds: [...selectedMap.keys()] })
    })
        .then(r => {
            if (!r.ok) throw new Error("Saqlashda xatolik");
            resetBuilder();
            loadSets();
            alert("Savollar to'plami muvaffaqiyatli saqlandi!");
        })
        .catch(err => {
            console.error(err);
            alert("Saqlashda xatolik yuz berdi.");
        });
}

function loadSets() {
    fetch("/api/teacher/questionsets")
        .then(r => r.json())
        .then(list => renderSets(list))
        .catch(err => console.error("To'plamlarni yuklashda xatolik:", err));
}

function renderSets(list) {
    const container = document.getElementById("setsList");
    if (!list.length) {
        container.innerHTML = `<div class="teacher-empty">Hali to'plam yo'q</div>`;
        return;
    }

    container.innerHTML = list.map(s => `
        <div class="teacher-set-row">
            <span class="teacher-set-name">${escapeHtml(s.name)}</span>
            <span class="teacher-set-count">${s.questionIds.length} ta savol</span>
            <button class="teacher-icon-btn" onclick="renameSetPrompt(${s.id}, ${JSON.stringify(s.name).replace(/"/g, "&quot;")})" title="Nomini tahrirlash">✏️</button>
            <button class="teacher-icon-btn teacher-btn-danger" onclick="deleteSet(${s.id})" title="O'chirish">🗑</button>
        </div>
    `).join("");
}

function renameSetPrompt(id, oldName) {
    const newName = prompt("Yangi nom:", oldName);
    if (newName === null) return;
    const trimmed = newName.trim();
    if (!trimmed || trimmed === oldName) return;

    fetch(`/api/teacher/questionsets/${id}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name: trimmed })
    })
        .then(r => {
            if (!r.ok) throw new Error();
            loadSets();
        })
        .catch(() => alert("Nomini o'zgartirishda xatolik"));
}

function deleteSet(id) {
    if (!confirm("Bu to'plamni o'chirmoqchimisiz?")) return;

    fetch(`/api/teacher/questionsets/${id}`, { method: "DELETE" })
        .then(async r => {
            if (!r.ok) {
                const data = await r.json().catch(() => ({}));
                throw new Error(data.error || "O'chirishda xatolik");
            }
            loadSets();
        })
        .catch(err => alert(err.message || "O'chirishda xatolik"));
}
