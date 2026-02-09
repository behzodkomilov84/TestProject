const selectedMap = new Map();

document.addEventListener("DOMContentLoaded", () => {

    loadGroups();
    // loadStudents();
    loadSciences();
    loadSets();
    loadGroupSelect();

    document
        .getElementById("scienceSelect")
        ?.addEventListener("change", e => {

            const id = e.target.value;

            if (id) loadTopics(id);
        });

    updatePlaceholder();
});

/* SIDEBAR TOGGLE */

function toggleSidebar(id) {
    document.getElementById(id).classList.toggle("collapsed");
}

/* GROUPS */

function loadGroups() {

    fetch("/api/teacher/get-groups")
        .then(r => r.json())
        .then(list => {

            const ul = document.getElementById("groupList");
            ul.innerHTML = "";

            list.forEach(g => {

                ul.innerHTML += `
<li class="list-group-item d-flex justify-content-between align-items-center">

    <span class="group-name"
          data-id="${g.teacherGroupId}">
        ${g.groupName}
    </span>

    <div>
        <button onclick="startInlineEdit(${g.teacherGroupId})">✏️</button>
        <button onclick="deleteGroup(${g.teacherGroupId})">🗑</button>
        <button onclick="openAddStudentModal(${g.teacherGroupId})">➕</button>
    </div>

</li>`;
            });
        });
}

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
}

function cancelInlineEdit(input, value, groupId) {

    replaceWithSpan(input, groupId, value);
}

function replaceWithSpan(input, groupId, text) {

    const span = document.createElement("span");
    span.className = "group-name";
    span.dataset.id = groupId;
    span.innerText = text;

    input.replaceWith(span);
}

/*function createGroup() {
    fetch("/api/teacher/create-group", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({name: groupName.value})
    }).then(loadGroups);
}*/
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
            loadGroupSelect();  // обновляем select справа

            // очищаем input
            nameInput.value = "";
        })
        .catch(err => {
            console.error(err);
            alert("Ошибка при создании группы");
        });
}

function deleteGroup(id) {
    fetch(`/api/teacher/groups/${id}`, {method: "DELETE"})
        .then(()=>{
            loadGroups();
            loadGroupSelect();
        });



}

/* SCIENCE/TOPIC */

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
}

document
    .getElementById("scienceSelect")
    .addEventListener("change", e => {

        const scienceId = e.target.value;

        if (scienceId) loadTopics(scienceId);
    });


function loadTopics(scienceId) {
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
}

/* QUESTIONS */
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
}

function toggleQuestion(id, checkbox, text) {

    if (checkbox.checked) {

        selectedMap.set(id, {id, text});
        addSelectedUI(id, text);

    } else {

        selectedMap.delete(id);
        removeSelectedUI(id);
    }

    updateCounter();
}

function removeSelected(id) {

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
}

function removeSelectedUI(id) {

    const el =
        document.getElementById("sel-" + id);

    if (el) el.remove();
}

function updateCounter() {

    document.getElementById("counter")
        .innerText = selectedMap.size;
}

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
          onclick="removeSelected(${id})">
          ✖
    </span>

</div>`;

    updatePlaceholder();
}

/* TEST SET */
// Отправка выбранных вопросов на бэкенд для создания QuestionSet
function saveSet() {

    const name =
        document.getElementById("setName")
            .value.trim();

    if (!name || selectedMap.size === 0) {

        alert("Введите имя и выберите хотя бы один вопрос");
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
                throw new Error("Ошибка сохранения набора");

            // сбрасываем builder
            resetBuilder();

            // обновляем select справа
            loadSets();

            // уведомляем пользователя
            alert("Набор вопросов успешно сохранён!");
        })
        .catch(err => {
        console.error(err);
        alert("Ошибка при сохранении набора");
    });
}

function updatePlaceholder() {

    const ph =
        document.getElementById("selectedPlaceholder");

    if (!ph) return;

    ph.style.display =
        selectedMap.size === 0
            ? "flex"
            : "none";
}

function resetBuilder() {

    selectedMap.clear();

    document
        .querySelectorAll("#selectedList .question-item")
        .forEach(el => el.remove());

    document.getElementById("counter")
        .innerText = 0;

    document.getElementById("setName")
        .value = "";

    document
        .querySelectorAll("#questions input")
        .forEach(cb => cb.checked = false);

    updatePlaceholder();
}

function loadSets() {
    const setSelect = document.getElementById("setSelect");

    fetch("/api/teacher/questionsets")
        .then(r => r.json())
        .then(list => {

            setSelect.innerHTML = `<option value="">--Testni tanlang--</option>`;

            list.forEach(s =>
                setSelect.innerHTML += `<option value="${s.id}">${s.name}</option>`
            );
        })
        .catch(err => console.error("Error loading sets:", err));
}

/* STUDENTS */
function loadStudents() {
    fetch("/api/teacher/group/students")
        .then(r => r.json())
        .then(list => {

            studentList.innerHTML = "";

            list.forEach(s =>
                studentList.innerHTML += `
<label>
<input type="checkbox" value="${s.id}">
${s.name}
</label><br>`
            );
        });
}

/* ASSIGN */
function assignToGroup() {
    fetch("/api/teacher/assign/group", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
            groupId: groupSelect.value,
            setId: setSelect.value
        })
    });
}

function loadGroupSelect() {

    const select =
        document.getElementById("groupSelect");

    fetch("/api/teacher/groups/select")
        .then(r => r.json())
        .then(list => {

            select.innerHTML =
                `<option value="">--Guruhni tanlang--</option>`;

            list.forEach(g => {

                select.innerHTML += `<option value="${g.id}">${g.name}</option>`;
            });

            // Добавляем обработчик выбора группы
            select.addEventListener("change", e => {
                const groupId = e.target.value;
                if (groupId) {
                    loadGroupStudents(groupId);
                } else {
                    clearStudentTable();
                }
            });
        })
        .catch(err =>
            console.error("Groups load error:", err)
        );
}

//Load студентов для правого сайдбара
function loadGroupStudents(groupId) {
    fetch(`/api/teacher/group/${groupId}/students`)
        .then(r => {
            if (!r.ok) throw new Error("Failed to load group students");
            return r.json();
        })
        .then(list => {
            const table = document.getElementById("studentTable");
            table.innerHTML = "";

            list.forEach(s => {
                const color = s.status === "ACCEPTED" ? "green" : "orange";

                table.innerHTML += `
                                <tr>
                                <td>${s.username}</td>
                                <td style="font-weight:bold;color:${color}">${s.status}</td>
                                </tr>`;
            });
        })
        .catch(err => console.error(err));
}

function clearStudentTable() {
    document.getElementById("studentTable").innerHTML = "";
}

//==========================================================
//MODAL
let currentGroupId = null;

function openAddStudentModal(groupId) {
    currentGroupId = groupId;

    /*список всех студентов/users для invite modal.*/
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
//==========================================================

function inviteStudent(pupilId) {
    fetch(`/api/teacher/group/${currentGroupId}/invite`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({pupilId})
    })
        .then(r => {
            if (!r.ok) throw new Error("Ошибка приглашения");
            loadGroupStudents(currentGroupId);
            alert("Приглашение отправлено");
        })
        .catch(() => alert("Ошибка приглашения"));
}


