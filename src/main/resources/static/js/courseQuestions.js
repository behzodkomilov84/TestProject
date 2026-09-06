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
    } catch (e) {
        console.error(e);
        container.innerHTML = `<p class="cq-error">❌ Savollarni yuklashda xatolik yuz berdi.</p>`;
    }
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
                <button type="button" class="cq-section-header" onclick="toggleSection(${sectionId})">
                    <span class="cq-chevron" id="cqChevron-${sectionId}">▶</span>
                    📁 ${escapeHtml(section.title)}
                    <span class="cq-count">${questionsToShow.length} ta test</span>
                </button>
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
