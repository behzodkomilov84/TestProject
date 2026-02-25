document.addEventListener("DOMContentLoaded", loadAssignments);

async function loadAssignments() {

    const res = await fetch("/api/admin/assignments");
    const data = await res.json();

    const tbody = document.getElementById("assignmentTable");
    tbody.innerHTML = "";

    data.forEach(a => {

        tbody.innerHTML += `
            <tr>
                <td>${a.id}</td>
                <td>${a.questionSetName}</td>
                <td>${a.groupName}</td>
                <td>${formatDate(a.assignedAt)}</td>
                <td>${formatDate(a.dueDate)}</td>
                <td>${a.totalStudents}</td>
                <td>${a.finished}/${a.totalStudents}</td>
                <td>${a.avgPercent.toFixed(1)}%</td>
                <td>
                    <button class="btn btn-sm btn-primary"
                        onclick="openDetails(${a.id})">
                        Batafsil...
                    </button>
                </td>
            </tr>
        `;
    });
}

async function openDetails(id) {

    const res = await fetch(`/api/admin/assignments/${id}/details`);
    const data = await res.json();

    const tbody = document.getElementById("detailTable");
    tbody.innerHTML = "";

    data.forEach(s => {

        tbody.innerHTML += `
            <tr>
                <td>${s.pupilId}</td>
                <td>${s.pupilName}</td>
                <td>${s.status}</td>
                <td>${s.percent ?? 0}%</td>
                <td>${s.durationSec ?? 0}</td>
                <td>${formatDate(s.lastActivity)}</td>
            </tr>
        `;
    });

    new bootstrap.Modal(document.getElementById('detailModal')).show();
}

function formatDate(d) {
    if (!d) return "-";
    return new Date(d).toLocaleString();
}
