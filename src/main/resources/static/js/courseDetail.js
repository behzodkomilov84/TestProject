let cachedCourse = null;

document.addEventListener("DOMContentLoaded", () => {
    loadCourse();
});

function loadCourse() {
    fetch(`/api/courses/${COURSE_ID}`)
        .then(r => {
            if (!r.ok) throw new Error("Kurs topilmadi yoki ruxsat yo'q");
            return r.json();
        })
        .then(renderCourse)
        .catch(err => {
            console.error(err);
            document.getElementById("courseTitle").textContent = "Kurs topilmadi";
        });
}

function renderCourse(course) {
    cachedCourse = course;

    document.getElementById("courseTitle").textContent = course.title;
    document.getElementById("courseDescription").textContent = course.description || "";

    const showBanner = !course.subscribed && !course.canManage;
    document.getElementById("subscribeBanner").style.display = showBanner ? "flex" : "none";

    if (showBanner) {
        const btn = document.getElementById("requestSubscriptionBtn");
        if (course.requestPending) {
            document.getElementById("subscribeBannerText").textContent =
                "⏳ Obunaga so'rovingiz yuborilgan — administrator (OWNER) javobini kuting.";
            btn.style.display = "none";
        } else {
            document.getElementById("subscribeBannerText").textContent =
                "🔒 Bu kursning to'liq mazmuniga kirish uchun obuna kerak.";
            btn.style.display = "";
        }
    }

    // Boshqarish paneli (tahrirlash/chop etish/bo'lim qo'shish) — OWNER
    // uchun HAR DOIM, ADMIN uchun faqat O'ZI yaratgan kursda (backend
    // shu logikani hisoblab, canManage sifatida qaytaradi).
    document.getElementById("manageCoursePanel").style.display = course.canManage ? "block" : "none";

    if (course.canManage) {
        document.getElementById("togglePublishBtn").textContent =
            course.published ? "📕 Qoralamaga o'tkazish" : "📗 Chop etish";
        document.getElementById("editCourseTitle").value = course.title;
        document.getElementById("editCourseDescription").value = course.description || "";
    }

    renderSections(course.sections);
}

async function requestSubscription() {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/subscriptions/request`, { method: "POST" });
        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            alert(data.error || "Xatolik yuz berdi");
            return;
        }

        alert("✅ So'rovingiz yuborildi. Administrator (OWNER) ko'rib chiqib, obunani tasdiqlaydi.");
        loadCourse();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

function renderSections(sections) {
    const list = document.getElementById("sectionsList");

    if (!sections.length) {
        list.innerHTML = `<div class="courses-empty">Hali bo'lim yo'q</div>`;
        return;
    }

    list.innerHTML = sections.map(s => {
        const indexClass = s.completed ? "section-index completed" : "section-index";
        const indexIcon = s.completed ? "✓" : s.orderIndex;
        const typeIcon = s.type === "VIDEO" ? "🎬" : s.type === "MIXED" ? "📄🎬" : "📄";

        const link = s.locked
            ? `<span>${escapeHtml(s.title)}</span>`
            : `<a href="/courses/${COURSE_ID}/sections/${s.id}">${escapeHtml(s.title)}</a>`;

        const manageActions = cachedCourse && cachedCourse.canManage
            ? `<div class="section-manage-actions">
                   <button onclick="deleteSection(${s.id})" title="O'chirish">🗑️</button>
               </div>`
            : "";

        return `
            <div class="section-item ${s.locked ? "locked" : ""}">
                <div class="section-item-left">
                    <div class="${indexClass}">${indexIcon}</div>
                    ${link}
                    <span class="section-type-icon">${typeIcon}</span>
                    ${s.locked ? '<span class="section-type-icon">🔒</span>' : ""}
                </div>
                ${manageActions}
            </div>
        `;
    }).join("");
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
}

/* ===== OWNER: kursni boshqarish ===== */

async function togglePublish() {
    if (!cachedCourse) return;

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                title: cachedCourse.title,
                description: cachedCourse.description,
                coverImageUrl: null,
                published: !cachedCourse.published
            })
        });

        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "Xatolik yuz berdi");
            return;
        }

        loadCourse();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

function openEditCourseForm() {
    document.getElementById("editCourseForm").style.display = "flex";
}

function closeEditCourseForm() {
    document.getElementById("editCourseForm").style.display = "none";
}

async function submitEditCourse() {
    const title = document.getElementById("editCourseTitle").value.trim();
    const description = document.getElementById("editCourseDescription").value.trim();

    if (!title) {
        alert("❌ Kurs nomini kiriting");
        return;
    }

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                title, description,
                coverImageUrl: cachedCourse.coverImageUrl,
                published: cachedCourse.published
            })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            alert(data.error || "Xatolik yuz berdi");
            return;
        }

        closeEditCourseForm();
        loadCourse();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

async function deleteCourse() {
    if (!confirm("Kursni butunlay o'chirmoqchimisiz? Barcha bo'limlar ham o'chadi.")) return;

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "Xatolik yuz berdi");
            return;
        }
        location.href = "/courses";
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

/* ===== OWNER: bo'lim qo'shish ===== */

function openAddSectionForm() {
    document.getElementById("addSectionForm").style.display = "flex";
    document.getElementById("openAddSectionBtn").style.display = "none";
}

function closeAddSectionForm() {
    document.getElementById("addSectionForm").style.display = "none";
    document.getElementById("openAddSectionBtn").style.display = "";
}

// Matn va video mustaqil checkbox'lar — bittasi yoki ikkalasi ham
// belgilanishi mumkin, lekin ikkalasi ham bo'sh qolishi mumkin emas
// (oxirgisini o'chirib bo'lmaydi — avtomatik qayta belgilanadi).
function onContentToggle(changedCheckbox) {
    const includeText = document.getElementById("includeText");
    const includeVideo = document.getElementById("includeVideo");

    if (!includeText.checked && !includeVideo.checked) {
        // Ikkalasi ham o'chirilgan — hozir bosilgan checkbox'ni qayta yoqamiz.
        (changedCheckbox || includeText).checked = true;
    }

    document.getElementById("textFields").style.display = includeText.checked ? "block" : "none";
    document.getElementById("videoFields").style.display = includeVideo.checked ? "block" : "none";
}

function onVideoSourceChange() {
    const source = document.getElementById("newSectionVideoSource").value;
    document.getElementById("newSectionVideoUrl").style.display = source === "UPLOAD" ? "none" : "block";
    document.getElementById("newSectionVideoFile").style.display = source === "UPLOAD" ? "block" : "none";
    document.getElementById("newSectionVideoDuration").style.display = source === "EXTERNAL" ? "block" : "none";

    document.getElementById("newSectionVideoUrl").placeholder =
        source === "YOUTUBE" ? "YouTube video ID (masalan: dQw4w9WgXcQ)" : "Video URL";
}

async function submitAddSection() {
    const title = document.getElementById("newSectionTitle").value.trim();
    const includeText = document.getElementById("includeText").checked;
    const includeVideo = document.getElementById("includeVideo").checked;

    if (!title) {
        alert("❌ Bo'lim nomini kiriting");
        return;
    }

    if (!includeText && !includeVideo) {
        alert("❌ Kamida bittasini tanlang: Matn yoki Video");
        return;
    }

    const type = includeText && includeVideo ? "MIXED" : includeText ? "TEXT" : "VIDEO";
    const payload = { title, type, textContent: null, videoSourceType: null, videoUrl: null, videoDurationSeconds: null };

    if (includeText) {
        payload.textContent = document.getElementById("newSectionText").value.trim();
        if (!payload.textContent) {
            alert("❌ Matn kontentini kiriting");
            return;
        }
    }

    if (includeVideo) {
        const source = document.getElementById("newSectionVideoSource").value;
        payload.videoSourceType = source;

        if (source === "UPLOAD") {
            const fileInput = document.getElementById("newSectionVideoFile");
            if (!fileInput.files[0]) {
                alert("❌ Video faylni tanlang");
                return;
            }
            try {
                const formData = new FormData();
                formData.append("video", fileInput.files[0]);
                const uploadRes = await fetch(`/api/courses/${COURSE_ID}/sections/upload-video`, {
                    method: "POST", body: formData
                });
                const uploadData = await uploadRes.json().catch(() => ({}));
                if (!uploadRes.ok) {
                    alert(uploadData.error || "Video yuklashda xatolik");
                    return;
                }
                payload.videoUrl = uploadData.url;
            } catch (err) {
                console.error(err);
                alert("Video yuklashda tarmoq xatoligi");
                return;
            }
        } else {
            payload.videoUrl = document.getElementById("newSectionVideoUrl").value.trim();
            if (!payload.videoUrl) {
                alert("❌ Video URL/ID ni kiriting");
                return;
            }
            if (source === "EXTERNAL") {
                payload.videoDurationSeconds = Number(document.getElementById("newSectionVideoDuration").value) || null;
            }
        }
    }

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/sections`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            alert(data.error || "Bo'lim qo'shishda xatolik");
            return;
        }

        document.getElementById("newSectionTitle").value = "";
        document.getElementById("newSectionText").value = "";
        document.getElementById("newSectionVideoUrl").value = "";
        document.getElementById("includeText").checked = true;
        document.getElementById("includeVideo").checked = false;
        onContentToggle(document.getElementById("includeText"));
        closeAddSectionForm();
        loadCourse();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

async function deleteSection(sectionId) {
    if (!confirm("Bo'limni o'chirmoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/${sectionId}`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "Xatolik yuz berdi");
            return;
        }
        loadCourse();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

/* Obuna berish/tasdiqlash/bekor qilish — endi /courses/subscriptions
   sahifasida (courseSubscriptions.js), barcha kurslar uchun yagona joyda. */
