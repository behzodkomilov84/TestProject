// ================= Rasm/video yuklash (savol, javob, izoh uchun) =================
// Har bir ".image-upload" bloki: fayl input + "rasm qo'shish" tugmasi +
// preview <img> + "olib tashlash" tugmasi. Fayl tanlangan zahoti serverga
// yuklanadi va qaytgan URL fileInput.dataset.url'da saqlanadi — forma
// yuborilganda shu URL ishlatiladi. data-role'ga qarab (question-image /
// answer-image / commentary-image) tegishli endpoint tanlanadi.
const IMAGE_UPLOAD_ENDPOINTS = {
    "question-image": "/api/question/upload-image",
    "answer-image": "/api/question/upload-image",
    "commentary-image": "/api/question/upload-commentary-image"
};

function setupImageUpload(container) {
    const fileInput = container.querySelector(".image-input");
    const uploadBtn = container.querySelector(".image-btn");
    const preview = container.querySelector(".image-preview");
    const removeBtn = container.querySelector(".remove-image-btn");
    const endpoint = IMAGE_UPLOAD_ENDPOINTS[container.dataset.role] || "/api/question/upload-image";
    const originalLabel = uploadBtn.textContent;

    uploadBtn.addEventListener("click", () => fileInput.click());

    fileInput.addEventListener("change", async () => {
        const file = fileInput.files[0];
        if (!file) return;

        const formData = new FormData();
        formData.append("image", file);

        uploadBtn.disabled = true;
        uploadBtn.textContent = "⏳ Yuklanmoqda...";

        try {
            const res = await fetch(endpoint, {
                method: "POST",
                body: formData
            });

            const data = await res.json();

            if (!res.ok) {
                alert(data.error || "❌ Rasmni yuklab bo'lmadi");
                fileInput.value = "";
                return;
            }

            fileInput.dataset.url = data.url;
            preview.src = data.url;
            preview.classList.remove("hidden");
            removeBtn.classList.remove("hidden");

        } catch (err) {
            console.error(err);
            alert("❌ Rasmni yuklashda tarmoq xatoligi");
        } finally {
            uploadBtn.disabled = false;
            uploadBtn.textContent = originalLabel;
        }
    });

    removeBtn.addEventListener("click", () => {
        fileInput.value = "";
        delete fileInput.dataset.url;
        preview.src = "";
        preview.classList.add("hidden");
        removeBtn.classList.add("hidden");
    });
}

function getImageUrl(container) {
    const fileInput = container.querySelector(".image-input");
    return fileInput?.dataset.url || null;
}

function resetImageUpload(container) {
    const fileInput = container.querySelector(".image-input");
    const preview = container.querySelector(".image-preview");
    const removeBtn = container.querySelector(".remove-image-btn");

    fileInput.value = "";
    delete fileInput.dataset.url;
    preview.src = "";
    preview.classList.add("hidden");
    removeBtn.classList.add("hidden");
}

// ================= Video yuklash (faqat izoh uchun) =================
function setupVideoUpload(container) {
    const fileInput = container.querySelector(".video-input");
    const uploadBtn = container.querySelector(".video-btn");
    const preview = container.querySelector(".video-preview");
    const removeBtn = container.querySelector(".remove-video-btn");
    const originalLabel = uploadBtn.textContent;

    uploadBtn.addEventListener("click", () => fileInput.click());

    fileInput.addEventListener("change", async () => {
        const file = fileInput.files[0];
        if (!file) return;

        const formData = new FormData();
        formData.append("video", file);

        uploadBtn.disabled = true;
        uploadBtn.textContent = "⏳ Yuklanmoqda...";

        try {
            const res = await fetch("/api/question/upload-commentary-video", {
                method: "POST",
                body: formData
            });

            const data = await res.json();

            if (!res.ok) {
                alert(data.error || "❌ Videoni yuklab bo'lmadi");
                fileInput.value = "";
                return;
            }

            fileInput.dataset.url = data.url;
            preview.src = data.url;
            preview.classList.remove("hidden");
            removeBtn.classList.remove("hidden");

        } catch (err) {
            console.error(err);
            alert("❌ Videoni yuklashda tarmoq xatoligi");
        } finally {
            uploadBtn.disabled = false;
            uploadBtn.textContent = originalLabel;
        }
    });

    removeBtn.addEventListener("click", () => {
        fileInput.value = "";
        delete fileInput.dataset.url;
        preview.src = "";
        preview.classList.add("hidden");
        removeBtn.classList.add("hidden");
    });
}

function getVideoUrl(container) {
    const fileInput = container.querySelector(".video-input");
    return fileInput?.dataset.url || null;
}

function resetVideoUpload(container) {
    const fileInput = container.querySelector(".video-input");
    const preview = container.querySelector(".video-preview");
    const removeBtn = container.querySelector(".remove-video-btn");

    fileInput.value = "";
    delete fileInput.dataset.url;
    preview.src = "";
    preview.classList.add("hidden");
    removeBtn.classList.add("hidden");
}

// ================= "🔗 Mavzuga havola qo'shish" =================
// Izohga qo'lda <a href="..."> yozish o'rniga — bitta tugma bosilsa,
// joriy savol tegishli bo'lgan mavzuning kursdagi bo'limiga to'g'ri
// havola cursor turgan joyga qo'yiladi. Mavzu hech qaysi kurs bo'limiga
// bog'lanmagan bo'lsa (topicCourseLink == null), tugma yashirin qoladi.
let topicCourseLink = null;

async function loadTopicCourseLink() {
    const topicId = document.getElementById("topicId").value;
    if (!topicId) return;

    try {
        const res = await fetch(`/api/topic/${topicId}/course-link`);
        if (!res.ok) return; // 404 — bu mavzu hech qaysi bo'limga bog'lanmagan, tugma yashirin qoladi

        topicCourseLink = await res.json();
        document.querySelectorAll(".link-btn").forEach(btn => btn.classList.remove("hidden"));
    } catch (err) {
        console.error(err);
    }
}

function insertAtCursor(textarea, text) {
    const start = textarea.selectionStart ?? textarea.value.length;
    const end = textarea.selectionEnd ?? textarea.value.length;

    textarea.value = textarea.value.slice(0, start) + text + textarea.value.slice(end);

    const pos = start + text.length;
    textarea.focus();
    textarea.setSelectionRange(pos, pos);
}

document.addEventListener("click", (e) => {
    if (!e.target.classList.contains("link-btn") || !topicCourseLink) return;

    const textarea = e.target.closest(".commentary-box")?.querySelector(".commentary");
    if (!textarea) return;

    // Izoh talabaga innerHTML sifatida ko'rsatiladi (testSession.js), shu
    // sabab bu yerda HAQIQIY <a> tegi qo'yiladi — o'qituvchi esa uni
    // qo'lda yozmaydi, faqat tugmani bosadi.
    const url = `/courses/${topicCourseLink.courseId}/sections/${topicCourseLink.sectionId}`;
    const title = topicCourseLink.topicName.replace(/"/g, "&quot;");
    insertAtCursor(textarea, ` <a href="${url}">📖 "${title}" mavzusini kursda o'qish</a>`);
});

document.addEventListener("DOMContentLoaded", () => {

    const form = document.getElementById("testForm");

    document.querySelectorAll(".image-upload").forEach(setupImageUpload);
    document.querySelectorAll(".video-upload").forEach(setupVideoUpload);
    loadTopicCourseLink();

    // автоподбор высоты textarea
    document.querySelectorAll(".auto-textarea").forEach(t => {
        t.addEventListener("input", () => {
            t.style.height = "auto";
            t.style.height = t.scrollHeight + "px";
        });
    });

    form.addEventListener("submit", async (e) => {
        e.preventDefault();

        const topicId = Number(document.getElementById("topicId").value);
        const questionText = document.getElementById("question").value.trim();

        const answersBlocks = document.querySelectorAll(".answer");
        const correctRadio = document.querySelector("input[name='correct']:checked");

        if (!correctRadio) {
            alert("❌ To‘g‘ri javobni tanlang");
            return;
        }

        const correctIndex = Number(correctRadio.value);

        // ================= Валидация ответов =================
        const texts = [];

        answersBlocks.forEach((block, index) => {
            const ta = block.querySelector("textarea.auto-textarea");
            const value = ta.value.trim();

            if (!value) {
                alert("❌ Barcha javoblarni to‘ldiring");
                ta.focus();
                throw new Error("Validation failed");
            }

            texts.push(value.toLowerCase());
        });

        // уникальность
        if (new Set(texts).size !== texts.length) {
            alert("❌ Javob variantlari bir xil bo‘lishi mumkin emas");
            return;
        }

        // ================= Формирование answers =================

        const answers = [...answersBlocks].map((block, index) => {
            const answerText = block.querySelector("textarea.auto-textarea").value.trim();
            const commentaryTextarea = block.querySelector(".commentary");
            const imageUploadBlock = block.querySelector('.image-upload[data-role="answer-image"]');
            const commentaryImageBlock = block.querySelector('.image-upload[data-role="commentary-image"]');
            const commentaryVideoBlock = block.querySelector('.video-upload[data-role="commentary-video"]');

            return {
                answerText,
                isTrue: index === correctIndex,
                commentary: index === correctIndex
                    ? commentaryTextarea?.value.trim() || null
                    : null,
                imageUrl: imageUploadBlock ? getImageUrl(imageUploadBlock) : null,
                // Izohga (faqat to'g'ri javobga) qo'shilgan rasm/video — matn bilan birga.
                commentaryImageUrl: index === correctIndex && commentaryImageBlock
                    ? getImageUrl(commentaryImageBlock)
                    : null,
                commentaryVideoUrl: index === correctIndex && commentaryVideoBlock
                    ? getVideoUrl(commentaryVideoBlock)
                    : null
            };
        });

        const questionImageBlock = document.querySelector('.image-upload[data-role="question-image"]');

        // ================= Payload =================
        const payload = {
            topicId,
            questionText,
            imageUrl: questionImageBlock ? getImageUrl(questionImageBlock) : null,
            answers
        };

        console.log("CREATE PAYLOAD:", payload);

        try {
            const res = await fetch("/api/question/save", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });

            if (!res.ok) {
                throw new Error(await res.text());
            }

            alert("✅ Test muvaffaqiyatli saqlandi");
            form.reset();
            document.querySelectorAll(".image-upload").forEach(resetImageUpload);
            document.querySelectorAll(".video-upload").forEach(resetVideoUpload);
            document.querySelectorAll(".commentary-box").forEach(el => el.classList.add("hidden"));

        } catch (err) {
            console.error(err);
            alert("❌ Saqlashda xatolik");
        }
    });

});

document.addEventListener("change", (e) => {
    if (e.target.type !== "radio" || e.target.name !== "correct") return;

    const allAnswers = document.querySelectorAll(".answer");

    // скрываем всё — izoh faqat to'g'ri javobga tegishli bo'lishi kerak
    allAnswers.forEach(answer => {
        answer.querySelector(".comment-btn")?.classList.add("hidden");
        answer.querySelector(".commentary-box")?.classList.add("hidden");
    });

    // показываем кнопку только у выбранного
    const selectedAnswer = e.target.closest(".answer");
    selectedAnswer.querySelector(".comment-btn")?.classList.remove("hidden");
});

document.addEventListener("click", (e) => {
    if (!e.target.classList.contains("comment-btn")) return;

    const answer = e.target.closest(".answer");
    const box = answer.querySelector(".commentary-box");
    const textarea = answer.querySelector(".commentary");

    box?.classList.remove("hidden");
    textarea.focus();
});

function importExcel() {
    document.getElementById("excelFile").click();
}

document.getElementById("excelFile").addEventListener("change", async function () {
    const file = this.files[0];
    if (!file) return;

    const topicId = document.getElementById("topicId").value;

    const formData = new FormData();
    formData.append("file", file);
    formData.append("topicId", topicId);

    const res = await fetch("/api/import/excel", {
        method: "POST",
        body: formData
    });

    const data = await res.json();
    showResult(data);
});

function showResult(data) {
    const modal = document.getElementById("importModal");
    const title = document.getElementById("importTitle");
    const body = document.getElementById("importBody");

    if (data.success) {
        title.textContent = "Import successful";
        body.textContent = `Imported: ${data.imported} questions`;
    } else {
        title.textContent = "Import errors";
        body.textContent = data.errors.join("\n");
    }

    modal.classList.remove("hidden");
}

function closeModal() {
    document.getElementById("importModal").classList.add("hidden");
}

function downloadTemplate() {
    window.location.href = "/api/export/template";
}

