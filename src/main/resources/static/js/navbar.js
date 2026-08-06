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

/* ===== Bildirishnomalar (notification center) =====
   Bell ustidagi panel endi to'liq ro'yxatni ko'rsatmaydi — u faqat qisqa
   "kirish darvozasi": yangi bildirishnoma bo'lsa shu haqda yozadi va
   /notifications sahifasiga o'tkazadi, bo'lmasa faol emas. To'liq
   ro'yxat, tab'lar (Yangi/O'qilgan) va statistika — notifications.js. */

function toggleNotifications(e) {
    e.stopPropagation();
    const panel = document.getElementById("notif-panel");
    if (!panel) return;

    const willOpen = !panel.classList.contains("open");
    panel.classList.toggle("open", willOpen);

    if (willOpen) {
        refreshUnreadCount();
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

function goToNotifTab(tab) {
    if (tab === "new") {
        const gateway = document.getElementById("notifGatewayNew");
        if (!gateway || !gateway.classList.contains("active")) return; // yangi yo'q — bosilmaydi
    }
    location.href = "/notifications?tab=" + tab;
}

function refreshUnreadCount() {
    fetch("/api/notifications/unread-count")
        .then(r => r.ok ? r.json() : { count: 0 })
        .then(data => {
            const badge = document.getElementById("notif-badge");
            if (badge) {
                if (data.count > 0) {
                    badge.style.display = "inline-flex";
                    badge.textContent = data.count > 99 ? "99+" : data.count;
                } else {
                    badge.style.display = "none";
                }
            }

            const gatewayNew = document.getElementById("notifGatewayNew");
            const gatewayNewText = document.getElementById("notifGatewayNewText");
            if (gatewayNew && gatewayNewText) {
                if (data.count > 0) {
                    gatewayNew.classList.add("active");
                    gatewayNewText.textContent = `🆕 ${data.count} ta yangi bildirishnoma bor →`;
                } else {
                    gatewayNew.classList.remove("active");
                    gatewayNewText.textContent = "Yangi bildirishnoma yo'q";
                }
            }
        })
        .catch(err => console.error(err));
}

document.addEventListener("DOMContentLoaded", () => {
    if (document.querySelector(".btn-bell")) {
        refreshUnreadCount();
        setInterval(refreshUnreadCount, 30000);
    }
});


