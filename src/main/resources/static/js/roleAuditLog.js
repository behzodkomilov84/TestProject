// "Rol o'zgarishlari tarixi" — /users sahifasidan ajratilgan alohida
// sahifa (foydalanuvchi so'rovi, 2026-09-06).
const ROLE = document.body.dataset.role;

if (ROLE !== "ROLE_OWNER") {
    showAlertModal("⛔ Доступ запрещён");
    location.href = "/login";
}

const ROLE_AUDIT_ACTION_LABELS = {
    GRANTED: "✅ berildi",
    REVOKED: "❌ olib tashlandi"
};

const ROLE_AUDIT_SOURCE_LABELS = {
    MANUAL: "Qo'lda (checkbox)",
    SUBSCRIPTION: "Obuna (to'lov)",
    SYSTEM: "Tizim (avtomatik)"
};

document.addEventListener("DOMContentLoaded", () => {
    loadRoleAudit();
});

function loadRoleAudit() {
    fetch("/api/users/roles-audit")
        .then(r => r.ok ? r.json() : [])
        .then(renderRoleAudit)
        .catch(err => console.error(err));
}

function renderRoleAudit(logs) {
    const tbody = document.getElementById("roleAuditTableBody");
    if (!tbody) return;

    if (!logs.length) {
        tbody.innerHTML = `<tr><td colspan="6" class="empty-row">Hali rol o'zgarishi yo'q</td></tr>`;
        return;
    }

    tbody.innerHTML = logs.map(l => `
        <tr>
            <td>${new Date(l.createdAt).toLocaleString("uz-UZ")}</td>
            <td>${l.targetUsername}</td>
            <td>${l.roleName.replace("ROLE_", "")}</td>
            <td>${ROLE_AUDIT_ACTION_LABELS[l.action] || l.action}</td>
            <td>${l.changedByUsername}</td>
            <td>${ROLE_AUDIT_SOURCE_LABELS[l.source] || l.source}</td>
        </tr>
    `).join("");
}
