// ========================================================================
//                     Global fields
// ========================================================================

let itemBlock = []; // сюда будут загружены данные из БД
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

// "🔍 Bo'lim ichida qidiruv" tugmasi belgisi — EXCEL_ICON_SVG/WORD_ICON_SVG
// bilan bir xil andoza (28x28, topic-export-btn ichida ishlatiladi).
const SEARCH_ICON_SVG = `<svg width="22" height="22" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg">
    <circle cx="10.5" cy="10.5" r="6.5" stroke="currentColor" stroke-width="2.2"/>
    <line x1="15.3" y1="15.3" x2="20.5" y2="20.5" stroke="currentColor" stroke-width="2.2" stroke-linecap="round"/>
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
    document.getElementById("scienceTrashModal").classList.toggle("show", scienceTrashOpen);
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
            <div class="trash-row">
                <div class="trash-row-info">${escapeHtml(s.name)} — ${formatScienceTrashDate(s.deletedAt)}da o'chirilgan</div>
                <div class="trash-row-actions">
                    <button class="restore-btn" onclick="restoreScienceFromTrash(${s.id})">♻️ Tiklash</button>
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

// Sahifa yuqorisidagi "← Yo'nalishlar" panelini pageFieldId'ga qarab
// to'ldiradi/yashiradi ("+ Yangi Yo'nalish" tugmasi ham shu bitta
// Yo'nalish ichida ma'nosiz — yashiriladi, Yo'nalish boshqaruvi endi
// /science/fields sahifasida). DIQQAT: <h1> ATAYLAB har doim oddiy
// "Bo'limlar" bo'lib qoladi (Yo'nalish nomi bilan TO'LDIRILMAYDI) —
// foydalanuvchi so'rovi, 2026-09-05: nom "← Yo'nalishlar/<nom>"
// panelida allaqachon ko'rinadi, <h1>'da va pastdagi accordion
// sarlavhasida takrorlanmasin (ilgari BIR XIL nom 3 marta chiqardi).
function applyFieldScope() {
    if (pageFieldId === undefined) return; // eski, filtrsiz rejim

    const bar = document.getElementById("fieldScopeBar");
    const nameEl = document.getElementById("fieldScopeName");
    const createBtn = document.getElementById("createFieldBtn");
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
        // originalFieldId — saveOnClientSide() dirty-check uchun (s.original
        // bilan bir xil g'oya, faqat Yo'nalish uchun).
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
    // bir xil g'oya (addToGroup()/edit() dan keyin ham shu orqali ishlaydi).
    if (focusIndex !== null && itemBlock[focusIndex]) {
        expandedFieldKeys.add(fieldKeyOf(itemBlock[focusIndex]));
    }

    const groups = getSortedFieldGroups();

    // fieldId bilan (bitta Yo'nalish ichida) ko'rsatilganda — accordion
    // "box" (chevron/sarlavha) UMUMAN kerak emas, chunki doim faqat
    // BITTA guruh bor va u allaqachon "← Yo'nalishlar/<nom>" panelida
    // to'liq tasvirlangan (nomi, ✏️/🗑️'si) — shu sabab qatorlar
    // TO'G'RIDAN-TO'G'RI, hech qanday o'rovchisiz chiziladi (foydalanuvchi
    // so'rovi, 2026-09-05: "belgilangan joyni olib tashla" — bo'sh
    // qolib ketgan sarlavha qatori olib tashlandi).
    if (pageFieldId !== undefined) {
        const group = groups[0];
        list.innerHTML = group && group.items.length
            ? group.items.map(i => renderRowHtml(itemBlock[i], i)).join("")
            : `<div class="courses-empty">Hali bo'lim yo'q</div>`;
    } else {
        const realFieldGroups = groups.filter(g => g.fieldId != null);
        list.innerHTML = groups.map(g => renderFieldGroupBox(g, realFieldGroups)).join("");
    }
    renderPageTitleActions(groups);

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

// fieldId bilan (bitta Yo'nalish ichida) ko'rsatilganda — "(N ta bo'lim)"
// va "➕" endi <h1>"Bo'limlar" bilan BIR QATORDA, o'ng tomonda chiqadi
// (pastdagi accordion sarlavhasidan olib tashlangan — foydalanuvchi
// so'rovi, 2026-09-05). fieldId'siz (eski, filtrsiz) sahifada bu qator
// bo'sh/yashirin qoladi — u yerda BIR NECHTA guruh bo'lishi mumkin, shu
// sabab "yagona" yuqori tugma ma'nosiz (har bir guruh o'zining ➕'sini
// saqlab qoladi, renderFieldGroupBox).
function renderPageTitleActions(groups) {
    const el = document.getElementById("pageTitleActions");
    if (!el) return;

    if (pageFieldId === undefined) {
        el.classList.add("hidden");
        el.innerHTML = "";
        return;
    }

    const group = groups[0]; // scoped rejimda har doim ANIQ bitta guruh
    const count = group ? group.items.length : 0;
    const fieldIdArg = pageFieldId === null ? "null" : pageFieldId;

    el.innerHTML = `
        <span class="chapter-box-count">(${count} ta bo'lim)</span>
        <button class="add-primary-btn" onclick="addToGroup(${fieldIdArg})" title="Yangi bo'lim qo'shish">➕ Yangi bo'lim</button>
    `;
    el.classList.remove("hidden");
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

    // "+Add" global tugmasi o'rniga — har bir Yo'nalish qutisining O'Z
    // action'lariga ➕ qo'shildi (foydalanuvchi so'rovi, 2026-09-05):
    // bosilganda yangi Bo'lim DARHOL shu Yo'nalishga (yoki "Yo'nalishsiz"
    // psevdo-guruhga) tegishli holda ochiladi. pageFieldId bilan (bitta
    // Yo'nalish ichida) ko'rsatilganda — bu tugma BU YERDA emas, <h1>
    // "Bo'limlar" bilan bir qatorda chiqadi (renderPageTitleActions).
    const addBtn = pageFieldId !== undefined ? "" :
        `<button class="chapter-rename-btn" onclick="event.stopPropagation(); addToGroup(${group.fieldId != null ? group.fieldId : "null"})" title="Bu Yo'nalishga bo'lim qo'shish">➕</button>`;

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

    // pageFieldId bilan (bitta Yo'nalish ichida) ko'rsatilganda — guruh
    // NOMI bu yerda TAKRORLANMAYDI (allaqachon "← Yo'nalishlar" panelida
    // ko'rinadi), "(N ta bo'lim)" soni ham bu yerda emas, <h1> qatorida
    // (renderPageTitleActions) — foydalanuvchi so'rovi, 2026-09-05.
    const nameHtml = pageFieldId !== undefined ? "" : escapeHtml(group.name);
    const countHtml = pageFieldId !== undefined ? "" : `<span class="chapter-box-count">(${group.items.length} ta bo'lim)</span>`;

    return `
        <div class="chapter-box ${isExpanded ? "expanded" : "collapsed"}">
            <h3 class="chapter-box-title" onclick="toggleFieldBox('${group.key}')" title="${isExpanded ? "Yig'ish" : "Ochish"}">
                <span class="chapter-box-chevron">▸</span>
                🧭 ${nameHtml}
                ${countHtml}
                <span class="chapter-box-actions">${addBtn}${moveBtns}${renameBtn}${deleteBtn}</span>
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
        onclick="openTopics(${s.id})"
        oncontextmenu="event.preventDefault(); moveFocus(${i});"
        onkeydown="onViewKeyDown(event, ${i})"
        title="Enter — Мавзуларни очиш | ↑ ↓ — навигация | Home/End — биринчи/охирги | Ўнг тугма — ичига кирмасдан белгилаш"
    >
        <div
            id="input-${i}"
            class="topic-name ${inputClass}"
            tabindex="-1"
        ><div class="item-title-row"><span class="item-title-text">${escapeHtml(s.name)}</span>${isLink ? `<span class="item-count-badge">${s.sectionCount} ta mavzu</span>` : ""}${isLink ? `<button class="topic-export-btn" onclick="event.stopPropagation(); openScienceSearchModal(${s.id})" title="Shu bo'limdagi savollar orasidan qidirish">${SEARCH_ICON_SVG}</button>` : ""}${isLink ? `<button class="topic-export-btn" onclick="event.stopPropagation(); exportScienceQuestions(${s.id})" title="Shu bo'limdagi barcha darslarning testlarini Excel'ga eksport qilish">${EXCEL_ICON_SVG}</button>` : ""}${isLink ? `<button class="topic-export-btn" onclick="event.stopPropagation(); openWordExportModal(${s.id})" title="Shu bo'limdagi barcha darslarning testlarini Word'ga eksport qilish">${WORD_ICON_SVG}</button>` : ""}</div></div>
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
// "oninput" bilan bir xil g'oya) — haqiqiy saqlash "💾 Save" bosilganda
// (saveOnClientSide — bazaga DARHOL yoziladi).
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

// ========================================================================
//        "🔍 Bo'lim ichida qidiruv" — modal, science.html'da joylashgan
// ========================================================================
// Qator tugmasi bosilganda openScienceSearchModal aynan qaysi FAN (Bo'lim)
// uchun ekanini saqlab qo'yadi (wordExportScienceId bilan bir xil g'oya).
// Har bir kiritishda YANGI so'rov yubormaslik uchun oddiy debounce
// (300ms) — server har harfda emas, foydalanuvchi yozishni tugatgach
// so'raladi.
let scienceSearchScienceId = null;
let scienceSearchDebounceTimer = null;
let scienceSearchRequestSeq = 0;

function openScienceSearchModal(scienceId) {
    scienceSearchScienceId = scienceId;
    const input = document.getElementById("scienceSearchInput");
    input.value = "";
    document.getElementById("scienceSearchResults").innerHTML =
        `<p class="science-search-hint">Qidirish uchun yuqoriga yozing.</p>`;
    document.getElementById("scienceSearchModal").classList.add("show");
    setTimeout(() => input.focus(), 50);
}

function closeScienceSearchModal() {
    document.getElementById("scienceSearchModal").classList.remove("show");
    clearTimeout(scienceSearchDebounceTimer);
}

function onScienceSearchInput() {
    clearTimeout(scienceSearchDebounceTimer);
    scienceSearchDebounceTimer = setTimeout(runScienceSearch, 300);
}

async function runScienceSearch() {
    const query = document.getElementById("scienceSearchInput").value.trim();
    const resultsBox = document.getElementById("scienceSearchResults");

    if (!query) {
        resultsBox.innerHTML = `<p class="science-search-hint">Qidirish uchun yuqoriga yozing.</p>`;
        return;
    }

    // Так как so'rovlar tarmoq kechikishi sabab har xil tartibda qaytishi
    // mumkin — faqat ENG OXIRGI so'rov natijasi ko'rsatiladi.
    const mySeq = ++scienceSearchRequestSeq;
    resultsBox.innerHTML = `<p class="science-search-hint">Qidirilmoqda...</p>`;

    try {
        const res = await fetch(`/api/question/search-by-science?scienceId=${scienceSearchScienceId}&query=${encodeURIComponent(query)}`);
        if (mySeq !== scienceSearchRequestSeq) return;

        if (!res.ok) {
            resultsBox.innerHTML = `<p class="science-search-hint">Qidirishda xatolik yuz berdi.</p>`;
            return;
        }

        const list = await res.json();
        if (mySeq !== scienceSearchRequestSeq) return;

        renderScienceSearchResults(list);
    } catch (e) {
        if (mySeq !== scienceSearchRequestSeq) return;
        console.error(e);
        resultsBox.innerHTML = `<p class="science-search-hint">Tarmoq xatoligi.</p>`;
    }
}

function renderScienceSearchResults(list) {
    const resultsBox = document.getElementById("scienceSearchResults");

    if (!list || list.length === 0) {
        resultsBox.innerHTML = `<p class="science-search-hint">Hech narsa topilmadi.</p>`;
        return;
    }

    resultsBox.innerHTML = list.map(q => `
        <div class="science-search-result-row" tabindex="0"
             onclick="goToScienceSearchResult(${q.topicId}, ${q.sectionId ?? 'null'})"
             onkeydown="if(event.key==='Enter') goToScienceSearchResult(${q.topicId}, ${q.sectionId ?? 'null'})">
            <div class="science-search-result-text">${escapeHtml(q.questionText)}</div>
            <div class="science-search-result-meta">${escapeHtml(q.topicName)}${q.sectionName ? ` · ${escapeHtml(q.sectionName)}` : ""}</div>
        </div>
    `).join("");
}

// Natijaga bosilganda — TEST BOSHQARUVIga (topics.html) aynan shu
// Darsning haqiqiy Mavzusi bilan filtrlangan holda va unga fokus
// qilingan holda o'tkazadi (topic.js#afterStartPage "?focus=" ni allaqachon
// qo'llab-quvvatlaydi — topic.js#goToTopicInManagement bilan bir xil g'oya).
function goToScienceSearchResult(topicId, sectionId) {
    const params = new URLSearchParams({ scienceId: scienceSearchScienceId, focus: topicId });
    if (sectionId) params.set("sectionId", sectionId);
    window.location.href = `/topics?${params}`;
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

// Foydalanuvchi so'rovi, 2026-09-05: "Save to DB" tugmasi olib
// tashlandi — o'chirish DARHOL bazaga yoziladi (DELETE /science/{id} —
// mavjud yagona-elementli endpoint).
async function removeFromUi(i) {
    if (itemBlock[i].mode === "NEW") {
        itemBlock.splice(i, 1);
        render();
        return;
    }
    const s = itemBlock[i];
    const subjectName = s.name || "Bu bo'lim";
    const confirmDelete = await showConfirmModal(`⚠️ "${subjectName}"ni o'chirishni tasdiqlaysizmi?\n\nBu amalni bekor qilib bo'lmaydi.`, { danger: true });
    if (!confirmDelete) {
        cancel(i);
        return;
    }

    try {
        const res = await fetch(`/science/${s.id}`, {method: "DELETE"});
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "O'chirishda xatolik");
            return;
        }
        showToast('success', `"${subjectName}" o'chirildi`, 2000);
        await reloadAll("/api/science");
        render();
        refreshScienceTrashBadge();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
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

// "+ Add" global tugmasi o'rniga — har bir Yo'nalish qutisining o'z ➕
// tugmasi (foydalanuvchi so'rovi, 2026-09-05). fieldId — shu tugma
// qaysi guruhga tegishli bo'lsa, o'sha (yoki "Yo'nalishsiz" psevdo-guruh
// uchun null). Foydalanuvchi so'rovi, 2026-09-05: "янги бўлим қўшиш
// модалда бўлсин" — sahifa ichida inline qator ochish o'rniga endi
// markazlashtirilgan MODAL (showAddBolimModal) orqali, tasdiqlansa
// DARHOL bazaga yoziladi (itemBlock'ga vaqtinchalik NEW-rejim qator
// qo'shilmaydi — to'g'ridan-to'g'ri /api/science/save).
async function addToGroup(fieldId) {
    if (itemBlock.some(s => s.mode === "EDIT")) {
        showToast('warning', 'Avval tahrirlashni yakuniga yetkazing!');
        focusIndex = itemBlock.findIndex(s => s.mode !== "VIEW");
        render();
        return;
    }

    const result = await showAddBolimModal(fieldId);
    if (result == null) return; // bekor qilindi

    const name = result.name.trim();
    if (!name) {
        showAlertModal('❌ Bo\'lim nomi bo\'sh bo\'lishi mumkin emas!');
        return;
    }
    if (hasDuplicate(-1, name)) {
        showAlertModal('❌ Bu bo\'lim nomi allaqachon mavjud!');
        return;
    }

    try {
        const res = await fetch("/api/science/save", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({new: [{name, fieldId: result.fieldId}], updated: [], deletedIds: []})
        });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.message || data.error || "Saqlashda xatolik");
            return;
        }
        showToast('success', `"${name}" saqlandi`, 2000);

        expandedFieldKeys.add(result.fieldId != null ? String(result.fieldId) : "none");
        await reloadAll("/api/science");
        focusIndex = itemBlock.findIndex(x => x.name === name);
        render();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "+ Yangi bo'lim" modali — nom (matn) + Yo'nalish (select) birgalikda,
// showPromptModal (promptModal.js) faqat BITTA matn maydonini qo'llab-
// quvvatlagani uchun shu sahifaga xos alohida qurilgan, lekin AYNAN bir
// xil CSS klasslaridan foydalanadi (bir xillik uchun). defaultFieldId —
// qaysi Yo'nalish qutisining ➕'si bosilgan bo'lsa, select'da OLDINDAN
// shu tanlangan holda ochiladi (baribir o'zgartirish mumkin).
function showAddBolimModal(defaultFieldId) {
    injectPromptModalStyles();

    return new Promise((resolve) => {
        const overlay = document.createElement("div");
        overlay.className = "prompt-modal-overlay";

        const box = document.createElement("div");
        box.className = "prompt-modal-box";

        const messageEl = document.createElement("p");
        messageEl.className = "prompt-modal-message";
        messageEl.textContent = "Yangi bo'lim nomini kiriting:";

        const input = document.createElement("textarea");
        input.className = "prompt-modal-input";
        input.rows = 2;
        input.style.resize = "vertical";
        input.placeholder = "Yangi bo'lim nomini kiriting";

        const select = document.createElement("select");
        select.className = "field-select";
        select.title = "Yo'nalish";
        const options = [`<option value="">— Yo'nalishsiz —</option>`]
            .concat([...allFields].sort((a, b) => a.orderIndex - b.orderIndex)
                .map(f => `<option value="${f.id}" ${defaultFieldId === f.id ? "selected" : ""}>${escapeHtml(f.name)}</option>`));
        select.innerHTML = options.join("");

        const actions = document.createElement("div");
        actions.className = "prompt-modal-actions";

        const cancelBtn = document.createElement("button");
        cancelBtn.type = "button";
        cancelBtn.className = "prompt-modal-cancel";
        cancelBtn.textContent = "Bekor qilish";

        const okBtn = document.createElement("button");
        okBtn.type = "button";
        okBtn.className = "prompt-modal-ok";
        okBtn.textContent = "✅ Yaratish";

        actions.append(cancelBtn, okBtn);
        box.append(messageEl, input, select, actions);
        overlay.appendChild(box);
        document.body.appendChild(overlay);

        let settled = false;
        function close(result) {
            if (settled) return;
            settled = true;
            document.removeEventListener("keydown", onKeyDown);
            overlay.remove();
            resolve(result);
        }

        function submit() {
            close({name: input.value, fieldId: select.value ? Number(select.value) : null});
        }

        function onKeyDown(e) {
            if (e.key === "Escape") close(null);
            if (e.key === "Enter" && !e.shiftKey && document.activeElement === input) {
                e.preventDefault();
                submit();
            }
        }

        cancelBtn.onclick = () => close(null);
        okBtn.onclick = submit;
        overlay.onclick = (e) => { if (e.target === overlay) close(null); };
        document.addEventListener("keydown", onKeyDown);

        input.focus();
    });
}

// Foydalanuvchi so'rovi, 2026-09-05: "Save to DB" tugmasi olib
// tashlandi — "💾 Save" bosilganda (yoki Enter) o'zgarish DARHOL
// bazaga yoziladi (/api/science/save'ga BITTA elementli new/updated
// bilan — bo'sh massivlar bilan chaqirilsa ham backend xavfsiz ishlaydi).
async function saveOnClientSide(i) {
    const s = itemBlock[i];
    const newNameVal = s.name.trim();

    if (newNameVal === "") {
        showAlertModal('❌ Bo\'lim nomi bo\'sh bo\'lishi mumkin emas!');
        focusIndex = i;
        return;
    }

    if (hasDuplicate(i, newNameVal)) {
        showAlertModal('❌ Bu bo\'lim nomi allaqachon mavjud!');
        focusIndex = i;
        return;
    }

    const isNew = s.id < 0;
    const nameChanged = newNameVal !== s.original;
    const fieldChanged = s.fieldId !== s.originalFieldId;

    if (!isNew && !nameChanged && !fieldChanged) {
        s.mode = "VIEW";
        render();
        showToast('info', "O'zgarish bo'lmadi", 2000);
        return;
    }

    const payload = isNew
        ? {new: [{name: newNameVal, fieldId: s.fieldId}], updated: [], deletedIds: []}
        : {new: [], updated: [{id: s.id, name: newNameVal, fieldId: s.fieldId}], deletedIds: []};

    try {
        const res = await fetch("/api/science/save", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(payload)
        });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.message || data.error || "Saqlashda xatolik");
            return;
        }
        showToast('success', isNew ? `"${newNameVal}" saqlandi` : "Bo'lim saqlandi", 2000);

        await reloadAll("/api/science");
        focusIndex = itemBlock.findIndex(x => x.name === newNameVal);
        render();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
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



