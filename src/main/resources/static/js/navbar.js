document.addEventListener("DOMContentLoaded", () => {
    const roleB = document.querySelector(".nav-center b[data-role]");
    if (!roleB) return;

    // Dual-role: foydalanuvchida bir nechta rol bo'lishi mumkin
    // (masalan "OWNER,ADMIN"), shuning uchun eng "yuqori" rolga qarab rang tanlanadi.
    const roles = roleB.dataset.role.split(",").map(r => r.trim());

    if (roles.includes("OWNER")) {
        roleB.style.color = "#b71c1c"; // красный
    } else if (roles.includes("ADMIN")) {
        roleB.style.color = "#856404"; // золотой
    } else if (roles.includes("USER")) {
        roleB.style.color = "#1b5e20"; // зелёный
    }
});

function toggleMenu() {
    document.getElementById("nav-menu").classList.toggle("active");
}

document.querySelectorAll(".nav-items a").forEach(a =>
    a.addEventListener("click", () =>
        document.getElementById("nav-menu").classList.remove("active")
    )
);

/* закрытие при клике вне меню */
document.addEventListener("click", e => {
    const menu = document.getElementById("nav-menu");
    const burger = document.querySelector(".burger");

    if (!menu.contains(e.target) && !burger.contains(e.target)) {
        menu.classList.remove("active");
    }
});

document.querySelectorAll(".dropbtn").forEach(btn => {
    btn.addEventListener("click", () => {
        btn.parentElement.classList.toggle("active");
    });
});

async function linkTelegram() {

    try {
        const res = await fetch("/api/telegram/link", {
            method: "POST"
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok || !data.code) {
            alert(data.error || "❌ Kod olishda xatolik yuz berdi. Qayta urinib ko'ring.");
            return;
        }

        alert("Botga ulanish uchun botga quyidagini yozing: /link " + data.code);
    } catch (err) {
        console.error(err);
        alert("❌ Tarmoq xatoligi — qayta urinib ko'ring.");
    }
}

/* ===== Bildirishnomalar (notification center) ===== */

function toggleNotifications(e) {
    e.stopPropagation();
    const panel = document.getElementById("notif-panel");
    if (!panel) return;

    const willOpen = !panel.classList.contains("open");
    panel.classList.toggle("open", willOpen);

    if (willOpen) {
        loadNotifications();
    }
}

document.addEventListener("click", (e) => {
    const panel = document.getElementById("notif-panel");
    const bellBtn = document.querySelector(".btn-bell");
    if (!panel || !panel.classList.contains("open")) return;

    if (!panel.contains(e.target) && bellBtn && !bellBtn.contains(e.target)) {
        panel.classList.remove("open");
    }
});

function notifTimeAgo(dateStr) {
    const diffMs = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diffMs / 60000);
    if (mins < 1) return "hozir";
    if (mins < 60) return mins + " daqiqa oldin";
    const hours = Math.floor(mins / 60);
    if (hours < 24) return hours + " soat oldin";
    const days = Math.floor(hours / 24);
    return days + " kun oldin";
}

function renderNotifications(items) {
    const list = document.getElementById("notif-list");
    if (!list) return;

    if (!items.length) {
        list.innerHTML = '<div class="notif-empty">Bildirishnoma yo\'q</div>';
        return;
    }

    list.innerHTML = items.map(n => `
        <div class="notif-item ${n.read ? "" : "unread"}" data-id="${n.id}" data-link="${n.link || ""}">
            <div class="notif-message">${n.message}</div>
            <div class="notif-time">${notifTimeAgo(n.createdAt)}</div>
        </div>
    `).join("");

    list.querySelectorAll(".notif-item").forEach(item => {
        item.addEventListener("click", () => {
            const id = item.dataset.id;
            const link = item.dataset.link;

            fetch(`/api/notifications/${id}/read`, { method: "POST" })
                .then(() => {
                    item.classList.remove("unread");
                    refreshUnreadCount();
                    if (link) location.href = link;
                })
                .catch(err => console.error(err));
        });
    });
}

function loadNotifications() {
    fetch("/api/notifications")
        .then(r => r.ok ? r.json() : [])
        .then(renderNotifications)
        .catch(err => console.error(err));
}

function refreshUnreadCount() {
    fetch("/api/notifications/unread-count")
        .then(r => r.ok ? r.json() : { count: 0 })
        .then(data => {
            const badge = document.getElementById("notif-badge");
            if (!badge) return;

            if (data.count > 0) {
                badge.style.display = "inline-flex";
                badge.textContent = data.count > 99 ? "99+" : data.count;
            } else {
                badge.style.display = "none";
            }
        })
        .catch(err => console.error(err));
}

function markAllNotificationsRead() {
    fetch("/api/notifications/read-all", { method: "POST" })
        .then(() => {
            document.querySelectorAll(".notif-item.unread").forEach(el => el.classList.remove("unread"));
            refreshUnreadCount();
        })
        .catch(err => console.error(err));
}

document.addEventListener("DOMContentLoaded", () => {
    if (document.querySelector(".btn-bell")) {
        refreshUnreadCount();
        setInterval(refreshUnreadCount, 30000);
    }
});


