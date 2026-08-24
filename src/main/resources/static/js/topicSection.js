// "Bo'lim" (TopicSection) CRUD — topic.js bilan bir xil andoza
// (itemBlock[] + mode VIEW/NEW/EDIT + saveToDb()), + tartib
// o'zgartirish (yuqoriga/pastga) tugmalari.
// ========================================================================
//                     Global fields
// ========================================================================

let itemBlock = [];
let deletedSectionIds = [];
let focusIndex = null;

let oldName = "";
let newName = "";
// ========================================================================

const scienceId = getScienceId();

if (!scienceId) {
    alert("❌ scienceId topilmadi (HTML dan)");
} else {
    afterStartPage(`/api/topic-section?scienceId=${scienceId}`);
}

// ========================================================================
//                      Functions
// ========================================================================

function getScienceId() {
    const element = document.getElementById("scienceId");
    return element ? element.value : null;
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text ?? "";
    return div.innerHTML;
}

function afterStartPage(mapping) {
    reloadFromDb(mapping).then(() => {
        focusIndex = 0;
        render();
    });
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
        // Shu bo'lim biror KURSga bog'langan bo'lsa — o'sha kursning nomi
        // (render()'da "🔗 Kurs: ..." belgisi VA edit()'da tahrirlashni
        // bloklash uchun — bunday bo'limning nomi kurs Bo'limi bilan
        // sinxronlangan, faqat kurs ichidan o'zgartiriladi).
        linkedCourseTitle: s.linkedCourseTitle || null,
        mode: "VIEW"
    }));
}

function render() {
    const list = document.getElementById("list");
    list.innerHTML = "";

    itemBlock.forEach((s, i) => {
        const row = document.createElement("div");
        row.className = "row";

        const isView = s.mode === "VIEW";
        const isLink = isView && s.id !== null;
        const isNew = s.mode === "NEW";
        const placeholder = isNew ? 'placeholder="Yangi bo\'lim nomini kiriting"' : '';

        const hasDup = !isView && hasDuplicate(i, s.name);
        const inputClass = `
                                    ${isView ? 'view' : ''}
                                    ${isLink ? 'link' : ''}
                                    ${hasDup ? 'duplicate' : ''}
                                    `;

        // Kursga bog'langan bo'lim — kichik belgi (topic.js'dagi "🔗 Kurs"
        // belgisi bilan bir xil uslub/rang).
        const courseBadge = s.linkedCourseTitle
            ? `<span class="topic-course-badge" title="Bu bo'lim kursga bog'langan, faqat kurs ichidan tahrirlanadi">🔗 Kurs: ${escapeHtml(s.linkedCourseTitle)}</span> `
            : '';

        row.innerHTML = `
    ${
            isView
                ? `
            <div
            class="row-view"
            tabindex="0"
            ondblclick="openTopics(${s.id})"
            onkeydown="onViewKeyDown(event, ${i})"
            title="Enter — Mavzularni ochish | ↑ ↓ — navigatsiya"
        >
            <div
                id="input-${i}"
                class="topic-name ${inputClass}"
                tabindex="-1"
            >${courseBadge}${escapeHtml(s.name)}</div>
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
            `
        }
    ${buttons(s, i)}
`;

        list.appendChild(row);
    });

    if (focusIndex === null && itemBlock.length > 0) {
        focusIndex = 0;
    }

    if (focusIndex !== null) {
        const input = document.getElementById(`input-${focusIndex}`);
        if (input) {
            input.focus();
            input.scrollIntoView({behavior: 'smooth', block: 'nearest'});
        }
        focusIndex = null;
    }
}

function openTopics(sectionId) {
    if (!sectionId || sectionId < 0) {
        alert("❗ Avval bo'limni bazaga saqlang");
        return;
    }
    // Faqat shu bo'limga tegishli mavzularni ko'rsatadigan holatda ochiladi.
    window.location.href = `/topics?scienceId=${scienceId}&sectionId=${sectionId}`;
}

function hasDuplicate(currentIndex, name) {
    return itemBlock.some((s, index) =>
        index !== currentIndex &&
        s.name.toLowerCase().trim() === name.toLowerCase().trim()
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
}

function onViewKeyDown(event, index) {
    const s = itemBlock[index];
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
        s.mode = "VIEW";
        showToast('info', 'Amaliyot bekor qilindi', 2000);
    }
    render();
}

function undoAll() {
    reloadFromDb(`/api/topic-section?scienceId=${scienceId}`).then(() => render());
    showToast('info', 'Ma\'lumotlar bazasidan qayta yuklandi ', 4000);
}

function removeFromUi(i) {
    if (itemBlock[i].mode === "NEW") {
        itemBlock.splice(i, 1);
        render();
        return;
    }
    const sectionName = itemBlock[i].name || "Bu bo'lim";
    const confirmDelete = confirm(`⚠️ "${sectionName}"ni o'chirishni tasdiqlaysizmi?\n\nBo'limdagi mavzular O'CHMAYDI — faqat bo'limsiz bo'lib qoladi.\n\nKeyin bu amalni bekor qilib bo'lmaydi.`);
    if (confirmDelete) {
        const removed = itemBlock[i];

        if (removed.id > 0) {
            deletedSectionIds.push(removed.id);
        }

        itemBlock.splice(i, 1);
        showToast('success', `"${removed.name || 'Bo\'lim'}" o'chirildi`, 2000);
        render();
    } else {
        cancel(i);
    }
}

// Tugmalar guruhi ".row-actions" ichiga o'raladi (science.css) — shu
// tufayli bo'lim nomi qancha uzun bo'lib, bir necha qatorga o'ralib
// ketmasin, tugmalar HECH QACHON torayib/siqilib qolmaydi (flex-shrink:0).
function buttons(s, i) {
    if (s.mode === "VIEW") {
        const upDisabled = i === 0 ? "disabled" : "";
        const downDisabled = i === itemBlock.length - 1 ? "disabled" : "";
        return `
            <div class="row-actions">
                <button onclick="moveUp(${i})" ${upDisabled} title="Yuqoriga">⬆</button>
                <button onclick="moveDown(${i})" ${downDisabled} title="Pastga">⬇</button>
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
}

// "✏️ Edit" tugmasi shu funksiyani chaqiradi — topic.js'da bor, bu yerga
// klonlanganda (topicSection.js yaratilganda) tushib qolgan edi, shu
// sabab "✏️ Edit" bosilganda hech narsa sodir bo'lmasdi (browser konsolida
// "edit is not defined" xatosi bilan).
function edit(i) {
    // Kursga bog'langan bo'lim — nomi kurs Bo'limi bilan (bir tomonlama)
    // sinxronlangan (CourseService.renameChapter), shu sabab bu yerdan
    // tahrirlash BLOKLANADI — aks holda qo'lda kiritilgan o'zgarish
    // keyingi kurs tomonidagi rename'da ustidan yozilib, "yo'qolib
    // qolar" edi. Backend ham xuddi shu tekshiruvni qaytaradi
    // (TopicSectionService.updateSectionName) — bu yerdagi tekshiruv
    // foydalanuvchiga darhol, saqlashga urinmasdan tushuntirish beradi.
    if (itemBlock[i].linkedCourseTitle) {
        alert(`❌ Bu bo'lim "${itemBlock[i].linkedCourseTitle}" kursiga bog'langan.\n\nUni faqat shu kurs ichidan (kurs sahifasidagi Bo'lim ✏️ tugmasi orqali) tahrirlashingiz mumkin.`);
        return;
    }

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
}

// Faqat DB'da mavjud (id > 0) bo'limlar orasida joy almashtiradi va
// darhol serverga (reorder endpoint) yuboradi — yangi (hali saqlanmagan)
// bo'limlar bilan aralashtirmaslik uchun oddiy holatda saqlanadi.
function moveUp(i) {
    if (i <= 0) return;
    [itemBlock[i - 1], itemBlock[i]] = [itemBlock[i], itemBlock[i - 1]];
    persistOrder();
}

function moveDown(i) {
    if (i >= itemBlock.length - 1) return;
    [itemBlock[i], itemBlock[i + 1]] = [itemBlock[i + 1], itemBlock[i]];
    persistOrder();
}

async function persistOrder() {
    render();

    const orderedIds = itemBlock.filter(s => s.id > 0).map(s => s.id);
    if (orderedIds.length < 2) return;

    try {
        const response = await fetch(`/api/topic-section/reorder?scienceId=${scienceId}`, {
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

function showToast(type, message, duration = 4000) {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;

    const icons = {success: '✅', error: '❌', warning: '⚠️', info: 'ℹ️'};

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

function add() {
    if (itemBlock.some(s => s.mode === "NEW" || s.mode === "EDIT")) {
        showToast('warning', 'Avval saqlash tugmasini bosing!');
        focusIndex = itemBlock.findIndex(s => s.mode !== "VIEW");
        render();
        return;
    }

    const tempId = Date.now() * -1;

    itemBlock.push({
        id: tempId,
        name: "",
        original: "",
        mode: "NEW"
    });

    focusIndex = itemBlock.length - 1;
    render();
}

function saveOnClientSide(i) {
    const s = itemBlock[i];
    newName = s.name.trim();

    if (newName === "") {
        alert('❌ Bo\'lim nomi bo\'sh bo\'lishi mumkin emas!');
        focusIndex = i;
        return;
    }

    if (hasDuplicate(i, newName)) {
        alert('❌ Bu bo\'lim nomi allaqachon mavjud!');
        focusIndex = i;
        return;
    }

    s.name = newName;
    itemBlock[i].mode = "VIEW";

    render();

    if (s.id < 0) {
        showToast('success', 'Yangi bo\'lim saqlandi \n\n(bazaga saqlash uchun "Bazaga saqlash" tugmasini bosing)', 3000);
    } else if (newName !== oldName) {
        showToast('success', 'Bo\'lim muvaffaqiyatli saqlandi', 3000);
    } else {
        showToast('info', 'O\'zgarish bo\'lmadi', 3000);
    }
    oldName = "";
    newName = "";
}

async function saveToDb() {

    if (itemBlock.some(s => s.mode !== "VIEW")) {
        alert('❌ Avval tahrirlashni yakuniga yetkazing!');
        focusIndex = itemBlock.findIndex(s => s.mode !== "VIEW");
        render();
        return;
    }

    const payload = {
        new: itemBlock
            .filter(s => s.id < 0)
            .map(s => ({science_id: scienceId, name: s.name})),

        updated: itemBlock
            .filter(s => s.id > 0 && s.name !== s.original)
            .map(s => ({id: s.id, name: s.name})),

        deletedIds: deletedSectionIds
    };

    if (payload.new.length === 0 && payload.updated.length === 0 && deletedSectionIds.length === 0) {
        alert('ℹ️ Saqlash uchun o‘zgarishlar yo‘q');
        return;
    }

    const confirmed = confirm(
        `Yangi: ${payload.new.length} ta\n` +
        `O\'zgartirilgan: ${payload.updated.length} ta\n\n` +
        `O\'chirilgan: ${deletedSectionIds.length} ta\n\n` +
        `Saqlashni xohlaysizmi?`
    );
    if (!confirmed) return;

    try {
        showToast('info', 'Maʼlumotlar bazaga saqlanmoqda...', 5000);

        const response = await fetch("/api/topic-section/save", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(payload)
        });

        if (!response.ok) {
            // Backend {"error": "..."} shaklida qaytaradi (masalan
            // "Bu bo'lim ... kursiga bog'langan" xabari) — avval bu yerda
            // matn o'qib tashlanardi-yu, aniq sabab o'rniga umumiy "Server
            // error (not JSON)" ko'rsatilardi.
            const data = await response.json().catch(() => ({}));
            throw new Error(data.error || "Saqlashda xatolik");
        }

        await response.json();

        showToast(
            'success',
            `Saqlandi: yangi — ${payload.new.length}, o‘zgartirilgan — ${payload.updated.length}, o'chirilgan - ${deletedSectionIds.length} ta`,
            5000
        );

        deletedSectionIds = [];
        await reloadFromDb(`/api/topic-section?scienceId=${scienceId}`);
        focusIndex = 0;
        render();

    } catch (err) {
        console.error(err);
        showToast('error', err.message || 'Saqlashda xatolik', 7000);
        alert(err.message);
    }
}

//===========================================================================
//            BACK tugmasini bosganda ishlaydi.
//===========================================================================
document.addEventListener("DOMContentLoaded", () => {
    const btnBack = document.getElementById("btnBack");

    if (!btnBack) return;

    btnBack.onclick = () => {
        // Bo'limlar sahifasi endi Fan va Mavzular o'rtasida turadi
        // (Fan -> Bo'lim -> Mavzu) — shu sabab "Orqaga" Fanlar ro'yxatiga
        // qaytaradi.
        const scienceId =
            new URLSearchParams(window.location.search).get("scienceId");

        window.location.href = scienceId ? `/science?focus=${scienceId}` : "/science";
    };
});
