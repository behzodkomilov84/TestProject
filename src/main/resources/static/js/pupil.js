document.addEventListener("DOMContentLoaded", () => {
    loadInvites();
    loadAssignments();
});

function loadInvites() {

    fetch("/pupil/invites")
        .then(r => r.json())
        .then(list => {

            const box = document.getElementById("invites");
            box.innerHTML = "";

            list.forEach(i => {

                const div = document.createElement("div");

                div.innerHTML = `
                    ${i.groupName}
                    <button onclick="acceptInvite(${i.id})">
                        Принять
                    </button>
                `;

                box.appendChild(div);
            });
        });
}

function acceptInvite(id) {

    fetch("/pupil/invite/accept", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({inviteId: id})
    }).then(loadInvites);
}

function loadAssignments() {

    fetch("/pupil/assignments")
        .then(r => r.json())
        .then(list => {

            const box = document.getElementById("assignments");
            box.innerHTML = "";

            list.forEach(a => {

                const div = document.createElement("div");
                div.className = "assignment";

                div.innerHTML = `
                    ${a.name}
                    <button onclick="startAssignment(${a.id})">
                        Начать
                    </button>
                `;

                box.appendChild(div);
            });
        });
}

function startAssignment(id) {

    fetch(`/pupil/assignment/${id}`)
        .then(r => r.json())
        .then(test => {

            const correct = Math.floor(Math.random() * test.total);
            const duration = 120;

            fetch("/pupil/attempt", {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    assignmentId: id,
                    correct,
                    total: test.total,
                    duration
                })
            });
        });
}
