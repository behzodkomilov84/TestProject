// "O'chirilganlar savati" — soft-delete qilingan kurslar ro'yxati,
// "♻️ Tiklash" (bo'lim/mavzu/obuna/progress — hech qachon o'chirilmagan,
// darhol qaytadi) va "🗑️ Butunlay o'chirish" (qaytarib bo'lmaydi) amallari.
//
// "📦 Backup orqali tiklash" (pastda) — FAQAT ROLE_OWNER (backendda ham
// shunday cheklangan, BackupController) — savatdan ham topilmaydigan
// (butunlay o'chirilgan) kurslar uchun.
const ROLE = document.body.dataset.role;

document.addEventListener("DOMContentLoaded", () => {
    loadTrash();
    if (ROLE === "ROLE_OWNER") {
        document.querySelectorAll(".owner-only-el").forEach(el => el.style.display = "");
        loadBackupFiles();
        loadAdminArchivedCourses();
    }
});

function loadTrash() {
    fetch("/api/courses/deleted")
        .then(r => r.ok ? r.json() : [])
        .then(renderTrash)
        .catch(err => {
            console.error(err);
            document.getElementById("trashGrid").innerHTML =
                `<div class="courses-empty">O'chirilgan kurslarni yuklashda xatolik</div>`;
        });
}

function renderTrash(courses) {
    const grid = document.getElementById("trashGrid");

    if (!courses.length) {
        grid.innerHTML = `<div class="courses-empty">O'chirilgan kurs yo'q</div>`;
        return;
    }

    grid.innerHTML = courses.map(c => {
        const cover = c.coverImageUrl
            ? `<img class="course-card-cover" src="${c.coverImageUrl}" alt="">`
            : `<div class="course-card-cover"></div>`;

        return `
            <div class="course-card">
                ${cover}
                <div class="course-card-body">
                    <h3 class="course-card-title">${escapeHtml(c.title)}</h3>
                    <p class="course-card-desc">${escapeHtml(c.description || "")}</p>
                    <div class="course-card-footer">
                        <span>${c.sectionCount} bo'lim • ${formatDate(c.deletedAt)}da o'chirilgan</span>
                    </div>
                    <div class="course-form-actions">
                        <button onclick="restoreCourse(${c.id})">♻️ Tiklash</button>
                        <button class="danger-btn" onclick="permanentlyDeleteCourse(${c.id}, ${JSON.stringify(c.title).replace(/"/g, "&quot;")})">🗑️ Butunlay o'chirish</button>
                    </div>
                </div>
            </div>
        `;
    }).join("");
}

async function restoreCourse(courseId) {
    if (!confirm("Bu kursni tiklamoqchimisiz? Bo'limlari, mavzulari, obunalari va o'quvchilar progressi ham birga qaytadi.")) {
        return;
    }

    try {
        const res = await fetch(`/api/courses/${courseId}/restore`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "Tiklashda xatolik");
            return;
        }
        alert("✅ Kurs tiklandi.");
        loadTrash();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

async function permanentlyDeleteCourse(courseId, title) {
    // ROLE_ADMIN uchun bu amal endi ENDI QAYTARIB BO'LMAYDIGAN emas —
    // kurs shu ADMIN'dan/katalogdan yo'qoladi, lekin bazada saqlanadi,
    // ROLE_OWNER xohlasa qaytadan tiklashi mumkin (CourseService#
    // permanentlyDeleteCourse). Shu sabab ogohlantirish matni ROLE'ga
    // qarab farq qiladi — ADMIN'ni behuda cho'chitmaslik uchun.
    const isOwner = ROLE === "ROLE_OWNER";
    const warning = isOwner
        ? `⚠️ "${title}" kursini BUTUNLAY o'chirmoqchimisiz?\n\nBu amalni HECH QANDAY tarzda bekor qilib bo'lmaydi (backup orqali qo'lda tiklashdan boshqa).`
        : `⚠️ "${title}" kursini butunlay o'chirmoqchimisiz?\n\nKurs sizda va katalogda ENDI ko'rinmaydi. Ma'lumotlari bazada saqlanadi — faqat OWNER xohlasa, qaytadan tiklashi mumkin.`;
    if (!confirm(warning)) {
        return;
    }
    if (isOwner && !confirm("Haqiqatan ham ishonchingiz komilmi? Barcha bo'lim/mavzu/obuna/progress ma'lumotlari abadiy yo'qoladi.")) {
        return;
    }

    try {
        const res = await fetch(`/api/courses/${courseId}/permanent`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "O'chirishda xatolik");
            return;
        }
        alert("✅ Kurs butunlay o'chirildi.");
        loadTrash();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text ?? "";
    return div.innerHTML;
}

function formatDate(isoString) {
    if (!isoString) return "";
    const d = new Date(isoString);
    return d.toLocaleDateString("uz-UZ") + " " + d.toLocaleTimeString("uz-UZ", { hour: "2-digit", minute: "2-digit" });
}

// ========================================================================
//         "🗄️ Adminlar butunlay o'chirgan kurslar" (FAQAT ROLE_OWNER)
// ========================================================================
// ROLE_ADMIN "🗑️ Butunlay o'chirish"ni bosganda kurs HAQIQIY o'chmaydi —
// shu ADMIN'dan va katalogdan yo'qoladi, lekin bazada to'liq saqlanadi
// (CourseService.permanentlyDeleteCourse). Faqat ROLE_OWNER shu ro'yxatda
// ko'radi, xohlasa "📤 O'zim nomimdan qayta nashr qilish" bilan tiklaydi.

function loadAdminArchivedCourses() {
    fetch("/api/courses/archived-by-admin")
        .then(r => r.ok ? r.json() : [])
        .then(renderAdminArchivedCourses)
        .catch(err => {
            console.error(err);
            document.getElementById("adminArchivedList").innerHTML =
                `<p class="error">Yuklashda xatolik</p>`;
        });
}

function renderAdminArchivedCourses(courses) {
    const list = document.getElementById("adminArchivedList");

    if (!courses.length) {
        list.innerHTML = `<p>Hech qanday admin arxivlagan kurs yo'q</p>`;
        return;
    }

    list.innerHTML = `
        <ul>
            ${courses.map(c => `
                <li>
                    <b>${escapeHtml(c.title)}</b>
                    — O'chirgan: ${escapeHtml(c.archivedByAdminName)}, ${formatDate(c.archivedAt)}
                    <button onclick="reclaimArchivedCourse(${c.id}, ${JSON.stringify(c.title).replace(/"/g, "&quot;")})">📤 O'zim nomimdan qayta nashr qilish</button>
                </li>
            `).join("")}
        </ul>
    `;
}

async function reclaimArchivedCourse(courseId, title) {
    if (!confirm(`"${title}" kursini o'z nomingizga o'tkazib, qaytadan tiklamoqchimisiz?\n\n(Chop etish keyin alohida yoqiladi — hozircha qoralama sifatida qoladi.)`)) {
        return;
    }

    try {
        const res = await fetch(`/api/courses/${courseId}/reclaim`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "Tiklashda xatolik");
            return;
        }
        alert("✅ Kurs sizning nomingizga o'tkazildi va tiklandi (qoralama sifatida).");
        loadAdminArchivedCourses();
        loadTrash();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

// ========================================================================
//                  "📦 Backup orqali tiklash" (FAQAT ROLE_OWNER)
// ========================================================================

function loadBackupFiles() {
    fetch("/api/backups")
        .then(r => r.ok ? r.json() : [])
        .then(files => {
            const select = document.getElementById("backupFileSelect");
            if (!files.length) {
                select.innerHTML = `<option value="">Backup fayllar topilmadi</option>`;
                return;
            }
            select.innerHTML = files.map(f =>
                `<option value="${f.fileName}" data-captured-at="${f.capturedAt}">${formatDate(f.capturedAt)} — ${formatBytes(f.sizeBytes)}</option>`
            ).join("");
            // Vaqt oralig'ini QAYERDAN boshlash kerakligini foydalanuvchi
            // bilmasligi mumkin (kurs qachon YARATILGANini aniq eslay
            // olmaydi) — shu sabab standart holatda ENG KENG oraliq
            // (juda uzoq o'tmishdan, backup olingan payt/hozirgacha)
            // avtomatik to'ldiriladi, shunda "Ko'rish" birinchi urinishda
            // ham natija berishi kerak; kerak bo'lsa keyin torайтirish mumkin.
            fillDefaultBackupRange();
        })
        .catch(err => console.error(err));
}

function fillDefaultBackupRange() {
    const select = document.getElementById("backupFileSelect");
    const option = select.options[select.selectedIndex];
    const capturedAt = option ? option.dataset.capturedAt : null;

    document.getElementById("backupFromInput").value = "2000-01-01T00:00";
    document.getElementById("backupToInput").value = capturedAt
        ? capturedAt.slice(0, 16)
        : new Date().toISOString().slice(0, 16);
}

function formatBytes(bytes) {
    if (!bytes) return "0 B";
    const units = ["B", "KB", "MB", "GB"];
    let i = 0, n = bytes;
    while (n >= 1024 && i < units.length - 1) {
        n /= 1024;
        i++;
    }
    return `${n.toFixed(1)} ${units[i]}`;
}

// Preview'da ishlatilgan (from, to) — "♻️ Tiklash" bosilganda AYNAN shu
// qiymatlar bilan qayta yuboriladi (backend ham qayta tekshiradi).
let lastPreviewRange = null;

async function previewBackupCourses() {
    const fileName = document.getElementById("backupFileSelect").value;
    const from = document.getElementById("backupFromInput").value;
    const to = document.getElementById("backupToInput").value;
    const resultDiv = document.getElementById("backupPreviewResult");

    if (!fileName) {
        alert("❌ Avval backup faylni tanlang.");
        return;
    }
    if (!from || !to) {
        alert("❌ Vaqt oralig'ini (Dan/Gacha) to'liq kiriting.");
        return;
    }

    resultDiv.innerHTML = `<p>Yuklanmoqda...</p>`;

    try {
        const url = `/api/backups/${encodeURIComponent(fileName)}/preview?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`;
        const res = await fetch(url);
        const data = await res.json().catch(() => []);
        if (!res.ok) {
            resultDiv.innerHTML = `<p class="error">${(data && data.error) || "Ko'rishda xatolik"}</p>`;
            return;
        }

        lastPreviewRange = { fileName, from, to };

        if (!data.length) {
            resultDiv.innerHTML = `<p>Shu vaqt oralig'ida kurs topilmadi.</p>`;
            return;
        }

        // MUHIM: checkbox HAQIQIY "disabled" atributi bilan EMAS — brauzer
        // "disabled" elementga umuman click hodisasini bermaydi/ko'tarmaydi
        // (na o'ziga, na ota elementlarga), shu sabab avval bosilganda hech
        // qanday izoh chiqarib bo'lmas edi. Buning o'rniga oddiy (ishlaydigan)
        // checkbox, lekin bosilganda darhol qaytarib o'chiriladi va SABABI
        // alert orqali ko'rsatiladi (explainBackupCheckboxBlocked) — CSS
        // klassi (.backup-checkbox-blocked) esa faqat "disabled"dek KO'RINISH
        // uchun.
        resultDiv.innerHTML = `
            <ul id="backupCandidateList">
                ${data.map(c => `
                    <li>
                        <label>
                            <input type="checkbox" value="${c.id}"
                                class="${c.alreadyExistsLive ? "backup-checkbox-blocked" : ""}"
                                ${c.alreadyExistsLive ? `onclick="return explainBackupCheckboxBlocked(this, ${c.id})"` : ""}>
                            #${c.id} — ${escapeHtml(c.title)} (${formatDate(c.createdAt)})
                            ${c.alreadyExistsLive ? '<span class="topic-course-badge">Jonli bazada allaqachon bor — o\'tkazib yuboriladi</span>' : ""}
                        </label>
                    </li>
                `).join("")}
            </ul>
            <button onclick="applyBackupRestore()">♻️ Tanlanganlarni tiklash</button>
        `;
    } catch (err) {
        console.error(err);
        resultDiv.innerHTML = `<p class="error">Tarmoq xatoligi</p>`;
    }
}

// checkbox "band" (jonli bazada allaqachon bor) bo'lganda bosilganda
// chaqiriladi — darhol qaytarib o'chiradi (checkbox click hodisasi
// checked=true qilib bo'lgach chaqiriladi, shu sabab shu yerda qaytarib
// olinadi) va sababini alert orqali tushuntiradi.
function explainBackupCheckboxBlocked(checkbox, courseId) {
    checkbox.checked = false;
    alert(`❌ #${courseId} kursi jonli bazada ALLAQACHON bor (faol yoki "O'chirilganlar savati"da) — shu sabab backup'dan tiklab bo'lmaydi, checkbox band qilingan.\n\nAgar bu kursni backup'dagi holatiga qaytarmoqchi bo'lsangiz, avval uni joriy holatidan olib tashlash (masalan butunlay o'chirish) kerak — bu funksiya mavjud kursning USTIGA yozmaydi.`);
    return false;
}

async function applyBackupRestore() {
    if (!lastPreviewRange) {
        alert("❌ Avval \"🔍 Ko'rish\" tugmasini bosing.");
        return;
    }

    const courseIds = Array.from(document.querySelectorAll("#backupCandidateList input[type=checkbox]:checked"))
        .map(el => Number(el.value));

    if (!courseIds.length) {
        alert("❌ Tiklash uchun kamida bitta kursni belgilang.");
        return;
    }

    if (!confirm(`⚠️ ${courseIds.length} ta kursni backupdan jonli bazaga tiklamoqchimisiz?\n\nBu amal jonli bazaga to'g'ridan-to'g'ri yozadi.`)) {
        return;
    }

    try {
        const res = await fetch(`/api/backups/${encodeURIComponent(lastPreviewRange.fileName)}/restore`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ from: lastPreviewRange.from, to: lastPreviewRange.to, courseIds })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            alert(data.error || "Tiklashda xatolik");
            return;
        }

        alert(
            `✅ Tiklandi:\n` +
            `Kurslar — ${data.restoredCourses}\n` +
            `Bo'limlar — ${data.restoredChapters}\n` +
            `Mavzular (dars) — ${data.restoredSections}\n` +
            `Obunalar — ${data.restoredSubscriptions}\n` +
            `Progress yozuvlari — ${data.restoredProgress}` +
            (data.skippedCourseIds && data.skippedCourseIds.length
                ? `\n\nO'tkazib yuborildi (allaqachon mavjud/oraliqdan tashqarida): ${data.skippedCourseIds.join(", ")}`
                : "")
        );

        document.getElementById("backupPreviewResult").innerHTML = "";
        lastPreviewRange = null;
        loadTrash();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}
