const ROLE = document.body.dataset.role;
// OWNER barcha kurslarni, ADMIN esa faqat o'zi yaratgan kurslarni
// boshqara oladi — ikkalasi ham "+ Yangi kurs yaratish" VA "+ Yangi
// Yo'nalish" tugmalarini ko'rishi kerak (foydalanuvchi so'rovi bo'yicha,
// 2026-09-04 — Yo'nalish CRUD huquqi kurs yaratish bilan bir xil).
const CAN_CREATE_COURSE = ROLE === "ROLE_OWNER" || ROLE === "ROLE_ADMIN";

let allCourses = [];
let allFields = [];
// Qaysi Yo'nalish "box"lari ochiq — courseDetail.js#expandedChapterKeys
// bilan bir xil g'oya (bir nechtasi bir vaqtda ochiq turishi mumkin).
const expandedFieldKeys = new Set();

// Kurs sahifasidan (courseDetail.html) "← Kurslar" bosilganda "?focus=<id>"
// beriladi — shu Kursning Yo'nalish qutisi avtomatik ochiladi va o'sha
// kartaga skroll qilinadi (science.js#focusId bilan bir xil g'oya — aks
// holda "orqaga" bosilganda foydalanuvchi qaysi Yo'nalish ichida
// ekanini qayta qidirishga majbur bo'lardi).
let focusCourseId = Number(new URLSearchParams(window.location.search).get("focus")) || null;

document.addEventListener("DOMContentLoaded", () => {
    if (CAN_CREATE_COURSE) {
        document.querySelectorAll(".owner-only-el").forEach(el => el.style.display = "");
        refreshCourseTrashBadge();
    }
    loadCourses();
});

// Badge'ni (".notif-badge" — navbar.js#refreshUnreadCount bilan bir xil
// uslub) sonini yangilaydi — 0 bo'lsa yashiradi.
function refreshCourseTrashBadge() {
    fetch("/api/courses/deleted")
        .then(r => r.ok ? r.json() : [])
        .then(items => {
            const badge = document.getElementById("courseTrashBadge");
            if (!badge) return;
            if (items.length > 0) {
                badge.style.display = "inline-flex";
                badge.textContent = items.length > 99 ? "99+" : items.length;
            } else {
                badge.style.display = "none";
            }
        })
        .catch(err => console.error(err));
}

// Kurslar (Bo'limlar) VA Yo'nalishlar BIRGALIKDA yuklanadi — bo'sh
// (hali hech qanday kursi yo'q) Yo'nalish ham katalogda ko'rinishi kerak
// (faqat kurslar ro'yxatidan Yo'nalish ro'yxatini "chiqarib olish"
// bo'lmaydi, chunki bo'sh Yo'nalish hech qaysi kursda uchramaydi).
function loadCourses() {
    Promise.all([
        fetch("/api/courses").then(r => r.ok ? r.json() : []),
        fetch("/api/course-fields").then(r => r.ok ? r.json() : [])
    ])
        .then(([courses, fields]) => {
            allCourses = courses;
            allFields = fields;
            renderGroupedCourses();
        })
        .catch(err => {
            console.error(err);
            document.getElementById("coursesGrid").innerHTML =
                `<div class="courses-empty">Kurslarni yuklashda xatolik</div>`;
        });
}

// allCourses'ni Yo'nalish (field) bo'yicha guruhlab, tartib bo'yicha
// saralab qaytaradi — courseDetail.js#getSortedChapterGroups bilan bir
// xil andoza. "none" — hali hech qanday Yo'nalishga biriktirilmagan
// (eski, migratsiyadan oldingi) kurslar uchun psevdo-guruh.
function getSortedFieldGroups() {
    const groups = new Map();
    // AVVAL — BARCHA Yo'nalishlar (bo'sh bo'lsa ham) qo'shiladi, shu
    // bilan hali kursi yo'q Yo'nalish ham katalogda ko'rinadi.
    for (const f of allFields) {
        groups.set(String(f.id), { key: String(f.id), fieldId: f.id, name: f.name, orderIndex: f.orderIndex, items: [] });
    }

    for (const c of allCourses) {
        const key = c.fieldId != null ? String(c.fieldId) : "none";
        if (!groups.has(key)) {
            groups.set(key, {
                key,
                fieldId: c.fieldId,
                name: c.fieldId != null ? c.fieldName : "— Yo'nalishsiz kurslar —",
                orderIndex: c.fieldId != null ? Number.MAX_SAFE_INTEGER - 1 : Number.MAX_SAFE_INTEGER,
                items: []
            });
        }
        groups.get(key).items.push(c);
    }

    return [...groups.values()].sort((a, b) => a.orderIndex - b.orderIndex);
}

function renderGroupedCourses() {
    const grid = document.getElementById("coursesGrid");
    const groups = getSortedFieldGroups();

    if (groups.length === 0) {
        grid.innerHTML = `<div class="courses-empty">Hali Yo'nalishlar yo'q</div>`;
        return;
    }

    // focusCourseId'ni o'z ichiga olgan guruhni HTML qurishdan OLDIN
    // ochamiz (science.js#render bilan bir xil g'oya) — aks holda karta
    // DOM'da bo'lmay, scrollIntoView ishlamay qolardi.
    if (focusCourseId != null) {
        const focusGroup = groups.find(g => g.items.some(c => c.id === focusCourseId));
        if (focusGroup) expandedFieldKeys.add(focusGroup.key);
    }

    const realFieldGroups = groups.filter(g => g.fieldId != null);
    grid.innerHTML = groups.map(g => renderFieldBox(g, realFieldGroups)).join("");

    if (focusCourseId != null) {
        const card = document.getElementById(`course-card-${focusCourseId}`);
        if (card) {
            card.scrollIntoView({ behavior: "smooth", block: "center" });
            card.classList.add("course-card-focused");
            setTimeout(() => card.classList.remove("course-card-focused"), 2000);
        }
        // Faqat BIRINCHI render'da qo'llaniladi — keyingi qayta chizishlarda
        // (masalan boshqa Yo'nalishni ochish/yopish) foydalanuvchini
        // qaytadan shu kartaga tashlab yubormaslik uchun.
        focusCourseId = null;
    }
}

function toggleFieldBox(key) {
    if (expandedFieldKeys.has(key)) {
        expandedFieldKeys.delete(key);
    } else {
        expandedFieldKeys.add(key);
    }
    renderGroupedCourses();
}

function renderFieldBox(group, realFieldGroups) {
    const isExpanded = expandedFieldKeys.has(group.key);

    let bodyHtml = "";
    if (isExpanded) {
        const cardsHtml = group.items.length
            ? `<div class="courses-grid">${group.items.map(renderCourseCard).join("")}</div>`
            : `<div class="courses-empty">Bu Yo'nalishda hali kurs (Bo'lim) yo'q</div>`;
        bodyHtml = `<div class="chapter-box-body">${cardsHtml}</div>`;
    }

    // "✏️"/"🗑️" — faqat HAQIQIY Yo'nalishlarda (group.fieldId != null),
    // "— Yo'nalishsiz kurslar —" psevdo-guruhida ko'rsatilmaydi.
    const renameBtn = (CAN_CREATE_COURSE && group.fieldId != null)
        ? `<button class="chapter-rename-btn" onclick="event.stopPropagation(); renameFieldPrompt(${group.fieldId})" title="Yo'nalish nomini tahrirlash">✏️</button>`
        : "";
    const deleteBtn = (CAN_CREATE_COURSE && group.fieldId != null)
        ? `<button class="chapter-rename-btn danger-btn" onclick="event.stopPropagation(); deleteFieldPrompt(${group.fieldId}, ${JSON.stringify(group.name).replace(/"/g, "&quot;")})" title="Yo'nalishni o'chirish (faqat bo'sh bo'lsa)">🗑️</button>`
        : "";

    // Global "+ Yangi kurs" tugmasi o'rniga — har bir Yo'nalish qutisining
    // o'z ➕ tugmasi (science.js#addToGroup bilan bir xil g'oya,
    // foydalanuvchi so'rovi, 2026-09-05). Kurs Yo'nalishi MAJBURIY bo'lgani
    // uchun "— Yo'nalishsiz kurslar —" psevdo-guruhida ko'rsatilmaydi.
    const addBtn = (CAN_CREATE_COURSE && group.fieldId != null)
        ? `<button class="add-primary-btn" onclick="event.stopPropagation(); openCreateCourseForm(${group.fieldId})" title="Bu Yo'nalishga kurs qo'shish">➕</button>`
        : "";

    let moveBtns = "";
    if (CAN_CREATE_COURSE && group.fieldId != null && realFieldGroups.length > 1) {
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
                <span class="chapter-box-count">(bo'lim — ${group.items.length} ta)</span>
                <span class="chapter-box-actions">${addBtn}${moveBtns}${renameBtn}${deleteBtn}</span>
            </h3>
            ${bodyHtml}
        </div>
    `;
}

function renderCourseCard(c) {
    let badge;
    if (!c.published) {
        badge = `<span class="course-badge draft">Qoralama</span>`;
    } else if (c.free) {
        badge = `<span class="course-badge free">🆓 Bepul</span>`;
    } else if (c.subscribed) {
        badge = `<span class="course-badge subscribed">✅ Obuna bor</span>`;
    } else {
        // Narxi belgilangan bo'lsa — foydalanuvchi obuna so'rovini
        // yuborishdan oldin qancha to'lashini ko'rib turishi uchun.
        const priceText = c.price ? ` — ${formatPrice(c.price)} so'm` : "";
        badge = `<span class="course-badge locked">🔒 Obuna kerak${priceText}</span>`;
    }

    const cover = c.coverImageUrl
        ? `<img class="course-card-cover" src="${c.coverImageUrl}" alt="">`
        : `<div class="course-card-cover"></div>`;

    return `
        <div class="course-card" id="course-card-${c.id}" onclick="location.href='/courses/${c.id}'">
            ${cover}
            <div class="course-card-body">
                <h3 class="course-card-title">${escapeHtml(c.title)}</h3>
                <p class="course-card-desc">${escapeHtml(c.description || "")}</p>
                <div class="course-card-footer">
                    <span>${c.chapterCount} ta mavzu, ${c.sectionCount} ta dars</span>
                    ${badge}
                </div>
            </div>
        </div>
    `;
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
}

// "150000" -> "150 000" — minglik ajratkichi doim bo'shliq bo'lishi uchun
// (toLocaleString brauzer/OS lokaliga qarab boshqa belgi ishlatishi mumkin).
function formatPrice(price) {
    return String(Math.round(Number(price))).replace(/\B(?=(\d{3})+(?!\d))/g, " ");
}

/* ===== OWNER/ADMIN: Yo'nalish CRUD ===== */

// Ilgari inline forma (#createFieldForm) edi — endi science-fields.js bilan
// bir xil andoza: showPromptModal (sayt bo'ylab BARCHA prompt() o'rniga
// ishlatiladigan, markazlashtirilgan modal) orqali (foydalanuvchi so'rovi,
// 2026-09-05).
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

        loadCourses();
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
        loadCourses();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

async function deleteFieldPrompt(fieldId, fieldName) {
    if (!await showConfirmModal(`"${fieldName}" Yo'nalishini o'chirmoqchimisiz?\n\n(Faqat bo'sh — hech qanday kursi yo'q Yo'nalishni o'chirish mumkin.)`, { danger: true })) return;

    try {
        const res = await fetch(`/api/course-fields/${fieldId}`, { method: "DELETE" });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "O'chirishda xatolik");
            return;
        }
        loadCourses();
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
        loadCourses();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

/* ===== OWNER/ADMIN: kurs (Bo'lim) yaratish ===== */

// fieldId — qaysi Yo'nalish qutisining ➕ tugmasi bosilgan bo'lsa, o'sha
// select'da OLDINDAN tanlangan holda ochiladi (foydalanuvchi so'rovi,
// 2026-09-05: global "+ Yangi kurs" o'rniga har bir Yo'nalishning o'z
// tugmasi — science.js#addToGroup bilan bir xil g'oya).
function openCreateCourseForm(fieldId) {
    document.getElementById("createCourseForm").classList.add("show");
    onNewCourseFreeToggle();
    populateNewCourseFieldSelect();
    if (fieldId != null) {
        document.getElementById("newCourseField").value = String(fieldId);
    }
}

function closeCreateCourseForm() {
    document.getElementById("createCourseForm").classList.remove("show");
}

// Yangi kurs qaysi Yo'nalishga tegishli — MAJBURIY (foydalanuvchi
// so'rovi bo'yicha, 2026-09-04).
function populateNewCourseFieldSelect() {
    const select = document.getElementById("newCourseField");
    select.innerHTML = `<option value="">--Yo'nalishni tanlang--</option>` +
        allFields.map(f => `<option value="${f.id}">${escapeHtml(f.name)}</option>`).join("");
}

// "🆓 Bepul kurs" belgilansa — narx maydoni keraksiz, yashiriladi.
function onNewCourseFreeToggle() {
    const free = document.getElementById("newCourseFree").checked;
    document.getElementById("newCoursePriceField").style.display = free ? "none" : "block";
}

async function submitCreateCourse() {
    const title = document.getElementById("newCourseTitle").value.trim();
    const description = document.getElementById("newCourseDescription").value.trim();
    const fieldId = document.getElementById("newCourseField").value;
    const fileInput = document.getElementById("newCourseCoverFile");

    if (!title) {
        showAlertModal("❌ Kurs nomini kiriting");
        return;
    }
    if (!fieldId) {
        showAlertModal("❌ Yo'nalishni tanlang");
        return;
    }

    let coverImageUrl = null;

    try {
        if (fileInput.files[0]) {
            document.getElementById("newCourseCoverStatus").textContent = "Yuklanmoqda...";
            const formData = new FormData();
            formData.append("image", fileInput.files[0]);
            const uploadRes = await fetch("/api/courses/upload-cover", { method: "POST", body: formData });
            const uploadData = await uploadRes.json().catch(() => ({}));
            if (!uploadRes.ok) {
                showAlertModal(uploadData.error || "Rasm yuklashda xatolik");
                return;
            }
            coverImageUrl = uploadData.url;
        }

        const free = document.getElementById("newCourseFree").checked;
        const priceValue = document.getElementById("newCoursePrice").value;
        const price = !free && priceValue ? Number(priceValue) : null;

        const res = await fetch("/api/courses", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ title, description, coverImageUrl, published: false, free, price, fieldId: Number(fieldId) })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            showAlertModal(data.error || "Kurs yaratishda xatolik");
            return;
        }

        location.href = "/courses/" + data.id;
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}
