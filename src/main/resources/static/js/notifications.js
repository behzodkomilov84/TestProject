/* /notifications sahifasi — "Yangi" va "O'qilgan" tab'lari, KPI statistika. */

let currentNotifTab = "new";

function notifPageTimeAgo(dateStr) {
    // navbar.js'dagi notifTimeAgo bilan bir xil hisoblash — bu sahifada
    // ham mustaqil ishlashi uchun (skript yuklanish tartibiga bog'liq
    // bo'lmasin) alohida yozilgan.
    const diffMs = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diffMs / 60000);
    if (mins < 1) return "hozir";
    if (mins < 60) return mins + " daqiqa oldin";
    const hours = Math.floor(mins / 60);
    if (hours < 24) return hours + " soat oldin";
    const days = Math.floor(hours / 24);
    return days + " kun oldin";
}

function loadNotifStats() {
    fetch("/api/notifications/stats")
        .then(r => r.ok ? r.json() : { total: 0, unread: 0, read: 0 })
        .then(stats => {
            document.getElementById("statTotal").textContent = stats.total;
            document.getElementById("statUnread").textContent = stats.unread;
            document.getElementById("statRead").textContent = stats.read;
        })
        .catch(err => console.error(err));
}

function renderNotifPageList(items) {
    const list = document.getElementById("notifPageList");
    if (!list) return;

    if (!items.length) {
        list.innerHTML = currentNotifTab === "new"
            ? '<div class="notif-empty">🆕 Yangi bildirishnoma yo\'q</div>'
            : '<div class="notif-empty">✓ O\'qilgan bildirishnoma yo\'q</div>';
        return;
    }

    list.innerHTML = items.map(n => `
        <div class="notif-page-item ${n.read ? "" : "unread"}" data-id="${n.id}" data-link="${n.link || ""}">
            <div class="notif-message">${n.message}</div>
            <div class="notif-time">${notifPageTimeAgo(n.createdAt)}</div>
        </div>
    `).join("");

    list.querySelectorAll(".notif-page-item").forEach(item => {
        item.addEventListener("click", () => {
            const id = item.dataset.id;
            const link = item.dataset.link;

            if (item.classList.contains("unread")) {
                fetch(`/api/notifications/${id}/read`, { method: "POST" })
                    .then(() => {
                        if (link) location.href = link;
                    })
                    .catch(err => console.error(err));
            } else if (link) {
                location.href = link;
            }
        });
    });
}

function loadNotifTab(tab) {
    const list = document.getElementById("notifPageList");
    if (list) list.innerHTML = '<div class="notif-empty">Yuklanmoqda...</div>';

    fetch(`/api/notifications/by-status?read=${tab === "read"}`)
        .then(r => r.ok ? r.json() : [])
        .then(renderNotifPageList)
        .catch(err => console.error(err));
}

function switchNotifTab(tab) {
    currentNotifTab = tab;

    document.getElementById("tabNewBtn").classList.toggle("active", tab === "new");
    document.getElementById("tabReadBtn").classList.toggle("active", tab === "read");

    const url = new URL(location.href);
    url.searchParams.set("tab", tab);
    history.replaceState(null, "", url);

    loadNotifTab(tab);
}

function markAllRead() {
    fetch("/api/notifications/read-all", { method: "POST" })
        .then(() => {
            loadNotifStats();
            loadNotifTab(currentNotifTab);
            if (typeof refreshUnreadCount === "function") refreshUnreadCount();
        })
        .catch(err => console.error(err));
}

document.addEventListener("DOMContentLoaded", () => {
    if (!document.getElementById("notifPageList")) return;

    const initialTab = new URLSearchParams(location.search).get("tab") === "read" ? "read" : "new";

    loadNotifStats();
    switchNotifTab(initialTab);
});
