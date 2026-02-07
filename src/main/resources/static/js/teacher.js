const selected = new Set();

document.addEventListener("DOMContentLoaded", () => {
    loadQuestions();
    loadGroups();
    loadSets();
});

function loadQuestions() {

    fetch("/api/questions")
        .then(r => r.json())
        .then(list => {

            const box = document.getElementById("questions");
            box.innerHTML = "";

            list.forEach(q => {

                const div = document.createElement("div");
                div.className = "question";

                div.innerHTML = `
                    <label>
                        <input type="checkbox" value="${q.id}">
                        ${q.questionText}
                    </label>
                `;

                const cb = div.querySelector("input");

                cb.addEventListener("change", () => {

                    cb.checked
                        ? selected.add(q.id)
                        : selected.delete(q.id);

                    document.getElementById("counter")
                        .innerText = selected.size;
                });

                box.appendChild(div);
            });
        });
}

function createGroup() {

    const name = document.getElementById("groupName").value;

    fetch("/teacher/group", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({name})
    }).then(loadGroups);
}

function saveSet() {

    const name = document.getElementById("setName").value;

    fetch("/teacher/questionset", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
            name,
            questionIds: [...selected]
        })
    }).then(loadSets);
}

function assignToGroup() {

    const groupId = document.getElementById("groupSelect").value;
    const setId = document.getElementById("setSelect").value;

    fetch("/teacher/assign/group", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({groupId, setId})
    });
}

function loadGroups() {

    fetch("/teacher/groups")
        .then(r => r.json())
        .then(list => {

            const sel = document.getElementById("groupSelect");
            sel.innerHTML = "";

            list.forEach(g => {
                sel.innerHTML += `<option value="${g.id}">${g.name}</option>`;
            });
        });
}

function loadSets() {

    fetch("/teacher/questionsets")
        .then(r => r.json())
        .then(list => {

            const sel = document.getElementById("setSelect");
            sel.innerHTML = "";

            list.forEach(s => {
                sel.innerHTML += `<option value="${s.id}">${s.name}</option>`;
            });
        });
}
