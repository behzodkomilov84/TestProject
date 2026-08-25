// ========================================================================
//                     Global fields
// ========================================================================

let itemBlock = []; // сюда будут загружены данные из БД
let deletedTopicIds = []; // FRONTEND da o'chirilganlarni id'si (Agar u DB da ham bo'lsa)
let focusIndex = null;//для курсора

let oldName = ""; //for EDIT uses
let newName = ""; //for EDIT uses

// Fan ichidagi Bo'limlar ro'yxati — "— Bo'limsiz —" varianti bilan
// birga tanlash dropdown'ini to'ldirish uchun (loadSections()).
let sectionList = [];

// Bo'limlar sahifasidan (Fan -> Bo'lim -> Mavzu) kelinganda URL'da
// "sectionId" beriladi — shu bo'limga tegishli mavzular bo'lim ustida
// FAQAT KO'RSATILADI (itemBlock'ning o'zi to'liq qoladi — dublikat
// nom tekshiruvi butun fan bo'yicha bo'lishi kerak, faqat bitta
// bo'lim ichida emas, chunki DB'da unique(science_id, name)).
const filterSectionId = new URLSearchParams(window.location.search).get("sectionId");
// ========================================================================

const scienceId = getScienceId();

if (!scienceId) {
    alert("❌ scienceId topilmadi (HTML dan)");
} else {
    loadSections().then(() => {
        showSectionFilterBanner();
        afterStartPage(`/api/topic?scienceId=${scienceId}`);
    });
}

function showSectionFilterBanner() {
    const banner = document.getElementById("sectionFilterBanner");
    if (!filterSectionId) {
        banner.classList.add("hidden");
        return;
    }
    const name = sectionNameById(filterSectionId) || "Bo'lim";
    banner.innerHTML = `🔎 <b>${escapeHtml(name)}</b> mavzulari ko'rsatilmoqda — ` +
        `<a href="/topics?scienceId=${scienceId}">barcha mavzularni ko'rish</a>`;
    banner.classList.remove("hidden");
}

async function loadSections() {
    try {
        const response = await fetch(`/api/topic-section?scienceId=${scienceId}`);
        if (!response.ok) throw new Error(`Server error: ${response.status}`);
        sectionList = await response.json();
    } catch (err) {
        console.error("Bo'limlarni yuklashda xatolik:", err);
        sectionList = [];
    }
}

function sectionOptionsHtml(selectedSectionId) {
    let options = `<option value="" ${!selectedSectionId ? "selected" : ""}>— Bo'limsiz —</option>`;
    sectionList.forEach(sec => {
        const selected = Number(selectedSectionId) === Number(sec.id) ? "selected" : "";
        options += `<option value="${sec.id}" ${selected}>${escapeHtml(sec.name)}</option>`;
    });
    return options;
}

function sectionNameById(sectionId) {
    if (!sectionId) return null;
    const found = sectionList.find(sec => Number(sec.id) === Number(sectionId));
    return found ? found.name : null;
}



// ========================================================================
//                      Functions
// ========================================================================

function getScienceId() {
    const element = document.getElementById("scienceId");
    return element ? element.value : null;
}

// "🗑️ Testi yo'q mavzularni o'chirish" — shu Fanda hech qanday savoli
// bo'lmagan (questionCount==0) BARCHA mavzularni bir yo'la o'chiradi.
// Kursga bog'langan mavzular backend tomonidan avtomatik chetlab
// o'tiladi (TopicService.deleteQuestionlessTopics).
async function deleteQuestionlessTopics() {
    if (itemBlock.some(s => s.mode !== "VIEW")) {
        alert("❌ Avval tahrirlashni yakuniga yetkazing (yoki saqlang)!");
        return;
    }

    const candidateCount = itemBlock.filter(s => s.id > 0 && (s.questionCount || 0) === 0).length;
    if (candidateCount === 0) {
        alert("ℹ️ Testi yo'q mavzu topilmadi.");
        return;
    }

    if (!confirm(`⚠️ Testi yo'q ${candidateCount} ta mavzuni o'chirmoqchimisiz?\n\n(Kursga bog'langan mavzular, agar bo'lsa, avtomatik chetlab o'tiladi.)\n\nBu amalni bekor qilib bo'lmaydi.`)) {
        return;
    }

    try {
        const res = await fetch(`/api/topic/questionless?scienceId=${getScienceId()}`, { method: "DELETE" });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            alert(data.error || "O'chirishda xatolik");
            return;
        }
        showToast('success', `✅ ${data.deleted} ta mavzu o'chirildi`, 4000);
        await reloadFromDb(`/api/topic?scienceId=${getScienceId()}`);
        focusIndex = 0;
        render();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

// "🗑️ O'chirilgan mavzular" paneli — soft-delete qilingan Topic'lar
// ro'yxati (bir zumda "♻️ Tiklash" qilinadigan). Panel yopiq holatda
// boshlanadi, bosilganda ochilib ro'yxatni yuklaydi.
let topicTrashOpen = false;

function toggleTopicTrash() {
    topicTrashOpen = !topicTrashOpen;
    document.getElementById("topicTrashPanel").style.display = topicTrashOpen ? "block" : "none";
    if (topicTrashOpen) {
        loadTopicTrash();
    }
}

async function loadTopicTrash() {
    const list = document.getElementById("topicTrashList");
    list.innerHTML = "<p>Yuklanmoqda...</p>";

    try {
        const res = await fetch(`/api/topic/deleted?scienceId=${getScienceId()}`);
        if (!res.ok) {
            list.innerHTML = "<p>Yuklashda xatolik</p>";
            return;
        }
        const items = await res.json();
        if (!items.length) {
            list.innerHTML = "<p>O'chirilgan mavzu yo'q</p>";
            return;
        }
        list.innerHTML = items.map(t => `
            <div class="row">
                <div>${escapeHtml(t.name)} (${t.questionCount} ta test) — ${formatTopicTrashDate(t.deletedAt)}da o'chirilgan</div>
                <div class="row-actions">
                    <button onclick="restoreTopic(${t.id})">♻️ Tiklash</button>
                    <button class="danger-btn" onclick="permanentlyDeleteTopic(${t.id}, ${JSON.stringify(t.name)})">🗑️ Butunlay o'chirish</button>
                </div>
            </div>
        `).join("");
    } catch (err) {
        console.error(err);
        list.innerHTML = "<p>Tarmoq xatoligi</p>";
    }
}

function formatTopicTrashDate(isoString) {
    if (!isoString) return "";
    const d = new Date(isoString);
    return d.toLocaleDateString("uz-UZ") + " " + d.toLocaleTimeString("uz-UZ", { hour: "2-digit", minute: "2-digit" });
}

async function restoreTopic(topicId) {
    if (!confirm("Bu mavzuni tiklamoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/topic/${topicId}/restore`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "Tiklashda xatolik");
            return;
        }
        loadTopicTrash();
        await reloadFromDb(`/api/topic?scienceId=${scienceId}`);
        render();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

async function permanentlyDeleteTopic(topicId, name) {
    if (!confirm(`⚠️ "${name}" mavzusini BUTUNLAY (savollari bilan birga) o'chirmoqchimisiz?\n\nBu amalni HECH QANDAY tarzda bekor qilib bo'lmaydi.`)) return;
    if (!confirm("Haqiqatan ham ishonchingiz komilmi?")) return;

    try {
        const res = await fetch(`/api/topic/${topicId}/permanent`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "O'chirishda xatolik");
            return;
        }
        loadTopicTrash();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text ?? "";
    return div.innerHTML;
}

function afterStartPage(mapping) {
        reloadFromDb(mapping).then(r => {
            focusIndex = 0;// выбрать первый элемент
            render();// отрисовать список с выделением
        });
} //DONE

async function reloadFromDb(mapping) {
    const response = await fetch(mapping);

    try {
        if (!response.ok) {
            throw new Error(`Server error: ${response.status}`);
        }
    } catch (err) {
        console.error('Yuklash xatosi:', err);
        showToast('error', `Mavzularni yuklashda xatolik`, 4000);
    }

    const data = await response.json();

    itemBlock = data.map(s => ({
        id: s.id,
        name: s.name,
        original: s.name,
        sectionId: s.sectionId || null,
        originalSectionId: s.sectionId || null,
        // Shu mavzu biror KURS bo'limiga bog'langan bo'lsa — o'sha kursning
        // nomi (render()'da "🔗 Kurs: ..." belgisi uchun). Faqat KO'RISH
        // maqsadida — bu yerdan tahrirlanmaydi (bog'lanish kurs tahrirlash
        // sahifasida, CourseSectionSaveDto.scienceName/topicName orqali
        // boshqariladi).
        linkedCourseTitle: s.linkedCourseTitle || null,
        // Shu mavzuda nechta savol borligi — render()'da "(N ta test)"
        // ko'rsatish uchun.
        questionCount: s.questionCount || 0,
        mode: "VIEW"
    }));

} //DONE

function render() {
    const list = document.getElementById("list");
    list.innerHTML = "";

    itemBlock.forEach((s, i) => {
        // Bo'lim ustidan kelingan bo'lsa — faqat shu bo'limga tegishli
        // (yoki hali saqlanmagan NEW) qatorlar ko'rsatiladi. itemBlock'ning
        // o'zi to'liq qoladi (dublikat nom tekshiruvi butun fan bo'yicha
        // ishlashi kerak), shu sabab faqat CHIZISHDA o'tkazib yuboriladi.
        if (filterSectionId && s.mode === "VIEW" && Number(s.sectionId) !== Number(filterSectionId)) {
            return;
        }

        const row = document.createElement("div");
        row.className = "row";

        const isView = s.mode === "VIEW";
        const isLink = isView && s.id !== null;
        const isNew = s.mode === "NEW";
        const placeholder = isNew ? 'placeholder="Yangi mavzu nomini kiriting"' : '';

        // Проверяем дубликаты для текущего элемента
        const hasDup = !isView && hasDuplicate(i, s.name);
        const inputClass = `
                                    ${isView ? 'view' : ''} 
                                    ${isLink ? 'link' : ''} 
                                    ${hasDup ? 'duplicate' : ''}
                                    `;
        // VIEW rejimida joriy bo'lim nomi qator boshida kichik belgi
        // (badge) sifatida ko'rsatiladi — bo'limsiz bo'lsa hech narsa
        // chiqmaydi.
        const sectionName = sectionNameById(s.sectionId);
        const sectionBadge = sectionName
            ? `<span class="topic-section-badge">${escapeHtml(sectionName)}</span>`
            : '';

        // Shu mavzu biror KURS bo'limiga bog'langan bo'lsa — kichik belgi
        // (topic.js#reloadFromDb linkedCourseTitle'ni to'ldiradi). Admin
        // shu mavzuni o'chirsa/nomini butunlay o'zgartirsa, kursdagi
        // bog'lanish "yetim" qolib ketishi mumkinligini eslatib turadi.
        const courseBadge = s.linkedCourseTitle
            ? `<span class="topic-course-badge" title="Bu mavzu kursga bog'langan">🔗 Kurs: ${escapeHtml(s.linkedCourseTitle)}</span>`
            : '';

        // Bo'lim/Kurs belgilari — o'z alohida qatorida (bo'lsagina);
        // mavzu NOMI esa har doim YANGI qatordan boshlanadi, savol soni
        // belgisi esa nom bilan bir qatorda, lekin O'NG tomonda (ajralib
        // turadigan fon bilan) — hammasi bir-biriga "yopishib" ketmasin
        // deb (item-badges/item-title-row, science.css).
        const badgesRow = (sectionBadge || courseBadge)
            ? `<div class="item-badges">${sectionBadge}${courseBadge}</div>`
            : '';

        row.innerHTML = `
    ${
            isView
                ? `
            <div
            class="row-view"
            tabindex="0"
            ondblclick="openQuestions(${s.id})"
            onkeydown="onViewKeyDown(event, ${i})"
            title="Enter — Саволларни очиш | ↑ ↓ — навигация"
        >
            <div
                id="input-${i}"
                class="topic-name ${inputClass}"
                tabindex="-1"
            >${badgesRow}<div class="item-title-row"><span class="item-title-text">${escapeHtml(s.name)}</span><span class="item-count-badge">${s.questionCount} ta test</span></div></div>
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
            <select class="topic-section-select" onchange="itemBlock[${i}].sectionId=this.value?Number(this.value):null" title="Bo'lim">
                ${sectionOptionsHtml(s.sectionId)}
            </select>
            `
        }
    ${buttons(s, i)}
`;

        list.appendChild(row);
    });

    // если фокус не задан — выбрать первый элемент
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
} //DONE

function openQuestions(topicId) {
    if (!topicId || topicId < 0) {
        // ВАРИАНТ 1 — запрет
        alert("❗ Бу мавзу бўйича саволлар базада йўқ");
        return;

        // ВАРИАНТ 2 — разрешить пустые темы
        // window.location.href = "/topics";
        // return;
    }

    window.location.href = `/question?topicId=${topicId}`;
} //TODO

function hasDuplicate(currentIndex, name) {

    return itemBlock.some((topic, index) =>
        index !== currentIndex &&
        topic.name.toLowerCase().trim() === name.toLowerCase().trim()
    );
} //DONE

function onClickKey(event, i) {
    // Nom maydoni endi <textarea> (bir necha qatorli tahrirlash uchun) —
    // Shift+Enter bilan qator ko'chirish (agar kerak bo'lsa) ochiq
    // qoldirilgan, oddiy Enter esa saqlaydi (avvalgi <input>'dagi bilan
    // bir xil xulq-atvor) — preventDefault() shart, aks holda textarea'ga
    // qo'shimcha bo'sh qator ham qo'shilib qolardi.
    if (event.key === "Enter" && !event.shiftKey && itemBlock[i].mode !== "VIEW") {
        event.preventDefault();
        saveOnClientSide(i);
    }

    if (event.key === "Escape" && itemBlock[i].mode !== "VIEW") {
        cancel(i);
    }

    // "Delete" tugmasi ORQALI QATORNI O'CHIRISH endi olib tashlandi —
    // <textarea> ichida matn tahrirlashda "Delete" odatiy (kursordan
    // keyingi belgini o'chirish) ma'noda ishlatiladi, avvalgi <input>'da
    // bo'lgani kabi butun QATORNI o'chirib yubormasligi kerak. Qatorni
    // o'chirish endi faqat 🗑️ tugmasi orqali (buttons()).
} //DONE

function onViewKeyDown(event, index) {
    const s = itemBlock[index];

    // работаем ТОЛЬКО в VIEW
    if (s.mode !== "VIEW") return;

    switch (event.key) {

        case "Enter":
            event.preventDefault();
            openQuestions(s.id);
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
} //DONE

function moveFocus(newIndex) {
    if (newIndex < 0 || newIndex >= itemBlock.length) return;
    focusIndex = newIndex;
    render();
}//DONE

function cancel(i) {
    const s = itemBlock[i];
    if (s.mode !== "VIEW") {
        if (s.mode === "NEW") {
            itemBlock.splice(i, 1);
        }
        s.name = s.original;
        s.sectionId = s.originalSectionId; // Bo'lim tanlovi ham bekor qilinadi
        s.mode = "VIEW";
        showToast('info', 'Amaliyot bekor qilindi', 2000);
    }
    render();
} //DONE

function undoAll() {
    reloadFromDb(`/api/topic?scienceId=${scienceId}`).then(r => {
        render()
    });
    showToast('info', 'Ma\'lumotlar bazasidan qayta yuklandi ', 4000);
}//DONE

function removeFromUi(i) {
    if (itemBlock[i].mode === "NEW") {
        itemBlock.splice(i, 1);
        render();
        return;
    }
    const topicName = itemBlock[i].name || "Bu mavzu";
    const confirmDelete = confirm(`⚠️ "${topicName}"ni o'chirishni tasdiqlaysizmi?\n\nKeyin bu amalni bekor qilib bo'lmaydi.`);
    if (confirmDelete) {
        const removedTopic = itemBlock[i];

        if (removedTopic.id > 0) {
            deletedTopicIds.push(removedTopic.id);
        }

        itemBlock.splice(i, 1);
        showToast('success', `"${removedTopic.name || 'Mavzu'}" o'chirildi`, 2000);
        render();
    } else {
        cancel(i);
    }
} //DONE

// Tugmalar guruhi ".row-actions" ichiga o'raladi (science.css) — mavzu
// nomi qancha uzun bo'lib, bir necha qatorga o'ralib ketmasin, tugmalar
// HECH QACHON torayib/siqilib qolmaydi (flex-shrink:0).
function buttons(s, i) {
    if (s.mode === "VIEW") {
        return `<div class="row-actions"><button onclick="edit(${i})">✏️ Edit</button></div>`;
    }
    return `
        <div class="row-actions">
            <button onclick="saveOnClientSide(${i})">💾 Save</button>
            <button onclick="cancel(${i})">↩ Cancel</button>
            <button onclick="removeFromUi(${i})">🗑️ Delete</button>
        </div>
           `;
} //DONE

function edit(i) {
    // Kursga bog'langan mavzu — nomi HAM, Bo'limi HAM faqat kurs ichidan
    // o'zgartiriladi (foydalanuvchi so'rovi bo'yicha: "mavzu kursni ichida
    // o'zgartiriladi" — bu yerdan umuman tahrirlash imkoni yo'q, na
    // qisman). Backend ham xuddi shu tekshiruvni qaytaradi
    // (TopicService.updateTopic, TopicSectionService.assignTopicToSection)
    // — bu yerdagi tekshiruv foydalanuvchiga darhol, saqlashga
    // urinmasdan tushuntirish beradi.
    if (itemBlock[i].linkedCourseTitle) {
        alert(`❌ Bu mavzu "${itemBlock[i].linkedCourseTitle}" kursiga bog'langan.\n\nUni (nomini ham, Bo'limini ham) faqat shu kurs ichidan (kurs sahifasidagi mavzu ✏️ tugmasi orqali) tahrirlashingiz mumkin.`);
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
} //DONE

function add() {
    if (itemBlock.some(s => s.mode === "NEW" || s.mode === "EDIT")) {
        showToast('warning', 'Avval saqlash tugmasini bosing!');
        focusIndex = itemBlock.findIndex(s => s.mode !== "VIEW");
        render();
        return;
    }

    // ИЗМЕНЕНИЕ: Увеличиваем временный ID
    const tempId = Date.now() * -1; // Отрицательный ID для временных записей

    // Bo'lim ustidan kelingan bo'lsa (filterSectionId) — yangi mavzu
    // avtomatik o'sha bo'limga tanlangan holda ochiladi (teacher har safar
    // qo'lda tanlamasin uchun).
    itemBlock.push({
        id: tempId, // Временный ID
        name: "",
        original: "",
        sectionId: filterSectionId ? Number(filterSectionId) : null,
        originalSectionId: null,
        mode: "NEW"
    });

    focusIndex = itemBlock.length - 1;
    render();
} //DONE

function saveOnClientSide(i) {
    const s = itemBlock[i];
    newName = s.name.trim();


    if (newName === "") {
        alert('❌ Mavzu matni bo\'sh bo\'lishi mumkin emas!');
        focusIndex = i;
        console.error("Mavzu matni bo\'sh bo\'lishi mumkin emas!");

        return;
    }

    // проверка дубликатов на фронте
    if (hasDuplicate(i, newName)) {
        alert('❌ Bu mavzu nomi allaqachon mavjud!');
        focusIndex = i;
        console.log("hasDuplicate = true");
        return;
    }

    s.name = newName;
    itemBlock[i].mode = "VIEW";

    render();

    // Определяем тип операции
    if (newName === oldName) {
        showToast('info', 'O\'zgarish bo\'lmadi', 3000);
    }

    if (s.id < 0) {
        if (newName === oldName) {
            showToast('info', 'O\'zgarish bo\'lmadi', 3000);
        } else {
            showToast('info', 'Yangi mavzu o\'zgardi', 3000);
        }
        showToast('success', 'Yangi mavzu saqlandi \n\n(bazaga saqlash uchun "Bazaga saqlash" tugmasini bosing)', 3000);
    } else {
        // Существующая запись из БД
        if (newName === oldName) {
            showToast('warm', 'O\'zgarish bo\'lmadi', 3000);
        } else {
            showToast('success', 'Mavzu muvaffaqiyatli saqlandi', 3000);
        }

    }
    oldName = "";
    newName = "";
}//DONE

async function saveToDb() {

    // Запрет: есть незавершённые записи
    if (itemBlock.some(s => s.mode !== "VIEW")) {
        alert('❌ Avval tahrirlashni yakuniga yetkazing!');
        focusIndex = itemBlock.findIndex(s => s.mode !== "VIEW");
        render();
        return;
    }

    // Формируем payload
    const payload = {
        new: itemBlock
            .filter(s => s.id < 0)
            .map(s => (
                {science_id: scienceId, name: s.name, sectionId: s.sectionId || null})),

        // E'tibor bering: nom o'zgarmagan, faqat Bo'lim o'zgargan bo'lsa
        // ham "updated"ga tushishi kerak — shu sabab shart ikkalasini
        // ham tekshiradi (avval faqat nom tekshirilardi).
        updated: itemBlock
            .filter(s => s.id > 0 && (s.name !== s.original || s.sectionId !== s.originalSectionId))
            .map(s => (
                {id: s.id, name: s.name, sectionId: s.sectionId || null}
            )),

        deletedIds: deletedTopicIds
    };

    // Если нечего сохранять — выходим
    if (
        payload.new.length === 0 &&
        payload.updated.length === 0 &&
        deletedTopicIds.length === 0) {
        alert('ℹ️ Saqlash uchun o‘zgarishlar yo‘q');
        return;
    }

    // 5. Подтверждение
    const confirmed = confirm(
        `Yangi: ${payload.new.length} ta\n` +
        `O\'zgartirilgan: ${payload.updated.length} ta\n\n` +
        `O\'chirilgan: ${deletedTopicIds.length} ta\n\n` +
        `Saqlashni xohlaysizmi?`
    );
    if (!confirmed) return;

    try {
        showToast('info', 'Maʼlumotlar bazaga saqlanmoqda...', 5000);

        // 6. Отправка в backend
        const response = await fetch("/api/topic/save",
            {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(payload)
            });

        if (!response.ok) {
            // Backend {"error": "..."} shaklida qaytaradi — avval bu yerda
            // matn o'qib tashlanardi-yu, aniq sabab o'rniga umumiy "Server
            // error (not JSON)" ko'rsatilardi.
            const data = await response.json().catch(() => ({}));
            throw new Error(data.error || "Saqlashda xatolik");
        }

        const data = await response.json();   // теперь это безопасно

        // Успешное сообщение
        showToast(
            'success',
            `Saqlandi: yangi — ${payload.new.length}, \n
            o‘zgartirilgan — ${payload.updated.length}, \n\n
            o'chirilgan - ${deletedTopicIds.length} ta`,
            5000
        );

        // 🔑 КЛЮЧЕВОЕ МЕСТО — ПОЛНАЯ СИНХРОНИЗАЦИЯ С БД
        deletedTopicIds = [];
        await reloadFromDb(`/api/topic?scienceId=${scienceId}`);
        focusIndex = 0;
        render(); // ❗ shu qator yo'q edi — shuning uchun DB yangilangan, lekin ekran eskicha qolardi

    } catch (err) {
        console.error(err);
        showToast('error', err.message || 'Saqlashda xatolik', 7000);
        alert(err.message);
    }
}//DONE

//===========================================================================
//            BACK tugmasini bosganda ishlaydi.
//===========================================================================
document.addEventListener("DOMContentLoaded", () => {
    const btnBack = document.getElementById("btnBack");

    if (!btnBack) return;

    btnBack.onclick = () => {
        const scienceId =
            new URLSearchParams(window.location.search).get("scienceId");

        if (!scienceId) {
            // fallback
            window.location.href = "/science";
            return;
        }

        // Bo'lim ichidan kelingan bo'lsa (Fan -> Bo'lim -> Mavzu) — Bo'limlar
        // ro'yxatiga qaytariladi, aks holda to'g'ridan-to'g'ri Fanlarga.
        window.location.href = filterSectionId
            ? `/topic-sections?scienceId=${scienceId}`
            : `/science?focus=${scienceId}`;
    };
});
//===========================================================================






