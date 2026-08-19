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
   Endi alohida ochiladigan panel yo'q — "🔔 Bildirishnomalar" Profil
   menyusi ichidagi oddiy matnli havola (/notifications), yonida faqat
   o'qilmagan sonini ko'rsatuvchi belgi (badge) yangilanib turadi.
   To'liq ro'yxat, tab'lar (Yangi/O'qilgan) va statistika — notifications.js. */

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
        })
        .catch(err => console.error(err));
}

document.addEventListener("DOMContentLoaded", () => {
    if (document.getElementById("notif-badge")) {
        refreshUnreadCount();
        setInterval(refreshUnreadCount, 30000);
    }
});


