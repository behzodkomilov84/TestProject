const ROLE = document.body.dataset.role;
const IS_OWNER = ROLE === "ROLE_OWNER";

let cachedCourse = null;

document.addEventListener("DOMContentLoaded", () => {
    loadCourse();
    if (IS_OWNER) {
        document.getElementById("manageCoursePanel").style.display = "block";
        loadUsersForGrant();
        loadSubscribers();
    }
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

    document.getElementById("subscribeBanner").style.display =
        (!course.subscribed && !course.canManage) ? "block" : "none";

    if (course.canManage) {
        document.getElementById("togglePublishBtn").textContent =
            course.published ? "📕 Qoralamaga o'tkazish" : "📗 Chop etish";
        document.getElementById("editCourseTitle").value = course.title;
        document.getElementById("editCourseDescription").value = course.description || "";
    }

    renderSections(course.sections);
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
        const typeIcon = s.type === "VIDEO" ? "🎬" : "📄";

        const link = s.locked
            ? `<span>${escapeHtml(s.title)}</span>`
            : `<a href="/courses/${COURSE_ID}/sections/${s.id}">${escapeHtml(s.title)}</a>`;

        const manageActions = IS_OWNER
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

function onSectionTypeChange() {
    const type = document.getElementById("newSectionType").value;
    document.getElementById("textFields").style.display = type === "TEXT" ? "block" : "none";
    document.getElementById("videoFields").style.display = type === "VIDEO" ? "block" : "none";
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
    const type = document.getElementById("newSectionType").value;

    if (!title) {
        alert("❌ Bo'lim nomini kiriting");
        return;
    }

    const payload = { title, type, textContent: null, videoSourceType: null, videoUrl: null, videoDurationSeconds: null };

    if (type === "TEXT") {
        payload.textContent = document.getElementById("newSectionText").value.trim();
        if (!payload.textContent) {
            alert("❌ Matn kontentini kiriting");
            return;
        }
    } else {
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

/* ===== OWNER: obuna berish ===== */

function loadUsersForGrant() {
    fetch("/api/users")
        .then(r => r.ok ? r.json() : [])
        .then(users => {
            const select = document.getElementById("grantUserSelect");
            if (!select) return;
            select.innerHTML = users.map(u => `<option value="${u.id}">${escapeHtml(u.username)}</option>`).join("");
        })
        .catch(err => console.error(err));
}

async function submitGrantSubscription() {
    const userId = Number(document.getElementById("grantUserSelect").value);
    const amount = Number(document.getElementById("grantAmount").value) || 0;
    const note = document.getElementById("grantNote").value.trim();

    if (!userId) {
        alert("❌ Foydalanuvchini tanlang");
        return;
    }

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/subscriptions`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ userId, amount, note })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            alert(data.error || "Xatolik yuz berdi");
            return;
        }

        alert("✅ Obuna berildi");
        document.getElementById("grantAmount").value = "";
        document.getElementById("grantNote").value = "";
        loadSubscribers();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

function loadSubscribers() {
    fetch(`/api/courses/${COURSE_ID}/subscriptions`)
        .then(r => r.ok ? r.json() : [])
        .then(renderSubscribers)
        .catch(err => console.error(err));
}

function renderSubscribers(subs) {
    const tbody = document.getElementById("subscribersTableBody");
    if (!tbody) return;

    if (!subs.length) {
        tbody.innerHTML = `<tr><td colspan="5" class="empty-row">Hali obunachi yo'q</td></tr>`;
        return;
    }

    tbody.innerHTML = subs.map(s => `
        <tr>
            <td>${escapeHtml(s.username)}</td>
            <td>${Number(s.amount).toLocaleString("uz-UZ")} so'm</td>
            <td>${s.status === "CONFIRMED" ? "✅ Faol" : "❌ Bekor qilingan"}</td>
            <td>${new Date(s.createdAt).toLocaleDateString("uz-UZ")}</td>
            <td>
                ${s.status === "CONFIRMED"
                    ? `<button onclick="cancelSubscription(${s.id})">Bekor qilish</button>`
                    : "—"}
            </td>
        </tr>
    `).join("");
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
