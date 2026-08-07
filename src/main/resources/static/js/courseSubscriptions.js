/* Kursga obuna berish — barcha kurslar bo'yicha obunalarni bir joyda
   boshqarish (avval har bir kurs sahifasida alohida-alohida edi). */

let allCourses = [];
let allUsers = [];
let allSubs = [];

document.addEventListener("DOMContentLoaded", () => {
    loadCourses();
    loadUsers();
    loadSubscribers();

    document.addEventListener("click", (e) => {
        const wrap = document.querySelector(".user-search-wrap");
        if (wrap && !wrap.contains(e.target)) {
            document.getElementById("grantUserSuggestions").style.display = "none";
        }
    });
});

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
}

/* ===== Kurslar — bir nechtasini tanlash (checkbox ro'yxati) ===== */

function loadCourses() {
    fetch("/api/courses")
        .then(r => r.ok ? r.json() : [])
        .then(courses => {
            allCourses = courses;
            const list = document.getElementById("grantCourseList");

            if (!courses.length) {
                list.innerHTML = `<div class="courses-empty">Hali kurslar yo'q</div>`;
                return;
            }

            list.innerHTML = courses.map(c => `
                <label class="course-checkbox-item">
                    <input type="checkbox" class="grant-course-checkbox" value="${c.id}">
                    ${escapeHtml(c.title)}
                </label>
            `).join("");

            // Kurs sahifasidan "Bu kurs uchun obunalarni boshqarish" tugmasi
            // orqali kelingan bo'lsa (?courseId=), o'sha kurs oldindan belgilansin.
            const preselect = new URLSearchParams(location.search).get("courseId");
            if (preselect) {
                const cb = list.querySelector(`.grant-course-checkbox[value="${preselect}"]`);
                if (cb) cb.checked = true;
            }
        })
        .catch(err => console.error(err));
}

/* ===== Foydalanuvchini qidirib tanlash ===== */

function loadUsers() {
    fetch("/api/users")
        .then(r => r.ok ? r.json() : [])
        .then(users => {
            allUsers = users;

            // Bildirishnomadan (masalan "student2 obuna so'radi") kelingan
            // bo'lsa (?userId=), o'sha foydalanuvchi qidirish maydonida
            // oldindan tanlangan holda ko'rsatiladi.
            const preselect = new URLSearchParams(location.search).get("userId");
            if (preselect) {
                const user = users.find(u => String(u.id) === preselect);
                if (user) selectGrantUser(user.id, user.username);
            }
        })
        .catch(err => console.error(err));
}

function onUserSearchInput() {
    // Matn qo'lda o'zgartirilsa — oldingi tanlov bekor bo'ladi, foydalanuvchi
    // ro'yxatdan qayta tanlashi kerak (noto'g'ri odamga obuna berilmasligi uchun).
    document.getElementById("grantUserId").value = "";

    const query = document.getElementById("grantUserSearch").value.trim().toLowerCase();
    const box = document.getElementById("grantUserSuggestions");

    if (!query) {
        box.style.display = "none";
        box.innerHTML = "";
        return;
    }

    const matches = allUsers.filter(u => u.username.toLowerCase().includes(query)).slice(0, 8);

    if (!matches.length) {
        box.innerHTML = `<div class="user-suggestion-empty">Topilmadi</div>`;
        box.style.display = "block";
        return;
    }

    box.innerHTML = matches.map(u => `
        <div class="user-suggestion-item" onclick="selectGrantUser(${u.id}, '${escapeHtml(u.username)}')">
            ${escapeHtml(u.username)}
            <span class="user-suggestion-roles">${(u.roles || []).map(r => r.replace("ROLE_", "")).join(", ")}</span>
        </div>
    `).join("");
    box.style.display = "block";
}

function selectGrantUser(id, username) {
    document.getElementById("grantUserId").value = id;
    document.getElementById("grantUserSearch").value = username;
    document.getElementById("grantUserSuggestions").style.display = "none";
}

/* ===== Obuna berish ===== */

// Bir nechta kurs tanlangan bo'lsa — har biriga ketma-ket (parallel emas,
// xatolikni har bir kurs uchun alohida aniq ko'rsatish uchun) alohida
// obuna so'rovi yuboriladi, xuddi bitta kursga berilayotgandek.
async function submitGrantSubscription() {
    const courseIds = Array.from(document.querySelectorAll(".grant-course-checkbox:checked"))
        .map(cb => Number(cb.value));
    const userId = Number(document.getElementById("grantUserId").value);
    const amount = Number(document.getElementById("grantAmount").value) || 0;
    const durationMonths = Number(document.getElementById("grantDuration").value) || 1;
    const note = document.getElementById("grantNote").value.trim();

    if (!courseIds.length) {
        alert("❌ Kamida bitta kursni tanlang");
        return;
    }

    if (!userId) {
        alert("❌ Foydalanuvchini qidirib, ro'yxatdan tanlang");
        return;
    }

    const okTitles = [];
    const errors = [];

    for (const courseId of courseIds) {
        const course = allCourses.find(c => c.id === courseId);
        const courseTitle = course ? course.title : ("#" + courseId);

        try {
            const res = await fetch(`/api/courses/${courseId}/subscriptions`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ userId, amount, durationMonths, note })
            });

            const data = await res.json().catch(() => ({}));

            if (!res.ok) {
                errors.push(`${courseTitle}: ${data.error || "xatolik"}`);
            } else {
                okTitles.push(courseTitle);
            }
        } catch (err) {
            console.error(err);
            errors.push(`${courseTitle}: tarmoq xatoligi`);
        }
    }

    if (okTitles.length) {
        let msg = `✅ ${okTitles.length} ta kursga obuna berildi (${durationMonths} oy):\n` + okTitles.join(", ");
        if (errors.length) msg += `\n\n⚠️ Xatoliklar:\n` + errors.join("\n");
        alert(msg);
    } else {
        alert("❌ Hech qaysi kursga obuna berilmadi:\n" + errors.join("\n"));
    }

    document.getElementById("grantUserSearch").value = "";
    document.getElementById("grantUserId").value = "";
    document.getElementById("grantAmount").value = "";
    document.getElementById("grantDuration").value = "";
    document.getElementById("grantNote").value = "";
    document.querySelectorAll(".grant-course-checkbox:checked").forEach(cb => cb.checked = false);
    loadSubscribers();
}

/* ===== Obunalar jadvali (barcha kurslar) ===== */

function loadSubscribers() {
    fetch("/api/course-subscriptions")
        .then(r => r.ok ? r.json() : [])
        .then(subs => {
            allSubs = subs;
            renderSubscribers();
        })
        .catch(err => console.error(err));
}

function renderSubscribers() {
    const tbody = document.getElementById("subscribersTableBody");
    if (!tbody) return;

    const filter = (document.getElementById("subsFilter").value || "").trim().toLowerCase();
    const subs = filter
        ? allSubs.filter(s =>
            s.username.toLowerCase().includes(filter) ||
            s.courseTitle.toLowerCase().includes(filter))
        : allSubs;

    if (!subs.length) {
        tbody.innerHTML = `<tr><td colspan="7" class="empty-row">Hali obuna yo'q</td></tr>`;
        return;
    }

    const statusLabels = {
        CONFIRMED: "✅ Faol",
        PENDING: "⏳ So'rov kutmoqda",
        EXPIRED: "⌛ Muddati tugagan",
        CANCELLED: "❌ Bekor qilingan"
    };

    tbody.innerHTML = subs.map(s => {
        let actions = "—";
        if (s.status === "PENDING") {
            actions = `<button onclick="confirmRequest(${s.id})">✅ Tasdiqlash</button>
                       <button onclick="cancelSubscription(${s.id})">❌ Rad etish</button>`;
        } else if (s.status === "CONFIRMED") {
            actions = `<button onclick="cancelSubscription(${s.id})">Bekor qilish</button>`;
        }

        const muddat = s.endDate ? new Date(s.endDate).toLocaleDateString("uz-UZ") : "—";

        return `
            <tr>
                <td>${escapeHtml(s.courseTitle)}</td>
                <td>${escapeHtml(s.username)}</td>
                <td>${Number(s.amount).toLocaleString("uz-UZ")} so'm</td>
                <td>${statusLabels[s.status] || s.status}</td>
                <td>${muddat}</td>
                <td>${new Date(s.createdAt).toLocaleDateString("uz-UZ")}</td>
                <td>${actions}</td>
            </tr>
        `;
    }).join("");
}

// PENDING so'rovni tasdiqlash — summa va muddatni so'raymiz, keyin mavjud
// "obuna berish" endpoint'i orqali (u avtomatik PENDING'ni CONFIRMED'ga o'tkazadi).
async function confirmRequest(subscriptionId) {
    const sub = allSubs.find(s => s.id === subscriptionId);
    if (!sub) return;

    const amountStr = prompt(`"${sub.username}" uchun to'lov summasini kiriting (so'm):`, "0");
    if (amountStr === null) return;

    const amount = Number(amountStr);
    if (isNaN(amount) || amount < 0) {
        alert("❌ Noto'g'ri summa");
        return;
    }

    const durationStr = prompt("Obuna necha oyga beriladi?", "1");
    if (durationStr === null) return;

    const durationMonths = Number(durationStr) || 1;

    try {
        const res = await fetch(`/api/courses/${sub.courseId}/subscriptions`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ userId: sub.userId, amount, durationMonths, note: "So'rov orqali tasdiqlandi" })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            alert(data.error || "Xatolik yuz berdi");
            return;
        }

        alert("✅ Obuna tasdiqlandi");
        loadSubscribers();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

async function cancelSubscription(id) {
    if (!confirm("Obunani bekor qilmoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/course-subscriptions/${id}/cancel`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "Xatolik yuz berdi");
            return;
        }
        loadSubscribers();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}
