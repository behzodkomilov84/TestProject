// ========================================================================
//                     Global fields
// ========================================================================

let itemBlock = []; // сюда будут загружены данные из БД
let deletedTopicIds = []; // FRONTEND da o'chirilganlarni id'si (Agar u DB da ham bo'lsa)
let focusIndex = null;//для курсора

let oldName = ""; //for EDIT uses
let newName = ""; //for EDIT uses
// ========================================================================

const scienceId = getScienceId();

if (!scienceId) {
    alert("❌ scienceId topilmadi (HTML dan)");
} else {
    afterStartPage(`/api/topic?scienceId=${scienceId}`);
}



// ========================================================================
//                      Functions
// ========================================================================

function getScienceId() {
    const element = document.getElementById("scienceId");
    return element ? element.value : null;
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
        mode: "VIEW"
    }));

} //DONE

function render() {
    const list = document.getElementById("list");
    list.innerHTML = "";

    itemBlock.forEach((s, i) => {
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

function buttons(s, i) {
    if (s.mode === "VIEW") {
        return `<button onclick="edit(${i})">✏️ Edit</button>`;
    }
    return `
               <button onclick="saveOnClientSide(${i})">💾 Save</button>
               <button onclick="cancel(${i})">↩ Cancel</button>
               <button onclick="removeFromUi(${i})">🗑️ Delete</button> 
           `;
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

    itemBlock.push({
        id: tempId, // Временный ID
        name: "",
        original: "",
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
                {science_id: scienceId, name: s.name})),

        updated: itemBlock
            .filter(s => s.id > 0 && s.name !== s.original)
            .map(s => (
                {id: s.id, name: s.name}
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
            const text = await response.text();   // 👈 читаем как ТЕКСТ
            console.error("SERVER RESPONSE:", text);
            throw new Error("Server error (not JSON)");
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

        window.location.href = `/science?focus=${scienceId}`;
    };
});
//===========================================================================






