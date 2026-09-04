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
            select.innerHTML = `<option value="">--Bo'limni tanlang--</option>` +
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
            renderTopicTree();
        })
        .catch(err => console.error(err));
}

// cachedTopics'ni Bo'lim bo'yicha guruhlaydi (sectionSelect ham,
// "🎲 Avtomatik tanlash" daraxti ham shundan foydalanadi) — bo'limsiz
// mavzular oxirida, "— Bo'limsiz mavzular —" psevdo-guruh sifatida.
function groupTopicsBySection() {
    const sectionsById = new Map();
    const unlinked = [];
    cachedTopics.forEach(t => {
        if (t.sectionId != null) {
            if (!sectionsById.has(t.sectionId)) {
                sectionsById.set(t.sectionId, { id: String(t.sectionId), name: t.sectionName, orderIndex: t.sectionOrderIndex, topics: [] });
            }
            sectionsById.get(t.sectionId).topics.push(t);
        } else {
            unlinked.push(t);
        }
    });
    const groups = [...sectionsById.values()].sort((a, b) => a.orderIndex - b.orderIndex);
    if (unlinked.length) {
        groups.push({ id: "none", name: "— Mavzusiz darslar —", topics: unlinked });
    }
    return groups;
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
    document.getElementById("sectionSelect").innerHTML = `<option value="">--Avval bo'limni tanlang--</option>`;
    document.getElementById("topicTree").innerHTML = `<div class="teacher-empty">Avval bo'limni tanlang</div>`;
    resetTopicAndBelow();
}

function resetTopicAndBelow() {
    document.getElementById("topicSelect").innerHTML = `<option value="">--Avval mavzuni tanlang--</option>`;
    loadQuestions("");
}

//--------------------------------------------------------
//          "🎲 Avtomatik tanlash" — Mavzu/Dars checkbox daraxti
//--------------------------------------------------------

// Bo'lim guruhlari — teacher-groups.js'dagi "guruh a'zolari" accordion
// bilan BIR XIL ".teacher-group-card/.teacher-group-header/..." uslubi
// (qayta yozilmagan, CSS ham umumiy). YOPIQ holatda boshlanadi (150+
// mavzuli bo'lim ham bo'lishi mumkin — hammasini ochiq qilib yuborish
// sahifani og'ir qilardi), lekin checkbox holati yopiq bo'lsa ham
// to'g'ri ishlaydi.
function renderTopicTree() {
    const container = document.getElementById("topicTree");
    if (!cachedTopics.length) {
        container.innerHTML = `<div class="teacher-empty">Bu fanda mavzu yo'q</div>`;
        return;
    }

    const groups = groupTopicsBySection();

    container.innerHTML = groups.map(g => {
        const totalQuestions = g.topics.reduce((sum, t) => sum + Number(t.questionCount || 0), 0);
        const topicsHtml = g.topics.map(t => `
            <label class="teacher-question-item">
                <input type="checkbox" class="topic-checkbox" data-topic-id="${t.id}" checked>
                <span>${escapeHtml(t.name)} (${t.questionCount} ta)</span>
            </label>
        `).join("");

        return `
        <div class="teacher-group-card">
            <div class="teacher-group-header" onclick="toggleTopicGroupExpand(this)">
                <span class="teacher-group-chevron">▸</span>
                <span class="teacher-group-name">${escapeHtml(g.name)}</span>
                <span class="teacher-set-count">(${g.topics.length} mavzu, ${totalQuestions} test)</span>
                <span class="teacher-group-actions" onclick="event.stopPropagation()">
                    <input type="checkbox" class="section-checkbox" checked>
                </span>
            </div>
            <div class="teacher-group-body">${topicsHtml}</div>
        </div>`;
    }).join("");

    container.querySelectorAll(".section-checkbox").forEach(cb => {
        cb.addEventListener("change", () => onSectionCheckboxToggle(cb));
    });
    container.querySelectorAll(".topic-checkbox").forEach(cb => {
        cb.addEventListener("change", () => onTopicCheckboxToggle(cb));
    });

    updateToggleAllButtonLabel();
}

function toggleTopicGroupExpand(headerEl) {
    headerEl.closest(".teacher-group-card").classList.toggle("expanded");
}

// Bo'lim checkbox'i bosilganda — shu bo'limning BARCHA mavzu
// checkbox'lari ham shunga qarab belgilanadi/bekor qilinadi.
function onSectionCheckboxToggle(sectionCheckbox) {
    const group = sectionCheckbox.closest(".teacher-group-card");
    group.querySelectorAll(".topic-checkbox").forEach(tcb => tcb.checked = sectionCheckbox.checked);
    sectionCheckbox.indeterminate = false;
    updateToggleAllButtonLabel();
}

// Bitta mavzu checkbox'i qo'lda o'zgartirilsa — Bo'lim checkbox'ining
// holati shunga qarab yangilanadi (hammasi belgilangan/hech biri/
// qisman — oxirgisi uchun "indeterminate" ko'rinishi ishlatiladi).
function onTopicCheckboxToggle(topicCheckbox) {
    const group = topicCheckbox.closest(".teacher-group-card");
    const topicBoxes = [...group.querySelectorAll(".topic-checkbox")];
    const checkedCount = topicBoxes.filter(t => t.checked).length;
    const sectionCheckbox = group.querySelector(".section-checkbox");
    sectionCheckbox.checked = checkedCount === topicBoxes.length;
    sectionCheckbox.indeterminate = checkedCount > 0 && checkedCount < topicBoxes.length;
    updateToggleAllButtonLabel();
}

// "❌ Barchasini bekor qilish" / "✅ Barchasini belgilash" — bitta
// tugma, joriy holatga qarab ikkalasini ham bajaradi (foydalanuvchi
// ANIQ shuni so'ragan: "bitta knopka bilan barcha tanlovni olib
// tashlash ham mumkin bo'lsin").
function toggleAllTopics() {
    const allBoxes = document.querySelectorAll("#topicTree input[type=checkbox]");
    const anyChecked = [...allBoxes].some(cb => cb.checked);
    const newState = !anyChecked;
    allBoxes.forEach(cb => {
        cb.checked = newState;
        cb.indeterminate = false;
    });
    updateToggleAllButtonLabel();
}

function updateToggleAllButtonLabel() {
    const btn = document.getElementById("toggleAllTopicsBtn");
    if (!btn) return;
    const allBoxes = document.querySelectorAll("#topicTree input[type=checkbox]");
    const anyChecked = [...allBoxes].some(cb => cb.checked);
    btn.textContent = anyChecked ? "❌ Barchasini bekor qilish" : "✅ Barchasini belgilash";
}

// Belgilangan mavzular orasidan, HAR BIRIGA TENG bo'lib (savollar
// soniga qarab EMAS — "🎲 Variantlar yaratish" bilan bir xil "suv
// quyish" algoritmi), jami kiritilgan sonda tasodifiy savol tanlab,
// "Tanlangan savollar" ro'yxatiga QO'SHADI (mavjudlarini o'chirmaydi —
// bir necha marta bosib, sonini oshirib borish mumkin).
async function autoSelectQuestions() {
    const topicIds = [...document.querySelectorAll("#topicTree .topic-checkbox:checked")]
        .map(cb => Number(cb.dataset.topicId));

    if (!topicIds.length) {
        alert("Kamida bitta darsni belgilang.");
        return;
    }

    const totalCount = Number(document.getElementById("autoSelectCount").value);
    if (!totalCount || totalCount < 1) {
        alert("Jami nechta savol kerakligini kiriting.");
        return;
    }

    try {
        const res = await fetch("/api/teacher/questions/auto-select", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ topicIds, totalCount })
        });

        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            throw new Error(data.error || "Avtomatik tanlashda xatolik");
        }

        const questions = await res.json();
        questions.forEach(q => {
            const safeText = escapeHtml(q.questionText);
            selectedMap.set(q.id, { id: q.id, text: safeText });
            addSelectedUI(q.id, safeText);
        });
        updateCounter();
        alert(`✅ ${questions.length} ta savol avtomatik tanlandi va "Tanlangan savollar"ga qo'shildi.`);
    } catch (err) {
        alert(err.message || "Avtomatik tanlashda xatolik");
    }
}

function loadQuestions(topicId) {
    const box = document.getElementById("questions");
    if (!topicId) {
        box.innerHTML = `<div class="teacher-empty">Avval bo'lim, mavzu va darsni tanlang</div>`;
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

// Hozir "📂 Tarkibini tahrirlash" orqali ochilgan MAVJUD to'plam ID'si —
// null bo'lsa, "Saqlash" YANGI to'plam yaratadi (avvalgidek); ID
// berilgan bo'lsa, "Saqlash" (endi "Yangilash" deb ko'rinadi) O'SHA
// to'plamning nomi VA savollarini ALMASHTIRADI.
let editingSetId = null;

function resetBuilder() {
    selectedMap.clear();
    document.getElementById("selectedList").innerHTML = "";
    updateCounter();
    document.getElementById("setName").value = "";
    document.querySelectorAll("#questions input").forEach(cb => cb.checked = false);
    editingSetId = null;
    updateSaveButtonMode();
}

function updateSaveButtonMode() {
    const saveBtn = document.getElementById("saveSetBtn");
    const cancelBtn = document.getElementById("cancelEditBtn");
    if (editingSetId != null) {
        saveBtn.textContent = "💾 Yangilash";
        cancelBtn.style.display = "inline-flex";
    } else {
        saveBtn.textContent = "💾 Saqlash";
        cancelBtn.style.display = "none";
    }
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

    const isEditing = editingSetId != null;
    const url = isEditing ? `/api/teacher/questionsets/${editingSetId}` : "/api/teacher/questionset";
    const method = isEditing ? "PUT" : "POST";

    fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ name, questionIds: [...selectedMap.keys()] })
    })
        .then(async r => {
            if (!r.ok) {
                const data = await r.json().catch(() => ({}));
                throw new Error(data.error || "Saqlashda xatolik");
            }
            resetBuilder();
            loadSets();
            alert(isEditing ? "Savollar to'plami muvaffaqiyatli yangilandi!" : "Savollar to'plami muvaffaqiyatli saqlandi!");
        })
        .catch(err => {
            console.error(err);
            alert(err.message || "Saqlashda xatolik yuz berdi.");
        });
}

// "📂 Tarkibini tahrirlash" — mavjud to'plamning nomi va savollarini
// (matni bilan) qurilmaga qayta yuklaydi — o'qituvchi qo'shimcha savol
// qo'sha oladi (Fan/Bo'lim/Mavzu orqali yoki avtomatik tanlash bilan)
// yoki "✖" bilan mavjudlarini olib tashlay oladi, keyin "Yangilash"ni bosadi.
async function openSetForEditing(id) {
    try {
        const res = await fetch(`/api/teacher/questionsets/${id}`);
        if (!res.ok) throw new Error("Yuklashda xatolik");
        const detail = await res.json();

        selectedMap.clear();
        document.getElementById("selectedList").innerHTML = "";
        detail.questions.forEach(q => {
            const safeText = escapeHtml(q.questionText);
            selectedMap.set(q.id, { id: q.id, text: safeText });
            addSelectedUI(q.id, safeText);
        });
        updateCounter();

        document.getElementById("setName").value = detail.name;
        editingSetId = detail.id;
        updateSaveButtonMode();

        document.getElementById("selectedList").scrollIntoView({ behavior: "smooth", block: "center" });
    } catch (err) {
        console.error(err);
        alert("Tarkibini yuklashda xatolik");
    }
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
            <button class="teacher-icon-btn" onclick="openSetForEditing(${s.id})" title="Tarkibini (savollarini) tahrirlash">📂</button>
            <button class="teacher-icon-btn" onclick="renameSetPrompt(${s.id}, ${JSON.stringify(s.name).replace(/"/g, "&quot;")})" title="Faqat nomini tahrirlash">✏️</button>
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
