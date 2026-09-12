const ROLE = document.body.dataset.role;
// OWNER barcha kurslarni, ADMIN esa faqat o'zi yaratgan kurslarni
// boshqara oladi — ikkalasi ham "+ Yangi kurs yaratish" VA "+ Yangi
// Yo'nalish" tugmalarini ko'rishi kerak (foydalanuvchi so'rovi bo'yicha,
// 2026-09-04 — Yo'nalish CRUD huquqi kurs yaratish bilan bir xil).
const CAN_CREATE_COURSE = ROLE === "ROLE_OWNER" || ROLE === "ROLE_ADMIN";

let allCourses = [];
let allFields = [];

// Brauzerning o'z "Fayl tanlash" tugmasi (native <input type="file">)
// TILGA (masalan ruscha "Выберите файл") qarab chiqadi va matnini
// o'zgartirib bo'lmaydi — shu sabab ".file-picker-input" bilan
// ko'rinmas qilib, o'rniga o'zbekcha tugma + shu funksiya orqali
// tanlangan fayl nomi ko'rsatiladi (foydalanuvchi so'rovi, 2026-09-07:
// "выберите файлни ўзбекча қил").
function updateFilePickerName(input, spanId) {
    const span = document.getElementById(spanId);
    if (!span) return;
    span.textContent = (input.files && input.files[0]) ? input.files[0].name : "Fayl tanlanmagan";
}
// Qaysi Yo'nalish "box"lari ochiq — courseDetail.js#expandedChapterKeys
// bilan bir xil g'oya (bir nechtasi bir vaqtda ochiq turishi mumkin).
const expandedFieldKeys = new Set();

// courseDetail.js#settledChapterKeys/chapterKeyBeingAnimated BILAN AYNAN
// BIR XIL — HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-12:
// "Klaviatura yorlig'i tooltip'i ... tuzalmabdi") — "settled" klassi
// ilgari faqat JS orqali (transitionend'dan keyin) o'sha bitta DOM
// elementiga qo'shilardi, shablonda esa yo'q edi — ro'yxat boshqa sabab
// bilan qayta chizilganda (masalan boshqa Yo'nalishni ochish/yopish)
// allaqachon ochiq Yo'nalish ham YANGI (settled'siz) elementga aylanib,
// "⌨️" popover'i yana kesilib qolardi.
const settledFieldKeys = new Set();
let fieldKeyBeingAnimated = null;

// courseDetail.js#closedChapterActionKeys BILAN BIR XIL — "⋯" amallar
// menyusi endi DEFAULT holatda OCHIQ (foydalanuvchi so'rovi, 2026-09-12:
// "icon defaultda ochiq tursin. istasa yopib qo'yadi"), faqat ANIQ
// YOPILGAN Yo'nalishlar shu Set'da eslab qolinadi.
const closedFieldActionKeys = new Set();

// Kurs sahifasidan (courseDetail.html) "← Kurs yo'nalishlari" bosilganda "?focus=<id>"
// beriladi — shu Kursning Yo'nalish qutisi avtomatik ochiladi va o'sha
// kartaga skroll qilinadi (science.js#focusId bilan bir xil g'oya — aks
// holda "orqaga" bosilganda foydalanuvchi qaysi Yo'nalish ichida
// ekanini qayta qidirishga majbur bo'lardi).
let focusCourseId = Number(new URLSearchParams(window.location.search).get("focus")) || null;

// Klaviatura navigatsiyasi (←/→/↑/↓/Home/End/Ctrl+↑/↓) va o'ng tugma
// bilan belgilash uchun — courseDetail.js#selectedSectionId bilan bir
// xil g'oya (foydalanuvchi so'rovi, 2026-09-05: "navigatsiyani kurslarning
// barcha iyerarxiyasiga qo'sh"). ".course-card.selected" CSS klassi
// orqali ko'rsatiladi — brauzerning standart :focus halqasiga emas.
let selectedCourseId = null;

// Sahifa birinchi ochilganda "?focus=<id>" bo'lsa — o'sha kursga bir
// martalik tanlov/scroll qo'yiladi (courseDetail.js#pendingFocusApplied
// bilan bir xil g'oya). "?focus=" bo'lmasa — ILGARI birinchi Yo'nalishning
// birinchi kursi default tanlanardi, lekin bu Yo'nalish guruhini
// majburan ochib yuborardi (haqiqiy foydalanuvchi shikoyati, 2026-09-08:
// guruhlar "berk" turishi kerak bo'lsa ham birinchisi ochiq chiqardi) —
// courseDetail.js#selectFirstCardByDefault bilan bir xil sabab bilan
// olib tashlandi.

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
        groups.set(String(f.id), {
            key: String(f.id), fieldId: f.id, name: f.name, orderIndex: f.orderIndex,
            // Yo'nalishni yaratgan admin (yoki OWNER) — ✏️/🗑️ tugmalarini
            // ko'rsatish/yashirish uchun (foydalanuvchi so'rovi, 2026-09-08).
            canManage: f.canManage !== false,
            items: []
        });
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

    // Har bir guruh ICHIDA ham kurslar o'z order_index'i bo'yicha
    // saralanadi (moveCourse shu tartibni ⬆⬇ orqali o'zgartiradi) —
    // orderIndex bo'lmagan (eski, hali migratsiya qilinmagan) kurslar
    // oxiriga suriladi.
    const withOrder = c => c.orderIndex != null ? c.orderIndex : Number.MAX_SAFE_INTEGER;
    for (const g of groups.values()) {
        g.items.sort((a, b) => withOrder(a) - withOrder(b));
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
    // courseDetail.js#renderGroupedSections BILAN BIR XIL — foydalanuvchi
    // so'rovi, 2026-09-12: "ochiq mavzu tepaga render bo'lsin... Mavzuni
    // yopsa, qaytib joyiga kelib qolsin". FAQAT KO'RSATISH tartibi
    // o'zgaradi — "realFieldGroups" (⬆⬇ tugmalari uchun) haqiqiy
    // (o'zgarmagan) tartibdan hisoblanadi.
    const orderedGroups = [
        ...groups.filter(g => expandedFieldKeys.has(g.key)),
        ...groups.filter(g => !expandedFieldKeys.has(g.key))
    ];
    grid.innerHTML = orderedGroups.map(g => renderFieldBox(g, realFieldGroups)).join("");
    equalizeGroupCardHeights("coursesGrid");

    // courseDetail.js#renderGroupedSections BILAN AYNAN BIR XIL —
    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-12: "...
    // tuzalmabdi"). Hech qanday animatsiya ketmayotgan (aynan HOZIR
    // toggleFieldBox orqali ochilayotgan bittasidan tashqari), lekin
    // ALLAQACHON ochiq Yo'nalishlarni darhol "settled" qilamiz — aks
    // holda ular ham qayta chizilganda "⌨️" popover'i kesilib qolaveradi.
    orderedGroups.forEach(group => {
        if (!expandedFieldKeys.has(group.key)) return;
        if (group.key === fieldKeyBeingAnimated) return;
        if (settledFieldKeys.has(group.key)) return;
        settledFieldKeys.add(group.key);
        const el = document.getElementById(`fieldCollapse-${group.key}`);
        if (el) el.classList.add("settled");
    });

    if (focusCourseId != null) {
        const targetId = focusCourseId;
        // Faqat BIRINCHI render'da qo'llaniladi — keyingi qayta chizishlarda
        // (masalan boshqa Yo'nalishni ochish/yopish) foydalanuvchini
        // qaytadan shu kartaga tashlab yubormaslik uchun.
        focusCourseId = null;

        selectCourseCard(targetId, { scroll: true });
        const card = document.getElementById(`course-card-${targetId}`);
        if (card) {
            card.classList.add("course-card-focused");
            setTimeout(() => card.classList.remove("course-card-focused"), 2000);
        }
    }
    // "?focus=" bo'lmasa — ILGARI BIRINCHI Yo'nalishning birinchi kursi
    // default tanlanardi (courseDetail.js#selectFirstCardByDefault bilan
    // bir xil g'oya edi), lekin selectCourseCard() kartani DOM'da
    // topolmasa (guruh yopiq bo'lsa) O'ZI guruhni ochib yuborar edi —
    // natijada sahifa YANGI yuklanganda Yo'nalishlar "berk" turishi
    // kerak bo'lsa ham, birinchisi HAR DOIM ochiq chiqardi (haqiqiy
    // foydalanuvchi shikoyati, 2026-09-08: "sahifa yangi yuklanganda
    // berk tursin"). courseDetail.js'da xuddi shu sabab bilan avval
    // butunlay OLIB TASHLANGAN edi (2026-09-08) — endi bu yerda ham.
}

// courseDetail.js#equalizeGroupCardHeights BILAN AYNAN BIR XIL — foydalanuvchi
// so'rovi, 2026-09-12: "height'ini dinamik qil ichidagi tekstlarini
// sig'adigan qilib. Lekin barcha kartochkalar eni va bo'yi bir xil
// bo'lsin". CSS Grid faqat BIR QATOR ICHIDAGI kartalarni tenglashtiradi
// (align-items:stretch), shu sabab BARCHA (necha qatorda bo'lishidan
// qat'iy nazar) kartalarning eng balandini o'lchab, hammasiga shu
// balandlik JS orqali qo'llaniladi.
function equalizeGroupCardHeights(containerId) {
    const container = document.getElementById(containerId);
    if (!container) return;
    const cards = [...container.querySelectorAll(":scope > .group-card.collapsed")];
    if (cards.length < 2) return;

    cards.forEach(c => { c.style.height = "auto"; });
    const maxHeight = Math.max(...cards.map(c => c.offsetHeight));
    cards.forEach(c => { c.style.height = maxHeight + "px"; });
}

let fieldCardResizeTimeout = null;
window.addEventListener("resize", () => {
    clearTimeout(fieldCardResizeTimeout);
    fieldCardResizeTimeout = setTimeout(() => equalizeGroupCardHeights("coursesGrid"), 200);
});

// courseDetail.js#toggleChapterBox BILAN BIR XIL animatsiya mantig'i
// (foydalanuvchi so'rovi, 2026-09-12: "карточкани босганда, анимацион
// ҳолатда ичидагиларни очсин") — ".group-card-collapse" CSS klassi
// (grid-template-rows: 0fr <-> 1fr) ikkala sahifada (bu yerda —
// Yo'nalishlar, courseDetail.js'da — Mavzular) UMUMIY (courses.css).
function toggleFieldBox(key) {
    const isOpen = expandedFieldKeys.has(key);
    const collapseEl = document.getElementById(`fieldCollapse-${key}`);

    if (isOpen) {
        if (collapseEl) {
            // courseDetail.js#toggleChapterBox BILAN BIR XIL — "settled"
            // darhol olib tashlanadi (klass HAM, to'plamdan HAM — pastga
            // qarang) — qayta ochilganda animatsiya yana to'g'ri o'ynashi
            // uchun.
            collapseEl.classList.remove("is-open", "settled");
            settledFieldKeys.delete(key);
            const onEnd = (e) => {
                if (e.target !== collapseEl || e.propertyName !== "grid-template-rows") return;
                collapseEl.removeEventListener("transitionend", onEnd);
                expandedFieldKeys.delete(key);
                renderGroupedCourses();
            };
            collapseEl.addEventListener("transitionend", onEnd);
        } else {
            expandedFieldKeys.delete(key);
            settledFieldKeys.delete(key);
            renderGroupedCourses();
        }
    } else {
        expandedFieldKeys.add(key);
        // courseDetail.js#toggleChapterBox BILAN BIR XIL — shu Yo'nalishni
        // renderGroupedCourses()dagi avtomatik-settle mantig'idan ATAYLAB
        // chetlab o'tadi (haqiqiy, bosqichma-bosqich ochilish animatsiyasi
        // buzilmasligi uchun).
        fieldKeyBeingAnimated = key;
        renderGroupedCourses();
        requestAnimationFrame(() => {
            requestAnimationFrame(() => {
                const el = document.getElementById(`fieldCollapse-${key}`);
                if (!el) { fieldKeyBeingAnimated = null; return; }
                el.classList.add("is-open");
                // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-12:
                // "klavish yorliqlari yarmi ko'rinmayapti") — kurs
                // kartalarining "⌨️" popover'i konteyner chegarasidan
                // tashqariga chiqsa, animatsiya uchun shart bo'lgan
                // "overflow:hidden" uni kesib tashlardi. Ochilish
                // animatsiyasi TUGAGANDAN KEYIN ("settled") cheklov
                // olib tashlanadi (courses.css). "settledFieldKeys"ga HAM
                // yoziladi — ro'yxat KEYINROQ qayta chizilsa ham, bu
                // Yo'nalish "settled" holatini yo'qotmasligi uchun
                // (HAQIQIY TOPILGAN BUG, 2026-09-12: "tuzalmabdi").
                const onOpenEnd = (e) => {
                    if (e.target !== el || e.propertyName !== "grid-template-rows") return;
                    el.removeEventListener("transitionend", onOpenEnd);
                    el.classList.add("settled");
                    settledFieldKeys.add(key);
                    fieldKeyBeingAnimated = null;
                };
                el.addEventListener("transitionend", onOpenEnd);
            });
        });
    }
}

// Kartani "tanlangan" deb belgilaydi — courseDetail.js#selectCard bilan
// AYNAN bir xil g'oya (foydalanuvchi so'rovi, 2026-09-05: "navigatsiyani
// kurslarning barcha iyerarxiyasiga qo'sh"). Karta hozir YOPIQ Yo'nalish
// qutisi ichida bo'lishi mumkin — shu holatda avval o'sha guruhni ochib
// qayta chizamiz, keyin yana qidiramiz.
function selectCourseCard(courseId, { scroll = false } = {}) {
    selectedCourseId = courseId;

    let el = document.getElementById(`course-card-${courseId}`);
    if (!el) {
        const c = allCourses.find(x => x.id === courseId);
        if (c) {
            const key = c.fieldId != null ? String(c.fieldId) : "none";
            if (!expandedFieldKeys.has(key)) {
                expandedFieldKeys.add(key);
                renderGroupedCourses();
                el = document.getElementById(`course-card-${courseId}`);
            }
        }
    }

    document.querySelectorAll(".course-card.selected").forEach(x => x.classList.remove("selected"));
    if (el) {
        el.classList.add("selected");
        el.focus({ preventScroll: !scroll });
        if (scroll) el.scrollIntoView({ behavior: "smooth", block: "center" });
    }
}

// ←/→ va oddiy ↑/↓ — bitta Yo'nalish ICHIDA kursdan-kursga (sahifalash
// yo'q, shu sabab ↑/↓ ham ←/→ bilan bir xil — courseDetail.js'dagi
// "sahifalar orasida" tushunchasi bu yerda yo'q).
function moveCourseSelection(courseId, dir) {
    const groups = getSortedFieldGroups();
    const group = groups.find(g => g.items.some(c => c.id === courseId));
    if (!group) return;

    const idx = group.items.findIndex(c => c.id === courseId);
    const newIdx = idx + dir;
    if (newIdx < 0 || newIdx >= group.items.length) return;

    selectCourseCard(group.items[newIdx].id, { scroll: true });
}

// Ctrl+↑ / Ctrl+↓ — Yo'nalishlar ORASIDA o'tadi (courseDetail.js#
// moveToAdjacentChapter bilan bir xil g'oya) — bo'sh (hali kursi yo'q)
// Yo'nalishlar avtomatik o'tkazib yuboriladi (tanlanadigan kursi yo'q).
function moveToAdjacentField(courseId, dir) {
    const groups = getSortedFieldGroups();
    const idx = groups.findIndex(g => g.items.some(c => c.id === courseId));
    if (idx === -1) return;

    let newIdx = idx + dir;
    while (newIdx >= 0 && newIdx < groups.length && groups[newIdx].items.length === 0) {
        newIdx += dir;
    }
    if (newIdx < 0 || newIdx >= groups.length) return;

    // Guruhni ochish selectCourseCard()'ning O'ZIGA qoldiriladi — u
    // faqat kerak bo'lsagina (hali ochilmagan bo'lsa) qo'shib, qayta
    // chizadi. Bu yerda oldindan qo'shib qo'yish xato edi: keyin
    // selectCourseCard "allaqachon ochiq" deb hisoblab, render()'ni
    // UMUMAN chaqirmay qoldirardi — karta hech qachon DOM'ga chiqmasdi
    // (haqiqiy topilgan bug, foydalanuvchi so'rovi, 2026-09-05).
    selectCourseCard(groups[newIdx].items[0].id, { scroll: true });
}

// Home/End — joriy Yo'nalishning birinchi/oxirgi kursiga.
function moveCourseToFirst(courseId) {
    const groups = getSortedFieldGroups();
    const group = groups.find(g => g.items.some(c => c.id === courseId));
    if (!group || group.items.length === 0) return;
    selectCourseCard(group.items[0].id, { scroll: true });
}

function moveCourseToLast(courseId) {
    const groups = getSortedFieldGroups();
    const group = groups.find(g => g.items.some(c => c.id === courseId));
    if (!group || group.items.length === 0) return;
    selectCourseCard(group.items[group.items.length - 1].id, { scroll: true });
}

// Enter — tanlangan kartaga "kirish" (sichqon bilan bosgandagi bilan
// bir xil xulq-atvor).
function openSelectedCourseCard(courseId) {
    location.href = `/courses/${courseId}`;
}

function onCourseCardKeyDown(event, courseId) {
    switch (event.key) {
        case "ArrowRight":
            event.preventDefault();
            moveCourseSelection(courseId, 1);
            break;
        case "ArrowLeft":
            event.preventDefault();
            moveCourseSelection(courseId, -1);
            break;
        case "ArrowDown":
            event.preventDefault();
            if (event.ctrlKey || event.metaKey) {
                moveToAdjacentField(courseId, 1);
            } else {
                moveCourseSelection(courseId, 1);
            }
            break;
        case "ArrowUp":
            event.preventDefault();
            if (event.ctrlKey || event.metaKey) {
                moveToAdjacentField(courseId, -1);
            } else {
                moveCourseSelection(courseId, -1);
            }
            break;
        case "Home":
            event.preventDefault();
            moveCourseToFirst(courseId);
            break;
        case "End":
            event.preventDefault();
            moveCourseToLast(courseId);
            break;
        case "Enter":
            event.preventDefault();
            openSelectedCourseCard(courseId);
            break;
    }
}

// "⌨️" belgisi bosilganda — klaviatura-yo'riqnoma pufakchasini
// ochadi/yopadi (courseDetail.js#toggleKbdHint bilan bir xil andoza).
function toggleCourseKbdHint(badgeEl) {
    const card = badgeEl.closest(".course-card");
    if (!card) return;
    const wasOpen = card.classList.contains("kbd-hint-open");
    document.querySelectorAll(".course-card.kbd-hint-open").forEach(el => el.classList.remove("kbd-hint-open"));
    if (!wasOpen) card.classList.add("kbd-hint-open");
}

document.addEventListener("click", (e) => {
    if (!e.target.closest(".kbd-hint-badge")) {
        document.querySelectorAll(".course-card.kbd-hint-open").forEach(el => el.classList.remove("kbd-hint-open"));
    }
});

function renderFieldBox(group, realFieldGroups) {
    const isExpanded = expandedFieldKeys.has(group.key);

    let bodyHtml = "";
    if (isExpanded) {
        const cardsHtml = group.items.length
            ? `<div class="courses-grid">${group.items.map((c, idx) => renderCourseCard(c, idx, group.items.length, group.fieldId)).join("")}</div>`
            : `<div class="courses-empty">Bu Yo'nalishda hali kurs (Bo'lim) yo'q</div>`;
        bodyHtml = `<div class="chapter-box-body">${cardsHtml}</div>`;
    }

    // "✏️"/"🗑️" — faqat HAQIQIY Yo'nalishlarda (group.fieldId != null)
    // VA joriy foydalanuvchi shu Yo'nalishni boshqara olsa (o'zi
    // yaratgan yoki OWNER) ko'rsatiladi — "— Yo'nalishsiz kurslar —"
    // psevdo-guruhida ko'rsatilmaydi (canManage — foydalanuvchi so'rovi,
    // 2026-09-08).
    const canManageField = CAN_CREATE_COURSE && group.fieldId != null && group.canManage !== false;
    const renameBtn = canManageField
        ? `<button class="chapter-rename-btn" onclick="event.stopPropagation(); renameFieldPrompt(${group.fieldId})" title="Yo'nalish nomini tahrirlash">✏️</button>`
        : "";
    const deleteBtn = canManageField
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

    // Karto chka ko'rinishi (foydalanuvchi so'rovi, 2026-09-12: "курс
    // йўналишларини ҳам карточка кўринишига келтир. Йўналиш номлари
    // сиғмай қолмасин... CSS ини чиройли қил иккаласини ҳам") —
    // courseDetail.js#renderChapterBox BILAN AYNAN bir xil ".group-card-*"
    // klasslari (courses.css'da umumiy) — ikkala sahifa ham bir xil
    // ko'rinish/xatti-harakatga ega bo'lishi uchun.
    const actionsHtml = `${addBtn}${moveBtns}${renameBtn}${deleteBtn}`;
    const hasActions = actionsHtml.trim() !== "";
    const isMenuOpen = !closedFieldActionKeys.has(group.key);

    return `
        <div class="group-card ${isExpanded ? "expanded" : "collapsed"}">
            ${hasActions ? `
            <div class="group-card-corner">
                <button class="group-card-menu-trigger" onclick="event.stopPropagation(); toggleFieldActions('${group.key}')" title="Amallar">⋯</button>
                <div class="group-card-menu ${isMenuOpen ? "" : "hidden"}" id="fieldActionsExtra-${group.key}">
                    ${actionsHtml}
                </div>
            </div>` : ""}
            <h3 class="group-card-title" onclick="toggleFieldBox('${group.key}')" title="${isExpanded ? "Yig'ish" : "Ochish"}">
                <span class="group-card-chevron">▸</span>
                <span class="group-card-name">🧭 ${escapeHtml(group.name)}</span>
            </h3>
            <!-- Ilgari "(bo'lim — N ta)" edi — bu NOTO'G'RI edi, chunki
                 bu yerda sanalayotgan narsa KURS (Course), Bo'lim
                 (CourseChapter) emas — foydalanuvchi so'rovi, 2026-09-05. -->
            <div class="group-card-count">(kurs — ${group.items.length} ta)</div>
            <div class="group-card-collapse ${isExpanded ? "is-open" : ""} ${settledFieldKeys.has(group.key) ? "settled" : ""}" id="fieldCollapse-${group.key}">
                <div class="group-card-collapse-inner">${bodyHtml}</div>
            </div>
        </div>
    `;
}

// courseDetail.js#toggleChapterActions BILAN BIR XIL — default OCHIQ,
// bu yerda faqat YOPISH closedFieldActionKeys'ga yoziladi (foydalanuvchi
// so'rovi, 2026-09-12: "icon defaultda ochiq tursin. istasa yopib qo'yadi").
function toggleFieldActions(key) {
    const el = document.getElementById(`fieldActionsExtra-${key}`);
    if (!el) return;
    const willBeHidden = !el.classList.contains("hidden");
    el.classList.toggle("hidden");
    if (willBeHidden) {
        closedFieldActionKeys.add(key);
    } else {
        closedFieldActionKeys.delete(key);
    }
}

// courseDetail.js'dagi bilan AYNAN BIR XIL — HAQIQIY TOPILGAN BUG
// (foydalanuvchi so'rovi, 2026-09-12: "Мавзуларда по умолчанию да очиқ
// турсин actionlar") — tekshiruv ilgari ".group-card-corner" bilan
// cheklangani sabab, Yo'nalish SARLAVHASIGA bosish (toggleFieldBox,
// "event.stopPropagation()"siz) SINXRON qayta render qilib, YANGI
// (default OCHIQ) popover'larni O'SHA BIR XIL bosish document'gacha
// ko'tarilganda DARHOL yopib qo'yardi. Endi butun ".group-card" bilan.
document.addEventListener("click", (e) => {
    if (e.target.closest(".group-card")) return;
    document.querySelectorAll(".group-card-menu:not(.hidden)").forEach(el => {
        el.classList.add("hidden");
        closedFieldActionKeys.add(el.id.replace("fieldActionsExtra-", ""));
    });
});

// "⬆⬇" — shu Yo'nalish ICHIDA kurs kartochkasini surish (foydalanuvchi
// so'rovi, 2026-09-05: "bo'limlarni o'rnini almashtirish funksiyasini
// qo'shish kerak") — faqat HAQIQIY Yo'nalishda (fieldId != null, kamida
// 2 ta kurs bo'lganda) ko'rsatiladi, CourseFieldService/moveField bilan
// bir xil g'oya (chapter-move-btn uslubi).
function renderCourseCard(c, idx, total, fieldId) {
    let moveBtnsHtml = "";
    if (CAN_CREATE_COURSE && fieldId != null && total > 1) {
        const upDisabled = idx === 0 ? "disabled" : "";
        const downDisabled = idx === total - 1 ? "disabled" : "";
        moveBtnsHtml = `
            <div class="course-card-actions" onclick="event.stopPropagation()">
                <button class="chapter-move-btn" onclick="moveCourse(${c.id}, -1, ${fieldId})" ${upDisabled} title="Yuqoriga">⬆</button>
                <button class="chapter-move-btn" onclick="moveCourse(${c.id}, 1, ${fieldId})" ${downDisabled} title="Pastga">⬇</button>
            </div>
        `;
    }

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
        // c.price — 1 OYLIK narx (courseDetail.js'dagi bilan bir xil).
        const priceText = c.price ? ` — 1 oyga ${formatPrice(c.price)} so'm` : "";
        badge = `<span class="course-badge locked">🔒 Obuna kerak${priceText}</span>`;
    }

    const cover = c.coverImageUrl
        ? `<img class="course-card-cover" src="${c.coverImageUrl}" alt="">`
        : `<div class="course-card-cover"></div>`;

    const isSelected = c.id === selectedCourseId;

    // Sarlavha ostida "Muallif: Ism Familiya" (foydalanuvchi so'rovi,
    // 2026-09-07: "барча курсларни тагига... Автор: курсни яратувчи исм
    // фамилияси ёзилсин").
    const authorHtml = c.authorName
        ? `<p class="course-card-author">✍️ Muallif: ${escapeHtml(c.authorName)}</p>`
        : "";

    return `
        <div class="course-card ${isSelected ? "selected" : ""}" id="course-card-${c.id}" tabindex="0"
             onclick="selectCourseCard(${c.id}); location.href='/courses/${c.id}'"
             oncontextmenu="event.preventDefault(); selectCourseCard(${c.id});"
             onkeydown="onCourseCardKeyDown(event, ${c.id})">
            <span class="kbd-hint-badge" onclick="event.stopPropagation(); toggleCourseKbdHint(this)" title="Klaviatura yorliqlari">⌨️</span>
            ${cover}
            <div class="course-card-body">
                <h3 class="course-card-title">${escapeHtml(c.title)}</h3>
                ${authorHtml}
                <p class="course-card-desc">${escapeHtml(c.description || "")}</p>
                <div class="course-card-footer">
                    <span>${c.chapterCount} ta mavzu, ${c.sectionCount} ta dars, ${c.testCount} ta test</span>
                    ${badge}
                </div>
                ${moveBtnsHtml}
            </div>
        </div>
    `;
}

// "⬆⬇" bosilganda — client tomonda joy almashtirib DARHOL qayta chizadi,
// so'ng serverga (fieldId ICHIDA TO'LIQ ID ro'yxati) yuboradi — moveField
// bilan bir xil andoza (foydalanuvchi so'rovi, 2026-09-05).
async function moveCourse(courseId, direction, fieldId) {
    const groups = getSortedFieldGroups();
    const group = groups.find(g => g.fieldId === fieldId);
    if (!group) return;

    const pos = group.items.findIndex(c => c.id === courseId);
    const newPos = pos + direction;
    if (pos < 0 || newPos < 0 || newPos >= group.items.length) return;

    [group.items[pos], group.items[newPos]] = [group.items[newPos], group.items[pos]];
    const orderedIds = group.items.map(c => c.id);

    // allCourses'ni ham shu tartibga moslab qo'yamiz — aks holda keyingi
    // getSortedFieldGroups() eski (server) tartibdan qayta boshlagan
    // bo'lardi (render() chaqirilguncha).
    orderedIds.forEach((id, idx) => {
        const course = allCourses.find(c => c.id === id);
        if (course) course.orderIndex = idx + 1;
    });
    renderGroupedCourses();

    try {
        const res = await fetch(`/api/courses/reorder?fieldId=${fieldId}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(orderedIds)
        });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Tartibni o'zgartirishda xatolik");
            await loadCourses();
        }
        // Muvaffaqiyatli bo'lsa — hech narsa ko'rsatilmaydi (moveField bilan
        // bir xil andoza, courses.js'da toast-container yo'q).
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
        await loadCourses();
    }
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
        // Darslar ochilish tartibi (foydalanuvchi so'rovi, 2026-09-07) —
        // bepul/pullik kursdan qat'i nazar.
        const sequentialUnlock = document.querySelector('input[name="newCourseUnlockMode"]:checked').value === "sequential";

        const res = await fetch("/api/courses", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ title, description, coverImageUrl, published: false, free, price, fieldId: Number(fieldId), sequentialUnlock })
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
