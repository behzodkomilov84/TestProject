// "📋 Barcha savollarni ko'rish" — kurs detail sahifasidagi tugma orqali
// ochiladi (courseDetail.html). Kirish huquqi (ADMIN — faqat o'zi
// yaratgan kurs, OWNER — barchasi) BUTUNLAY API darajasida (Course
// Controller#getQuestionsForCourse -> CourseService#requireManageableCourse)
// tekshiriladi — bu sahifa 403 kelsa shunchaki xabar ko'rsatadi.
//
// Katta kurslarda (masalan minglab test) BARCHA savolni bir zumda flat
// jadval qilib chizish sahifani sekinlashtiradi — shu sabab har bir DARS
// alohida "accordion" bo'limi sifatida, YOPIQ holda boshlanadi (faqat
// sarlavha + son ko'rinadi), foydalanuvchi bosgandagina o'sha darsning
// jadvali chiziladi. Qidiruv FAOL bo'lganda mos darslar avtomatik ochiladi.

const ANSWER_LETTERS = ["A", "B", "C", "D", "E"];

// Haqiqiy Excel/Word ilova belgilariga o'xshash SVG — topic.js/science.js
// bilan bir xil andoza (barcha eksport tugmalarida bir xil belgi).
const EXCEL_ICON_SVG = `<svg width="24" height="24" viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">
    <rect x="4" y="4" width="40" height="40" rx="7" fill="#107C41"/>
    <rect x="4" y="4" width="18" height="40" rx="7" fill="#0B5C31"/>
    <g stroke="#fff" stroke-width="4" stroke-linecap="round">
        <line x1="14" y1="16" x2="30" y2="32"/>
        <line x1="30" y1="16" x2="14" y2="32"/>
    </g>
</svg>`;

const WORD_ICON_SVG = `<svg width="24" height="24" viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">
    <rect x="4" y="4" width="40" height="40" rx="7" fill="#185ABD"/>
    <rect x="4" y="4" width="18" height="40" rx="7" fill="#103F91"/>
    <text x="31" y="30" font-family="Arial, sans-serif" font-size="20" font-weight="bold" fill="#fff" text-anchor="middle">W</text>
</svg>`;

let allQuestions = []; // flat CourseQuestionDto ro'yxati (backenddan)
let sectionsMap = new Map(); // courseSectionId -> {title, topicId, questions}
let searchQuery = "";

document.addEventListener("DOMContentLoaded", () => {
    loadCourseQuestions();

    document.getElementById("cqSearchInput").addEventListener("input", (e) => {
        searchQuery = e.target.value;
        renderSections();
    });
});

async function loadCourseQuestions() {
    const container = document.getElementById("cqSectionsList");

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/questions`);

        if (res.status === 403) {
            container.innerHTML = `<p class="cq-error">⛔ Bu kursni ko'rish huquqingiz yo'q — faqat o'zingiz yaratgan kurslarning savollarini ko'rishingiz mumkin.</p>`;
            return;
        }
        if (!res.ok) throw new Error("Server xatoligi: " + res.status);

        allQuestions = await res.json();
        buildSectionsMap();
        renderSections();
        renderExportButtons();
    } catch (e) {
        console.error(e);
        container.innerHTML = `<p class="cq-error">❌ Savollarni yuklashda xatolik yuz berdi.</p>`;
    }
}

// Shu KURSga bog'langan BARCHA darslarning savollarini bitta faylga
// yig'ib eksport qilish tugmalari (foydalanuvchi so'rovi, 2026-09-06) —
// savol umuman bo'lmasa (bo'sh kurs) ko'rsatilmaydi.
function renderExportButtons() {
    const container = document.getElementById("cqExportButtons");
    if (allQuestions.length === 0) {
        container.innerHTML = "";
        return;
    }

    container.innerHTML = `
        <button type="button" class="cq-export-btn" onclick="exportAllToExcel()" title="Shu kursdagi BARCHA darslarning testlarini Excel'ga eksport qilish">${EXCEL_ICON_SVG}</button>
        <button type="button" class="cq-export-btn" onclick="exportAllToWord()" title="Shu kursdagi BARCHA darslarning testlarini Word'ga eksport qilish">${WORD_ICON_SVG}</button>
    `;
}

function exportAllToExcel() {
    window.location.href = `/api/courses/${COURSE_ID}/export/questions/excel`;
}

function exportAllToWord() {
    window.location.href = `/api/courses/${COURSE_ID}/export/questions/word-simple`;
}

// Shu bitta darsning (Topic) testlarini eksport qilish — question.js/
// topic.js'dagi BILAN BIR XIL, umumiy (kurs bilan bog'liq bo'lmagan)
// endpointlar, chunki bu sahifaga kirish huquqi ALLAQACHON kurs
// darajasida tekshirilgan (getQuestionsForCourse muvaffaqiyatli
// bo'lmasa, bu tugmalar umuman ko'rinmaydi).
function exportSectionToExcel(topicId) {
    window.location.href = `/api/export/questions?topicId=${topicId}`;
}

function exportSectionToWord(topicId) {
    window.location.href = `/api/export/questions/word?topicId=${topicId}`;
}

// Flat ro'yxatni DARS (courseSectionId) bo'yicha guruhlaydi — har bir
// guruh o'zining nomi, bog'langan Topic id'si (👁️/✏️ tugmalari uchun)
// va shu darsga tegishli savollar ro'yxatini saqlaydi.
function buildSectionsMap() {
    sectionsMap = new Map();
    allQuestions.forEach(cq => {
        if (!sectionsMap.has(cq.courseSectionId)) {
            sectionsMap.set(cq.courseSectionId, {
                title: cq.courseSectionTitle,
                topicId: cq.topicId,
                questions: []
            });
        }
        sectionsMap.get(cq.courseSectionId).questions.push(cq.question);
    });
}

function renderSections() {
    const container = document.getElementById("cqSectionsList");
    const summary = document.getElementById("cqSummary");

    summary.textContent = `Jami ${allQuestions.length} ta test, ${sectionsMap.size} ta darsda`;

    if (allQuestions.length === 0) {
        container.innerHTML = `<p class="cq-empty">Bu kursga bog'langan hech qanday test yo'q.</p>`;
        return;
    }

    const q = searchQuery.trim().toLowerCase();
    let html = "";
    const sectionIdsToOpen = [];

    for (const [sectionId, section] of sectionsMap.entries()) {
        const sectionTitleMatches = q && section.title.toLowerCase().includes(q);
        const matchingQuestions = q
            ? section.questions.filter(qq => qq.questionText.toLowerCase().includes(q))
            : section.questions;

        // Qidiruv faol bo'lganda — na dars nomi, na savollari mos kelmasa,
        // shu dars umuman ko'rsatilmaydi.
        if (q && !sectionTitleMatches && matchingQuestions.length === 0) continue;

        // Dars NOMI mos kelgan, lekin savol matnidan hech biri mos kelmasa —
        // shu darsning BARCHA savollari ko'rsatiladi (foydalanuvchi aynan
        // shu darsni qidirgan bo'lishi mumkin).
        const questionsToShow = (sectionTitleMatches && matchingQuestions.length === 0)
            ? section.questions
            : matchingQuestions;

        if (q) sectionIdsToOpen.push(sectionId);

        html += `
            <div class="cq-section">
                <div class="cq-section-header-row">
                    <button type="button" class="cq-section-header" onclick="toggleSection(${sectionId})">
                        <span class="cq-chevron" id="cqChevron-${sectionId}">▶</span>
                        📁 ${escapeHtml(section.title)}
                    </button>
                    <!-- Shu bitta darsning testlarini alohida eksport
                         qilish (foydalanuvchi so'rovi, 2026-09-06) —
                         topic.js#exportTopicQuestions/openWordExportModal
                         bilan bir xil oddiy (izohsiz) eksport endpointlari,
                         chunki bu dars ANIQ shu Topic'ga bog'langan. -->
                    <button type="button" class="cq-section-export-btn" onclick="event.stopPropagation(); exportSectionToExcel(${section.topicId})" title="Shu darsdagi testlarni Excel'ga eksport qilish">${EXCEL_ICON_SVG}</button>
                    <button type="button" class="cq-section-export-btn" onclick="event.stopPropagation(); exportSectionToWord(${section.topicId})" title="Shu darsdagi testlarni Word'ga eksport qilish">${WORD_ICON_SVG}</button>
                    <span class="cq-count">${questionsToShow.length} ta test</span>
                </div>
                <div class="cq-section-body hidden" id="cqBody-${sectionId}">
                    ${buildQuestionsTable(questionsToShow, section.topicId)}
                </div>
            </div>
        `;
    }

    container.innerHTML = html || `<p class="cq-empty">Hech narsa topilmadi.</p>`;

    // Qidiruv faol bo'lganda — mos darslar avtomatik ochiq holda ko'rsatiladi.
    sectionIdsToOpen.forEach(sectionId => {
        const body = document.getElementById(`cqBody-${sectionId}`);
        const chevron = document.getElementById(`cqChevron-${sectionId}`);
        if (body) body.classList.remove("hidden");
        if (chevron) chevron.textContent = "▼";
    });
}

function buildQuestionsTable(questions, topicId) {
    if (questions.length === 0) {
        return `<p class="cq-empty">Mos test topilmadi.</p>`;
    }

    return `
        <table class="cq-table">
            <thead>
                <tr>
                    <th>№</th>
                    <th>Savol</th>
                    ${ANSWER_LETTERS.map(l => `<th>${l}</th>`).join("")}
                    <th>To'g'ri</th>
                    <th>Amallar</th>
                </tr>
            </thead>
            <tbody>
                ${questions.map((qq, i) => buildQuestionRow(qq, i, topicId)).join("")}
            </tbody>
        </table>
    `;
}

function buildQuestionRow(q, index, topicId) {
    const answers = (q.answers || []).slice(0, 5);
    const correctLetters = answers
        .map((a, i) => a.isTrue ? ANSWER_LETTERS[i] : null)
        .filter(Boolean)
        .join(",");

    return `
        <tr>
            <td class="cq-num">${index + 1}</td>
            <td class="cq-question-text">${escapeHtml(q.questionText)}</td>
            ${ANSWER_LETTERS.map((_, i) => {
                const a = answers[i];
                if (!a) return `<td></td>`;
                return `<td class="${a.isTrue ? "cq-correct" : ""}">${escapeHtml(a.answerText)}</td>`;
            }).join("")}
            <td class="cq-correct-letter"><b>${correctLetters || "-"}</b></td>
            <td class="cq-actions">
                <button type="button" class="cq-action-btn" onclick="viewCourseQuestion(${topicId}, ${q.id})" title="Savolni ko'rish">👁️</button>
                <button type="button" class="cq-action-btn" onclick="editCourseQuestion(${topicId}, ${q.id})" title="Savolni tahrirlash">✏️</button>
            </td>
        </tr>
    `;
}

function toggleSection(sectionId) {
    const body = document.getElementById(`cqBody-${sectionId}`);
    const chevron = document.getElementById(`cqChevron-${sectionId}`);
    const isHidden = body.classList.toggle("hidden");
    chevron.textContent = isHidden ? "▶" : "▼";
}

// "👁️ Ko'rish"/"✏️ Tahrirlash" — question.js'ga to'g'ridan-to'g'ri
// o'tkazadi, aynan shu savolga qaratilgan holda (science.js
// #viewScienceSearchResult/#editScienceSearchResult bilan bir xil g'oya —
// question.js#handleIncomingFocusOrEdit "?focus="/"?edit=" ni o'qiydi).
function viewCourseQuestion(topicId, questionId) {
    window.location.href = `/question?topicId=${topicId}&focus=${questionId}`;
}

function editCourseQuestion(topicId, questionId) {
    window.location.href = `/question?topicId=${topicId}&edit=${questionId}`;
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text ?? "";
    return div.innerHTML;
}
