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
            <div class="field-card" onclick="openField('none')">
                <div class="field-card-main">
                    <span class="field-card-icon">🧭</span>
                    <span class="field-card-name">— Yo'nalishsiz bo'limlar —</span>
                    <span class="field-card-count">${unassignedCount} ta bo'lim</span>
                </div>
            </div>
        `;
    }

    list.innerHTML = html || `<div class="courses-empty">Hali Yo'nalish yo'q — "+ Yangi Yo'nalish" tugmasi bilan qo'shing.</div>`;
}

function renderFieldCard(f, idx, total) {
    const upDisabled = idx === 0 ? "disabled" : "";
    const downDisabled = idx === total - 1 ? "disabled" : "";
    return `
        <div class="field-card" onclick="openField(${f.id})">
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
        alert("❌ Yo'nalish nomini kiriting");
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
            alert(data.error || "Yo'nalish yaratishda xatolik");
            return;
        }

        await loadAndRender();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

async function renameFieldPrompt(fieldId) {
    const field = allFields.find(f => f.id === fieldId);
    const newName = await showPromptModal("Yangi Yo'nalish nomi:", field ? field.name : "");
    if (newName == null) return; // bekor qilindi
    if (!newName.trim()) {
        alert("❌ Yo'nalish nomi bo'sh bo'lishi mumkin emas");
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
            alert(data.error || "Nomini o'zgartirishda xatolik");
            return;
        }
        await loadAndRender();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

async function deleteFieldPrompt(fieldId, fieldName) {
    if (!confirm(`"${fieldName}" Yo'nalishini o'chirmoqchimisiz?\n\n(Faqat bo'sh — hech qanday kursi/bo'limi yo'q Yo'nalishni o'chirish mumkin.)`)) return;

    try {
        const res = await fetch(`/api/course-fields/${fieldId}`, { method: "DELETE" });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            alert(data.error || "O'chirishda xatolik");
            return;
        }
        await loadAndRender();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
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
            alert(data.error || "Tartibni o'zgartirishda xatolik");
            return;
        }
        await loadAndRender();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}
