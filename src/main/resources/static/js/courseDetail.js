let cachedCourse = null;

document.addEventListener("DOMContentLoaded", () => {
    loadCourse();
    loadScienceNamesList();
    // Enter bosilganda <div> o'rniga <p> hosil bo'lishi uchun — brauzerlar
    // orasida bir xil natija beradi va courses.css'dagi
    // .rich-text-editor p qoidasi to'g'ri ishlaydi.
    try {
        document.execCommand("defaultParagraphSeparator", false, "p");
    } catch (e) { /* eski brauzerlarda yo'q bo'lishi mumkin — muhim emas */ }

    setupPasteSanitizer("newSectionTextEditor");
    setupPasteSanitizer("editSectionTextEditor");
});

// PDF/Word/Google Docs'dan joylashtirilgan (paste) matn ko'pincha o'z
// rangini (masalan qora fonli hujjatlarda oq matn) olib keladi —
// saytning oq foniga tushganda matn butunlay ko'rinmay qolardi. Shuning
// uchun paste hodisasi to'xtatilib, brauzer taqdim etgan HTML tozalanadi:
// abzats/qalin/kursiv/ro'yxat/jadval kabi FORMAT saqlanadi, faqat
// rang (color/background) bilan bog'liq inline uslublar olib tashlanadi
// — matn har doim saytning o'z ranglarida, lekin formatlash bilan ko'rinadi.
function setupPasteSanitizer(editorId) {
    const editor = document.getElementById(editorId);
    if (!editor) return;

    editor.addEventListener("paste", (e) => {
        e.preventDefault();
        const html = e.clipboardData.getData("text/html");
        const content = html
            ? sanitizePastedHtml(html)
            : escapeHtml(e.clipboardData.getData("text/plain")).replace(/\n/g, "<br>");
        document.execCommand("insertHTML", false, content);
    });
}

function sanitizePastedHtml(html) {
    const container = document.createElement("div");
    container.innerHTML = html;

    container.querySelectorAll("*").forEach(el => {
        el.style.removeProperty("color");
        el.style.removeProperty("background");
        el.style.removeProperty("background-color");
        el.removeAttribute("color");
        el.removeAttribute("bgcolor");
    });

    return container.innerHTML;
}

// Matn maydoni oddiy <textarea> emas, contenteditable ("rich-text-editor")
// — shuning uchun PDF/Word'dan Ctrl+C/Ctrl+V qilinganda qalin matn,
// ro'yxat va (imkon qadar) jadval formatlashi saqlanib qoladi (avval
// <textarea> hamma narsani oddiy matnga aylantirib, formatni yo'qotardi).
function richExec(editorId, command) {
    document.getElementById(editorId).focus();
    document.execCommand(command, false, null);
}

// .docx faylni mammoth.js orqali HTML'ga aylantiradi — abzatslar,
// qalin/kursiv, sarlavhalar, ro'yxatlar kabi formatlash saqlanadi (fayl
// ichidagi shriftlar/uslublar o'zgartirilmaydi, faqat saytning umumiy
// dizayniga moslashtiriladi). Natija to'g'ridan-to'g'ri tahrirlash
// maydoniga qo'yiladi — kerak bo'lsa qo'lda ham tahrirlash mumkin.
async function importDocxFile(fileInput, editorId) {
    const file = fileInput.files[0];
    if (!file) return;

    if (typeof mammoth === "undefined") {
        alert("❌ Import kutubxonasi yuklanmadi. Internet aloqasini tekshirib, sahifani qayta yuklang.");
        fileInput.value = "";
        return;
    }

    try {
        const arrayBuffer = await file.arrayBuffer();
        const result = await mammoth.convertToHtml({ arrayBuffer });
        document.getElementById(editorId).innerHTML = result.value;
    } catch (err) {
        console.error(err);
        alert("❌ Faylni import qilishda xatolik: " + err.message);
    } finally {
        fileInput.value = "";
    }
}

// "Fan nomi" maydonlariga (qo'shish/tahrirlash) mavjud fanlarni <datalist>
// orqali taklif qilish — yozish paytida mos nom bo'lsa, avtomatik yaratish
// o'rniga o'shaning ustiga bog'lanadi.
function loadScienceNamesList() {
    fetch("/api/science")
        .then(r => r.ok ? r.json() : [])
        .then(sciences => {
            const list = document.getElementById("scienceNamesList");
            list.innerHTML = sciences.map(s => `<option value="${escapeHtml(s.name)}">`).join("");
        })
        .catch(err => console.error(err));
}

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
    document.getElementById("sectionsSortBar").style.display = course.canManage ? "flex" : "none";

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

    list.innerHTML = sections.map((s, i) => {
        const indexClass = s.completed ? "section-index completed" : "section-index";
        const indexIcon = s.completed ? "✓" : s.orderIndex;
        const typeIcon = s.type === "VIDEO" ? "🎬" : s.type === "MIXED" ? "📄🎬" : "📄";

        // Butun karta bosiladigan qilindi (kurslar katalogidagi kartalar
        // bilan bir xil uslub) — shuning uchun sarlavha endi alohida <a>
        // emas, oddiy matn; hover effekti ham shu tashqi kartada.
        const titleEl = `<span class="section-title-text" title="${escapeHtml(s.title)}">${escapeHtml(s.title)}</span>`;
        const cardClick = s.locked ? "" : ` onclick="location.href='/courses/${COURSE_ID}/sections/${s.id}'"`;

        // Ichidagi tugmalar (boshqarish, test) bosilganda kartaning o'zi
        // ham navigatsiya qilib yubormasligi uchun — shu wrapper'larga
        // event.stopPropagation() qo'yiladi.
        const manageActions = cachedCourse && cachedCourse.canManage
            ? `<div class="section-manage-actions" onclick="event.stopPropagation()">
                   <button onclick="moveSectionUp(${s.id})" title="Yuqoriga" ${i === 0 ? "disabled" : ""}>⬆️</button>
                   <button onclick="moveSectionDown(${s.id})" title="Pastga" ${i === sections.length - 1 ? "disabled" : ""}>⬇️</button>
                   <button onclick="openEditSectionForm(${s.id})" title="Tahrirlash">✏️</button>
                   <button onclick="deleteSection(${s.id})" title="O'chirish">🗑️</button>
               </div>`
            : "";

        // Shu mavzu haqiqiy test tizimidagi bir mavzuga bog'langan bo'lsa —
        // ro'yxatdan turib ham, bo'limni ochmasdan, testlarni yechish tugmasi
        // (faqat ochilgan/qulflanmagan mavzularda — qulflangan bo'lsa
        // bo'limning o'zini ham ko'rib bo'lmaydi).
        const testLink = (!s.locked && s.linkedTopicId)
            ? `<button class="topic-test-btn-inline" onclick="event.stopPropagation(); location.href='/testConfigPage?scienceId=${s.linkedScienceId}&topicId=${s.linkedTopicId}'">🎯 Mavzuga oid testlarni yechish</button>`
            : "";

        return `
            <div class="section-item ${s.locked ? "locked" : ""}"${cardClick}>
                <div class="section-item-top">
                    <div class="section-item-left">
                        <div class="${indexClass}">${indexIcon}</div>
                        ${titleEl}
                        <span class="section-type-icon">${typeIcon}</span>
                        ${s.locked ? '<span class="section-type-icon">🔒</span>' : ""}
                    </div>
                    ${testLink}
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
                coverImageUrl: cachedCourse.coverImageUrl,
                free: cachedCourse.free,
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
    const preview = document.getElementById("editCourseCoverPreview");
    document.getElementById("editCourseCoverFile").value = "";
    document.getElementById("editCourseCoverStatus").textContent = "";
    document.getElementById("editCourseFree").checked = !!(cachedCourse && cachedCourse.free);

    if (cachedCourse && cachedCourse.coverImageUrl) {
        preview.src = cachedCourse.coverImageUrl;
        preview.style.display = "block";
    } else {
        preview.style.display = "none";
    }

    document.getElementById("editCourseForm").style.display = "flex";
}

function closeEditCourseForm() {
    document.getElementById("editCourseForm").style.display = "none";
}

// Fayl tanlanganda darhol ko'rinadi (yuklashdan oldin) — hozirgi
// rasm o'rniga qaysi rasm tanlanganini ko'rish uchun.
function previewEditCourseCover(fileInput) {
    const file = fileInput.files[0];
    if (!file) return;

    const preview = document.getElementById("editCourseCoverPreview");
    preview.src = URL.createObjectURL(file);
    preview.style.display = "block";
}

async function submitEditCourse() {
    const title = document.getElementById("editCourseTitle").value.trim();
    const description = document.getElementById("editCourseDescription").value.trim();

    if (!title) {
        alert("❌ Kurs nomini kiriting");
        return;
    }

    let coverImageUrl = cachedCourse.coverImageUrl;
    const fileInput = document.getElementById("editCourseCoverFile");

    try {
        if (fileInput.files[0]) {
            document.getElementById("editCourseCoverStatus").textContent = "Yuklanmoqda...";
            const formData = new FormData();
            formData.append("image", fileInput.files[0]);
            const uploadRes = await fetch("/api/courses/upload-cover", { method: "POST", body: formData });
            const uploadData = await uploadRes.json().catch(() => ({}));
            if (!uploadRes.ok) {
                alert(uploadData.error || "Rasm yuklashda xatolik");
                document.getElementById("editCourseCoverStatus").textContent = "";
                return;
            }
            coverImageUrl = uploadData.url;
            document.getElementById("editCourseCoverStatus").textContent = "";
        }

        const free = document.getElementById("editCourseFree").checked;

        const res = await fetch(`/api/courses/${COURSE_ID}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                title, description, coverImageUrl, free,
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
    document.getElementById("newSectionTextEditor").innerHTML = "";
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
    const payload = {
        title, type, textContent: null, videoSourceType: null, videoUrl: null, videoDurationSeconds: null,
        scienceName: document.getElementById("newSectionScienceName").value.trim() || null,
        topicName: document.getElementById("newSectionTopicName").value.trim() || null,
        textContentFormat: "HTML"
    };

    if (includeText) {
        const editor = document.getElementById("newSectionTextEditor");
        if (!editor.innerText.trim()) {
            alert("❌ Matn kontentini kiriting");
            return;
        }
        payload.textContent = editor.innerHTML;
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
        document.getElementById("newSectionTextEditor").innerHTML = "";
        document.getElementById("newSectionVideoUrl").value = "";
        document.getElementById("newSectionScienceName").value = "";
        document.getElementById("newSectionTopicName").value = "";
        document.getElementById("includeText").checked = true;
        document.getElementById("includeVideo").checked = false;
        onContentToggle(document.getElementById("includeText"));
        closeAddSectionForm();
        loadCourse();
        loadScienceNamesList();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

async function deleteSection(sectionId) {
    // Backend 409 (bog'liq ma'lumotlar — progress yozuvlari) qaytarishi
    // mumkin edi, lekin CourseService.deleteSection endi ularni avtomatik
    // o'chiradi (kursni o'chirishdagi FK bug bilan bir xil sabab/tuzatish).
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

/* ===== OWNER/ADMIN: bo'limni tahrirlash ===== */

let editingSectionId = null;

async function openEditSectionForm(sectionId) {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/${sectionId}`);
        if (!res.ok) {
            alert("Bo'lim ma'lumotlarini yuklab bo'lmadi");
            return;
        }
        const section = await res.json();
        editingSectionId = sectionId;

        document.getElementById("editSectionTitle").value = section.title;

        const hasText = section.type === "TEXT" || section.type === "MIXED";
        const hasVideo = section.type === "VIDEO" || section.type === "MIXED";
        document.getElementById("editIncludeText").checked = hasText;
        document.getElementById("editIncludeVideo").checked = hasVideo;

        // Eski PLAIN (qo'lda yozilgan, hali WYSIWYG'gacha) bo'limlar xom
        // matn sifatida saqlangan — tahrirlash oynasida to'g'ri ko'rinishi
        // uchun xavfsiz escape qilib, qatorlarni <br>'ga aylantiramiz.
        // Saqlashda esa hammasi HTML formatga o'tadi (orqaga qaytish shart
        // emas — bu shunchaki bir martalik yaxshilanish).
        const editor = document.getElementById("editSectionTextEditor");
        editor.innerHTML = section.textContentFormat === "HTML"
            ? (section.textContent || "")
            : escapeHtml(section.textContent || "").replace(/\n/g, "<br>");

        onEditContentToggle(document.getElementById("editIncludeText"));

        if (hasVideo) {
            document.getElementById("editSectionVideoSource").value = section.videoSourceType || "YOUTUBE";
            document.getElementById("editSectionVideoUrl").value = section.videoUrl || "";
            document.getElementById("editSectionVideoDuration").value = section.videoDurationSeconds || "";
            onEditVideoSourceChange();
        }

        document.getElementById("editSectionScienceName").value = section.linkedScienceName || "";
        document.getElementById("editSectionTopicName").value = section.linkedTopicName || "";

        document.getElementById("editSectionForm").style.display = "flex";
        document.getElementById("editSectionForm").scrollIntoView({ behavior: "smooth", block: "center" });
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

function closeEditSectionForm() {
    editingSectionId = null;
    document.getElementById("editSectionForm").style.display = "none";
}

function onEditContentToggle(changedCheckbox) {
    const includeText = document.getElementById("editIncludeText");
    const includeVideo = document.getElementById("editIncludeVideo");

    if (!includeText.checked && !includeVideo.checked) {
        (changedCheckbox || includeText).checked = true;
    }

    document.getElementById("editTextFields").style.display = includeText.checked ? "block" : "none";
    document.getElementById("editVideoFields").style.display = includeVideo.checked ? "block" : "none";
}

function onEditVideoSourceChange() {
    const source = document.getElementById("editSectionVideoSource").value;
    document.getElementById("editSectionVideoUrl").style.display = source === "UPLOAD" ? "none" : "block";
    document.getElementById("editSectionVideoFile").style.display = source === "UPLOAD" ? "block" : "none";
    document.getElementById("editSectionVideoDuration").style.display = source === "EXTERNAL" ? "block" : "none";

    document.getElementById("editSectionVideoUrl").placeholder =
        source === "YOUTUBE" ? "YouTube video ID (masalan: dQw4w9WgXcQ)" : "Video URL";
}

async function submitEditSection() {
    if (!editingSectionId) return;

    const title = document.getElementById("editSectionTitle").value.trim();
    const includeText = document.getElementById("editIncludeText").checked;
    const includeVideo = document.getElementById("editIncludeVideo").checked;

    if (!title) {
        alert("❌ Bo'lim nomini kiriting");
        return;
    }

    if (!includeText && !includeVideo) {
        alert("❌ Kamida bittasini tanlang: Matn yoki Video");
        return;
    }

    const type = includeText && includeVideo ? "MIXED" : includeText ? "TEXT" : "VIDEO";
    const payload = {
        title, type, textContent: null, videoSourceType: null, videoUrl: null, videoDurationSeconds: null,
        scienceName: document.getElementById("editSectionScienceName").value.trim() || null,
        topicName: document.getElementById("editSectionTopicName").value.trim() || null,
        textContentFormat: "HTML"
    };

    if (includeText) {
        const editor = document.getElementById("editSectionTextEditor");
        if (!editor.innerText.trim()) {
            alert("❌ Matn kontentini kiriting");
            return;
        }
        payload.textContent = editor.innerHTML;
    }

    if (includeVideo) {
        const source = document.getElementById("editSectionVideoSource").value;
        payload.videoSourceType = source;

        if (source === "UPLOAD") {
            const fileInput = document.getElementById("editSectionVideoFile");
            if (fileInput.files[0]) {
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
                // Yangi fayl tanlanmagan — eski video URL saqlanib qoladi.
                payload.videoUrl = document.getElementById("editSectionVideoUrl").value.trim();
            }
        } else {
            payload.videoUrl = document.getElementById("editSectionVideoUrl").value.trim();
            if (!payload.videoUrl) {
                alert("❌ Video URL/ID ni kiriting");
                return;
            }
            if (source === "EXTERNAL") {
                payload.videoDurationSeconds = Number(document.getElementById("editSectionVideoDuration").value) || null;
            }
        }
    }

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/${editingSectionId}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(payload)
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            alert(data.error || "Bo'limni saqlashda xatolik");
            return;
        }

        closeEditSectionForm();
        loadCourse();
        loadScienceNamesList();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

/* ===== OWNER/ADMIN: bo'limlarni saralash ===== */

async function reorderTo(sectionIds) {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/reorder`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(sectionIds)
        });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "Tartibni saqlashda xatolik");
            return;
        }
        loadCourse();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

function moveSectionUp(sectionId) {
    if (!cachedCourse) return;
    const ids = cachedCourse.sections.map(s => s.id);
    const i = ids.indexOf(sectionId);
    if (i <= 0) return;
    [ids[i - 1], ids[i]] = [ids[i], ids[i - 1]];
    reorderTo(ids);
}

function moveSectionDown(sectionId) {
    if (!cachedCourse) return;
    const ids = cachedCourse.sections.map(s => s.id);
    const i = ids.indexOf(sectionId);
    if (i === -1 || i >= ids.length - 1) return;
    [ids[i], ids[i + 1]] = [ids[i + 1], ids[i]];
    reorderTo(ids);
}

// dir: "AZ" | "ZA" — bo'lim nomlari bo'yicha to'liq qayta saralash.
function sortSections(dir) {
    if (!cachedCourse) return;
    const sorted = [...cachedCourse.sections].sort((a, b) =>
        dir === "AZ" ? a.title.localeCompare(b.title, "uz") : b.title.localeCompare(a.title, "uz"));
    reorderTo(sorted.map(s => s.id));
}

/* Obuna berish/tasdiqlash/bekor qilish — endi /courses/subscriptions
   sahifasida (courseSubscriptions.js), barcha kurslar uchun yagona joyda. */
