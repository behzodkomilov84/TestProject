// "🔗 Darsga havola qo'shish" — bir nechta sahifada ishlatiladi (test
// yaratish formasi — test-form.js, va savollar jadvalidagi izohni
// tahrirlash oynasi — question.js). Joriy darsga (topicId) bog'langan
// kurs mavzusi (CourseChapter) mavjud bo'lsa, izohga ALOHIDA ko'rinishdagi
// (rang/format bilan boshqa matndan ajralib turadigan, "belgi/badge"
// ko'rinishidagi) havola qo'shish imkonini beradi — o'qituvchi HTML
// tegini qo'lda yozmaydi, faqat tugmani bosadi.

// Diqqat: bu yerdagi rang/format o'zgarsa, avval import qilingan
// (bulk-import) savollardagi eski (formatlanmagan) havolalar bilan
// ko'rinish farq qilishi mumkin — ularni ham shu formatga keltirish
// alohida (bir martalik) skript orqali qilinadi.
const TOPIC_LINK_BADGE_STYLE =
    "display:inline-block;margin-top:6px;padding:4px 10px 4px 8px;" +
    "background:#e8f5f3;border-left:3px solid #00796b;border-radius:4px;" +
    "color:#00695c;font-weight:600;font-style:normal;text-decoration:none";
const TOPIC_LINK_ANCHOR_STYLE = "color:#00695c;text-decoration:underline";

async function fetchTopicCourseLink(topicId) {
    if (!topicId) return null;

    try {
        const res = await fetch(`/api/topic/${topicId}/course-link`);
        if (!res.ok) return null; // 404 — dars hech qaysi mavzuga bog'lanmagan
        return await res.json();
    } catch (err) {
        console.error(err);
        return null;
    }
}

function buildTopicLinkHtml(courseLink) {
    const url = `/courses/${courseLink.courseId}/sections/${courseLink.sectionId}`;
    const title = courseLink.topicName.replace(/"/g, "&quot;");
    return ` <span style="${TOPIC_LINK_BADGE_STYLE}">📖 <a href="${url}" style="${TOPIC_LINK_ANCHOR_STYLE}">"${title}" darsini kursda o'qish</a></span>`;
}

function insertTextAtCursor(textarea, text) {
    const start = textarea.selectionStart ?? textarea.value.length;
    const end = textarea.selectionEnd ?? textarea.value.length;

    textarea.value = textarea.value.slice(0, start) + text + textarea.value.slice(end);

    const pos = start + text.length;
    textarea.focus();
    textarea.setSelectionRange(pos, pos);
}
