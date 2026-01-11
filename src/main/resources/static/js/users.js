// Получаем роль из data-role
const ROLE = document.body.dataset.role;

// Если роль не OWNER — редирект на логин
if (ROLE !== "ROLE_OWNER") {
    alert("⛔ Доступ запрещён");
    location.href = "/login";
}

document.addEventListener("DOMContentLoaded", loadUsers);

function loadUsers() {
    fetch("/api/users")
        .then(r => {
            if (!r.ok) throw new Error("403 or not authorized");
            return r.json();
        })
        .then(users => renderUsers(users))
        .catch(err => {
            alert("Ошибка загрузки пользователей");
            console.error(err);
        });
}

function renderUsers(users) {
    const tbody = document.getElementById("usersTableBody");
    tbody.innerHTML = "";

    users.forEach(user => {
        const tr = document.createElement("tr");

        tr.innerHTML = `
            <td>${user.id}</td>
            <td>${user.username}</td>
            <td>
                <select class="role-select" data-user-id="${user.id}" name="role">
                    <option value="ROLE_OWNER">OWNER</option>
                    <option value="ROLE_ADMIN">ADMIN</option>
                    <option value="ROLE_USER">USER</option>
                </select>
            </td>
            <td>
                <button class="action-btn" onclick="deleteUser(${user.id})" title="Delete">🗑️</button>
            </td>
        `;

        const select = tr.querySelector("select");
        // Устанавливаем текущую роль из БД
        select.value = user.role;

        // Сохраняем реальную роль из БД
        select.dataset.original = user.role;

        // Инициализируем цвет роли сразу
        updateRoleColor(select, user.role);

        // Обработчик изменения
        select.addEventListener("change", () => {
            changeRole(user.id, select.value, select);
        });

        tbody.appendChild(tr);
    });
}

async function changeRole(userId, newRole, select) {
    try {
        const response = await fetch("/api/users/change-role", {
            method: "PATCH",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({
                userId: userId,
                newRole: newRole
            })
        });
        if (response.status === 403) {
            const data = await response.json();
            alert(data.error); // ⛔ You cannot change your own role
            // Возвращаем селект к старой роли
            select.value = select.dataset.original;
        } else if (response.ok) {
            // Успешно
            const result = await response.json();
            alert(`Role updated: ${result.newRole}`);
            // Обновляем data-original
            select.dataset.original = result.newRole;

            // Можно добавить цветовую индикацию
            updateRoleColor(select, result.newRole);
        } else {
            alert("Error updating role");
            select.value = select.dataset.original;
        }
    } catch (err) {
        console.error(err);
        alert("Network error");
        select.value = select.dataset.original;
    }
}

// Функция для цветовой индикации ролей
function updateRoleColor(select, role) {
    switch (role) {
        case "ROLE_OWNER":
            select.style.backgroundColor = "#e1adad"; // красный
            select.style.color = "#000";
            break;
        case "ROLE_ADMIN":
            select.style.backgroundColor = "#cec07e"; // желтый
            select.style.color = "#000";
            break;
        case "ROLE_USER":
            select.style.backgroundColor = "#a8d7a8"; // зеленый
            select.style.color = "#000";
            break;
        default:
            select.style.backgroundColor = "";
            select.style.color = "";
    }
}

/*

// Инициализируем цвета при загрузке
document.querySelectorAll("select.role-select").forEach(select => {
    updateRoleColor(select, select.value);
});

*/

async function deleteUser(id) {
    if (!confirm("Foydalanuvchini o'chirmoqchimisiz?")) return;

    const response = await fetch(`/api/users/${id}`,
        {method: "DELETE"});
    if (response.status === 403) {
        const data = await response.json();
        alert(data.error); // ⛔ You cannot delete yourself
    } else if (response.ok) {
        // Успешно — удаляем строку из таблицы
        loadUsers();
    }
}


