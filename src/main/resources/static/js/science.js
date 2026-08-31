// ========================================================================
//                     Global fields
// ========================================================================

let itemBlock = []; // сюда будут загружены данные из БД
let deletedSubjectIds = []; // FRONTEND da o'chirilganlarni id'si (Agar u DB da ham bo'lsa)
let focusIndex = null;//для курсора

let oldName = ""; //for EDIT uses
let newName = ""; //for EDIT uses
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
            list.innerHTML = "<p>O'chirilgan fan yo'q</p>";
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
    if (!confirm("Bu fanni tiklamoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/science/${scienceId}/restore`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "Tiklashda xatolik");
            return;
        }
        loadScienceTrash();
        await reloadFromDb("/api/science");
        render();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

async function permanentlyDeleteScienceFromTrash(scienceId, name) {
    if (!confirm(`⚠️ "${name}" fanini BUTUNLAY o'chirmoqchimisiz?\n\nBu amalni HECH QANDAY tarzda bekor qilib bo'lmaydi.\n\n(Agar bu fanda hali Bo'lim/mavzu bo'lsa, avval ularni o'chirish kerak bo'ladi.)`)) return;

    try {
        const res = await fetch(`/api/science/${scienceId}/permanent`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "O'chirishda xatolik");
            return;
        }
        loadScienceTrash();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
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
        reloadFromDb(mapping).then(r => {
            focusIndex = 0;// выбрать первый элемент
            render();// отрисовать список с выделением
        });
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
        showToast('error', `Fanlarni yuklashda xatolik`, 4000);
    }

    const data = await response.json();

    itemBlock = data.map(s => ({
        id: s.id,
        name: s.name,
        original: s.name,
        // Shu fanda nechta Bo'lim (TopicSection) borligi — render()'da
        // "(N ta bo'lim)" ko'rsatish uchun.
        sectionCount: s.sectionCount || 0,
        mode: "VIEW"
    }));

}

function render() {
    const list = document.getElementById("list");
    list.innerHTML = "";

    itemBlock.forEach((s, i) => {
        const row = document.createElement("div");
        const isView = s.mode === "VIEW";
        // "science-row" — 768px+ ekranlarda fan nomi va "✏️ Edit" tugmasi
        // BITTA qatorda (yonma-yon) joylashishi uchun (science.css) —
        // FAQAT ko'rish (VIEW) rejimida (tahrirlashda — textarea + bir
        // nechta tugma — hamon ustunli, tor bo'lib qolmasin deb).
        row.className = isView ? "row science-row" : "row";

        const isLink = isView && s.id !== null;
        const isNew = s.mode === "NEW";
        const placeholder = isNew ? 'placeholder="Yangi fan nomini kiriting"' : '';

        // Проверяем дубликаты для текущего элемента
        const hasDup = !isView && hasDuplicate(i, s.name);
        const inputClass = `
                                    ${isView ? 'view' : ''} 
                                    ${isLink ? 'link' : ''} 
                                    ${hasDup ? 'duplicate' : ''}
                                    `;
        row.innerHTML = `
    ${
            isView
                ? `
            <div
            class="row-view"
            tabindex="0"
            ondblclick="openTopics(${s.id})"
            onkeydown="onViewKeyDown(event, ${i})"
            title="Enter — Мавзуларни очиш | ↑ ↓ — навигация"
        >
            <div
                id="input-${i}"
                class="topic-name ${inputClass}"
                tabindex="-1"
            >${isLink ? `<div class="item-badges"><button class="topic-export-btn" onclick="event.stopPropagation(); exportScienceQuestions(${s.id})" title="Shu fandagi barcha mavzularning testlarini Excel'ga eksport qilish">📊</button></div>` : ""}<div class="item-title-row"><span class="item-title-text">${escapeHtml(s.name)}</span>${isLink ? `<span class="item-count-badge">${s.sectionCount} ta bo'lim</span>` : ""}</div></div>
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

    // если фокус не задан — выбрать первый элемент
    if (focusIndex === null && itemBlock.length > 0) {
        focusIndex = 0;
    }

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

function openTopics(scienceId) {
    if (!scienceId || scienceId < 0) {
        // ВАРИАНТ 1 — запрет
        alert("❗ Бу фан базада йўқ");
        return;

        // ВАРИАНТ 2 — разрешить пустые темы
        // window.location.href = "/topics";
        // return;
    }

    // Endi to'g'ridan-to'g'ri mavzular emas, avval Bo'limlar sahifasiga
    // o'tiladi (Fan -> Bo'lim -> Mavzu ierarxiyasi).
    window.location.href = `/topic-sections?scienceId=${scienceId}`;
}

// "📊 Excel'ga eksport" — shu Fandagi BARCHA mavzularning savollarini
// BITTA .xlsx faylga yig'ib yuklab beradi (topic.js#exportTopicQuestions
// bilan bir xil andoza, faqat butun Fan miqyosida).
function exportScienceQuestions(scienceId) {
    window.location.href = `/api/export/questions/science?scienceId=${scienceId}`;
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
} //DONE

function undoAll() {
    reloadFromDb("/api/science").then(r => {
        render()
    });
    showToast('info', 'Ma\'lumotlar bazasidan qayta yuklandi ', 4000);
}

function removeFromUi(i) {
    if (itemBlock[i].mode === "NEW") {
        itemBlock.splice(i, 1);
        render();
        return;
    }
    const subjectName = itemBlock[i].name || "Bu fan";
    const confirmDelete = confirm(`⚠️ "${subjectName}"ni o'chirishni tasdiqlaysizmi?\n\nBu amalni bekor qilib bo'lmaydi.`);
    if (confirmDelete) {
        const removedSubject = itemBlock[i];

        if (removedSubject.id > 0) {
            deletedSubjectIds.push(removedSubject.id);
        }

        itemBlock.splice(i, 1);
        showToast('success', `"${removedSubject.name || 'Fan'}" o'chirildi`, 2000);
        render();
    } else {
        cancel(i);
    }
} //DONE

// Tugmalar guruhi ".row-actions" ichiga o'raladi (science.css) — karta
// ichida har doim ENG PASTGA "yopishadi" (margin-top:auto).
function buttons(s, i) {
    if (s.mode === "VIEW") {
        const upDisabled = i === 0 ? "disabled" : "";
        const downDisabled = i === itemBlock.length - 1 ? "disabled" : "";
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

// Faqat DB'da mavjud (id > 0) fanlar orasida joy almashtiradi va darhol
// serverga (reorder endpoint) yuboradi — yangi (hali saqlanmagan)
// fanlar bilan aralashtirmaslik uchun oddiy holatda saqlanadi
// (topicSection.js#moveUp bilan bir xil andoza).
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
        alert("❌ Avval tahrirlashni yakuniga yetkazing (yoki saqlang)!");
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
        mode: "NEW"
    });

    focusIndex = itemBlock.length - 1;
    render();
}

function saveOnClientSide(i) {
    const s = itemBlock[i];
    newName = s.name.trim();


    if (newName === "") {
        alert('❌ Fan nomi bo\'sh bo\'lishi mumkin emas!');
        focusIndex = i;
        console.error("Fan nomi bo\'sh bo\'lishi mumkin emas!");

        return;
    }

    // проверка дубликатов на фронте
    if (hasDuplicate(i, newName)) {
        alert('❌ Bu fan nomi allaqachon mavjud!');
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
            showToast('info', 'Yangi fan o\'zgardi', 3000);
        }
        showToast('success', 'Yangi fan saqlandi \n\n(bazaga saqlash uchun "Bazaga saqlash" tugmasini bosing)', 3000);
    } else {
        // Существующая запись из БД
        if (newName === oldName) {
            showToast('warm', 'O\'zgarish bo\'lmadi', 3000);
        } else {
            showToast('success', 'Fan muvaffaqiyatli saqlandi', 3000);
        }

    }
    oldName = "";
    newName = "";
}

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
            .map(s => s.name),

        updated: itemBlock
            .filter(s => s.id > 0 && s.name !== s.original)
            .map(s => (
                {id: s.id, name: s.name}
            )),

        deletedIds: deletedSubjectIds
    };

    // Если нечего сохранять — выходим
    if (
        payload.new.length === 0 &&
        payload.updated.length === 0 &&
        deletedSubjectIds.length === 0) {
        alert('ℹ️ Saqlash uchun o‘zgarishlar yo‘q');
        return;
    }

    // 5. Подтверждение
    const confirmed = confirm(
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
        await reloadFromDb("/api/science");
        focusIndex = 0;
        render(); // ❗ shu qator yo'q edi — shuning uchun DB yangilangan, lekin ekran eskicha qolardi
        refreshScienceTrashBadge();

    } catch (err) {
        console.error(err);
        showToast('error', err.message || 'Saqlashda xatolik', 7000);
        alert(err.message);
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



