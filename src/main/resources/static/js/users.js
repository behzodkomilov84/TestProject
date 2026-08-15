// Получаем роль из data-role
const ROLE = document.body.dataset.role;

// Если роль не OWNER — редирект на логин
if (ROLE !== "ROLE_OWNER") {
    alert("⛔ Доступ запрещён");
    location.href = "/login";
}

// Barcha mavjud rollar (checkbox sifatida ko'rsatiladi — dual-role)
const ALL_ROLES = ["ROLE_OWNER", "ROLE_ADMIN", "ROLE_USER"];

let cachedUsers = [];

document.addEventListener("DOMContentLoaded", () => {
    loadUsers();
    loadPendingSubscriptions();
    loadRoleAudit();
    loadMinAmount();
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
            cachedUsers = users;
            renderUsers(users, subscriptions);
            populateManualUserSelect(users);
        })
        .catch(err => {
            alert("Ошибка загрузки пользователей");
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

function renderUsers(users, subscriptions) {
    const tbody = document.getElementById("usersTableBody");
    tbody.innerHTML = "";

    users.forEach(user => {
        const tr = document.createElement("tr");

        // Har bir rol uchun checkbox — foydalanuvchi bir vaqtning o'zida
        // bir nechta rolga ega bo'lishi mumkin (masalan, ham o'qituvchi,
        // ham o'quvchi).
        const checkboxesHtml = ALL_ROLES.map(roleName => {
            const checked = user.roles.includes(roleName) ? "checked" : "";
            const label = roleName.replace("ROLE_", "");
            return `
                <label class="role-checkbox" style="margin-right:8px;">
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

        tr.innerHTML = `
            <td>${user.id}</td>
            <td>${user.username} ${user.locked ? '<span title="Bloklangan">🔒</span>' : ""}</td>
            <td><div class="roles-cell">${checkboxesHtml}</div></td>
            <td>${adminDurationText}</td>
            <td>
                <div class="actions-cell">
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

function populateManualUserSelect(users) {
    const select = document.getElementById("manualUserSelect");
    if (!select) return;

    select.innerHTML = users
        .map(u => `<option value="${u.id}">${u.username}</option>`)
        .join("");
}

// ================= To'lov / obuna paneli =================

function loadPendingSubscriptions() {
    fetch("/api/subscriptions?status=PENDING")
        .then(r => r.ok ? r.json() : [])
        .then(renderPendingSubscriptions)
        .catch(err => console.error(err));
}

function renderPendingSubscriptions(subscriptions) {
    const tbody = document.getElementById("pendingTableBody");
    if (!tbody) return;

    if (!subscriptions.length) {
        tbody.innerHTML = `<tr><td colspan="5" class="empty-row">Kutilayotgan so'rov yo'q</td></tr>`;
        return;
    }

    tbody.innerHTML = subscriptions.map(s => `
        <tr>
            <td>${s.username}</td>
            <td>${s.amount} so'm</td>
            <td>${s.source}</td>
            <td>${new Date(s.createdAt).toLocaleString("uz-UZ")}</td>
            <td>
                <button class="action-btn" onclick="confirmSubscription(${s.id})" title="Tasdiqlash">✅</button>
                <button class="action-btn" onclick="cancelSubscription(${s.id})" title="Rad etish">❌</button>
            </td>
        </tr>
    `).join("");
}

// ================= Rol o'zgarishlari tarixi (audit log) =================

const ROLE_AUDIT_ACTION_LABELS = {
    GRANTED: "✅ berildi",
    REVOKED: "❌ olib tashlandi"
};

const ROLE_AUDIT_SOURCE_LABELS = {
    MANUAL: "Qo'lda (checkbox)",
    SUBSCRIPTION: "Obuna (to'lov)",
    SYSTEM: "Tizim (avtomatik)"
};

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

async function confirmSubscription(id) {
    const months = prompt("ADMIN huquqi necha oyga beriladi?", "1");
    if (months === null) return;

    try {
        const res = await fetch(`/api/subscriptions/${id}/confirm`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ durationMonths: Number(months) || 1 })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            alert(data.error || "Xatolik yuz berdi");
            return;
        }

        alert("✅ Tasdiqlandi, ADMIN huquqi berildi.");
        loadPendingSubscriptions();
        loadUsers();
    } catch (err) {
        console.error(err);
        alert("Network error");
    }
}

async function cancelSubscription(id) {
    if (!confirm("So'rovni rad etmoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/subscriptions/${id}/cancel`, { method: "POST" });
        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            alert(data.error || "Xatolik yuz berdi");
            return;
        }

        loadPendingSubscriptions();
    } catch (err) {
        console.error(err);
        alert("Network error");
    }
}

async function createManualSubscription() {
    const userId = Number(document.getElementById("manualUserSelect").value);
    const amount = Number(document.getElementById("manualAmount").value);
    const durationMonths = Number(document.getElementById("manualDuration").value) || 1;
    const note = document.getElementById("manualNote").value.trim();

    if (!userId || !amount || amount <= 0) {
        alert("❌ Foydalanuvchi va to'g'ri summani kiriting");
        return;
    }

    try {
        const res = await fetch("/api/subscriptions", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ userId, amount, durationMonths, note })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            alert(data.error || "Xatolik yuz berdi");
            return;
        }

        alert("✅ To'lov qayd qilindi, ADMIN huquqi berildi.");
        document.getElementById("manualAmount").value = "";
        document.getElementById("manualNote").value = "";
        loadUsers();
        loadRoleAudit();
    } catch (err) {
        console.error(err);
        alert("Network error");
    }
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
            alert(data.error); // ⛔ Siz o'z rolingizni o'zgartira olmaysiz
            checkbox.checked = !adding; // eski holatga qaytaramiz
            return;
        }

        if (!response.ok) {
            const data = await response.json().catch(() => ({}));
            alert(data.error || "Xatolik yuz berdi");
            checkbox.checked = !adding; // eski holatga qaytaramiz
            return;
        }

        const result = await response.json();
        updateRoleColors(checkbox.closest("tr"), result.roles);
        loadRoleAudit();
    } catch (err) {
        console.error(err);
        alert("Network error");
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

// ================= To'lov sozlamalari (minimal summa) =================

function loadMinAmount() {
    const input = document.getElementById("minAmountInput");
    if (!input) return;

    fetch("/api/payments/min-amount")
        .then(r => r.ok ? r.json() : null)
        .then(data => {
            if (data) input.value = data.minAmountSom;
        })
        .catch(err => console.error(err));
}

async function saveMinAmount() {
    const value = Number(document.getElementById("minAmountInput").value);

    if (!value || value <= 0) {
        alert("❌ To'g'ri summa kiriting");
        return;
    }

    try {
        const res = await fetch("/api/payments/min-amount", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ minAmountSom: value })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            alert(data.error || "Xatolik yuz berdi");
            return;
        }

        alert("✅ Minimal summa saqlandi: " + data.minAmountSom + " so'm");
    } catch (err) {
        console.error(err);
        alert("Network error");
    }
}

async function unlockUser(id) {
    if (!confirm("Foydalanuvchini blokdan chiqarmoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/users/${id}/unlock`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "Xatolik yuz berdi");
            return;
        }
        loadUsers();
    } catch (err) {
        console.error(err);
        alert("Network error");
    }
}

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
