// ========================================================================
//                     Global fields
// ========================================================================

let itemBlock = []; // сюда будут загружены данные из БД
let deletedSubjectIds = []; // FRONTEND da o'chirilganlarni id'si (Agar u DB da ham bo'lsa)
let focusIndex = null;//для курсора

// "Yo'nalish" — Bo'limlar (Science) ro'yxatini kattaroq guruhga bo'ladi,
// courses.js#allFields/expandedFieldKeys bilan AYNAN bir xil g'oya —
// Yo'nalish kurslar VA TEST BOSHQARUVI uchun UMUMIY (foydalanuvchi
// so'rovi, 2026-09-05).
let allFields = [];
const expandedFieldKeys = new Set();

// ?fieldId=<id> — /science/fields sahifasidan bitta Yo'nalishni bosib
// kirilganda shu Yo'nalishning Bo'limlarigina ko'rsatiladi (foydalanuvchi
// so'rovi, 2026-09-05: "Test boshqaruvi"ni bosganda AVVAL Yo'nalishlarga
// kirsin). "none" — "— Yo'nalishsiz bo'limlar —" psevdo-guruhi uchun.
// undefined — fieldId UMUMAN berilmagan (masalan eski bookmark) — bu
// holda ORQAGA MOSLIK uchun BARCHA Bo'limlar, filtrsiz, ko'rsatiladi.
const pageFieldId = (() => {
    const raw = new URLSearchParams(window.location.search).get("fieldId");
    if (raw == null) return undefined;
    if (raw === "none") return null;
    const n = Number(raw);
    return Number.isFinite(n) ? n : undefined;
})();

let oldName = ""; //for EDIT uses
let newName = ""; //for EDIT uses

// Haqiqiy Excel ilovasi belgisiga o'xshash SVG (yashil hujjat + oq "X") —
// "Excel'ga eksport" tugmalarida emoji o'rniga ishlatiladi (foydalanuvchi
// so'rovi bo'yicha — barcha eksport tugmalarida bir xil belgi, topic.js
// bilan bir xil).
const EXCEL_ICON_SVG = `<svg width="28" height="28" viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">
    <rect x="4" y="4" width="40" height="40" rx="7" fill="#107C41"/>
    <rect x="4" y="4" width="18" height="40" rx="7" fill="#0B5C31"/>
    <g stroke="#fff" stroke-width="4" stroke-linecap="round">
        <line x1="14" y1="16" x2="30" y2="32"/>
        <line x1="30" y1="16" x2="14" y2="32"/>
    </g>
</svg>`;

// Haqiqiy Word ilovasi belgisiga o'xshash SVG (ko'k hujjat + oq "W") —
// "📝 Word'ga eksport" tugmalarida — EXCEL_ICON_SVG bilan bir xil andoza.
const WORD_ICON_SVG = `<svg width="28" height="28" viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">
    <rect x="4" y="4" width="40" height="40" rx="7" fill="#185ABD"/>
    <rect x="4" y="4" width="18" height="40" rx="7" fill="#103F91"/>
    <text x="31" y="30" font-family="Arial, sans-serif" font-size="20" font-weight="bold" fill="#fff" text-anchor="middle">W</text>
</svg>`;
// ========================================================================

afterStartPage("/api/science");
refreshScienceTrashBadge();

// Badge'ni (".notif-badge" — navbar.js#refreshUnreadCount bilan bir xil
// uslub) sonini yangilaydi — 0 bo'lsa yashiradi. Bir nechta sahifada
// (question.js/topic.js/...) bir xil andoza bilan takrorlanadi — mustaqil
// kichik JS fayllar bo'lgani uchun ataylab nusxalangan.
function setTrashBadgeCount(badgeId, count) {
    const badge = document.getElementById(badgeId);
    if (!badge) return;
    if (count > 0) {
        badge.style.display = "inline-flex";
        badge.textContent = count > 99 ? "99+" : count;
    } else {
        badge.style.display = "none";
    }
}

function refreshScienceTrashBadge() {
    fetch("/api/science/deleted")
        .then(r => r.ok ? r.json() : [])
        .then(items => setTrashBadgeCount("scienceTrashBadge", items.length))
        .catch(err => console.error(err));
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text ?? "";
    return div.innerHTML;
}

// "🗑️ O'chirilgan fanlar" paneli — soft-delete qilingan Science'lar
// ro'yxati (bir zumda "♻️ Tiklash" qilinadigan).
let scienceTrashOpen = false;

function toggleScienceTrash() {
    scienceTrashOpen = !scienceTrashOpen;
    document.getElementById("scienceTrashPanel").style.display = scienceTrashOpen ? "block" : "none";
    if (scienceTrashOpen) {
        loadScienceTrash();
    }
}

async function loadScienceTrash() {
    const list = document.getElementById("scienceTrashList");
    list.innerHTML = "<p>Yuklanmoqda...</p>";

    try {
        const res = await fetch("/api/science/deleted");
        if (!res.ok) {
            list.innerHTML = "<p>Yuklashda xatolik</p>";
            return;
        }
        const items = await res.json();
        setTrashBadgeCount("scienceTrashBadge", items.length);
        if (!items.length) {
            list.innerHTML = "<p>O'chirilgan bo'lim yo'q</p>";
            return;
        }
        list.innerHTML = items.map(s => `
            <div class="row">
                <div>${escapeHtml(s.name)} — ${formatScienceTrashDate(s.deletedAt)}da o'chirilgan</div>
                <div class="row-actions">
                    <button onclick="restoreScienceFromTrash(${s.id})">♻️ Tiklash</button>
                    <button class="danger-btn" onclick="permanentlyDeleteScienceFromTrash(${s.id}, ${JSON.stringify(s.name).replace(/"/g, "&quot;")})">🗑️ Butunlay o'chirish</button>
                </div>
            </div>
        `).join("");
    } catch (err) {
        console.error(err);
        list.innerHTML = "<p>Tarmoq xatoligi</p>";
    }
}

function formatScienceTrashDate(isoString) {
    if (!isoString) return "";
    const d = new Date(isoString);
    return d.toLocaleDateString("uz-UZ") + " " + d.toLocaleTimeString("uz-UZ", { hour: "2-digit", minute: "2-digit" });
}

async function restoreScienceFromTrash(scienceId) {
    if (!await showConfirmModal("Bu bo'limni tiklamoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/science/${scienceId}/restore`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Tiklashda xatolik");
            return;
        }
        loadScienceTrash();
        await reloadAll("/api/science");
        render();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

async function permanentlyDeleteScienceFromTrash(scienceId, name) {
    if (!await showConfirmModal(`⚠️ "${name}" bo'limini BUTUNLAY o'chirmoqchimisiz?\n\nBu amalni HECH QANDAY tarzda bekor qilib bo'lmaydi.\n\n(Agar bu bo'limda hali Mavzu/dars bo'lsa, avval ularni o'chirish kerak bo'ladi.)`, { danger: true })) return;

    try {
        const res = await fetch(`/api/science/${scienceId}/permanent`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "O'chirishda xatolik");
            return;
        }
        loadScienceTrash();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}


// ========================================================================
//                      Functions
// ========================================================================

function afterStartPage(mapping) {

    const messageName =
        mapping === "/api/science" ? "Fanlar"
            : mapping === "/api/topic" ? "Mavzular"
                : mapping === "/api/question" ? "Savollar" : '';

    document.addEventListener("DOMContentLoaded", () => {
        reloadAll(mapping).then(() => {
            focusIndex = 0;// выбрать первый элемент
            render();// отрисовать список с выделением
        });
    });
}

// Bo'limlar (Science) VA Yo'nalishlar BIRGALIKDA yuklanadi — bo'sh (hali
// hech qanday Bo'limi yo'q) Yo'nalish ham ro'yxatda ko'rinishi kerak
// (courses.js#loadCourses bilan bir xil g'oya).
async function reloadAll(mapping) {
    await Promise.all([reloadFromDb(mapping), loadFields()]);
    applyFieldScope();
}

async function loadFields() {
    try {
        const res = await fetch("/api/course-fields");
        allFields = res.ok ? await res.json() : [];
    } catch (err) {
        console.error(err);
        allFields = [];
    }
    // pageFieldId bilan (bitta Yo'nalish ichida) ko'rsatilganda —
    // getSortedFieldGroups() faqat SHU Yo'nalishni (yoki hech qaysini,
    // "Yo'nalishsiz" rejimida) ko'rsin, boshqa Yo'nalishlar accordion'da
    // chiqmasin.
    if (pageFieldId === null) {
        allFields = [];
    } else if (typeof pageFieldId === "number") {
        allFields = allFields.filter(f => f.id === pageFieldId);
    }
}

// Sahifa yuqorisidagi "← Yo'nalishlar" panelini va sarlavhani
// pageFieldId'ga qarab to'ldiradi/yashiradi ("+ Yangi Yo'nalish" tugmasi
// ham shu bitta Yo'nalish ichida ma'nosiz — yashiriladi, Yo'nalish
// boshqaruvi endi /science/fields sahifasida).
function applyFieldScope() {
    if (pageFieldId === undefined) return; // eski, filtrsiz rejim

    const bar = document.getElementById("fieldScopeBar");
    const nameEl = document.getElementById("fieldScopeName");
    const createBtn = document.getElementById("createFieldBtn");
    const title = document.getElementById("pageTitle");
    const backLink = document.getElementById("fieldScopeBackLink");

    bar.classList.remove("hidden");
    if (createBtn) createBtn.style.display = "none";

    // "← Yo'nalishlar"ga qaytilganda o'sha Yo'nalish kartasi topilib,
    // ajratib ko'rsatilsin deb ("courses.js#focusCourseId bilan bir xil
    // g'oya) — foydalanuvchi so'rovi, 2026-09-05.
    if (backLink) {
        backLink.href = "/science/fields?focus=" + (pageFieldId === null ? "none" : pageFieldId);
    }

    if (pageFieldId === null) {
        nameEl.textContent = "— Yo'nalishsiz bo'limlar —";
    } else {
        const field = allFields[0];
        nameEl.textContent = field ? field.name : "";
        if (field) title.textContent = `${field.name} — Bo'limlar`;
    }
}

async function reloadFromDb(mapping) {
    const response = await fetch(mapping);

    try {
        if (!response.ok) {
            throw new Error(`Server error: ${response.status}`);
        }
    } catch (err) {
        console.error('Yuklash xatosi:', err);
        showToast('error', `Bo'limlarni yuklashda xatolik`, 4000);
    }

    const data = await response.json();

    itemBlock = data.map(s => ({
        id: s.id,
        name: s.name,
        original: s.name,
        // Shu fanda nechta Bo'lim (TopicSection) borligi — render()'da
        // "(N ta bo'lim)" ko'rsatish uchun.
        sectionCount: s.sectionCount || 0,
        // Qaysi Yo'nalishga tegishli — render() shu bo'yicha guruhlaydi
        // (courses.js#getSortedFieldGroups bilan bir xil andoza).
        // originalFieldId — saveToDb() dirty-check uchun (s.original bilan
        // bir xil g'oya, faqat Yo'nalish uchun).
        fieldId: s.fieldId ?? null,
        fieldName: s.fieldName ?? null,
        originalFieldId: s.fieldId ?? null,
        mode: "VIEW"
    }));

    // pageFieldId bilan (bitta Yo'nalish ichida) ko'rsatilganda — faqat
    // shu Yo'nalishga (yoki "none" bo'lsa, hech qaysiga) tegishli
    // Bo'limlar qoladi.
    if (pageFieldId === null) {
        itemBlock = itemBlock.filter(s => s.fieldId == null);
    } else if (typeof pageFieldId === "number") {
        itemBlock = itemBlock.filter(s => s.fieldId === pageFieldId);
    }
}

function render() {
    const list = document.getElementById("list");

    // если фокус не задан — выбрать первый элемент (HTML qurishdan OLDIN,
    // shu bilan uning guruhini ham darhol ochish mumkin bo'ladi).
    if (focusIndex === null && itemBlock.length > 0) {
        focusIndex = 0;
    }

    // Fokusdagi elementning Yo'nalish guruhi hali yopiq bo'lsa — avtomatik
    // ochamiz (aks holda "input-<i>" DOM'da bo'lmay, fokus qo'yib
    // bo'lmaydi) — courseDetail.js#selectCard'dagi avtomatik ochish bilan
    // bir xil g'oya (add()/edit() dan keyin ham shu orqali ishlaydi).
    if (focusIndex !== null && itemBlock[focusIndex]) {
        expandedFieldKeys.add(fieldKeyOf(itemBlock[focusIndex]));
    }

    const groups = getSortedFieldGroups();
    const realFieldGroups = groups.filter(g => g.fieldId != null);
    list.innerHTML = groups.map(g => renderFieldGroupBox(g, realFieldGroups)).join("");

    if (focusIndex !== null) {
        const input = document.getElementById(`input-${focusIndex}`);
        if (input) {
            input.focus();
            // if (itemBlock[focusIndex].mode !== "VIEW") input.select();
            input.scrollIntoView({behavior: 'smooth', block: 'nearest'});
        }
        focusIndex = null;
    }
}

function fieldKeyOf(s) {
    return s.fieldId != null ? String(s.fieldId) : "none";
}

// itemBlock'ni Yo'nalish (field) bo'yicha guruhlab, tartib bo'yicha
// saralab qaytaradi — courses.js#getSortedFieldGroups bilan AYNAN bir xil
// andoza. Har bir guruh ICHIDA obyekt emas, GLOBAL INDEKS saqlanadi
// (itemBlock[i]) — mavjud onclick handlerlar shu indeksga tayanadi.
// "none" — hali hech qanday Yo'nalishga biriktirilmagan Bo'limlar uchun
// psevdo-guruh (Yo'nalish IXTIYORIY bo'lgani uchun har doim bo'lishi mumkin).
function getSortedFieldGroups() {
    const groups = new Map();
    for (const f of allFields) {
        groups.set(String(f.id), { key: String(f.id), fieldId: f.id, name: f.name, orderIndex: f.orderIndex, items: [] });
    }

    itemBlock.forEach((s, i) => {
        const key = fieldKeyOf(s);
        if (!groups.has(key)) {
            groups.set(key, {
                key,
                fieldId: s.fieldId,
                name: s.fieldId != null ? s.fieldName : "— Yo'nalishsiz bo'limlar —",
                orderIndex: s.fieldId != null ? Number.MAX_SAFE_INTEGER - 1 : Number.MAX_SAFE_INTEGER,
                items: []
            });
        }
        groups.get(key).items.push(i);
    });

    return [...groups.values()].sort((a, b) => a.orderIndex - b.orderIndex);
}

function toggleFieldBox(key) {
    if (expandedFieldKeys.has(key)) {
        expandedFieldKeys.delete(key);
    } else {
        expandedFieldKeys.add(key);
    }
    render();
}

function renderFieldGroupBox(group, realFieldGroups) {
    const isExpanded = expandedFieldKeys.has(group.key);

    let bodyHtml = "";
    if (isExpanded) {
        bodyHtml = group.items.length
            ? `<div class="chapter-box-body">${group.items.map(i => renderRowHtml(itemBlock[i], i)).join("")}</div>`
            : `<div class="chapter-box-body"><div class="courses-empty">Bu Yo'nalishda hali bo'lim yo'q</div></div>`;
    }

    // "✏️"/"🗑️" — faqat HAQIQIY Yo'nalishlarda (group.fieldId != null),
    // "— Yo'nalishsiz bo'limlar —" psevdo-guruhida ko'rsatilmaydi
    // (courses.js#renderFieldBox bilan bir xil qoida).
    const renameBtn = group.fieldId != null
        ? `<button class="chapter-rename-btn" onclick="event.stopPropagation(); renameFieldPrompt(${group.fieldId})" title="Yo'nalish nomini tahrirlash">✏️</button>`
        : "";
    const deleteBtn = group.fieldId != null
        ? `<button class="chapter-rename-btn danger-btn" onclick="event.stopPropagation(); deleteFieldPrompt(${group.fieldId}, ${JSON.stringify(group.name).replace(/"/g, "&quot;")})" title="Yo'nalishni o'chirish (faqat bo'sh bo'lsa)">🗑️</button>`
        : "";

    let moveBtns = "";
    if (group.fieldId != null && realFieldGroups.length > 1) {
        const pos = realFieldGroups.findIndex(g => g.fieldId === group.fieldId);
        const upDisabled = pos <= 0 ? "disabled" : "";
        const downDisabled = pos === realFieldGroups.length - 1 ? "disabled" : "";
        moveBtns = `
            <button class="chapter-move-btn" onclick="event.stopPropagation(); moveField(${group.fieldId}, -1)" ${upDisabled} title="Yo'nalishni yuqoriga surish">⬆</button>
            <button class="chapter-move-btn" onclick="event.stopPropagation(); moveField(${group.fieldId}, 1)" ${downDisabled} title="Yo'nalishni pastga surish">⬇</button>
        `;
    }

    return `
        <div class="chapter-box ${isExpanded ? "expanded" : "collapsed"}">
            <h3 class="chapter-box-title" onclick="toggleFieldBox('${group.key}')" title="${isExpanded ? "Yig'ish" : "Ochish"}">
                <span class="chapter-box-chevron">▸</span>
                🧭 ${escapeHtml(group.name)}
                <span class="chapter-box-count">(${group.items.length} ta bo'lim)</span>
                <span class="chapter-box-actions">${moveBtns}${renameBtn}${deleteBtn}</span>
            </h3>
            ${bodyHtml}
        </div>
    `;
}

// Bitta qatorning HTML'i — ilgari render() ICHIDA to'g'ridan-to'g'ri DOM
// elementga yozilardi, endi renderFieldGroupBox() har bir guruh ICHIDA
// chaqiradigan alohida funksiya (mazmuni o'zgarmagan, faqat Yo'nalish
// select'i EDIT/NEW rejimida qo'shildi).
function renderRowHtml(s, i) {
    const isView = s.mode === "VIEW";
    // "science-row" — 768px+ ekranlarda fan nomi va "✏️ Edit" tugmasi
    // BITTA qatorda (yonma-yon) joylashishi uchun (science.css) — FAQAT
    // ko'rish (VIEW) rejimida (tahrirlashda — textarea + bir nechta
    // tugma — hamon ustunli, tor bo'lib qolmasin deb).
    const rowClass = isView ? "row science-row" : "row";

    const isLink = isView && s.id !== null;
    const isNew = s.mode === "NEW";
    const placeholder = isNew ? 'placeholder="Yangi bo\'lim nomini kiriting"' : '';

    // Проверяем дубликаты для текущего элемента
    const hasDup = !isView && hasDuplicate(i, s.name);
    const inputClass = `
                                ${isView ? 'view' : ''}
                                ${isLink ? 'link' : ''}
                                ${hasDup ? 'duplicate' : ''}
                                `;
    const bodyHtml = isView
        ? `
        <div
        class="row-view"
        tabindex="0"
        ondblclick="openTopics(${s.id})"
        onkeydown="onViewKeyDown(event, ${i})"
        title="Enter — Мавзуларни очиш | ↑ ↓ — навигация | Home/End — биринчи/охирги"
    >
        <div
            id="input-${i}"
            class="topic-name ${inputClass}"
            tabindex="-1"
        ><div class="item-title-row"><span class="item-title-text">${escapeHtml(s.name)}</span>${isLink ? `<span class="item-count-badge">${s.sectionCount} ta mavzu</span>` : ""}${isLink ? `<button class="topic-export-btn" onclick="event.stopPropagation(); exportScienceQuestions(${s.id})" title="Shu bo'limdagi barcha darslarning testlarini Excel'ga eksport qilish">${EXCEL_ICON_SVG}</button>` : ""}${isLink ? `<button class="topic-export-btn" onclick="event.stopPropagation(); openWordExportModal(${s.id})" title="Shu bo'limdagi barcha darslarning testlarini Word'ga eksport qilish">${WORD_ICON_SVG}</button>` : ""}</div></div>
    </div>
        `
        : `
        <textarea
            class="name-edit-area ${inputClass}"
            rows="2"
            ${placeholder}
            oninput="itemBlock[${i}].name=this.value"
            onkeydown="onClickKey(event, ${i})"
            id="input-${i}"
        >${escapeHtml(s.name)}</textarea>
        ${fieldSelectHtml(s, i)}
        `;

    return `<div class="${rowClass}">${bodyHtml}${buttons(s, i)}</div>`;
}

// EDIT/NEW rejimidagi Yo'nalish tanlash select'i — IXTIYORIY (foydalanuvchi
// so'rovi, 2026-09-05: kurslardan farqli, Bo'lim uchun Yo'nalish majburiy
// emas). O'zgarganda darhol itemBlock[i].fieldId'ga yoziladi (name'dagi
// "oninput" bilan bir xil g'oya) — haqiqiy saqlash Save/Save to DB'da.
function fieldSelectHtml(s, i) {
    const options = [`<option value="">— Yo'nalishsiz —</option>`]
        .concat([...allFields].sort((a, b) => a.orderIndex - b.orderIndex)
            .map(f => `<option value="${f.id}" ${s.fieldId === f.id ? "selected" : ""}>${escapeHtml(f.name)}</option>`));
    return `<select class="field-select" onchange="itemBlock[${i}].fieldId = this.value ? Number(this.value) : null" title="Yo'nalish">${options.join("")}</select>`;
}

function openTopics(scienceId) {
    if (!scienceId || scienceId < 0) {
        // ВАРИАНТ 1 — запрет
        showAlertModal("❗ Бу бўлим базада йўқ");
        return;

        // ВАРИАНТ 2 — разрешить пустые темы
        // window.location.href = "/topics";
        // return;
    }

    // Endi to'g'ridan-to'g'ri mavzular emas, avval Mavzu guruhlari
    // (TopicSection) sahifasiga o'tiladi (Bo'lim -> Mavzu -> Dars
    // ierarxiyasi). fieldId — joriy Yo'nalish qamrovi (agar bo'lsa) —
    // "Orqaga" zanjiri bo'ylab olib o'tiladi, shu sabab "Bo'lim -> Mavzu
    // -> Dars"dan "Orqaga" bosilganda foydalanuvchi tanlagan Yo'nalish
    // yo'qolib qolmaydi (topicSection.js/topic.js shu parametrni o'qiydi).
    const fieldQuery = typeof pageFieldId === "number" ? `&fieldId=${pageFieldId}`
        : pageFieldId === null ? `&fieldId=none` : "";
    window.location.href = `/topic-sections?scienceId=${scienceId}${fieldQuery}`;
}

// "📊 Excel'ga eksport" — shu Fandagi BARCHA mavzularning savollarini
// BITTA .xlsx faylga yig'ib yuklab beradi (topic.js#exportTopicQuestions
// bilan bir xil andoza, faqat butun Fan miqyosida).
function exportScienceQuestions(scienceId) {
    window.location.href = `/api/export/questions/science?scienceId=${scienceId}`;
}

// "📝 Word'ga eksport" oynasi — galochka qo'yilmasa oddiy bitta faylli
// eksport, qo'yilsa "🎲 Variantlar yaratish" — har biri BOSHQA
// savollardan iborat bir nechta imtihon varianti (ExamVariantService),
// ZIP + javoblar kaliti holida. Qator tugmasi bosilganda openWordExportModal
// aynan qaysi FAN uchun ekanini saqlab qo'yadi (wordExportScienceId).
let wordExportScienceId = null;

function openWordExportModal(scienceId) {
    wordExportScienceId = scienceId;
    document.getElementById("wordExportVariantsCheckbox").checked = false;
    document.getElementById("wordExportVariantFields").classList.add("hidden");
    document.querySelector('input[name="wordExportVariantMode"][value="different"]').checked = true;
    updateWordExportHint();
    document.getElementById("wordExportModal").classList.add("show");
}

function closeWordExportModal() {
    document.getElementById("wordExportModal").classList.remove("show");
}

function toggleWordExportVariantFields() {
    const useVariants = document.getElementById("wordExportVariantsCheckbox").checked;
    document.getElementById("wordExportVariantFields").classList.toggle("hidden", !useVariants);
}

// Tanlangan rejimga ("different"/"same") qarab izoh matnini yangilaydi.
// Ikki matn UZUNLIGI har xil (bittasi ko'proq qatorga o'raladi) —
// shunchaki almashtirsak, modal balandligi rejim tanlanganda "sakrab"
// qolar edi. Shu sabab har ikkalasining tabiiy balandligini o'lchab,
// KATTASINI min-height sifatida qotirib qo'yamiz (ekran kengligidan
// qat'i nazar to'g'ri ishlashi uchun har chaqiriqda qayta o'lchanadi).
function updateWordExportHint() {
    const sameQuestions = document.querySelector('input[name="wordExportVariantMode"]:checked').value === "same";
    const textDifferent = "Savollar shu bo'limdagi BARCHA darslar bo'yicha TENG taqsimlanadi (darsda savol yetmasa, qolgan qismi boshqa darslarga teng bo'lib beriladi). Natija — har biri alohida .docx fayl bo'lgan ZIP arxiv + javoblar kaliti (Excel).";
    const textSame = "Savollar shu bo'limdagi BARCHA darslar bo'yicha TENG taqsimlanib BIR MARTA tanlanadi va BARCHA nusxada bir xil bo'ladi — faqat savollar (va javob variantlari) tartibi har bir nusxada alohida aralashtiriladi.";

    const hint = document.getElementById("wordExportHint");
    hint.style.minHeight = "";
    hint.textContent = textDifferent;
    const hDifferent = hint.offsetHeight;
    hint.textContent = textSame;
    const hSame = hint.offsetHeight;
    hint.style.minHeight = Math.max(hDifferent, hSame) + "px";

    hint.textContent = sameQuestions ? textSame : textDifferent;
}

async function confirmWordExport() {
    const useVariants = document.getElementById("wordExportVariantsCheckbox").checked;

    if (!useVariants) {
        window.location.href = `/api/export/questions/word/science?scienceId=${wordExportScienceId}`;
        closeWordExportModal();
        return;
    }

    const variantCount = Number(document.getElementById("wordExportVariantCount").value);
    const perVariant = Number(document.getElementById("wordExportPerVariant").value);
    const shuffleAnswers = document.getElementById("wordExportShuffleAnswers").checked;
    const sameQuestions = document.querySelector('input[name="wordExportVariantMode"]:checked').value === "same";

    if (!variantCount || variantCount < 1) {
        showAlertModal("Nechta variant kerakligini kiriting");
        return;
    }
    if (!perVariant || perVariant < 1) {
        showAlertModal("Har bir variantga nechta savol kerakligini kiriting");
        return;
    }

    await downloadWordVariants(
        `/api/export/questions/word/variants/science?scienceId=${wordExportScienceId}&variantCount=${variantCount}&perVariant=${perVariant}&shuffleAnswers=${shuffleAnswers}&sameQuestions=${sameQuestions}`,
        "variantlar.zip"
    );
}

// ZIP faylni fetch orqali yuklab olish — oddiy window.location.href
// ISHLATILMAYDI, chunki backend xato bo'lsa (masalan "savol yetmadi")
// {"error": "..."} JSON qaytaradi, buni foydalanuvchiga ko'rsatish uchun
// javobni avval o'qib chiqish kerak (GlobalRestExceptionHandler).
async function downloadWordVariants(url, filename) {
    const btn = document.getElementById("wordExportConfirmBtn");
    const originalText = btn.textContent;
    btn.disabled = true;
    btn.textContent = "Tayyorlanmoqda...";

    try {
        const res = await fetch(url);
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Eksport qilishda xatolik");
            return;
        }

        const blob = await res.blob();
        const objectUrl = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = objectUrl;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(objectUrl);
        closeWordExportModal();
    } catch (e) {
        console.error(e);
        showAlertModal("Tarmoq xatoligi");
    } finally {
        btn.disabled = false;
        btn.textContent = originalText;
    }
}

function hasDuplicate(currentIndex, name) {

    return itemBlock.some((subject, index) =>
        index !== currentIndex &&
        subject.name.toLowerCase().trim() === name.toLowerCase().trim()
    );
}

function onClickKey(event, i) {
    // Nom maydoni endi <textarea> — oddiy Enter saqlaydi (preventDefault
    // shart, aks holda qo'shimcha bo'sh qator ham qo'shilib qolardi),
    // Shift+Enter esa qator ko'chirish uchun ochiq qoldirilgan. "Delete"
    // tugmasi orqali QATORNI o'chirish olib tashlandi — <textarea> ichida
    // bu tugma endi odatiy (kursordan keyingi belgini o'chirish) ma'noda
    // ishlaydi; qatorni o'chirish faqat 🗑️ tugmasi orqali.
    if (event.key === "Enter" && !event.shiftKey && itemBlock[i].mode !== "VIEW") {
        event.preventDefault();
        saveOnClientSide(i);
    }

    if (event.key === "Escape" && itemBlock[i].mode !== "VIEW") {
        cancel(i);
    }
} //DONE

function onViewKeyDown(event, index) {
    const s = itemBlock[index];

    // работаем ТОЛЬКО в VIEW
    if (s.mode !== "VIEW") return;

    switch (event.key) {

        case "Enter":
            event.preventDefault();
            openTopics(s.id);
            break;

        case "ArrowUp":
            event.preventDefault();
            moveFocus(index - 1);
            break;

        case "ArrowDown":
            event.preventDefault();
            moveFocus(index + 1);
            break;

        case "Home":
            event.preventDefault();
            moveFocus(0);
            break;

        case "End":
            event.preventDefault();
            moveFocus(itemBlock.length - 1);
            break;
    }
}

function moveFocus(newIndex) {
    if (newIndex < 0 || newIndex >= itemBlock.length) return;
    focusIndex = newIndex;
    render();
}

function cancel(i) {
    const s = itemBlock[i];
    if (s.mode !== "VIEW") {
        if (s.mode === "NEW") {
            itemBlock.splice(i, 1);
        }
        s.name = s.original;
        s.fieldId = s.originalFieldId;
        s.mode = "VIEW";
        showToast('info', 'Amaliyot bekor qilindi', 2000);
    }
    render();
} //DONE

function undoAll() {
    reloadAll("/api/science").then(() => {
        render()
    });
    showToast('info', 'Ma\'lumotlar bazasidan qayta yuklandi ', 4000);
}

async function removeFromUi(i) {
    if (itemBlock[i].mode === "NEW") {
        itemBlock.splice(i, 1);
        render();
        return;
    }
    const subjectName = itemBlock[i].name || "Bu bo'lim";
    const confirmDelete = await showConfirmModal(`⚠️ "${subjectName}"ni o'chirishni tasdiqlaysizmi?\n\nBu amalni bekor qilib bo'lmaydi.`, { danger: true });
    if (confirmDelete) {
        const removedSubject = itemBlock[i];

        if (removedSubject.id > 0) {
            deletedSubjectIds.push(removedSubject.id);
        }

        itemBlock.splice(i, 1);
        showToast('success', `"${removedSubject.name || 'Bo\'lim'}" o'chirildi`, 2000);
        render();
    } else {
        cancel(i);
    }
} //DONE

// Tugmalar guruhi ".row-actions" ichiga o'raladi (science.css) — karta
// ichida har doim ENG PASTGA "yopishadi" (margin-top:auto). "⬆⬇" endi
// GLOBAL emas, shu Bo'limning O'Z Yo'nalish guruhi ICHIDA birinchi/oxirgi
// ekanligiga qarab o'chiriladi (aks holda tugma bosilganda Bo'lim boshqa
// Yo'nalish "box"iga sirg'alib o'tib ketgandek ko'rinardi — Yo'nalishi
// o'zgarmagani holda, faqat GLOBAL tartibi o'zgargani uchun).
function buttons(s, i) {
    if (s.mode === "VIEW") {
        const siblings = groupIndicesFor(s.fieldId);
        const posInGroup = siblings.indexOf(i);
        const upDisabled = posInGroup <= 0 ? "disabled" : "";
        const downDisabled = posInGroup === siblings.length - 1 ? "disabled" : "";
        return `
            <div class="row-actions">
                <button class="order-move-btn" onclick="moveUp(${i})" ${upDisabled} title="Yuqoriga">⬆</button>
                <button class="order-move-btn" onclick="moveDown(${i})" ${downDisabled} title="Pastga">⬇</button>
                <button onclick="edit(${i})">✏️ Edit</button>
            </div>
        `;
    }
    return `
        <div class="row-actions">
            <button onclick="saveOnClientSide(${i})">💾 Save</button>
            <button onclick="cancel(${i})">↩ Cancel</button>
            <button onclick="removeFromUi(${i})">🗑️ Delete</button>
        </div>
    `;
} //DONE

// itemBlock ICHIDAGI, berilgan fieldId bilan bir xil Yo'nalishga tegishli
// barcha elementlarning GLOBAL indekslari (o'z tartibida) — buttons()/
// moveUp()/moveDown() shu bo'yicha "guruh ichidagi" chegarani hisoblaydi.
function groupIndicesFor(fieldId) {
    const key = fieldId != null ? String(fieldId) : "none";
    const indices = [];
    itemBlock.forEach((s, idx) => {
        if (fieldKeyOf(s) === key) indices.push(idx);
    });
    return indices;
}

// Faqat DB'da mavjud (id > 0) fanlar orasida joy almashtiradi va darhol
// serverga (reorder endpoint) yuboradi — yangi (hali saqlanmagan)
// fanlar bilan aralashtirmaslik uchun oddiy holatda saqlanadi
// (topicSection.js#moveUp bilan bir xil andoza). Endi shu Bo'limning O'Z
// Yo'nalish guruhi ICHIDAGI qo'shnisi bilan almashadi (GLOBAL qo'shni
// emas) — ikkalasining ARRAY o'rni almashtiriladi, boshqalarning nisbiy
// tartibi tegilmaydi.
function moveUp(i) {
    const siblings = groupIndicesFor(itemBlock[i].fieldId);
    const pos = siblings.indexOf(i);
    if (pos <= 0) return;
    const otherIdx = siblings[pos - 1];
    [itemBlock[otherIdx], itemBlock[i]] = [itemBlock[i], itemBlock[otherIdx]];
    persistOrder();
}

function moveDown(i) {
    const siblings = groupIndicesFor(itemBlock[i].fieldId);
    const pos = siblings.indexOf(i);
    if (pos === -1 || pos >= siblings.length - 1) return;
    const otherIdx = siblings[pos + 1];
    [itemBlock[i], itemBlock[otherIdx]] = [itemBlock[otherIdx], itemBlock[i]];
    persistOrder();
}

async function persistOrder() {
    render();

    const orderedIds = itemBlock.filter(s => s.id > 0).map(s => s.id);
    if (orderedIds.length < 2) return;

    try {
        const response = await fetch("/api/science/reorder", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(orderedIds)
        });
        if (!response.ok) throw new Error("Server error: " + response.status);
        showToast('success', 'Tartib saqlandi', 2000);
    } catch (err) {
        console.error(err);
        showToast('error', 'Tartibni saqlashda xatolik', 4000);
    }
}

// "Saralash: A→Z / Z→A" — hali saqlanmagan (NEW/EDIT) qatorlar bo'lsa
// avval ularni yakunlash so'raladi (topicSection.js'dagi 🗑️ bo'sh
// bo'limlarni o'chirish bilan bir xil ehtiyot chorasi).
function sortAllAZ(dir) {
    if (itemBlock.some(s => s.mode !== "VIEW")) {
        showAlertModal("❌ Avval tahrirlashni yakuniga yetkazing (yoki saqlang)!");
        return;
    }

    itemBlock.sort((a, b) =>
        dir === "AZ" ? a.name.localeCompare(b.name, "uz") : b.name.localeCompare(a.name, "uz"));

    persistOrder();
}

function edit(i) {
    if (itemBlock.some(s => s.mode === "EDIT")) {
        showToast('warning', 'Avval tahrirlashni yakuniga yetkazing!');
        focusIndex = itemBlock.findIndex(s => s.mode !== "VIEW");
        render();
        return;
    }
    itemBlock[i].mode = "EDIT";
    focusIndex = i;

    oldName = itemBlock[i].name;

    render();
} //DONE

function showToast(type, message, duration = 4000) {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;

    const icons = {
        success: '✅',
        error: '❌',
        warning: '⚠️',
        info: 'ℹ️'
    };

    toast.innerHTML = `
               <span class="toast-icon">${icons[type] || ''}</span>
               <span class="toast-message">${message}</span>
               <button class="toast-close" onclick="this.parentElement.remove()">❌</button>
           `;

    const container = document.getElementById('toast-container');
    container.appendChild(toast);

    // Автоматическое удаление через указанное время
    setTimeout(() => {
        if (toast.parentElement) {
            toast.style.opacity = '0';
            setTimeout(() => toast.remove(), 300);
        }
    }, duration);

    return toast;
} //TODO

function add() {
    if (itemBlock.some(s => s.mode === "NEW" || s.mode === "EDIT")) {
        showToast('warning', 'Avval saqlash tugmasini bosing!');
        focusIndex = itemBlock.findIndex(s => s.mode !== "VIEW");
        render();
        return;
    }

    // ИЗМЕНЕНИЕ: Увеличиваем временный ID
    const tempId = Date.now() * -1; // Отрицательный ID для временных записей

    itemBlock.push({
        id: tempId, // Временный ID
        name: "",
        original: "",
        // Bitta Yo'nalish ichida (pageFieldId) turilganda — yangi Bo'lim
        // AVTOMATIK shu Yo'nalishga tegishli bo'ladi (select orqali
        // baribir o'zgartirish mumkin).
        fieldId: typeof pageFieldId === "number" ? pageFieldId : null,
        originalFieldId: null,
        mode: "NEW"
    });

    focusIndex = itemBlock.length - 1;
    render();
}

function saveOnClientSide(i) {
    const s = itemBlock[i];
    newName = s.name.trim();


    if (newName === "") {
        showAlertModal('❌ Bo\'lim nomi bo\'sh bo\'lishi mumkin emas!');
        focusIndex = i;
        console.error("Bo'lim nomi bo\'sh bo\'lishi mumkin emas!");

        return;
    }

    // проверка дубликатов на фронте
    if (hasDuplicate(i, newName)) {
        showAlertModal('❌ Bu bo\'lim nomi allaqachon mavjud!');
        focusIndex = i;
        console.log("hasDuplicate = true");
        return;
    }

    s.name = newName;
    itemBlock[i].mode = "VIEW";

    render();

    // Сохраняем текущее значение как оригинальное для будущих сравнений
    // s.original = name;

    // Определяем тип операции
    if (newName === oldName) {
        showToast('info', 'O\'zgarish bo\'lmadi', 3000);
    }

    if (s.id < 0) {
        if (newName === oldName) {
            showToast('info', 'O\'zgarish bo\'lmadi', 3000);
        } else {
            showToast('info', 'Yangi bo\'lim o\'zgardi', 3000);
        }
        showToast('success', 'Yangi bo\'lim saqlandi \n\n(bazaga saqlash uchun "Bazaga saqlash" tugmasini bosing)', 3000);
    } else {
        // Существующая запись из БД
        if (newName === oldName) {
            showToast('warm', 'O\'zgarish bo\'lmadi', 3000);
        } else {
            showToast('success', 'Bo\'lim muvaffaqiyatli saqlandi', 3000);
        }

    }
    oldName = "";
    newName = "";
}

async function saveToDb() {

    // Запрет: есть незавершённые записи
    if (itemBlock.some(s => s.mode !== "VIEW")) {
        showAlertModal('❌ Avval tahrirlashni yakuniga yetkazing!');
        focusIndex = itemBlock.findIndex(s => s.mode !== "VIEW");
        render();
        return;
    }

    // Формируем payload
    const payload = {
        // fieldId — IXTIYORIY (foydalanuvchi so'rovi bo'yicha Yo'nalish
        // majburiy emas). "new" endi {name, fieldId} obyekti (ilgari
        // oddiy string edi) — ScienceController#saveScience shu formatni
        // kutadi.
        new: itemBlock
            .filter(s => s.id < 0)
            .map(s => ({name: s.name, fieldId: s.fieldId})),

        // Nom O'ZGARGAN bo'lsa HAM, YOKI faqat Yo'nalish o'zgargan bo'lsa
        // HAM — "updated"ga tushadi (s.original bilan bir xil dirty-check
        // g'oyasi, faqat fieldId uchun originalFieldId).
        updated: itemBlock
            .filter(s => s.id > 0 && (s.name !== s.original || s.fieldId !== s.originalFieldId))
            .map(s => (
                {id: s.id, name: s.name, fieldId: s.fieldId}
            )),

        deletedIds: deletedSubjectIds
    };

    // Если нечего сохранять — выходим
    if (
        payload.new.length === 0 &&
        payload.updated.length === 0 &&
        deletedSubjectIds.length === 0) {
        showAlertModal('ℹ️ Saqlash uchun o‘zgarishlar yo‘q');
        return;
    }

    // 5. Подтверждение
    const confirmed = await showConfirmModal(
        `Yangi: ${payload.new.length} ta\n` +
        `O\'zgartirilgan: ${payload.updated.length} ta\n\n` +
        `O\'chirilgan: ${deletedSubjectIds.length} ta\n\n` +
        `Saqlashni xohlaysizmi?`
    );
    if (!confirmed) return;

    try {
        showToast('info', 'Maʼlumotlar bazaga saqlanmoqda...', 5000);

        // 6. Отправка в backend
        const response = await fetch("/api/science/save",
            {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(payload)
            });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || "Server xatosi");
        }

        // Успешное сообщение
        showToast(
            'success',
            `Saqlandi: yangi — ${payload.new.length}, \n
            o‘zgartirilgan — ${payload.updated.length}, \n\n
            o'chirilgan - ${deletedSubjectIds.length} ta`,
            5000
        );

        // 🔑 КЛЮЧЕВОЕ МЕСТО — ПОЛНАЯ СИНХРОНИЗАЦИЯ С БД
        deletedSubjectIds = [];
        await reloadAll("/api/science");
        focusIndex = 0;
        render(); // ❗ shu qator yo'q edi — shuning uchun DB yangilangan, lekin ekran eskicha qolardi
        refreshScienceTrashBadge();

    } catch (err) {
        console.error(err);
        showToast('error', err.message || 'Saqlashda xatolik', 7000);
        showAlertModal(err.message);
    }
}

/* ===== Yo'nalish CRUD (courses.js bilan bir xil andoza — /api/course-fields
   endpoint'lari UMUMIY, Kurslar VA TEST BOSHQARUVI uchun bitta Yo'nalish
   ro'yxati, foydalanuvchi so'rovi 2026-09-05) ===== */

function openCreateFieldForm() {
    document.getElementById("createFieldForm").style.display = "flex";
}

function closeCreateFieldForm() {
    document.getElementById("createFieldForm").style.display = "none";
    document.getElementById("newFieldName").value = "";
}

async function submitCreateField() {
    const name = document.getElementById("newFieldName").value.trim();
    if (!name) {
        showAlertModal("❌ Yo'nalish nomini kiriting");
        return;
    }

    try {
        const res = await fetch("/api/course-fields", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "Yo'nalish yaratishda xatolik");
            return;
        }

        closeCreateFieldForm();
        await reloadAll("/api/science");
        render();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

async function renameFieldPrompt(fieldId) {
    const field = allFields.find(f => f.id === fieldId);
    const newName = await showPromptModal("Yangi Yo'nalish nomi:", field ? field.name : "");
    if (newName == null) return; // bekor qilindi
    if (!newName.trim()) {
        showAlertModal("❌ Yo'nalish nomi bo'sh bo'lishi mumkin emas");
        return;
    }

    try {
        const res = await fetch(`/api/course-fields/${fieldId}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name: newName.trim() })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "Nomini o'zgartirishda xatolik");
            return;
        }
        await reloadAll("/api/science");
        render();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

async function deleteFieldPrompt(fieldId, fieldName) {
    if (!await showConfirmModal(`"${fieldName}" Yo'nalishini o'chirmoqchimisiz?\n\n(Faqat bo'sh — hech qanday kursi/bo'limi yo'q Yo'nalishni o'chirish mumkin.)`, { danger: true })) return;

    try {
        const res = await fetch(`/api/course-fields/${fieldId}`, { method: "DELETE" });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "O'chirishda xatolik");
            return;
        }
        expandedFieldKeys.delete(String(fieldId));
        await reloadAll("/api/science");
        render();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "⬆⬇" — Yo'nalish "box"ini boshqa Yo'nalish bilan o'rin almashtiradi
// (CourseFieldService.reorderFields — TO'LIQ ID ro'yxati kutiladi).
async function moveField(fieldId, direction) {
    const realFields = [...allFields].sort((a, b) => a.orderIndex - b.orderIndex);
    const pos = realFields.findIndex(f => f.id === fieldId);
    const newPos = pos + direction;
    if (newPos < 0 || newPos >= realFields.length) return;

    [realFields[pos], realFields[newPos]] = [realFields[newPos], realFields[pos]];
    const orderedIds = realFields.map(f => f.id);

    try {
        const res = await fetch("/api/course-fields/reorder", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(orderedIds)
        });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Tartibni o'zgartirishda xatolik");
            return;
        }
        await reloadAll("/api/science");
        render();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

//===========================================================================
//          BACK tugmasini bosganda ishlaydi.
// ===========================================================================
const
    focusId =
        Number(new URLSearchParams(window.location.search).get("focus"));

if (focusId) {
    focusIndex = itemBlock.findIndex(s => s.id === focusId);
}
render();

//===========================================================================



