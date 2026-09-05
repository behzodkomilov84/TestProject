// ========================================================================
// "Yo'nalishlar" — TEST BOSHQARUVIning ENG BIRINCHI sahifasi (foydalanuvchi
// so'rovi, 2026-09-05: "Test boshqaruvi"ni bosganda avval shu sahifa
// ochilsin, kurslardagi bilan bir xil Yo'nalish -> Bo'lim -> Mavzu -> Dars
// ierarxiyasi uchun). Yo'nalish CRUD funksiyalari (submitCreateField/
// renameFieldPrompt/deleteFieldPrompt/moveField — barchasi showPromptModal
// markazlashtirilgan modali orqali) science.js/courses.js'dagi bilan AYNAN
// bir xil andoza — /api/course-fields UMUMIY
// endpoint (kurslar VA TEST BOSHQARUVI uchun), shu sabab ataylab
// nusxalangan (loyihadagi mavjud "har bir sahifa mustaqil kichik JS fayl"
// konvensiyasiga ko'ra), faqat re-render() logikasi accordion emas,
// oddiy karta ro'yxati.
// ========================================================================

let allFields = [];
// Yo'nalishga hali biriktirilmagan (fieldId=null) Bo'limlar soni — ular
// uchun ham alohida "— Yo'nalishsiz bo'limlar —" kartasi ko'rsatiladi
// (Yo'nalish IXTIYORIY bo'lgani uchun har doim bo'lishi mumkin).
let unassignedCount = 0;

// science.js#applyFieldScope'dan "← Yo'nalishlar" bosilganda "?focus=<id>"
// (yoki "none") beriladi — o'sha Yo'nalish kartasi ajratib ko'rsatiladi
// (courses.js#focusCourseId bilan bir xil g'oya, foydalanuvchi so'rovi
// 2026-09-05).
let focusFieldKey = new URLSearchParams(window.location.search).get("focus");

// Klaviatura navigatsiyasi (←→/↑↓/Home/End) va o'ng tugma bilan
// belgilash uchun — courses.js#selectedCourseId bilan bir xil g'oya
// (foydalanuvchi so'rovi, 2026-09-05: "Yo'nalishlar sahifasida ham
// xuddi shunday tekshirib chiq"). ".field-card.selected" CSS klassi
// orqali ko'rsatiladi.
let selectedFieldKey = null;

// Sahifa birinchi ochilganda — bir marta: "?focus=" bo'lsa o'sha
// kartaga, bo'lmasa BIRINCHI kartaga default tanlov qo'yiladi
// (courses.js#pendingDefaultCourseSelectionApplied bilan bir xil g'oya).
let pendingDefaultFieldSelectionApplied = false;

document.addEventListener("DOMContentLoaded", () => {
    loadAndRender();
});

async function loadAndRender() {
    await Promise.all([loadFields(), loadUnassignedCount()]);
    render();
}

async function loadFields() {
    try {
        const res = await fetch("/api/course-fields");
        allFields = res.ok ? await res.json() : [];
    } catch (err) {
        console.error(err);
        allFields = [];
    }
}

// Bitta yengil so'rov bilan — /api/science to'liq ro'yxatini olib,
// fieldId=null bo'lganlarini sanaydi (alohida backend endpoint shart
// emas, ro'yxat kichik).
async function loadUnassignedCount() {
    try {
        const res = await fetch("/api/science");
        const data = res.ok ? await res.json() : [];
        unassignedCount = data.filter(s => s.fieldId == null).length;
    } catch (err) {
        console.error(err);
        unassignedCount = 0;
    }
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text ?? "";
    return div.innerHTML;
}

function render() {
    const list = document.getElementById("fieldsList");
    const sorted = [...allFields].sort((a, b) => a.orderIndex - b.orderIndex);

    let html = sorted.map((f, idx) => renderFieldCard(f, idx, sorted.length)).join("");

    if (unassignedCount > 0) {
        html += `
            <div class="field-card" id="field-card-none" tabindex="0"
                 onclick="selectFieldCard('none'); openField('none')"
                 oncontextmenu="event.preventDefault(); selectFieldCard('none');"
                 onkeydown="onFieldCardKeyDown(event, 'none')">
                <span class="kbd-hint-badge" onclick="event.stopPropagation(); toggleFieldKbdHint(this)" title="Klaviatura yorliqlari">⌨️</span>
                <div class="field-card-main">
                    <span class="field-card-icon">🧭</span>
                    <span class="field-card-name">— Yo'nalishsiz bo'limlar —</span>
                    <span class="field-card-count">${unassignedCount} ta bo'lim</span>
                </div>
            </div>
        `;
    }

    list.innerHTML = html || `<div class="courses-empty">Hali Yo'nalish yo'q — "+ Yangi Yo'nalish" tugmasi bilan qo'shing.</div>`;

    const countEl = document.getElementById("fieldsCount");
    if (countEl) countEl.textContent = `(${sorted.length} ta Yo'nalish)`;

    if (focusFieldKey != null) {
        const targetKey = focusFieldKey;
        // Faqat BIRINCHI render'da qo'llaniladi (courses.js#focusCourseId
        // bilan bir xil sabab).
        focusFieldKey = null;
        pendingDefaultFieldSelectionApplied = true;

        selectFieldCard(targetKey, { scroll: true });
        const card = document.getElementById(`field-card-${targetKey}`);
        if (card) {
            card.classList.add("field-card-focused");
            setTimeout(() => card.classList.remove("field-card-focused"), 2000);
        }
    } else if (!pendingDefaultFieldSelectionApplied) {
        // "?focus=" bo'lmasa — BIRINCHI kartaga default tanlov qo'yiladi
        // (courseDetail.js#selectFirstCardByDefault bilan bir xil g'oya).
        pendingDefaultFieldSelectionApplied = true;
        const ids = getFieldCardIds();
        if (ids.length > 0) selectFieldCard(ids[0]);
    }
}

// Ro'yxatdagi BARCHA navigatsiya qilinadigan kartalarning DOM tartibidagi
// kalitlari — haqiqiy Yo'nalishlar (orderIndex bo'yicha) + oxirida
// "— Yo'nalishsiz bo'limlar —" psevdo-kartasi (bo'lsa).
function getFieldCardIds() {
    const sorted = [...allFields].sort((a, b) => a.orderIndex - b.orderIndex);
    const ids = sorted.map(f => String(f.id));
    if (unassignedCount > 0) ids.push("none");
    return ids;
}

// Kartani "tanlangan" deb belgilaydi — courses.js#selectCourseCard bilan
// bir xil g'oya, faqat bu yerda accordion/yashirin karta yo'q (barcha
// kartalar har doim DOM'da) — shu sabab qayta chizish shart emas.
function selectFieldCard(key, { scroll = false } = {}) {
    selectedFieldKey = key;
    document.querySelectorAll(".field-card.selected").forEach(x => x.classList.remove("selected"));
    const el = document.getElementById(`field-card-${key}`);
    if (el) {
        el.classList.add("selected");
        el.focus({ preventScroll: !scroll });
        if (scroll) el.scrollIntoView({ behavior: "smooth", block: "center" });
    }
}

function moveFieldSelection(key, dir) {
    const ids = getFieldCardIds();
    const idx = ids.indexOf(key);
    if (idx === -1) return;
    const newIdx = idx + dir;
    if (newIdx < 0 || newIdx >= ids.length) return;
    selectFieldCard(ids[newIdx], { scroll: true });
}

function moveFieldToFirst() {
    const ids = getFieldCardIds();
    if (ids.length > 0) selectFieldCard(ids[0], { scroll: true });
}

function moveFieldToLast() {
    const ids = getFieldCardIds();
    if (ids.length > 0) selectFieldCard(ids[ids.length - 1], { scroll: true });
}

function onFieldCardKeyDown(event, key) {
    switch (event.key) {
        case "ArrowRight":
        case "ArrowDown":
            event.preventDefault();
            moveFieldSelection(key, 1);
            break;
        case "ArrowLeft":
        case "ArrowUp":
            event.preventDefault();
            moveFieldSelection(key, -1);
            break;
        case "Home":
            event.preventDefault();
            moveFieldToFirst();
            break;
        case "End":
            event.preventDefault();
            moveFieldToLast();
            break;
        case "Enter":
            event.preventDefault();
            openField(key);
            break;
    }
}

// "⌨️" belgisi bosilganda — klaviatura-yo'riqnoma pufakchasini
// ochadi/yopadi (courseDetail.js#toggleKbdHint bilan bir xil andoza).
function toggleFieldKbdHint(badgeEl) {
    const card = badgeEl.closest(".field-card");
    if (!card) return;
    const wasOpen = card.classList.contains("kbd-hint-open");
    document.querySelectorAll(".field-card.kbd-hint-open").forEach(el => el.classList.remove("kbd-hint-open"));
    if (!wasOpen) card.classList.add("kbd-hint-open");
}

document.addEventListener("click", (e) => {
    if (!e.target.closest(".kbd-hint-badge")) {
        document.querySelectorAll(".field-card.kbd-hint-open").forEach(el => el.classList.remove("kbd-hint-open"));
    }
});

function renderFieldCard(f, idx, total) {
    const upDisabled = idx === 0 ? "disabled" : "";
    const downDisabled = idx === total - 1 ? "disabled" : "";
    const isSelected = String(f.id) === selectedFieldKey;
    return `
        <div class="field-card ${isSelected ? "selected" : ""}" id="field-card-${f.id}" tabindex="0"
             onclick="selectFieldCard('${f.id}'); openField(${f.id})"
             oncontextmenu="event.preventDefault(); selectFieldCard('${f.id}');"
             onkeydown="onFieldCardKeyDown(event, '${f.id}')">
            <span class="kbd-hint-badge" onclick="event.stopPropagation(); toggleFieldKbdHint(this)" title="Klaviatura yorliqlari">⌨️</span>
            <div class="field-card-main">
                <span class="field-card-icon">🧭</span>
                <span class="field-card-name">${escapeHtml(f.name)}</span>
                <span class="field-card-count">${f.scienceCount} ta bo'lim</span>
            </div>
            <div class="field-card-actions">
                <button onclick="event.stopPropagation(); moveField(${f.id}, -1)" ${upDisabled} title="Yuqoriga">⬆</button>
                <button onclick="event.stopPropagation(); moveField(${f.id}, 1)" ${downDisabled} title="Pastga">⬇</button>
                <button onclick="event.stopPropagation(); renameFieldPrompt(${f.id})" title="Nomini tahrirlash">✏️</button>
                <button class="danger-btn" onclick="event.stopPropagation(); deleteFieldPrompt(${f.id}, ${JSON.stringify(f.name).replace(/"/g, "&quot;")})" title="O'chirish (faqat bo'sh bo'lsa)">🗑️</button>
            </div>
        </div>
    `;
}

function openField(fieldId) {
    window.location.href = `/science?fieldId=${fieldId}`;
}

function showToast(type, message, duration = 4000) {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;

    const icons = { success: '✅', error: '❌', warning: '⚠️', info: 'ℹ️' };

    toast.innerHTML = `
               <span class="toast-icon">${icons[type] || ''}</span>
               <span class="toast-message">${message}</span>
               <button class="toast-close" onclick="this.parentElement.remove()">❌</button>
           `;

    const container = document.getElementById('toast-container');
    container.appendChild(toast);

    setTimeout(() => {
        if (toast.parentElement) {
            toast.style.opacity = '0';
            setTimeout(() => toast.remove(), 300);
        }
    }, duration);

    return toast;
}

/* ===== Yo'nalish CRUD (science.js/courses.js bilan bir xil andoza — faqat
   yaratish endi inline forma emas, showPromptModal orqali markazlashtirilgan
   modal, foydalanuvchi so'rovi 2026-09-05: "Модал марказда кўринсин") ===== */

async function submitCreateField() {
    const name = await showPromptModal("Yangi Yo'nalish nomi:", "");
    if (name == null) return; // bekor qilindi
    if (!name.trim()) {
        showAlertModal("❌ Yo'nalish nomini kiriting");
        return;
    }

    try {
        const res = await fetch("/api/course-fields", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name: name.trim() })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "Yo'nalish yaratishda xatolik");
            return;
        }

        await loadAndRender();
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
        await loadAndRender();
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
        await loadAndRender();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

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
        await loadAndRender();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}
