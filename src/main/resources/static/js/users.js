
// Получаем роль из data-role
const ROLE = document.body.dataset.role;

// Если роль не OWNER — редирект на логин
if (ROLE !== "ROLE_OWNER") {
    alert("⛔ Доступ запрещён");
    location.href = "/login";
}

// Дальше сюда можно добавить загрузку пользователей через fetch/ajax
console.log("Роль пользователя:", ROLE);



document.addEventListener("DOMContentLoaded", loadUsers);

async function loadUsers() {
    const res = await fetch("/api/users");
    const users = await res.json();

    const tbody = document.getElementById("usersTableBody");
    tbody.innerHTML = "";

    users.forEach(u => {
        const tr = document.createElement("tr");

        const isOwner = u.role === "ROLE_OWNER";

        tr.innerHTML = `
            <td>${u.id}</td>
            <td>${u.username}</td>
            <td class="role ${u.role.replace("ROLE_", "")}">
                ${u.role}
            </td>
            <td>
                ${renderAction(u, isOwner)}
            </td>
        `;

        tbody.appendChild(tr);
    });
}

function renderAction(user, isOwner) {
    if (isOwner) {
        return `<button class="action-btn disabled">OWNER</button>`;
    }

    if (user.role === "ROLE_USER") {
        return `
            <button class="action-btn make-admin"
                onclick="changeRole(${user.id}, 'ADMIN')">
                Make Admin
            </button>
        `;
    }

    if (user.role === "ROLE_ADMIN") {
        return `
            <button class="action-btn remove-admin"
                onclick="changeRole(${user.id}, 'USER')">
                Remove Admin
            </button>
        `;
    }
}

async function changeRole(userId, role) {
    if (!confirm("Вы уверены?")) return;

    const res = await fetch(`/api/users/${userId}/role`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ role })
    });

    if (res.ok) {
        loadUsers();
    } else {
        alert("❌ Ошибка изменения роли");
    }
}
