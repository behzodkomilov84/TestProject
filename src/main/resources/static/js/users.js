// Получаем роль из data-role
const ROLE = document.body.dataset.role;

// Если роль не OWNER — редирект на логин
if (ROLE !== "ROLE_OWNER") {
    showAlertModal("⛔ Доступ запрещён");
    location.href = "/login";
}

// Barcha mavjud rollar (checkbox sifatida ko'rsatiladi — dual-role)
const ALL_ROLES = ["ROLE_OWNER", "ROLE_ADMIN", "ROLE_USER"];

// Oxirgi yuklangan foydalanuvchilar ro'yxati id bo'yicha — Edit oynasini
// to'ldirish uchun qayta so'rov yubormasdan shu yerdan olinadi.
let usersById = {};

document.addEventListener("DOMContentLoaded", () => {
    loadUsers();
    document.getElementById("editUserForm").addEventListener("submit", submitEditUser);
});

function loadUsers() {
    Promise.all([
        fetch("/api/users").then(r => {
            if (!r.ok) throw new Error("403 or not authorized");
            return r.json();
        }),
        fetch("/api/subscriptions").then(r => r.ok ? r.json() : [])
    ])
        .then(([users, subscriptions]) => {
            renderUsers(users, subscriptions);
        })
        .catch(err => {
            showAlertModal("Ошибка загрузки пользователей");
            console.error(err);
        });
}

// Har bir foydalanuvchi uchun eng so'nggi faol (CONFIRMED, muddati o'tmagan)
// obunani topadi — ADMIN roli qachongacha amal qilishini ko'rsatish uchun.
function findActiveSubscription(subscriptions, userId) {
    const now = new Date();

    return subscriptions
        .filter(s => s.userId === userId && s.status === "CONFIRMED" && s.endDate && new Date(s.endDate) > now)
        .sort((a, b) => new Date(b.endDate) - new Date(a.endDate))[0];
}

function escapeHtml(text) {
    if (text === null || text === undefined) return "";
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
}

function renderUsers(users, subscriptions) {
    const tbody = document.getElementById("usersTableBody");
    tbody.innerHTML = "";

    usersById = Object.fromEntries(users.map(u => [u.id, u]));

    users.forEach(user => {
        const tr = document.createElement("tr");

        // Har bir rol uchun checkbox — foydalanuvchi bir vaqtning o'zida
        // bir nechta rolga ega bo'lishi mumkin (masalan, ham o'qituvchi,
        // ham o'quvchi).
        const checkboxesHtml = ALL_ROLES.map(roleName => {
            const checked = user.roles.includes(roleName) ? "checked" : "";
            const label = roleName.replace("ROLE_", "");
            return `
                <label class="role-checkbox">
                    <input type="checkbox"
                           data-user-id="${user.id}"
                           data-role-name="${roleName}"
                           ${checked}>
                    ${label}
                </label>
            `;
        }).join("");

        // ADMIN muddati: agar obuna orqali berilgan bo'lsa — tugash sanasi,
        // aks holda (obunasiz, checkbox orqali berilgan bo'lsa) "doimiy".
        let adminDurationText = "—";
        if (user.roles.includes("ROLE_ADMIN")) {
            const active = findActiveSubscription(subscriptions, user.id);
            adminDurationText = active
                ? "⏳ " + new Date(active.endDate).toLocaleDateString("uz-UZ")
                : "♾️ doimiy";
        }

        const unlockButtonHtml = user.locked
            ? `<button class="action-btn" onclick="unlockUser(${user.id})" title="Blokdan chiqarish">🔓</button>`
            : "";

        // Barcha ustunlar (foydalanuvchi so'rovi, 2026-09-06: "users
        // jadvalidagi barcha ustunlarni Foydalanuvchilar sahifasiga
        // chiqar" — Telegram/telefon dublikat muammosini debug qilish
        // uchun). Bo'sh qiymatlar "—" bilan ko'rsatiladi.
        const fullName = [user.firstName, user.lastName].filter(Boolean).join(" ") || "—";

        tr.innerHTML = `
            <td>${user.id}</td>
            <td>${user.username} ${user.locked ? '<span title="Bloklangan">🔒</span>' : ""}</td>
            <td>${escapeHtml(fullName)}</td>
            <td>${escapeHtml(user.email) || "—"}</td>
            <td>${escapeHtml(user.phoneNumber) || "—"}</td>
            <td>${user.telegramId ?? "—"}</td>
            <td>${user.telegramUsername ? "@" + escapeHtml(user.telegramUsername) : "—"}</td>
            <td>${escapeHtml(user.googleId) || "—"}</td>
            <td>${escapeHtml(user.workplace) || "—"}</td>
            <td>${escapeHtml(user.jobTitle) || "—"}</td>
            <td><div class="roles-cell">${checkboxesHtml}</div></td>
            <td>${adminDurationText}</td>
            <td>
                <div class="actions-cell">
                    <button class="action-btn" onclick="openEditModal(${user.id})" title="Tahrirlash">✏️</button>
                    ${unlockButtonHtml}
                    <button class="action-btn" onclick="deleteUser(${user.id})" title="Delete">🗑️</button>
                </div>
            </td>
        `;

        tr.querySelectorAll('input[type="checkbox"]').forEach(checkbox => {
            checkbox.addEventListener("change", () => {
                toggleRole(user.id, checkbox.dataset.roleName, checkbox);
            });
        });

        updateRoleColors(tr, user.roles);

        tbody.appendChild(tr);
    });
}

async function toggleRole(userId, roleName, checkbox) {
    const adding = checkbox.checked;

    try {
        const response = await fetch(
            `/api/users/${userId}/roles/${roleName}`,
            { method: adding ? "POST" : "DELETE" }
        );

        if (response.status === 403) {
            const data = await response.json();
            showAlertModal(data.error); // ⛔ O'z rolingizni o'zgartira olmaysiz
            checkbox.checked = !adding; // eski holatga qaytaramiz
            return;
        }

        if (!response.ok) {
            const data = await response.json().catch(() => ({}));
            showAlertModal(data.error || "Xatolik yuz berdi");
            checkbox.checked = !adding; // eski holatga qaytaramiz
            return;
        }

        const result = await response.json();
        updateRoleColors(checkbox.closest("tr"), result.roles);
    } catch (err) {
        console.error(err);
        showAlertModal("Network error");
        checkbox.checked = !adding;
    }
}

// Rol checkboxlariga rang berish (faol rollarni ajratib ko'rsatish uchun)
function updateRoleColors(tr, roles) {
    tr.querySelectorAll('input[type="checkbox"]').forEach(cb => {
        const label = cb.closest("label");
        if (!label) return;

        if (roles.includes(cb.dataset.roleName)) {
            switch (cb.dataset.roleName) {
                case "ROLE_OWNER":
                    label.style.color = "#7a1f1f";
                    break;
                case "ROLE_ADMIN":
                    label.style.color = "#8a6d00";
                    break;
                case "ROLE_USER":
                    label.style.color = "#1b5e20";
                    break;
            }
        } else {
            label.style.color = "";
        }
    });
}

async function unlockUser(id) {
    if (!await showConfirmModal("Foydalanuvchini blokdan chiqarmoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/users/${id}/unlock`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }
        loadUsers();
    } catch (err) {
        console.error(err);
        showAlertModal("Network error");
    }
}

async function deleteUser(id) {
    if (!await showConfirmModal("Foydalanuvchini o'chirmoqchimisiz?", { danger: true })) return;

    const response = await fetch(`/api/users/${id}`,
        {method: "DELETE"});
    if (response.status === 403) {
        const data = await response.json();
        showAlertModal(data.error); // ⛔ You cannot delete yourself
    } else if (response.ok) {
        // Успешно — удаляем строку из таблицы
        loadUsers();
    }
}

// ===== Foydalanuvchini tahrirlash (Edit modal) =====
// Foydalanuvchi so'rovi, 2026-09-07: "sahifasiga edit ni qo'shish kerak".

function openEditModal(id) {
    const user = usersById[id];
    if (!user) return;

    document.getElementById("editUserId").value = user.id;
    document.getElementById("editUsername").value = user.username || "";
    document.getElementById("editFirstName").value = user.firstName || "";
    document.getElementById("editLastName").value = user.lastName || "";
    document.getElementById("editEmail").value = user.email || "";
    document.getElementById("editPhoneNumber").value = user.phoneNumber || "";
    document.getElementById("editWorkplace").value = user.workplace || "";
    document.getElementById("editJobTitle").value = user.jobTitle || "";
    document.getElementById("editTelegramId").value = user.telegramId || "";
    document.getElementById("editTelegramUsername").value = user.telegramUsername || "";
    document.getElementById("editGoogleId").value = user.googleId || "";

    const errorEl = document.getElementById("editUserError");
    errorEl.hidden = true;
    errorEl.textContent = "";

    document.getElementById("editUserOverlay").hidden = false;
}

function closeEditModal() {
    document.getElementById("editUserOverlay").hidden = true;
}

async function submitEditUser(event) {
    event.preventDefault();

    const id = document.getElementById("editUserId").value;
    const dto = {
        username: document.getElementById("editUsername").value.trim(),
        firstName: document.getElementById("editFirstName").value.trim(),
        lastName: document.getElementById("editLastName").value.trim(),
        email: document.getElementById("editEmail").value.trim(),
        phoneNumber: document.getElementById("editPhoneNumber").value.trim(),
        workplace: document.getElementById("editWorkplace").value.trim(),
        jobTitle: document.getElementById("editJobTitle").value.trim(),
        telegramId: document.getElementById("editTelegramId").value.trim(),
        telegramUsername: document.getElementById("editTelegramUsername").value.trim(),
        googleId: document.getElementById("editGoogleId").value.trim()
    };

    const errorEl = document.getElementById("editUserError");
    errorEl.hidden = true;

    try {
        const response = await fetch(`/api/users/${id}`, {
            method: "PUT",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(dto)
        });

        if (!response.ok) {
            const data = await response.json().catch(() => ({}));
            errorEl.textContent = data.error || "Xatolik yuz berdi";
            errorEl.hidden = false;
            return;
        }

        closeEditModal();
        loadUsers();
    } catch (err) {
        console.error(err);
        errorEl.textContent = "Network error";
        errorEl.hidden = false;
    }
}
