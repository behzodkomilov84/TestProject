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
            <input
                id="input-${i}"
                class="${inputClass}"
                readonly
                value="${s.name}"
            >
        </div>
            `
                : `
            <input
                class="${inputClass}"
                value="${s.name}"
                ${placeholder}
                oninput="itemBlock[${i}].name=this.value"
                onkeydown="onClickKey(event, ${i})"
                id="input-${i}"
            >
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

    window.location.href = `/topics?scienceId=${scienceId}`;
}

function hasDuplicate(currentIndex, name) {

    return itemBlock.some((subject, index) =>
        index !== currentIndex &&
        subject.name.toLowerCase().trim() === name.toLowerCase().trim()
    );
}

function onClickKey(event, i) {
    if (event.key === "Enter" && itemBlock[i].mode !== "VIEW") {
        saveOnClientSide(i);
    }

    if (event.key === "Escape" && itemBlock[i].mode !== "VIEW") {
        cancel(i);
    }

    if (event.key === "Delete" && itemBlock[i].mode !== "VIEW") {
        removeFromUi(i);
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

function buttons(s, i) {
    if (s.mode === "VIEW") {
        return `<button onclick="edit(${i})">✏️ Edit</button>`; //TODO
    }
    return `
               <button onclick="saveOnClientSide(${i})">💾 Save</button>
               <button onclick="cancel(${i})">↩ Cancel</button>
               <button onclick="removeFromUi(${i})">🗑️ Delete</button> 
           `; //TODO
} //DONE

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



