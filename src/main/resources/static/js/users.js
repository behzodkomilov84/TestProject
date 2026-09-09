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

// Oxirgi yuklangan obunalar ro'yxati (ADMIN-rol + kurs) — "Obuna holati"
// belgisi bosilganda detallarni qayta so'rovsiz ko'rsatish uchun
// (foydalanuvchi so'rovi, 2026-09-09: "obuna holatiga bosganda detalniy
// ma'lumotlar ko'rinsin: qaysi kursga obuna, adminga...").
let lastAdminSubscriptions = [];
let lastCourseSubscriptions = [];

document.addEventListener("DOMContentLoaded", () => {
    loadUsers();
    document.getElementById("editUserForm").addEventListener("submit", submitEditUser);
    document.getElementById("telegramMessageForm").addEventListener("submit", submitTelegramMessage);
});

function loadUsers() {
    // "return" — cancelSubscriptionFromDetails() jadval yangilanishini
    // "await" qilib, keyin o'sha oynani qayta ochishi uchun (foydalanuvchi
    // so'rovi, 2026-09-09).
    return Promise.all([
        fetch("/api/users").then(r => {
            if (!r.ok) throw new Error("403 or not authorized");
            return r.json();
        }),
        fetch("/api/subscriptions").then(r => r.ok ? r.json() : []),
        // "Obuna holati" ustuni — ADMIN-rol obunasi bilan bir qatorda,
        // KURS obunalarini ham hisobga oladi (foydalanuvchi so'rovi,
        // 2026-09-09: "ikkalasi ham" birlashtirilgan holat).
        fetch("/api/course-subscriptions").then(r => r.ok ? r.json() : [])
    ])
        .then(([users, subscriptions, courseSubscriptions]) => {
            renderUsers(users, subscriptions, courseSubscriptions);
        })
        .catch(err => {
            showAlertModal("Ошибка загрузки пользователей");
            console.error(err);
        });
}

// Har bir foydalanuvchi uchun BARCHA faol (CONFIRMED, muddati o'tmagan)
// obunalarni topadi — umumiy so'rov o'lchamdan qat'i nazar bir xil
// shaklda (userId/status/startDate/endDate) kelgani uchun ADMIN-rol
// obunasi VA kurs obunalari BIR XIL funksiya bilan tekshiriladi.
function findActiveSubscriptions(subscriptions, userId) {
    const now = new Date();
    return subscriptions.filter(s => s.userId === userId && s.status === "CONFIRMED" && s.endDate && new Date(s.endDate) > now);
}

// "🟢 Faol — DD.MM.YYYY – DD.MM.YYYY" / "⚪ Faol emas" — ADMIN-rol
// obunasi VA kurs obunalarining BARCHASINI birlashtirib, umumiy "hozir
// biror narsaga to'lab turibdimi" holatini ko'rsatadi (foydalanuvchi
// so'rovi, 2026-09-09: "статус подписки"). Bir nechta faol obuna bo'lsa —
// eng ERTA boshlangan va eng KECH tugaydigan sanalar oralig'i ko'rsatiladi.
function buildSubscriptionStatusHtml(adminSubscriptions, courseSubscriptions, userId) {
    const active = [
        ...findActiveSubscriptions(adminSubscriptions, userId),
        ...findActiveSubscriptions(courseSubscriptions, userId)
    ];

    if (active.length === 0) {
        return `<span class="sub-status-badge sub-status-inactive">⚪ Faol emas</span>`;
    }

    const starts = active.map(s => new Date(s.startDate)).filter(d => !isNaN(d));
    const ends = active.map(s => new Date(s.endDate)).filter(d => !isNaN(d));
    const rangeText = starts.length && ends.length
        ? `${new Date(Math.min(...starts)).toLocaleDateString("uz-UZ")} – ${new Date(Math.max(...ends)).toLocaleDateString("uz-UZ")}`
        : "";
    const countText = active.length > 1 ? ` (${active.length} ta)` : "";

    return `<span class="sub-status-badge sub-status-active">🟢 Faol${countText}${rangeText ? " — " + rangeText : ""}</span>`;
}

// "Obuna holati" belgisi bosilganda — batafsil tarix (foydalanuvchi
// so'rovi, 2026-09-09: "obuna holatiga bosganda detalniy ma'lumotlar
// ko'rinsin: qaysi kursga obuna, adminga... primechaniyega o'xshab").
// FAQAT faol emas, BARCHA (tarixiy — EXPIRED/CANCELLED ham) yozuvlar
// ko'rsatiladi, chunki bu "nima bo'lgan edi" degan audit ko'rinishi.
const SUB_STATUS_LABELS = {
    CONFIRMED: "✅ Faol",
    PENDING: "⏳ Kutilmoqda",
    EXPIRED: "⌛ Muddati tugagan",
    CANCELLED: "❌ Bekor qilingan"
};

const SUB_SOURCE_LABELS = {
    ONLINE: "💳 Onlayn to'lov",
    MANUAL: "✋ Qo'lda berilgan",
    TELEGRAM: "🤖 Telegram bot orqali"
};

// "cancelUrlFn" — berilsa, FAOL (CONFIRMED) yozuvlarga "❌ Bekor qilish"
// tugmasi qo'shiladi (foydalanuvchi so'rovi, 2026-09-09: "қўлда берилган
// админни бекор қилишни қаерга қиламан?" — bu oyna orqali to'g'ridan-
// to'g'ri bekor qilish imkoni).
function renderSubscriptionDetailGroup(title, items, labelFn, cancelUrlFn) {
    if (!items.length) return "";

    const rows = items.map(s => {
        const range = s.startDate && s.endDate
            ? `${new Date(s.startDate).toLocaleDateString("uz-UZ")} – ${new Date(s.endDate).toLocaleDateString("uz-UZ")}`
            : "—";
        const statusClass = s.status === "CONFIRMED" ? "sub-status-active" : "sub-status-inactive";
        const cancelBtn = (s.status === "CONFIRMED" && cancelUrlFn)
            ? `<button class="sub-detail-cancel-btn" onclick="cancelSubscriptionFromDetails('${cancelUrlFn(s)}', ${s.userId})">❌ Bekor qilish</button>`
            : "";

        return `
            <div class="sub-detail-row">
                <div class="sub-detail-row-top">
                    <span class="sub-detail-label">${escapeHtml(labelFn(s))}</span>
                    <span class="sub-status-badge ${statusClass}">${SUB_STATUS_LABELS[s.status] || s.status}</span>
                </div>
                <div class="sub-detail-row-meta">
                    ${Number(s.amount).toLocaleString("uz-UZ")} so'm · ${range}
                    ${s.note ? " · " + escapeHtml(s.note) : ""}
                </div>
                ${cancelBtn}
            </div>
        `;
    }).join("");

    return `<h3 class="sub-detail-group-title">${title}</h3>${rows}`;
}

function showSubscriptionDetails(userId) {
    const user = usersById[userId];
    if (!user) return;

    const byRecent = (a, b) => new Date(b.createdAt) - new Date(a.createdAt);
    const adminSubs = lastAdminSubscriptions.filter(s => s.userId === userId).sort(byRecent);
    const courseSubs = lastCourseSubscriptions.filter(s => s.userId === userId).sort(byRecent);

    document.getElementById("subscriptionDetailsTitle").textContent = `📋 ${user.username} — obuna tarixi`;

    const html =
        renderSubscriptionDetailGroup("🎓 ADMIN-rol obunalari", adminSubs, s => SUB_SOURCE_LABELS[s.source] || s.source,
            s => `/api/subscriptions/${s.id}/cancel`) +
        renderSubscriptionDetailGroup("📚 Kurs obunalari", courseSubs, s => s.courseTitle,
            s => `/api/course-subscriptions/${s.id}/cancel`);

    document.getElementById("subscriptionDetailsBody").innerHTML =
        html || `<p class="sub-detail-empty">Obunalar tarixi yo'q.</p>`;

    document.getElementById("subscriptionDetailsOverlay").hidden = false;
}

// FAOL obunani (ADMIN-rol yoki kurs) shu oynaning ichidan bekor qilish
// (foydalanuvchi so'rovi, 2026-09-09). ADMIN-rol obunasi bo'lsa VA bu
// foydalanuvchining yagona faol obunasi bo'lsa — ROLE_ADMIN DARHOL
// olib tashlanadi (SubscriptionService.cancel — server tarafida).
async function cancelSubscriptionFromDetails(endpoint, userId) {
    if (!await showConfirmModal(
        "Ushbu obunani bekor qilmoqchimisiz? Agar bu ADMIN huquqini bergan yagona faol obuna bo'lsa, huquq DARHOL olib tashlanadi."))
        return;

    try {
        const res = await fetch(endpoint, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }
        await loadUsers();
        showSubscriptionDetails(userId);
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

function closeSubscriptionDetailsModal() {
    document.getElementById("subscriptionDetailsOverlay").hidden = true;
}

// "5 daqiqa oldin" / "2 soat oldin" / "3 kun oldin" — notifications.js'dagi
// bilan bir xil hisoblash, mustaqil nusxa sifatida (skript yuklanish
// tartibiga bog'liq bo'lmasin).
function usersPageTimeAgo(dateStr) {
    const diffMs = Date.now() - new Date(dateStr).getTime();
    const mins = Math.floor(diffMs / 60000);
    if (mins < 1) return "hozir";
    if (mins < 60) return mins + " daqiqa oldin";
    const hours = Math.floor(mins / 60);
    if (hours < 24) return hours + " soat oldin";
    const days = Math.floor(hours / 24);
    return days + " kun oldin";
}

function escapeHtml(text) {
    if (text === null || text === undefined) return "";
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
}

function renderUsers(users, subscriptions, courseSubscriptions) {
    const tbody = document.getElementById("usersTableBody");
    tbody.innerHTML = "";

    usersById = Object.fromEntries(users.map(u => [u.id, u]));
    lastAdminSubscriptions = subscriptions;
    lastCourseSubscriptions = courseSubscriptions;

    // "👥 Jami ro'yxatdan o'tgan" — onlaynlikdan mustaqil, alohida
    // ko'rsatkich (foydalanuvchi so'rovi, 2026-09-09).
    document.getElementById("totalUsersStat").textContent =
        `👥 Jami ro'yxatdan o'tgan: ${users.length}`;

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

        // "Obuna holati" — ADMIN-rol obunasi VA kurs obunalarining
        // BARCHASINI birlashtiradi (foydalanuvchi so'rovi, 2026-09-09).
        // Istisno: ROLE_ADMIN'ga ega, lekin HECH QANDAY faol obunasi
        // bo'lmagan hisob — bu checkbox orqali qo'lda, obunasiz berilgan
        // "doimiy" ADMIN degani, "Faol emas" deb ko'rsatish chalg'ituvchi
        // bo'lardi (eski "ADMIN muddati" ustunidagi "♾️ doimiy" bilan
        // bir xil ma'no saqlab qolinadi).
        let subscriptionStatusHtml = buildSubscriptionStatusHtml(subscriptions, courseSubscriptions, user.id);
        if (subscriptionStatusHtml.includes("sub-status-inactive") && user.roles.includes("ROLE_ADMIN")) {
            subscriptionStatusHtml = `<span class="sub-status-badge sub-status-permanent">♾️ ADMIN (doimiy)</span>`;
        }

        const unlockButtonHtml = user.locked
            ? `<button class="action-btn" onclick="unlockUser(${user.id})" title="Blokdan chiqarish">🔓</button>`
            : "";

        // Barcha ustunlar (foydalanuvchi so'rovi, 2026-09-06: "users
        // jadvalidagi barcha ustunlarni Foydalanuvchilar sahifasiga
        // chiqar" — Telegram/telefon dublikat muammosini debug qilish
        // uchun). Bo'sh qiymatlar "—" bilan ko'rsatiladi.
        const fullName = [user.firstName, user.lastName].filter(Boolean).join(" ") || "—";

        // Avatar — bo'lsa rasm, bo'lmasa ismning bosh harfi bilan doira
        // (foydalanuvchi so'rovi, 2026-09-07: "фойдаланувчиларнинг
        // аватарини ҳам қўш").
        const avatarInitial = (user.firstName || user.username || "?").charAt(0).toUpperCase();
        const avatarHtml = user.avatarUrl
            ? `<img class="user-avatar" src="${escapeHtml(user.avatarUrl)}" alt="" onerror="this.replaceWith(Object.assign(document.createElement('div'),{className:'user-avatar user-avatar-placeholder',textContent:'${avatarInitial}'}))">`
            : `<div class="user-avatar user-avatar-placeholder">${avatarInitial}</div>`;

        // "✉️" — Telegram orqali shaxsiy xabar yuborish (foydalanuvchi
        // so'rovi, 2026-09-09). Faqat foydalanuvchi Telegramga ulangan
        // (telegramId bor) bo'lsa ko'rinadi — aks holda yuborish mumkin
        // emas (NotificationService.sendOwnerMessage shu shartni serverda
        // ham tekshiradi).
        const tgMessageBtnHtml = user.telegramId
            ? `<button class="tg-message-btn" onclick="openTelegramMessageModal(${user.id})" title="Telegram orqali shaxsiy xabar yuborish">✉️</button>`
            : "";

        // "Ro'yxatdan o'tgan sana" (foydalanuvchi so'rovi, 2026-09-09).
        // Migratsiyadan OLDIN yaratilgan eski hisoblarda noma'lum (null).
        const createdAtText = user.createdAt
            ? new Date(user.createdAt).toLocaleDateString("uz-UZ")
            : "—";

        // "Oxirgi tashrif vaqti" (foydalanuvchi so'rovi, 2026-09-09) —
        // OnlineUserTracker orqali (throttled) yangilanadi, hech qachon
        // kuzatilmagan bo'lsa (yoki xususiyat qo'shilishidan OLDIN oxirgi
        // marta kirgan bo'lsa) noma'lum.
        const lastSeenAtText = user.lastSeenAt ? usersPageTimeAgo(user.lastSeenAt) : "—";

        tr.innerHTML = `
            <td class="sticky-col-1">${user.id}</td>
            <td class="sticky-col-2">
                <div class="avatar-wrap">
                    ${avatarHtml}
                    <span class="online-dot" id="online-dot-${user.id}" title="Hozir onlayn" hidden></span>
                </div>
            </td>
            <td class="sticky-col-3" title="${escapeHtml(user.username)}">
                <div class="username-cell-row">
                    <span class="username-cell-text">${user.username} ${user.locked ? '<span title="Bloklangan">🔒</span>' : ""}</span>
                    ${tgMessageBtnHtml}
                </div>
            </td>
            <td>${escapeHtml(fullName)}</td>
            <td>${escapeHtml(user.email) || "—"}</td>
            <td>${escapeHtml(user.phoneNumber) || "—"}</td>
            <td>${user.telegramId ?? "—"}</td>
            <td>${user.telegramUsername ? "@" + escapeHtml(user.telegramUsername) : "—"}</td>
            <td>${escapeHtml(user.googleId) || "—"}</td>
            <td>${escapeHtml(user.facebookId) || "—"}</td>
            <td>${escapeHtml(user.workplace) || "—"}</td>
            <td>${escapeHtml(user.jobTitle) || "—"}</td>
            <td><div class="roles-cell">${checkboxesHtml}</div></td>
            <td class="sub-status-cell" onclick="showSubscriptionDetails(${user.id})" title="Batafsil ma'lumot uchun bosing">${subscriptionStatusHtml}</td>
            <td>${createdAtText}</td>
            <td>${lastSeenAtText}</td>
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

    syncTopScrollWidth();
    refreshOnlineStatus();
}

// ===== "🟢 Hozir onlayn" (foydalanuvchi so'rovi, 2026-09-09) =====
// Butun jadvalni qayta yuklamasdan (loadUsers() checkbox/forma holatini
// buzardi), faqat yengil /api/users/online-status so'ralib, har bir
// qatordagi nuqta va yuqoridagi statistika yangilanadi.

function refreshOnlineStatus() {
    fetch("/api/users/online-status")
        .then(r => r.ok ? r.json() : null)
        .then(status => {
            if (!status) return;

            document.querySelectorAll(".online-dot").forEach(dot => dot.hidden = true);
            status.onlineUserIds.forEach(id => {
                const dot = document.getElementById(`online-dot-${id}`);
                if (dot) dot.hidden = false;
            });

            // Jami ro'yxatdan o'tganlar soni endi ALOHIDA ko'rsatkich
            // (renderUsers#totalUsersStat) — bu yerda faqat onlaynlik.
            document.getElementById("onlineStatsBar").textContent =
                `🟢 Hozir onlayn: ${status.onlineCount}`;
        })
        .catch(err => console.error(err));
}

// Sahifa ochiq turganda statistika "jonli" (real-time'ga yaqin) bo'lib
// tursin — har 20 soniyada yangilanadi (notif badge bilan bir xil andoza).
setInterval(refreshOnlineStatus, 20000);

// Jadval tepasidagi ko'zgu (mirror) gorizontal scroll — pastki
// .table-scroll bilan bir xil kengroq ichki elementga ega bo'lib,
// UCHALASI (tepa/pastki/ekranga qotirilgan) bir-biriga scrollLeft
// orqali sinxronlanadi (foydalanuvchi so'rovi, 2026-09-08: "gorizontal
// scroll'ni jadval tepasiga ham qo'yish kerak"; 2026-09-09: "fixed
// qilish kerak" — sahifa pastga aylantirilganda ham ko'rinib tursin).
let topScrollSyncInitialized = false;

function syncTopScrollWidth() {
    const table = document.querySelector(".users-table");
    const topInner = document.getElementById("usersTableScrollTopInner");
    const fixedInner = document.getElementById("usersTableScrollFixedInner");
    if (!table || !topInner || !fixedInner) return;

    topInner.style.width = table.scrollWidth + "px";
    fixedInner.style.width = table.scrollWidth + "px";
    positionFixedScrollBar();
    buildFixedHeader();
    positionFixedHeader();

    if (topScrollSyncInitialized) return;
    topScrollSyncInitialized = true;

    const topScroll = document.getElementById("usersTableScrollTop");
    const bottomScroll = document.getElementById("usersTableScroll");
    const fixedScroll = document.getElementById("usersTableScrollFixed");
    const bars = [topScroll, bottomScroll, fixedScroll];
    let syncing = false;

    bars.forEach(bar => {
        bar.addEventListener("scroll", () => {
            if (syncing) return;
            syncing = true;
            bars.forEach(other => {
                if (other !== bar) other.scrollLeft = bar.scrollLeft;
            });
            // Qotirilgan sarlavhaning aylanadigan qismini (frozen
            // bo'lmagan ustunlar) ham xuddi shu gorizontal siljishga
            // sinxronlaydi — position:sticky EMAS, oddiy transform,
            // shu sabab hech qanday table-cell nuqsoniga ega emas.
            const headerInner = document.getElementById("usersTableHeaderFixedInner");
            if (headerInner) headerInner.style.transform = `translateX(-${bottomScroll.scrollLeft}px)`;
            syncing = false;
        });
    });

    window.addEventListener("resize", syncTopScrollWidth);
    window.addEventListener("scroll", () => {
        updateFixedScrollBarVisibility();
        updateFixedHeaderVisibility();
    }, { passive: true });
}

// Ekranga qotirilgan sarlavha ("zakrepit verx", foydalanuvchi so'rovi,
// 2026-09-09) — position:sticky jadval katakchalarida chuqur LAYOUT/
// PAINT uzilishiga ega ekanligi (getBoundingClientRect va
// elementFromPoint bir-biriga zid natija berishi) uch marta jonli
// tekshiruvda tasdiqlangani sabab, ASL <thead> oddiy (static) qoldirilib,
// UNING nusxasi shu yerda position:fixed <div>lar bilan (jadval
// katakchalari EMAS) alohida quriladi — table-scroll-fixed (pastki
// scrollbar) bilan bir xil, allaqachon ishonchli ishlagan andoza.
function buildFixedHeader() {
    const ths = [...document.querySelectorAll(".users-table thead th")];
    const frozenContainer = document.getElementById("usersTableHeaderFixedFrozen");
    const scrollContainer = document.getElementById("usersTableHeaderFixedInner");
    if (!ths.length || !frozenContainer || !scrollContainer) return;

    frozenContainer.innerHTML = "";
    scrollContainer.innerHTML = "";

    ths.forEach((th, i) => {
        const div = document.createElement("div");
        div.className = "fx-cell";
        div.textContent = th.textContent.trim();
        // Haqiqiy chizilgan (auto-hisoblangan) kenglikni o'qib, aynan
        // shu qiymatni qattiq belgilaymiz — shu bilan nusxa asl
        // ustunlar bilan pixel-aniqlikda tekislanadi.
        const width = th.getBoundingClientRect().width;
        div.style.width = width + "px";
        (i < 3 ? frozenContainer : scrollContainer).appendChild(div);
    });
}

function positionFixedHeader() {
    const bottomScroll = document.getElementById("usersTableScroll");
    const fixedHeader = document.getElementById("usersTableHeaderFixed");
    if (!bottomScroll || !fixedHeader) return;

    const rect = bottomScroll.getBoundingClientRect();
    fixedHeader.style.left = rect.left + "px";
    fixedHeader.style.width = rect.width + "px";

    const headerInner = document.getElementById("usersTableHeaderFixedInner");
    if (headerInner) headerInner.style.transform = `translateX(-${bottomScroll.scrollLeft}px)`;

    updateFixedHeaderVisibility();
}

// Nusxa FAQAT asl <thead> ekranning (navbar ostidagi, 72px) tepasidan
// chiqib ketganda ko'rinadi — aks holda ikkita sarlavha bir vaqtda
// ko'rinib, ortiqcha g'ijimlanish hosil qilardi.
function updateFixedHeaderVisibility() {
    const realThead = document.querySelector(".users-table thead");
    const fixedHeader = document.getElementById("usersTableHeaderFixed");
    if (!realThead || !fixedHeader) return;

    const rect = realThead.getBoundingClientRect();
    fixedHeader.hidden = !(rect.top < 72);
}

// Ekranga qotirilgan pastki scrollbar — FAQAT jadval haqiqatan
// gorizontal aylantirilishi kerak bo'lganda VA jadvalning o'z (native)
// pastki scrollbar'i hozir ekrandan tashqarida (ko'rinmayotgan) bo'lsa
// ko'rsatiladi — aks holda ikkita scrollbar bir vaqtda ko'rinib,
// ortiqcha g'ijimlanish hosil qilardi.
function positionFixedScrollBar() {
    const bottomScroll = document.getElementById("usersTableScroll");
    const fixedScroll = document.getElementById("usersTableScrollFixed");
    if (!bottomScroll || !fixedScroll) return;

    const rect = bottomScroll.getBoundingClientRect();
    fixedScroll.style.left = rect.left + "px";
    fixedScroll.style.width = rect.width + "px";

    updateFixedScrollBarVisibility();
}

function updateFixedScrollBarVisibility() {
    const bottomScroll = document.getElementById("usersTableScroll");
    const fixedScroll = document.getElementById("usersTableScrollFixed");
    if (!bottomScroll || !fixedScroll) return;

    const rect = bottomScroll.getBoundingClientRect();
    const isScrollable = bottomScroll.scrollWidth > bottomScroll.clientWidth + 1;
    // Jadvalning O'ZINING pastki cheti (shu yerda native scrollbar
    // turadi) ekrandan pastda qolgan VA jadval hozir ko'rinishda
    // (yuqori cheti hali ekran ostiga tushib ketmagan) — shu holatda
    // qotirilgan scrollbar kerak.
    const nativeScrollbarOffscreen = rect.bottom > window.innerHeight;
    const tableStillVisible = rect.top < window.innerHeight;

    fixedScroll.hidden = !(isScrollable && nativeScrollbarOffscreen && tableStillVisible);
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
    document.getElementById("editFacebookId").value = user.facebookId || "";

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
        googleId: document.getElementById("editGoogleId").value.trim(),
        facebookId: document.getElementById("editFacebookId").value.trim()
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

// ===== Telegram orqali shaxsiy xabar (foydalanuvchi so'rovi, 2026-09-09) =====

function openTelegramMessageModal(id) {
    const user = usersById[id];
    if (!user) return;

    document.getElementById("tgMessageUserId").value = user.id;
    document.getElementById("tgMessageUserLabel").textContent =
        `Kimga: ${user.username}` + (user.telegramUsername ? ` (@${user.telegramUsername})` : "");
    document.getElementById("tgMessageText").value = "";

    const errorEl = document.getElementById("tgMessageError");
    errorEl.hidden = true;
    errorEl.textContent = "";

    document.getElementById("telegramMessageOverlay").hidden = false;
    document.getElementById("tgMessageText").focus();
}

function closeTelegramMessageModal() {
    document.getElementById("telegramMessageOverlay").hidden = true;
}

async function submitTelegramMessage(event) {
    event.preventDefault();

    const id = document.getElementById("tgMessageUserId").value;
    const text = document.getElementById("tgMessageText").value.trim();
    const errorEl = document.getElementById("tgMessageError");
    errorEl.hidden = true;

    if (!text) {
        errorEl.textContent = "Xabar matni bo'sh bo'lishi mumkin emas";
        errorEl.hidden = false;
        return;
    }

    try {
        const response = await fetch(`/api/users/${id}/telegram-message`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({text})
        });

        if (!response.ok) {
            const data = await response.json().catch(() => ({}));
            errorEl.textContent = data.error || "Xatolik yuz berdi";
            errorEl.hidden = false;
            return;
        }

        closeTelegramMessageModal();
        showAlertModal("✅ Xabar yuborildi.");
    } catch (err) {
        console.error(err);
        errorEl.textContent = "Tarmoq xatoligi";
        errorEl.hidden = false;
    }
}
