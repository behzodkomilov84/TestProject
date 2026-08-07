function toggleSidebar() {
    const sidebar = document.getElementById("studentSidebar");
    const workspace = document.querySelector(".workspace");

    sidebar.classList.toggle("collapsed");
    workspace.classList.toggle("full");
}

async function loadInvites() {

    setTitle("Gruppaga taklif");

    try {
        const list = await apiFetch(`/api/student/invites`);

        let html = `
        <div class="table-box">
        <table class="table">
        <thead>
        <tr>
            <th>Group</th>
            <th>Status</th>
            <th></th>
        </tr>
        </thead><tbody>`;

        list.forEach(inv => {
            html += `
            <tr>
                <td>${inv.groupName}</td>
                <td>${inv.status}</td>
                <td>
                    <button class="btn btn-success btn-sm me-1"
                        onclick="acceptInvite(${inv.id})">
                        Accept
                    </button>

                    <button class="btn btn-danger btn-sm"
                        onclick="rejectInvite(${inv.id})">
                        Reject
                    </button>
                </td>
            </tr>`;
        });

        html += "</tbody></table></div>";

        render(html);

    } catch (e) {
        showError(e.message);
    }
}

async function acceptInvite(id) {
    try {
        await apiFetch(`/api/student/invite/${id}/accept`, {method: "POST"});
        await loadInvites();
    } catch (e) {
        showError(e.message);
    }
}

async function rejectInvite(id) {
    try {
        await apiFetch(`/api/student/invite/${id}/reject`, {method: "POST"});
        await loadInvites();
    } catch (e) {
        showError(e.message);
    }
}

function formatDateTime(iso) {
    if (!iso) return "";

    const [date, time] = iso.split("T");

    return date + ", " + time;
}

function render(html) {
    document.getElementById("workspaceContent").innerHTML = html;
}

function setTitle(text) {
    document.getElementById("workspaceTitle").innerText = text;
}

function showError(msg) {

    document.getElementById("errorText").innerText = msg;

    const modalEl = document.getElementById("errorModal");
    const modal = new bootstrap.Modal(modalEl);

    modalEl.addEventListener("hide.bs.modal", () => {
        document.activeElement?.blur();
    }, {once: true});

    modal.show();
}

async function apiFetch(url, options = {}) {

    options.headers = {
        "Content-Type": "application/json",
        ...(options.headers || {})
    };

    const res = await fetch(url, options);

    if (!res.ok) {
        const text = await res.text();
        throw new Error(text);
    }

    return res.json().catch(() => ({}));
}


async function loadMembershipGroups() {

    setTitle("A'zolik guruhlari");

    try {

        const groups = await apiFetch(
            `/api/student/memberships`,
            { method: "GET" }
        );

        renderGroups(groups);

    } catch (e) {
        showError(e.message);
    }
}

function renderGroups(groups) {

    if (!groups || groups.length === 0) {

        render(`
            <div class="center-msg">
                Siz hali hech qanday guruhga a'zo emassiz
            </div>
        `);

        return;
    }

    let html = `
        <div class="table-box">
        <table class="table">
        <thead>
        <tr>
            <th>Group</th>
            <th>Role</th>
            <th></th>
        </tr>
        </thead>
        <tbody>
    `;

    groups.forEach(g => {

        html += `
            <tr>
                <td>${escapeHtml(g.groupName)}</td>
                <td>${escapeHtml(g.role)}</td>
                <td>
                    <button class="btn btn-outline-primary btn-sm"
                        onclick="openGroup(${g.id})">
                        Open
                    </button>
                </td>
            </tr>
        `;
    });

    html += `
        </tbody>
        </table>
        </div>
    `;

    render(html);
}

function openGroup(groupId) {

    window.location.href = `/student/group/${groupId}`;
}

/*
    Statistika — /profile sahifasidagi "Test tarixi" bilan bir xil manba
    (/api/profile/stats + /api/profile/history), shu workspace ichida.
*/
const statsPageSize = 5;

async function loadStatistics(page = 0) {

    setTitle("Statistika");

    try {
        const [stats, history] = await Promise.all([
            apiFetch(`/api/profile/stats`),
            apiFetch(`/api/profile/history?page=${page}&size=${statsPageSize}`)
        ]);

        renderStatistics(stats, history);

    } catch (e) {
        showError(e.message);
    }
}

function renderStatistics(stats, history) {

    let html = `
        <div class="row g-3 mb-4">
            ${statBox("Jami testlar", stats.totalTests)}
            ${statBox("O'rtacha natija", stats.avgPercent + "%")}
            ${statBox("Eng yaxshi natija", stats.bestPercent + "%")}
            ${statBox("Eng yomon natija", stats.worstPercent + "%")}
            ${statBox("Jami vaqt", stats.totalDurationSec + " sek")}
        </div>

        <div class="table-box">
        <table class="table">
        <thead>
        <tr>
            <th>ID</th>
            <th>Boshlandi</th>
            <th>Tugadi</th>
            <th>%</th>
            <th></th>
        </tr>
        </thead>
        <tbody>
    `;

    if (!history.content || !history.content.length) {
        html += `<tr><td colspan="5" class="text-center text-muted">Hali test tarixi yo'q</td></tr>`;
    } else {
        history.content.forEach(t => {
            html += `
                <tr>
                    <td>${t.testSessionId}</td>
                    <td>${formatDateTime(t.startedAt)}</td>
                    <td>${t.finishedAt ? formatDateTime(t.finishedAt) : "—"}</td>
                    <td>${t.percent}%</td>
                    <td>
                        <button class="btn btn-outline-primary btn-sm"
                            onclick="viewStatTest(${t.testSessionId})">
                            Ko'rish
                        </button>
                    </td>
                </tr>
            `;
        });
    }

    html += `</tbody></table></div>`;

    if (history.totalPages > 1) {
        html += `<div class="d-flex gap-2 flex-wrap mt-3">`;
        for (let i = 0; i < history.totalPages; i++) {
            html += `
                <button class="btn btn-sm ${i === history.number ? "btn-primary" : "btn-outline-secondary"}"
                    onclick="loadStatistics(${i})">
                    ${i + 1}
                </button>
            `;
        }
        html += `</div>`;
    }

    render(html);
}

function statBox(label, value) {
    return `
        <div class="col-6 col-md-2">
            <div class="card text-center p-2 h-100">
                <div class="text-muted small">${label}</div>
                <div class="fs-4 fw-bold">${value}</div>
            </div>
        </div>
    `;
}

function viewStatTest(testSessionId) {
    window.location.href = `/profile/test/${testSessionId}`;
}


/*
    Мини-защита от XSS при выводе текста
*/
function escapeHtml(str) {

    if (!str) return "";

    return str
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

/*
    Bildirishnomadan aniq bo'limga o'tish — masalan guruhga taklif qilingan
    (?tab=invites) yoki yangi topshiriq berilgan (?tab=tasks&assignmentId=X)
    bildirishnomasini bosganda, foydalanuvchi yon paneldan qo'lda bo'lim
    tanlashi shart bo'lmasin, to'g'ridan-to'g'ri o'sha joyga tushsin.
*/
document.addEventListener("DOMContentLoaded", () => {
    const params = new URLSearchParams(location.search);
    const tab = params.get("tab");

    if (tab === "invites") {
        loadInvites();
    } else if (tab === "tasks") {
        const assignmentId = Number(params.get("assignmentId"));
        loadTasks().then(() => {
            if (assignmentId && taskStore.byId.has(assignmentId)) {
                showCurrentTask(assignmentId);
            }
        });
    } else if (tab === "membership") {
        loadMembershipGroups();
    } else if (tab === "stats") {
        loadStatistics();
    }
});

