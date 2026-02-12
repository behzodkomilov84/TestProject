const selectedMap = new Map();
let currentGroupId = null;


document.addEventListener("DOMContentLoaded", () => {

    loadGroups();
    loadSciences();
    void loadAllGroupSelects();
    loadSets();
    initSelectAll();

    document.getElementById("scienceSelect")
        ?.addEventListener("change", e => {

            const id = e.target.value;

            if (id) loadTopics(id);
        });

    updatePlaceholder();
});

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

//=======================================================================
//              GRUPPANI TAHRIRLASH
//=======================================================================
function startInlineEdit(groupId) {

    const span = document.querySelector(
        `.group-name[data-id="${groupId}"]`
    );

    const oldValue = span.innerText;

    const input = document.createElement("input");
    input.value = oldValue;
    input.className = "form-control";

    span.replaceWith(input);
    input.focus();
    input.select();

    // save on enter
    input.addEventListener("keydown", e => {

        if (e.key === "Enter") {
            saveInlineEdit(groupId, input, oldValue);
        }

        if (e.key === "Escape") {
            cancelInlineEdit(input, oldValue, groupId);
        }
    });

    // save on blur
    input.addEventListener("blur", () =>
        saveInlineEdit(groupId, input, oldValue)
    );
}

function saveInlineEdit(groupId, input, oldValue) {

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
        .then(()=>{
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

        const assignSelect =
            document.getElementById("groupSelectToAssignTask");

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
        fillSelect(assignSelect);

        // загрузка студентов при выборе группы
        if (showMembersSelect) {
            showMembersSelect.onchange = e => {
                const groupId = e.target.value;
                if (!groupId) return;

                loadGroupStudents(groupId);
            };
        }

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
//=======================================================================


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
async function loadGroupStudents(groupId) {

    try {

        const tbody =
            document.querySelector(".student-table");

        if (!tbody) {
            console.warn("student-table not found");
            return;
        }

        const list =
            await apiFetch(`/api/teacher/group/${groupId}/students`);

        tbody.innerHTML = "";

        if (!list || list.length === 0) {

            tbody.innerHTML = `
                <tr>
                    <td colspan="3" style="text-align:center">
                        Bu guruhga a'zo o'quvchi yo'q
                    </td>
                </tr>
            `;

            return;
        }

        list.forEach(s => {

            const row = document.createElement("tr");

            row.innerHTML = `
                
                <td></td>
                
                <td>
                    <input type="checkbox"
                           class="student-checkbox"
                           data-id="${s.id}">
                </td>
                <td>${s.username}</td>
                <td style="
                        font-weight:bold;
                        color:${s.status === "ACCEPTED" ? "green" : "orange"}
                    ">
                    ${s.status}
                </td>
            `;

            tbody.appendChild(row);
        });

    } catch (err) {

        console.error("loadGroupStudents error:", err);
    }
}

// Выбор всех студентов
function initSelectAll() {
    const selectAll = document.getElementById("selectAllStudents");

    if (!selectAll) return;

    selectAll.addEventListener("change", e => {
        document.querySelectorAll(".student-checkbox")
            .forEach(cb => cb.checked = e.target.checked);
    });
}

/* ===============================
   UNIVERSAL ASSIGN FUNCTION
================================ */

// Универсальная функция назначения теста
async function assignTest() {
    try {
        const assignBlock = document.querySelector("#rightSidebar .btn-warning").closest(".sidebar-body");

        const groupSelect = assignBlock.querySelector(".group-select");
        const groupId = Number(groupSelect.value);
        if (!groupId) return alert("Выберите группу");

        const setId = Number(document.getElementById("setSelect").value);
        if (!setId) return alert("Выберите тест");

        const dueDate = document.getElementById("dueDate").value || null;

        // Получаем всех студентов из таблицы (если ничего не выбрано, назначаем всей группе)
        let studentIds = [...assignBlock.querySelectorAll(".student-checkbox")].map(cb => Number(cb.value));
        const checked = [...assignBlock.querySelectorAll(".student-checkbox:checked")].map(cb => Number(cb.value));
        if (checked.length > 0) studentIds = checked;

        const payload = { groupId, setId, dueDate, studentIds };

        await apiFetch("/api/teacher/assign", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        alert("Тест успешно назначен!");
    } catch (err) {
        console.error(err);
        alert("Ошибка назначения: " + err.message);
    }
}


