/*document.addEventListener("DOMContentLoaded", () => {
    void loadMembershipGroups();
});*/

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
                    <button class="btn btn-success btn-sm"
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

    return date + "<br>" + time;
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

