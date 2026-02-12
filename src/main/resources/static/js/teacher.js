const selectedMap = new Map();
let currentGroupId = null;
let activeGroupIdOnRightSidebar = null;

document.addEventListener("DOMContentLoaded", () => {

    loadGroups();
    loadSciences();
    void loadAllGroupSelects();
    loadSets();
    initSelectAll();
    void loadGroupStudents(null); //sahifa ochilganda o'ng sidebardagi table ni parent' ini yashirish uchun kk.
    //-------------------------------------------------------------------------
    //Agar o'ng saydbarda gruppa ochiq bo'lsa, chap saydbarda ayni shu
    // gruppani tahrirlaganda berkilib ketmaydi
    const groupSelect = document.getElementById("groupSelectToShowMembers");

    if (groupSelect) {
        groupSelect.addEventListener("change", e => {

            activeGroupIdOnRightSidebar = e.target.value || null;

            void loadGroupStudents(activeGroupIdOnRightSidebar);
        });
    }
    //--------------------------------------------------------------------------
    /*  Fanni tanlaganda shu fanga oid mavzularni zagruzka qiladi*/
    document.getElementById("scienceSelect")
        ?.addEventListener("change", e => {

            const id = e.target.value;

            if (id) loadTopics(id);
        });

    updatePlaceholder();

    // Инициализация кнопки
    updateAssignButtonState();

});

// Универсальная функция обновления состояния кнопки "Назначить тест"
function updateAssignButtonState(assignBlock) {
    // Если assignBlock не передан, ищем стандартный блок справа
    let block = assignBlock || document.querySelector("#rightSidebar .group-block");
    if (!block) return;

    let assignBtn = block.querySelector(".btn-assignTest");
    let studentCheckboxes = block.querySelectorAll(".student-checkbox");
    let setSelect = document.getElementById("setSelect");
    let dueDateInput = document.getElementById("dueDate");

    if (!assignBtn || !setSelect || !dueDateInput || !studentCheckboxes) return;

    // 1️⃣ есть ли выбранные студенты
    let studentsOk = Array.from(studentCheckboxes)
        .some(cb => cb.checked);

    // 2️⃣ выбран ли тест
    let setOk = setSelect.value !== "";

    // 3️⃣ выбрана ли дата
    let dateOk = dueDateInput.value !== "";

    // итоговое состояние
    assignBtn.disabled = !(studentsOk && setOk && dateOk);
}

function updatePlaceholder() {

    const box = document.getElementById("selectedList");
    if (!box) return;

    // считаем только реальные элементы, исключая placeholder
    const hasItems =
        [...box.children].some(
            el => !el.classList.contains("placeholder")
        );

    box.classList.toggle("has-items", hasItems);
}

//--------------------------------------------------------
//          TESTLAR BLOKI
//--------------------------------------------------------
function loadSciences() {

    const scienceSelect = document.getElementById("scienceSelect");

    fetch("/api/teacher/sciences")
        .then(r => {

            if (!r.ok) throw new Error("Fetch sciences failed");

            return r.json();
        })
        .then(list => {

            scienceSelect.innerHTML =
                `<option value="">--Fanni tanlang--</option>`;

            list.forEach(s => {

                const option = document.createElement("option");

                option.value = s.id;
                option.textContent = s.name;

                scienceSelect.appendChild(option);
            });
        })
        .catch(err => console.error(err));
} //DONE

function loadTopics(scienceId) {

    const topicSelect = document.getElementById("topicSelect");

    fetch(`/api/teacher/topics/${scienceId}`)
        .then(r => r.json())
        .then(list => {

            // Считаем сумму вопросов
            const totalQuestions = list.reduce((sum, t) => sum + (t.questionCount || 0), 0);

            // Вставляем первый option с суммарным количеством вопросов
            topicSelect.innerHTML = `<option value="">--Mavzularni tanlang-- | Testlar soni: (${totalQuestions} ta)</option>`;

            // Добавляем остальные темы

            list.forEach(t =>
                topicSelect.innerHTML += `<option value="${t.id}">${t.name} | (${t.questionCount} ta)</option>`
            );

            topicSelect.onchange = () => loadQuestions(topicSelect.value);
        });
} //DONE

function loadQuestions(topicId) {

    fetch(`/api/teacher/questions/topic/${topicId}`)
        .then(r => r.json())
        .then(list => {

            const box =
                document.getElementById("questions");

            box.innerHTML = "";

            list.forEach((q, i) => {

                const checked =
                    selectedMap.has(q.id)
                        ? "checked"
                        : "";

                box.innerHTML += `
<div class="question-item">

    <input type="checkbox"
           ${checked}
           onchange="toggleQuestion(${q.id}, this,
                \`${q.questionText}\`)">

    <span>${i + 1}. </span>
    <span>${q.questionText}</span>

</div>`;
            });
        });
} //DONE

function toggleQuestion(id, checkbox, text) {

    if (checkbox.checked) {

        selectedMap.set(id, {id, text});
        addSelectedUI(id, text);

    } else {
        selectedMap.delete(id);
        removeSelectedUI(id);
    }
    updateCounter();
} //DONE

function removeSelectedQuestion(id) {

    selectedMap.delete(id);

    removeSelectedUI(id);

    // снять чекбокс сверху
    const checkbox =
        document.querySelector(
            `#questions input[onchange*="${id}"]`
        );

    if (checkbox)
        checkbox.checked = false;

    updateCounter();
    updatePlaceholder();
} //DONE

function removeSelectedUI(id) {

    const el =
        document.getElementById("sel-" + id);

    if (el) el.remove();
} //DONE

function updateCounter() {

    document.getElementById("counter")
        .innerText = String(selectedMap.size);
} //DONE

function addSelectedUI(id, text) {

    const list =
        document.getElementById("selectedList");

    if (document.getElementById("sel-" + id))
        return;

    list.innerHTML += `
<div class="question-item"
     id="sel-${id}">

    <span>${text}</span>

    <span class="remove-btn"
          onclick="removeSelectedQuestion(${id})">
          ✖
    </span>

</div>`;

    updatePlaceholder();
}

function resetBuilder() {

    selectedMap.clear();

    document
        .querySelectorAll("#selectedList .question-item")
        .forEach(el => el.remove());

    document.getElementById("counter")
        .innerText = String(0);

    document.getElementById("setName")
        .value = "";

    document
        .querySelectorAll("#questions input")
        .forEach(cb => cb.checked = false);

    updatePlaceholder();
}

//Savollar satrini bosganda checkbox tanlanadigan qilish
document.addEventListener("click", e => {

    const item = e.target.closest(".question-item");
    if (!item) return;

    // если кликнули прямо по checkbox — ничего не делаем
    if (e.target.tagName === "INPUT") return;

    const checkbox = item.querySelector("input[type='checkbox']");
    if (!checkbox) return;

    checkbox.click();
});


//=======================================================================
//              GRUPPANI TAHRIRLASH
//=======================================================================
function startInlineEdit(groupId) {

    const span = document.querySelector(
        `.group-name[data-id="${groupId}"]`
    );

    if (!span) return;

    const oldValue = span.innerText;

    const input = document.createElement("input");
    input.value = oldValue;
    input.className = "form-control";

    // флаг защиты от двойного вызова
    input.dataset.saved = "false";

    span.replaceWith(input);
    input.focus();
    input.select();

    input.addEventListener("keydown", e => {

        if (e.key === "Enter") {
            e.preventDefault();
            saveInlineEdit(groupId, input, oldValue);
        }

        if (e.key === "Escape") {
            cancelInlineEdit(input, oldValue, groupId);
        }
    });
}

function saveInlineEdit(groupId, input, oldValue) {

    // уже сохраняли → выход
    if (input.dataset.saved === "true") return;

    input.dataset.saved = "true";

    const newName = input.value.trim();

    if (!newName || newName === oldValue) {
        cancelInlineEdit(input, oldValue, groupId);
        return;
    }

    fetch(`/api/teacher/groups/${groupId}`, {
        method: "PATCH",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({name: newName})
    })
        .then(r => {

            if (!r.ok) throw new Error();

            replaceWithSpan(input, groupId, newName);

            // обновляем select'ы
            loadAllGroupSelects().then(() => {

                if (activeGroupIdOnRightSidebar) {

                    const select =
                        document.getElementById("groupSelectToShowMembers");

                    if (select) {
                        select.value = activeGroupIdOnRightSidebar;
                    }

                    // таблицу НЕ скрываем — просто обновляем данные
                    void loadGroupStudents(activeGroupIdOnRightSidebar);
                }
            });

        })
        .catch(() => {

            alert("Ошибка сохранения");
            replaceWithSpan(input, groupId, oldValue);
        });
} //DONE

function cancelInlineEdit(input, value, groupId) {

    replaceWithSpan(input, groupId, value);
} //DONE

function replaceWithSpan(input, groupId, text) {

    const span = document.createElement("span");
    span.className = "group-name";
    span.dataset.id = groupId;
    span.innerText = text;

    input.replaceWith(span);
} //DONE
//=======================================================================
//              GRUPPA BO'YICHA AMALLAR
//=======================================================================

function loadGroups() {

    fetch("/api/teacher/get-groups")
        .then(r => r.json())
        .then(list => {

            const ul = document.getElementById("groupList");
            ul.innerHTML = "";

            list.forEach(g => {

                ul.innerHTML += `
            <li class="list-group-item d-flex justify-content-between align-items-center">

                <span class="group-name" data-id="${g.teacherGroupId}">${g.groupName}</span>

            <div>
                <button onclick="startInlineEdit(${g.teacherGroupId})">✏️</button>
                <button onclick="deleteGroup(${g.teacherGroupId})">🗑</button>
                <button onclick="openAddStudentModal(${g.teacherGroupId})">➕</button>
            </div>

</li>`;
            });
        });
}

function createGroup() {
    const nameInput = document.getElementById("groupName");
    const name = nameInput.value.trim();

    if (!name) return alert("Введите название группы");

    fetch("/api/teacher/create-group", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({name})
    })
        .then(res => {
            if (!res.ok) throw new Error("Ошибка создания группы");

            // Обновляем sidebar и select
            loadGroups();       // обновляем список слева
            void loadAllGroupSelects();  // обновляем select справа

            // очищаем input
            nameInput.value = "";
        })
        .catch(err => {
            console.error(err);
            alert("Gruppa yaratishda xatolik yuz berdi.");
        });
}

function deleteGroup(id) {
    fetch(`/api/teacher/groups/${id}`, {method: "DELETE"})
        .then(() => {
            loadGroups();
            void loadAllGroupSelects();
        });
}

function openAddStudentModal(groupId) {
    currentGroupId = groupId;

    // список всех студентов/users для invite modal.
    fetch("/api/teacher/group/students")
        .then(r => {
            if (!r.ok) throw new Error("Forbidden or server error");
            return r.json();
        })
        .then(list => {
            const table = document.getElementById("inviteTable");
            table.innerHTML = "";

            list.forEach(u => {
                table.innerHTML += `
<tr>
<td>${u.username}</td>
<td>
<button class="btn btn-sm btn-primary"
onclick="inviteStudent(${u.id})">
Пригласить
</button>
</td>
</tr>`;
            });

            const modal = new bootstrap.Modal(
                document.getElementById("inviteModal")
            );
            modal.show();

        })
        .catch(err => console.error("Ошибка загрузки студентов:", err));
}

function inviteStudent(pupilId) {
    fetch(`/api/teacher/group/${currentGroupId}/invite`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({pupilId})
    })
        .then(r => {
            if (!r.ok) throw new Error("Ошибка приглашения");

            void loadGroupStudents(currentGroupId);
            alert("Приглашение отправлено");
        })
        .catch(() => alert("Ошибка приглашения"));
}

async function loadAllGroupSelects() {
    try {
        const groups = await apiFetch("/api/teacher/groups/select");

        const showMembersSelect =
            document.getElementById("groupSelectToShowMembers");

        // helper для заполнения select
        const fillSelect = select => {
            if (!select) return;

            select.innerHTML =
                `<option value="">--Guruhni tanlang--</option>`;

            groups.forEach(g => {
                select.innerHTML +=
                    `<option value="${g.id}">${g.name}</option>`;
            });
        };

        fillSelect(showMembersSelect);

    } catch (err) {
        console.error("Group select load error:", err);
    }
}

//=======================================================================
//              SIDEBAR BO'YICHA AMALLAR
//=======================================================================
//Sidebarni yashirib, ko'rsatadi
function toggleSidebar(id) {
    document.getElementById(id).classList.toggle("collapsed");
}

//Sidebarni razmerini boshqaradi
document.querySelectorAll(".resize-handle").forEach(handle => {

    handle.addEventListener("mousedown", e => {

        const sidebar =
            document.getElementById(handle.dataset.target);

        const startX = e.clientX;
        const startWidth = sidebar.offsetWidth;

        const isRight =
            sidebar.id === "rightSidebar";

        function onMove(ev) {

            const dx = ev.clientX - startX;

            let newWidth = isRight
                ? startWidth - dx
                : startWidth + dx;

            newWidth = Math.max(180, Math.min(600, newWidth));

            sidebar.style.width = newWidth + "px";
        }

        function stop() {
            document.removeEventListener("mousemove", onMove);
            document.removeEventListener("mouseup", stop);
        }

        document.addEventListener("mousemove", onMove);
        document.addEventListener("mouseup", stop);
    });
});

document.querySelectorAll(".resize-handle").forEach(handle => {

    handle.addEventListener("mousedown", e => {

        const sidebar =
            document.getElementById(handle.dataset.target);

        const styles = getComputedStyle(sidebar);

        const min =
            parseFloat(styles.minWidth) || 180;

        const max =
            parseFloat(styles.maxWidth) || 600;

        const startX = e.clientX;
        const startWidth = sidebar.offsetWidth;

        const isRight =
            sidebar.id === "rightSidebar";

        function onMove(ev) {

            const dx = ev.clientX - startX;

            let newWidth = isRight
                ? startWidth - dx
                : startWidth + dx;

            newWidth = Math.max(min, Math.min(max, newWidth));

            sidebar.style.width = newWidth + "px";
        }

        function stop() {
            document.removeEventListener("mousemove", onMove);
            document.removeEventListener("mouseup", stop);
        }

        document.addEventListener("mousemove", onMove);
        document.addEventListener("mouseup", stop);
    });
});

//=======================================================================
//              QUESTIONSET BO'YICHA AMALLAR
//=======================================================================
function saveSet() {

    const name = document.getElementById("setName")
        .value.trim();

    if (!name || selectedMap.size === 0) {

        alert("Paket nomini kiriting va kamida bitta savolni tanlang.");
        return;
    }

    fetch("/api/teacher/questionset", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
            name,
            questionIds: [...selectedMap.keys()]
        })
    })
        .then(r => {

            if (!r.ok)
                throw new Error("Paketni saqlashda xatolik yuz berdi.");

            // сбрасываем builder
            resetBuilder();

            // обновляем select справа
            loadSets();

            // уведомляем пользователя
            alert("Savollar paketi muvaffaqiyatli saqlandi!");
        })
        .catch(err => {
            console.error(err);
            alert("Paketni saqlashda xatolik yuz berdi.");
        });
}

function loadSets() {
    const setSelect = document.getElementById("setSelect");

    fetch("/api/teacher/questionsets")
        .then(r => r.json())
        .then(list => {

            setSelect.innerHTML = `<option value="">--Test paketini tanlang--</option>`;

            list.forEach(s =>
                setSelect.innerHTML += `<option value="${s.id}">${s.name}</option>`
            );
        })
        .catch(err => console.error("Error loading sets:", err));
}

//=======================================
//          API helper
//=======================================
async function apiFetch(url, options = {}) {

    const r = await fetch(url, options);

    if (!r.ok) {
        const err = await r.json().catch(() => ({}));
        throw new Error(err.error || "API error");
    }

    return r.json().catch(() => null);
}

//=======================================================================
function hideTableIfGroupNotSelected() {

    let block = document.querySelector("#rightSidebar .group-block");
    if (!block) return;

    let tbody = block.querySelector(".student-table");
    let assignBtn = block.querySelector(".btn-assignTest");
    let studentsTitle = document.getElementById("studentsTitle");
    let tableParent = document.getElementById("studentsTableParent");

    if (tbody) tbody.innerHTML = "";

    studentsTitle && studentsTitle.classList.add("students-hidden");
    tableParent && tableParent.classList.add("hidden");

    if (assignBtn) assignBtn.disabled = true;
}

function showTableIfGroupIsSelected() {

    let studentsTitle = document.getElementById("studentsTitle");
    let tableParent = document.getElementById("studentsTableParent");
    let table = document.getElementById("studentsTable");

    studentsTitle && studentsTitle.classList.remove("students-hidden");
    tableParent && tableParent.classList.remove("hidden");
    table && table.classList.remove("students-hidden");
}

async function loadGroupStudents(groupId) {

    const block = document.querySelector("#rightSidebar .group-block");
    if (!block) return;

    if (!groupId) {
        hideTableIfGroupNotSelected();
        return;
    }

    try {

        const list = await apiFetch(
            "/api/teacher/group/" + groupId + "/students"
        );

        renderGroupStudents(block, list);

    } catch (err) {

        console.error("loadGroupStudents error:", err);
        hideTableIfGroupNotSelected();
    }
}

function renderGroupStudents(block, list) {

    const tbody = block.querySelector(".student-table");
    if (!tbody) return;

    tbody.innerHTML = "";

    if (!list || list.length === 0) {

        tbody.innerHTML =
            `<tr>
                <td colspan="4" class="text-center text-muted">
                    Bu guruhga a'zo o'quvchi yo'q    
                </td>
            </tr>`;

        showTableIfGroupIsSelected();
        return;
    }
list.forEach(function (s, index) {

        const row = document.createElement("tr");

        row.innerHTML =
            '<td class="text-center">' + (index + 1) + '</td>' +
            '<td class="text-center">' +
            '<input type="checkbox" class="student-checkbox" data-id="' + s.pupilId + '">' +
            '</td>' +
            '<td>' + s.username + '</td>' +
            '<td class="text-center" style="font-weight:bold;color:' +
            (s.status === "ACCEPTED" ? "green" : "orange") +
            '">' + s.status + '</td>';

        tbody.appendChild(row);
    });

    showTableIfGroupIsSelected();

    updateAssignButtonState(block);
}

//=======================================================================

// Инициализация "Select All" и индивидуальных чекбоксов
function initSelectAll() {
    let selectAll = document.getElementById("selectAllStudents");

    if (!selectAll) return;

    // При выборе/снятии "Select All"
    selectAll.addEventListener("change", function (e) {
        const checked = e.target.checked;
        document.querySelectorAll(".student-checkbox")
            .forEach(function (cb) {
                cb.checked = checked;
            });

        updateAssignButtonState();
    });

    // Слушатели на все существующие и будущие чекбоксы
    document.addEventListener("change", function (e) {
        if (e.target.classList.contains("student-checkbox")) {
            updateAssignButtonState();
        }
    });

    // Инициализация кнопки при старте
    updateAssignButtonState();
}

/* ===============================
   UNIVERSAL ASSIGN FUNCTION
================================ */

// Функция назначения теста
async function assignTest() {
    try {
        let assignBlock = document.querySelector("#rightSidebar .btn-assignTest").closest(".group-block");
        if (!assignBlock) throw new Error("Assign block not found");

        const groupSelect = assignBlock.querySelector(".group-select");
        const groupId = Number(groupSelect.value);
        if (!groupId) return alert("Выберите группу");

        const setSelect = document.getElementById("setSelect");
        const setId = Number(setSelect.value);
        if (!setId) return alert("Выберите тест");

        const dueDateInput = document.getElementById("dueDate");
        const dueDate = dueDateInput.value ? new Date(dueDateInput.value).toISOString() : null;

        const block = document.querySelector("#rightSidebar .group-block");
        if (!block) return;

        const checked = block.querySelectorAll(
            ".student-checkbox:checked"
        );

        const studentIds = Array.from(checked)
            .map(cb => Number(cb.dataset.id))
            .filter(Boolean);

        if (studentIds.length === 0) {
            alert("Выберите хотя бы одного студента");
            return;
        }

        console.log("selected students:", studentIds);


        const payload = {groupId, setId, dueDate, studentIds};

        await apiFetch("/api/teacher/assign", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(payload)
        });

        alert("Тест успешно назначен!");
        assignBlock.querySelectorAll(".student-checkbox:checked").forEach(cb => cb.checked = false);
        // Обновляем состояние кнопки после назначения
        assignBlock.querySelector(".btn-assignTest").disabled = true;
    } catch (err) {
        console.error(err);
        alert("Ошибка назначения: " + (err.message || err));
    }
}