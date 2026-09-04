// ========================================================================
//                     Global fields
// ========================================================================

let itemBlock = []; // сюда будут загружены данные из БД
let deletedTopicIds = []; // FRONTEND da o'chirilganlarni id'si (Agar u DB da ham bo'lsa)
let focusIndex = null;//для курсора

let oldName = ""; //for EDIT uses
let newName = ""; //for EDIT uses

// Haqiqiy Excel ilovasi belgisiga o'xshash SVG (yashil hujjat + oq "X") —
// "📊 Excel'ga eksport" tugmalarida emoji o'rniga ishlatiladi (foydalanuvchi
// so'rovi bo'yicha — barcha eksport tugmalarida bir xil belgi).
const EXCEL_ICON_SVG = `<svg width="28" height="28" viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">
    <rect x="4" y="4" width="40" height="40" rx="7" fill="#107C41"/>
    <rect x="4" y="4" width="18" height="40" rx="7" fill="#0B5C31"/>
    <g stroke="#fff" stroke-width="4" stroke-linecap="round">
        <line x1="14" y1="16" x2="30" y2="32"/>
        <line x1="30" y1="16" x2="14" y2="32"/>
    </g>
</svg>`;

// Haqiqiy Word ilovasi belgisiga o'xshash SVG (ko'k hujjat + oq "W") —
// "📝 Word'ga eksport" tugmalarida — EXCEL_ICON_SVG bilan bir xil andoza.
const WORD_ICON_SVG = `<svg width="28" height="28" viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg">
    <rect x="4" y="4" width="40" height="40" rx="7" fill="#185ABD"/>
    <rect x="4" y="4" width="18" height="40" rx="7" fill="#103F91"/>
    <text x="31" y="30" font-family="Arial, sans-serif" font-size="20" font-weight="bold" fill="#fff" text-anchor="middle">W</text>
</svg>`;

// Bo'lim ichidagi Mavzular (TopicSection) ro'yxati — "— Mavzusiz —"
// varianti bilan birga tanlash dropdown'ini to'ldirish uchun (loadSections()).
let sectionList = [];

// Mavzu guruhlari (TopicSection) sahifasidan (Bo'lim -> Mavzu -> Dars)
// kelinganda URL'da "sectionId" beriladi — shu Mavzuga tegishli darslar
// FAQAT KO'RSATILADI (itemBlock'ning o'zi to'liq qoladi — dublikat
// nom tekshiruvi butun Bo'lim bo'yicha bo'lishi kerak, faqat bitta
// Mavzu ichida emas, chunki DB'da unique(science_id, name)).
const filterSectionId = new URLSearchParams(window.location.search).get("sectionId");

// science.js/topicSection.js'dan "&fieldId=..." bilan kelgan bo'lsa — shu
// Yo'nalish qamrovi "Orqaga"/"📂 Mavzular" havolalarida saqlab qolinadi.
const pageFieldId = new URLSearchParams(window.location.search).get("fieldId");
const fieldQuery = pageFieldId != null ? `&fieldId=${pageFieldId}` : "";

// "🔗 Kursga bog'lanmagan mavzular" filtri (toggleUnlinkedFilter) — yoqilsa
// faqat linkedCourseTitle'i YO'Q qatorlar ko'rsatiladi (render()).
let showOnlyUnlinkedTopics = false;

// question.html'dagi "← MAVZUGA QAYTISH" tugmasi (question.js#goBack)
// aynan qaysi mavzudan kelingani "?focus=" orqali beradi — sahifa
// ochilganda ANIQ shu mavzu qatoriga fokus tushishi uchun (courseDetail.js/
// science.js'dagi bir xil "?focus=" g'oyasi bilan bir xil).
const filterFocusId = new URLSearchParams(window.location.search).get("focus");
// ========================================================================

const scienceId = getScienceId();

if (!scienceId) {
    alert("❌ scienceId topilmadi (HTML dan)");
} else {
    loadSections().then(() => {
        showSectionFilterBanner();
        afterStartPage(`/api/topic?scienceId=${scienceId}`);
    });
    refreshTopicTrashBadge();
    refreshQuestionScienceTrashBadge();
}

// Badge'ni (".notif-badge" — navbar.js#refreshUnreadCount bilan bir xil
// uslub) sonini yangilaydi — 0 bo'lsa yashiradi. Bir nechta sahifada
// (question.js/science.js/...) bir xil andoza bilan takrorlanadi —
// mustaqil kichik JS fayllar bo'lgani uchun ataylab nusxalangan.
function setTrashBadgeCount(badgeId, count) {
    const badge = document.getElementById(badgeId);
    if (!badge) return;
    if (count > 0) {
        badge.style.display = "inline-flex";
        badge.textContent = count > 99 ? "99+" : count;
    } else {
        badge.style.display = "none";
    }
}

function refreshTopicTrashBadge() {
    fetch(`/api/topic/deleted?scienceId=${scienceId}`)
        .then(r => r.ok ? r.json() : [])
        .then(items => setTrashBadgeCount("topicTrashBadge", items.length))
        .catch(err => console.error(err));
}

function refreshQuestionScienceTrashBadge() {
    fetch(`/api/question/deleted-by-science?scienceId=${scienceId}`)
        .then(r => r.ok ? r.json() : [])
        .then(items => setTrashBadgeCount("questionScienceTrashBadge", items.length))
        .catch(err => console.error(err));
}

// "🔗 Kursga bog'lanmagan mavzular" filtri — yoqilganda tugma "faol"
// ko'rinishga o'tadi (unlinked-filter-btn.active, science.css) va faqat
// linkedCourseTitle'i yo'q qatorlar qoladi (render()).
function toggleUnlinkedFilter() {
    showOnlyUnlinkedTopics = !showOnlyUnlinkedTopics;
    document.getElementById("unlinkedFilterBtn").classList.toggle("active", showOnlyUnlinkedTopics);
    render();
}

// Nechta mavzu hali kursga bog'lanmaganini ko'rsatadi — itemBlock
// allaqachon frontendda yuklangani uchun (linkedCourseTitle bilan birga),
// alohida fetch shart emas, oddiy sanash yetarli.
function refreshUnlinkedTopicBadge() {
    const count = itemBlock.filter(s => s.id > 0 && !s.linkedCourseTitle).length;
    setTrashBadgeCount("unlinkedTopicBadge", count);
}

// "Mavzular" sarlavhasi yonida — nechta mavzu bor, nechtasi kursga
// bog'langan, nechtasi bog'lanmagan (render()'da chaqiriladi). Bo'lim
// ustidan (filterSectionId) kelingan bo'lsa — faqat SHU bo'lim mavzulari
// bo'yicha hisoblaydi (ro'yxatda haqiqatan ko'rinayotgan qatorlarga mos
// bo'lishi uchun) — "🔗 Kursga bog'lanmagan mavzular" filtrining o'zi
// (showOnlyUnlinkedTopics) esa E'TIBORGA OLINMAYDI, aks holda filtr
// yoqilganda sarlavha ham har doim "0 ta bog'langan" deb ko'rsatib
// chalg'itardi.
function updateTopicsSummary() {
    const el = document.getElementById("topicsSummary");
    if (!el) return;

    const relevant = itemBlock.filter(s =>
        s.id > 0 && (!filterSectionId || Number(s.sectionId) === Number(filterSectionId)));

    if (relevant.length === 0) {
        el.textContent = "";
        return;
    }

    const linked = relevant.filter(s => s.linkedCourseTitle).length;
    const unlinked = relevant.length - linked;
    el.textContent = ` (${relevant.length} ta — ${linked} tasi kursga bog'langan, ${unlinked} tasi bog'lanmagan)`;
}

function showSectionFilterBanner() {
    const banner = document.getElementById("sectionFilterBanner");
    if (!filterSectionId) {
        banner.classList.add("hidden");
        return;
    }
    const name = sectionNameById(filterSectionId) || "Mavzu";
    banner.innerHTML = `🔎 <b>${escapeHtml(name)}</b> darslari ko'rsatilmoqda — ` +
        `<a href="/topics?scienceId=${scienceId}">barcha darslarni ko'rish</a>`;
    banner.classList.remove("hidden");
}

async function loadSections() {
    try {
        const response = await fetch(`/api/topic-section?scienceId=${scienceId}`);
        if (!response.ok) throw new Error(`Server error: ${response.status}`);
        sectionList = await response.json();
    } catch (err) {
        console.error("Mavzularni yuklashda xatolik:", err);
        sectionList = [];
    }
}

function sectionOptionsHtml(selectedSectionId) {
    let options = `<option value="" ${!selectedSectionId ? "selected" : ""}>— Mavzusiz —</option>`;
    sectionList.forEach(sec => {
        const selected = Number(selectedSectionId) === Number(sec.id) ? "selected" : "";
        options += `<option value="${sec.id}" ${selected}>${escapeHtml(sec.name)}</option>`;
    });
    return options;
}

function sectionNameById(sectionId) {
    if (!sectionId) return null;
    const found = sectionList.find(sec => Number(sec.id) === Number(sectionId));
    return found ? found.name : null;
}



// ========================================================================
//                      Functions
// ========================================================================

function getScienceId() {
    const element = document.getElementById("scienceId");
    return element ? element.value : null;
}

// "🗑️ Testi yo'q mavzularni o'chirish" — shu Fanda hech qanday savoli
// bo'lmagan (questionCount==0) BARCHA mavzularni bir yo'la o'chiradi.
// Kursga bog'langan mavzular backend tomonidan avtomatik chetlab
// o'tiladi (TopicService.deleteQuestionlessTopics).
async function deleteQuestionlessTopics() {
    if (itemBlock.some(s => s.mode !== "VIEW")) {
        alert("❌ Avval tahrirlashni yakuniga yetkazing (yoki saqlang)!");
        return;
    }

    const candidateCount = itemBlock.filter(s => s.id > 0 && (s.questionCount || 0) === 0).length;
    if (candidateCount === 0) {
        alert("ℹ️ Testi yo'q dars topilmadi.");
        return;
    }

    if (!confirm(`⚠️ Testi yo'q ${candidateCount} ta darsni o'chirmoqchimisiz?\n\n(Kursga bog'langan darslar, agar bo'lsa, avtomatik chetlab o'tiladi.)\n\nBu amalni bekor qilib bo'lmaydi.`)) {
        return;
    }

    try {
        const res = await fetch(`/api/topic/questionless?scienceId=${getScienceId()}`, { method: "DELETE" });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            alert(data.error || "O'chirishda xatolik");
            return;
        }
        showToast('success', `✅ ${data.deleted} ta dars o'chirildi`, 4000);
        await reloadFromDb(`/api/topic?scienceId=${getScienceId()}`);
        focusIndex = 0;
        render();
        refreshTopicTrashBadge();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

// "🗑️ O'chirilgan mavzular" paneli — soft-delete qilingan Topic'lar
// ro'yxati (bir zumda "♻️ Tiklash" qilinadigan). Panel yopiq holatda
// boshlanadi, bosilganda ochilib ro'yxatni yuklaydi.
let topicTrashOpen = false;

function toggleTopicTrash() {
    topicTrashOpen = !topicTrashOpen;
    document.getElementById("topicTrashPanel").style.display = topicTrashOpen ? "block" : "none";
    if (topicTrashOpen) {
        loadTopicTrash();
    }
}

async function loadTopicTrash() {
    const list = document.getElementById("topicTrashList");
    list.innerHTML = "<p>Yuklanmoqda...</p>";

    try {
        const res = await fetch(`/api/topic/deleted?scienceId=${getScienceId()}`);
        if (!res.ok) {
            list.innerHTML = "<p>Yuklashda xatolik</p>";
            return;
        }
        const items = await res.json();
        setTrashBadgeCount("topicTrashBadge", items.length);
        if (!items.length) {
            list.innerHTML = "<p>O'chirilgan dars yo'q</p>";
            return;
        }
        list.innerHTML = items.map(t => `
            <div class="row">
                <div>${escapeHtml(t.name)} (${t.questionCount} ta test) — ${formatTopicTrashDate(t.deletedAt)}da o'chirilgan</div>
                <div class="row-actions">
                    <button onclick="restoreTopic(${t.id})">♻️ Tiklash</button>
                    <button class="danger-btn" onclick="permanentlyDeleteTopic(${t.id}, ${JSON.stringify(t.name).replace(/"/g, "&quot;")})">🗑️ Butunlay o'chirish</button>
                </div>
            </div>
        `).join("");
    } catch (err) {
        console.error(err);
        list.innerHTML = "<p>Tarmoq xatoligi</p>";
    }
}

function formatTopicTrashDate(isoString) {
    if (!isoString) return "";
    const d = new Date(isoString);
    return d.toLocaleDateString("uz-UZ") + " " + d.toLocaleTimeString("uz-UZ", { hour: "2-digit", minute: "2-digit" });
}

async function restoreTopic(topicId) {
    if (!confirm("Bu darsni tiklamoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/topic/${topicId}/restore`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "Tiklashda xatolik");
            return;
        }
        loadTopicTrash();
        await reloadFromDb(`/api/topic?scienceId=${scienceId}`);
        render();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

async function permanentlyDeleteTopic(topicId, name) {
    if (!confirm(`⚠️ "${name}" mavzusini BUTUNLAY (savollari bilan birga) o'chirmoqchimisiz?\n\nBu amalni HECH QANDAY tarzda bekor qilib bo'lmaydi.`)) return;
    if (!confirm("Haqiqatan ham ishonchingiz komilmi?")) return;

    try {
        const res = await fetch(`/api/topic/${topicId}/permanent`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "O'chirishda xatolik");
            return;
        }
        loadTopicTrash();
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

// ========================================================================
//     "🗑️ Barcha o'chirilgan testlar" — BUTUN FAN bo'yicha (barcha
//     mavzular birga) o'chirilgan savollar ro'yxati, har biri qaysi
//     mavzu/bo'limga tegishli ekani bilan. question.html'dagi ALOHIDA
//     (bitta mavzu doirasidagi) savatdan farqli — bu yerda butun fan
//     ko'lamida bitta joyda ko'rish/tiklash/butunlay o'chirish.
// ========================================================================
let questionScienceTrashOpen = false;
let selectedScienceTrashQuestionIds = new Set();

function toggleQuestionScienceTrash() {
    questionScienceTrashOpen = !questionScienceTrashOpen;
    document.getElementById("questionScienceTrashPanel").style.display = questionScienceTrashOpen ? "block" : "none";
    if (questionScienceTrashOpen) {
        loadQuestionScienceTrash();
    }
}

async function loadQuestionScienceTrash() {
    const list = document.getElementById("questionScienceTrashList");
    list.innerHTML = "<p>Yuklanmoqda...</p>";
    selectedScienceTrashQuestionIds.clear();

    try {
        const res = await fetch(`/api/question/deleted-by-science?scienceId=${getScienceId()}`);
        if (!res.ok) {
            list.innerHTML = "<p>Yuklashda xatolik</p>";
            return;
        }
        const items = await res.json();
        setTrashBadgeCount("questionScienceTrashBadge", items.length);
        if (!items.length) {
            list.innerHTML = "<p>O'chirilgan test yo'q</p>";
            return;
        }
        list.innerHTML = `
            <div class="trash-bulk-actions">
                <label><input type="checkbox" id="selectAllScienceTrashCheckbox" onchange="toggleSelectAllScienceTrash(this)"> Hammasini belgilash</label>
                <button id="bulkRestoreScienceTrashBtn" class="restore-bulk-btn hidden" onclick="restoreSelectedScienceTrashQuestions()">♻️ Tanlanganlarni tiklash (<span id="bulkRestoreScienceTrashCount">0</span>)</button>
                <button id="bulkPermanentDeleteScienceTrashBtn" class="bulk-delete-btn hidden" onclick="permanentlyDeleteSelectedScienceTrashQuestions()">🗑️ Tanlanganlarni BUTUNLAY o'chirish (<span id="bulkPermanentDeleteScienceTrashCount">0</span>)</button>
            </div>
            ${items.map(q => {
                // Qaysi mavzu va (bo'lsa) qaysi bo'limga tegishli ekani —
                // foydalanuvchi so'rovi bo'yicha aynan shu batafsillik
                // ko'rsatilishi kerak edi.
                const location = q.sectionName
                    ? `${escapeHtml(q.sectionName)} → ${escapeHtml(q.topicName)}`
                    : escapeHtml(q.topicName);
                return `
            <div class="row">
                <input type="checkbox" class="science-trash-select-checkbox" data-question-id="${q.id}" onchange="onScienceTrashCheckboxChange(${q.id}, this)">
                <div>
                    ${escapeHtml(q.questionText)}<br>
                    <span class="topic-section-badge">${location}</span>
                    <span style="color:#94a3b8; font-size:12px;"> — ${formatQuestionTrashDate(q.deletedAt)}da o'chirilgan</span>
                </div>
                <div class="row-actions">
                    <button onclick="restoreScienceTrashQuestion(${q.id})">♻️ Tiklash</button>
                    <button class="danger-btn" onclick="permanentlyDeleteScienceTrashQuestion(${q.id})">🗑️ Butunlay o'chirish</button>
                </div>
            </div>
        `;
            }).join("")}`;
        updateScienceTrashBulkButtons();
    } catch (err) {
        console.error(err);
        list.innerHTML = "<p>Tarmoq xatoligi</p>";
    }
}

// "N ta test" / "🗑️ N ta savatda" belgilari joriy holatga mos bo'lishi
// uchun mavzular ro'yxatini ham qayta yuklaydi (reloadFromDb + render).
async function refreshTopicsAfterScienceTrashChange() {
    await reloadFromDb(`/api/topic?scienceId=${scienceId}`);
    render();
}

function formatQuestionTrashDate(isoString) {
    if (!isoString) return "";
    const d = new Date(isoString);
    return d.toLocaleDateString("uz-UZ") + " " + d.toLocaleTimeString("uz-UZ", { hour: "2-digit", minute: "2-digit" });
}

function onScienceTrashCheckboxChange(questionId, checkbox) {
    if (checkbox.checked) {
        selectedScienceTrashQuestionIds.add(questionId);
    } else {
        selectedScienceTrashQuestionIds.delete(questionId);
        const selectAll = document.getElementById("selectAllScienceTrashCheckbox");
        if (selectAll) selectAll.checked = false;
    }
    updateScienceTrashBulkButtons();
}

function toggleSelectAllScienceTrash(selectAllCheckbox) {
    document.querySelectorAll("#questionScienceTrashList .science-trash-select-checkbox").forEach((cb) => {
        cb.checked = selectAllCheckbox.checked;
        const questionId = Number(cb.dataset.questionId);
        if (selectAllCheckbox.checked) {
            selectedScienceTrashQuestionIds.add(questionId);
        } else {
            selectedScienceTrashQuestionIds.delete(questionId);
        }
    });
    updateScienceTrashBulkButtons();
}

function updateScienceTrashBulkButtons() {
    const count = selectedScienceTrashQuestionIds.size;

    const restoreBtn = document.getElementById("bulkRestoreScienceTrashBtn");
    if (restoreBtn) {
        document.getElementById("bulkRestoreScienceTrashCount").textContent = String(count);
        restoreBtn.classList.toggle("hidden", count === 0);
    }

    const deleteBtn = document.getElementById("bulkPermanentDeleteScienceTrashBtn");
    if (deleteBtn) {
        document.getElementById("bulkPermanentDeleteScienceTrashCount").textContent = String(count);
        deleteBtn.classList.toggle("hidden", count === 0);
    }
}

async function restoreScienceTrashQuestion(questionId) {
    if (!confirm("Bu testni tiklamoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/question/${questionId}/restore`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "Tiklashda xatolik");
            return;
        }
        loadQuestionScienceTrash();
        await refreshTopicsAfterScienceTrashChange();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

async function permanentlyDeleteScienceTrashQuestion(questionId) {
    if (!confirm("⚠️ Bu testni BUTUNLAY o'chirmoqchimisiz?\n\nBu amalni HECH QANDAY tarzda bekor qilib bo'lmaydi.")) return;
    if (!confirm("Haqiqatan ham ishonchingiz komilmi?")) return;

    try {
        const res = await fetch(`/api/question/${questionId}/permanent`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "O'chirishda xatolik");
            return;
        }
        loadQuestionScienceTrash();
        await refreshTopicsAfterScienceTrashChange();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

async function restoreSelectedScienceTrashQuestions() {
    const ids = [...selectedScienceTrashQuestionIds];
    if (!ids.length) return;

    if (!confirm(`${ids.length} ta testni tiklamoqchimisiz?`)) return;

    try {
        const res = await fetch("/api/question/bulk/restore", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(ids)
        });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            throw new Error(data.error || "Tiklashda xatolik");
        }
        selectedScienceTrashQuestionIds.clear();
        loadQuestionScienceTrash();
        await refreshTopicsAfterScienceTrashChange();
    } catch (err) {
        console.error(err);
        alert(err.message || "Tarmoq xatoligi");
    }
}

async function permanentlyDeleteSelectedScienceTrashQuestions() {
    const ids = [...selectedScienceTrashQuestionIds];
    if (!ids.length) return;

    if (!confirm(`⚠️ ${ids.length} ta testni BUTUNLAY o'chirmoqchimisiz?\n\nBu amalni HECH QANDAY tarzda bekor qilib bo'lmaydi.`)) {
        return;
    }
    if (!confirm("Haqiqatan ham ishonchingiz komilmi?")) {
        return;
    }

    try {
        const res = await fetch("/api/question/bulk/permanent", {
            method: "DELETE",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(ids)
        });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            throw new Error(data.error || "O'chirishda xatolik");
        }
        selectedScienceTrashQuestionIds.clear();
        loadQuestionScienceTrash();
        await refreshTopicsAfterScienceTrashChange();
    } catch (err) {
        console.error(err);
        alert(err.message || "Tarmoq xatoligi");
    }
}

// ========================================================================
//     Kurs ichidan mavzu yoritmasi bo'yicha qidiruv
// ========================================================================
// Shu sahifadagi (joriy bo'lim filtriga mos) mavzular qaysi kurs(lar)ga
// bog'langan bo'lsa, o'sha kurs(lar)ning BARCHA mavzuga bog'langan
// bo'limlaridagi matn darsi ("mavzu yoritmasi" — CourseSection.textContent)
// ichidan qidiradi (backend: CourseService.searchTopicExplanations).
// Bir nechta kursga bog'langan bo'lsa — BARCHA o'sha kurslar qidiriladi.
// Topilgan natijaga bosilsa — o'sha kurs bo'limining o'ziga o'tadi.
let explanationSearchTimeout = null;

document.getElementById("explanationSearchInput")?.addEventListener("input", (e) => {
    clearTimeout(explanationSearchTimeout);
    const query = e.target.value.trim();
    explanationSearchTimeout = setTimeout(() => runExplanationSearch(query), 400);
});

async function runExplanationSearch(query) {
    lastExplanationSearchQuery = query;
    const resultsEl = document.getElementById("explanationSearchResults");
    if (!query) {
        resultsEl.classList.add("hidden");
        resultsEl.innerHTML = "";
        return;
    }

    // Faqat SAHIFADAGI (joriy bo'lim filtriga mos, allaqachon bazaga
    // saqlangan) mavzular id'lari — render()dagi ko'rinish filtri bilan bir xil.
    const topicIds = itemBlock
        .filter(s => s.id > 0 && (!filterSectionId || Number(s.sectionId) === Number(filterSectionId)))
        .map(s => s.id);

    if (topicIds.length === 0) {
        resultsEl.classList.remove("hidden");
        resultsEl.innerHTML = `<div class="explanation-search-empty">Bu sahifada hech qanday mavzu yo'q</div>`;
        return;
    }

    try {
        const params = new URLSearchParams({ q: query });
        topicIds.forEach(id => params.append("topicIds", id));
        const res = await fetch(`/api/course-sections/search-explanations?${params}`);
        if (!res.ok) throw new Error("Qidiruvda xatolik");
        const results = await res.json();
        renderExplanationSearchResults(results);
    } catch (err) {
        console.error(err);
        resultsEl.classList.remove("hidden");
        resultsEl.innerHTML = `<div class="explanation-search-empty">❌ Qidirishda xatolik</div>`;
    }
}

// Oxirgi qidiruv natijalari va so'zi — goToExplanationResult() shundan
// o'qib, bosilgan natijaning BUTUN ro'yxati + qidirilgan so'zni
// sessionStorage'ga saqlaydi (courseSectionView.js#setupSearchNav
// "Oldingi/Keyingi natija" tugmalarini, courseSectionView.js#
// highlightSearchQuery esa mavzu matni ichida shu so'zni topib fonini
// o'zgartirishni shundan oladi).
let lastExplanationSearchResults = [];
let lastExplanationSearchQuery = "";

function renderExplanationSearchResults(results) {
    lastExplanationSearchResults = results;
    const resultsEl = document.getElementById("explanationSearchResults");
    resultsEl.classList.remove("hidden");

    if (!results.length) {
        resultsEl.innerHTML = `<div class="explanation-search-empty">Hech narsa topilmadi</div>`;
        return;
    }

    resultsEl.innerHTML = results.map((r, i) => `
        <button class="explanation-search-result-item" onclick="goToExplanationResult(${i})">
            <span class="explanation-search-result-topic">${escapeHtml(r.topicName)}</span>
            <span class="explanation-search-result-meta">${escapeHtml(r.courseTitle)} — ${escapeHtml(r.sectionTitle)}</span>
        </button>
    `).join("");
}

// Natijaga bosilganda — BUTUN natijalar ro'yxati + joriy index + qidirilgan
// so'z + qaysi sahifadan qidirilgani sessionStorage'ga saqlanadi
// (courseSectionView.js shundan o'qib, "Oldingi/Keyingi natija"/
// "Natijalarga qaytish" tugmalarini ko'rsatadi VA mavzu matni ichida shu
// so'zni topib fonini o'zgartiradi — qidiruvni qayta berishga hojat qolmaydi).
function goToExplanationResult(index) {
    const target = lastExplanationSearchResults[index];
    if (!target) return;
    sessionStorage.setItem("explanationSearchNav", JSON.stringify({
        results: lastExplanationSearchResults,
        index,
        query: lastExplanationSearchQuery,
        returnUrl: window.location.pathname + window.location.search
    }));
    location.href = `/courses/${target.courseId}/sections/${target.sectionId}`;
}

function afterStartPage(mapping) {
        reloadFromDb(mapping).then(r => {
            // "?focus=" URL'da bo'lsa — ANIQ shu mavzu id'siga mos qatorga
            // fokus tushadi (question.js'dan "← MAVZUGA QAYTISH" orqali
            // kelinganda). Topilmasa — eskicha birinchi qatorga.
            const focusFound = filterFocusId
                ? itemBlock.findIndex(s => Number(s.id) === Number(filterFocusId))
                : -1;
            focusIndex = focusFound !== -1 ? focusFound : 0;
            render();// отрисовать список с выделением
        });
} //DONE

async function reloadFromDb(mapping) {
    const response = await fetch(mapping);

    try {
        if (!response.ok) {
            throw new Error(`Server error: ${response.status}`);
        }
    } catch (err) {
        console.error('Yuklash xatosi:', err);
        showToast('error', `Mavzularni yuklashda xatolik`, 4000);
    }

    const data = await response.json();

    itemBlock = data.map(s => ({
        id: s.id,
        name: s.name,
        original: s.name,
        sectionId: s.sectionId || null,
        originalSectionId: s.sectionId || null,
        // Shu mavzu biror KURS bo'limiga bog'langan bo'lsa — o'sha kursning
        // nomi (render()'da "🔗 Kurs: ..." belgisi uchun). Faqat KO'RISH
        // maqsadida — bu yerdan tahrirlanmaydi (bog'lanish kurs tahrirlash
        // sahifasida, CourseSectionSaveDto.scienceName/topicName orqali
        // boshqariladi).
        linkedCourseTitle: s.linkedCourseTitle || null,
        // Shu mavzuda nechta FAOL savol borligi — render()'da "(N ta test)"
        // ko'rsatish uchun (savatdagi/o'chirilgan savollar bu songa
        // KIRMAYDI — alohida, quyidagi trashedQuestionCount'da).
        questionCount: s.questionCount || 0,
        // Shu mavzuning "O'chirilganlar savati"da nechta savoli borligi —
        // questionCount'dan ALOHIDA belgi sifatida ko'rsatiladi.
        trashedQuestionCount: s.trashedQuestionCount || 0,
        mode: "VIEW"
    }));

} //DONE

function render() {
    const list = document.getElementById("list");
    list.innerHTML = "";

    itemBlock.forEach((s, i) => {
        // "🔗 Kursga bog'lanmagan mavzular" filtri YOQILGANDA — butun FAN
        // bo'yicha qidiradi, bo'lim filtrini (filterSectionId) E'TIBORGA
        // OLMAYDI. Aks holda: "yetim" mavzu joriy filtrlangan bo'limdan
        // BOSHQA bo'limda (yoki bo'limsiz) bo'lsa, hisoblagichda (badge)
        // soni ko'rinib turib, ro'yxatning o'zi bo'sh chiqib qolardi —
        // haqiqiy topilgan bug (foydalanuvchi "ajratib bermadi" deb
        // xabar berdi).
        if (showOnlyUnlinkedTopics) {
            if (s.mode === "VIEW" && s.linkedCourseTitle) {
                return;
            }
        } else if (filterSectionId && s.mode === "VIEW" && Number(s.sectionId) !== Number(filterSectionId)) {
            // Bo'lim ustidan kelingan bo'lsa — faqat shu bo'limga tegishli
            // (yoki hali saqlanmagan NEW) qatorlar ko'rsatiladi. itemBlock'ning
            // o'zi to'liq qoladi (dublikat nom tekshiruvi butun fan bo'yicha
            // ishlashi kerak), shu sabab faqat CHIZISHDA o'tkazib yuboriladi.
            return;
        }

        const row = document.createElement("div");
        row.className = "row";

        const isView = s.mode === "VIEW";
        const isLink = isView && s.id !== null;
        const isNew = s.mode === "NEW";
        const placeholder = isNew ? 'placeholder="Yangi dars nomini kiriting"' : '';

        // Проверяем дубликаты для текущего элемента
        const hasDup = !isView && hasDuplicate(i, s.name);
        const inputClass = `
                                    ${isView ? 'view' : ''} 
                                    ${isLink ? 'link' : ''} 
                                    ${hasDup ? 'duplicate' : ''}
                                    `;
        // VIEW rejimida joriy bo'lim nomi qator boshida kichik belgi
        // (badge) sifatida ko'rsatiladi — bo'limsiz bo'lsa hech narsa
        // chiqmaydi.
        const sectionName = sectionNameById(s.sectionId);
        const sectionBadge = sectionName
            ? `<span class="topic-section-badge">${escapeHtml(sectionName)}</span>`
            : '';

        // Shu mavzu biror KURS bo'limiga bog'langan bo'lsa — kichik belgi
        // (topic.js#reloadFromDb linkedCourseTitle'ni to'ldiradi). Admin
        // shu mavzuni o'chirsa/nomini butunlay o'zgartirsa, kursdagi
        // bog'lanish "yetim" qolib ketishi mumkinligini eslatib turadi.
        const courseBadge = s.linkedCourseTitle
            ? `<span class="topic-course-badge" title="Bu dars kursga bog'langan">🔗 Kurs: ${escapeHtml(s.linkedCourseTitle)}</span>`
            : '';

        // "📊 Excel'ga eksport" — shu mavzudagi barcha faol testlarni
        // .xlsx fayl sifatida yuklab olish (question.html'dagi "📥
        // Excel'ga eksport" bilan bir xil endpoint — /api/export/questions).
        // Bo'lim/Kurs belgilaridan farqli, BARCHA mavzularda ko'rinadi
        // (savoli yo'q mavzuda ham — bosilsa shunchaki bo'sh fayl tushadi).
        const exportBtn = `<button class="topic-export-btn" onclick="event.stopPropagation(); exportTopicQuestions(${s.id})" title="Shu darsdagi testlarni Excel'ga eksport qilish">${EXCEL_ICON_SVG}</button>`;

        // "📝 Word'ga eksport" — shu mavzudagi testlarni chop etishga
        // tayyor .docx fayl sifatida (Excel eksport tugmasi yonida).
        const wordExportBtn = `<button class="topic-export-btn" onclick="event.stopPropagation(); openWordExportModal(${s.id})" title="Shu darsdagi testlarni Word'ga eksport qilish">${WORD_ICON_SVG}</button>`;

        // Bo'lim/Kurs belgilari — o'z alohida qatorida; mavzu NOMI esa
        // har doim YANGI qatordan boshlanadi, savol soni belgisi esa nom
        // bilan bir qatorda, lekin O'NG tomonda (ajralib turadigan fon
        // bilan) — hammasi bir-biriga "yopishib" ketmasin deb (item-
        // badges/item-title-row, science.css). Export tugmasi bo'lgani
        // uchun endi HAR DOIM ko'rsatiladi (Bo'lim/Kurs belgisi
        // bo'lmasa ham).
        const badgesRow = `<div class="item-badges">${sectionBadge}${courseBadge}<div class="topic-export-btn-group">${exportBtn}${wordExportBtn}</div></div>`;

        row.innerHTML = `
    ${
            isView
                ? `
            <div
            class="row-view"
            tabindex="0"
            ondblclick="openQuestions(${s.id})"
            onkeydown="onViewKeyDown(event, ${i})"
            title="Enter — Саволларни очиш | ↑ ↓ — навигация | Home/End — биринчи/охирги"
        >
            <div
                id="input-${i}"
                class="topic-name ${inputClass}"
                tabindex="-1"
            >${badgesRow}<div class="item-title-row"><span class="item-title-text">${escapeHtml(s.name)}</span><span class="item-count-badge">${s.questionCount} ta test</span>${s.trashedQuestionCount > 0 ? `<span class="item-count-badge item-trash-badge" title="Savatdagi (o'chirilgan) testlar">🗑️ ${s.trashedQuestionCount} ta savatda</span>` : ""}</div></div>
        </div>
            `
                : `
            <textarea
                class="name-edit-area ${inputClass}"
                rows="2"
                ${placeholder}
                oninput="itemBlock[${i}].name=this.value"
                onkeydown="onClickKey(event, ${i})"
                id="input-${i}"
            >${escapeHtml(s.name)}</textarea>
            <select class="topic-section-select" onchange="itemBlock[${i}].sectionId=this.value?Number(this.value):null" title="Mavzu">
                ${sectionOptionsHtml(s.sectionId)}
            </select>
            `
        }
    ${buttons(s, i)}
`;

        list.appendChild(row);
    });

    // если фокус не задан — выбрать первый элемент
    if (focusIndex === null && itemBlock.length > 0) {
        focusIndex = 0;
    }

    if (focusIndex !== null) {
        const input = document.getElementById(`input-${focusIndex}`);
        if (input) {
            input.focus();
            input.scrollIntoView({behavior: 'smooth', block: 'nearest'});
        }
        focusIndex = null;
    }

    refreshUnlinkedTopicBadge();
    updateTopicsSummary();
} //DONE

function openQuestions(topicId) {
    if (!topicId || topicId < 0) {
        // ВАРИАНТ 1 — запрет
        alert("❗ Бу мавзу бўйича саволлар базада йўқ");
        return;

        // ВАРИАНТ 2 — разрешить пустые темы
        // window.location.href = "/topics";
        // return;
    }

    window.location.href = `/question?topicId=${topicId}`;
} //TODO

// "📊 Excel'ga eksport" — shu mavzudagi barcha faol testlarni .xlsx
// fayl sifatida yuklab beradi (question.js#exportQuestionsToExcel bilan
// bir xil endpoint). Oddiy GET + Content-Disposition:attachment orqali —
// brauzerning o'zi faylni yuklab beradi, fetch/blob shart emas.
function exportTopicQuestions(topicId) {
    window.location.href = `/api/export/questions?topicId=${topicId}`;
}

// "📝 Word'ga eksport" oynasi — galochka qo'yilmasa oddiy bitta faylli
// eksport, qo'yilsa "🎲 Variantlar yaratish" — har biri BOSHQA
// savollardan iborat bir nechta imtihon varianti (ExamVariantService),
// ZIP + javoblar kaliti holida. Qator tugmasi bosilganda openWordExportModal
// aynan qaysi MAVZU uchun ekanini saqlab qo'yadi (wordExportTopicId).
let wordExportTopicId = null;

function openWordExportModal(topicId) {
    wordExportTopicId = topicId;
    document.getElementById("wordExportVariantsCheckbox").checked = false;
    document.getElementById("wordExportVariantFields").classList.add("hidden");
    document.querySelector('input[name="wordExportVariantMode"][value="different"]').checked = true;
    updateWordExportHint();
    document.getElementById("wordExportModal").classList.add("show");
}

function closeWordExportModal() {
    document.getElementById("wordExportModal").classList.remove("show");
}

function toggleWordExportVariantFields() {
    const useVariants = document.getElementById("wordExportVariantsCheckbox").checked;
    document.getElementById("wordExportVariantFields").classList.toggle("hidden", !useVariants);
}

// Tanlangan rejimga ("different"/"same") qarab izoh matnini yangilaydi.
// Ikki matn UZUNLIGI har xil (bittasi ko'proq qatorga o'raladi) —
// shunchaki almashtirsak, modal balandligi rejim tanlanganda "sakrab"
// qolar edi. Shu sabab har ikkalasining tabiiy balandligini o'lchab,
// KATTASINI min-height sifatida qotirib qo'yamiz (ekran kengligidan
// qat'i nazar to'g'ri ishlashi uchun har chaqiriqda qayta o'lchanadi).
function updateWordExportHint() {
    const sameQuestions = document.querySelector('input[name="wordExportVariantMode"]:checked').value === "same";
    const textDifferent = "Savollar shu darsdan tasodifiy tanlanadi. Natija — har biri alohida .docx fayl bo'lgan ZIP arxiv + javoblar kaliti (Excel).";
    const textSame = "Savollar shu darsdan BIR MARTA tanlanadi va BARCHA nusxada bir xil bo'ladi — faqat savollar (va javob variantlari) tartibi har bir nusxada alohida aralashtiriladi.";

    const hint = document.getElementById("wordExportHint");
    hint.style.minHeight = "";
    hint.textContent = textDifferent;
    const hDifferent = hint.offsetHeight;
    hint.textContent = textSame;
    const hSame = hint.offsetHeight;
    hint.style.minHeight = Math.max(hDifferent, hSame) + "px";

    hint.textContent = sameQuestions ? textSame : textDifferent;
}

async function confirmWordExport() {
    const useVariants = document.getElementById("wordExportVariantsCheckbox").checked;

    if (!useVariants) {
        window.location.href = `/api/export/questions/word?topicId=${wordExportTopicId}`;
        closeWordExportModal();
        return;
    }

    const variantCount = Number(document.getElementById("wordExportVariantCount").value);
    const perVariant = Number(document.getElementById("wordExportPerVariant").value);
    const shuffleAnswers = document.getElementById("wordExportShuffleAnswers").checked;
    const sameQuestions = document.querySelector('input[name="wordExportVariantMode"]:checked').value === "same";

    if (!variantCount || variantCount < 1) {
        alert("Nechta variant kerakligini kiriting");
        return;
    }
    if (!perVariant || perVariant < 1) {
        alert("Har bir variantga nechta savol kerakligini kiriting");
        return;
    }

    await downloadWordVariants(
        `/api/export/questions/word/variants?topicId=${wordExportTopicId}&variantCount=${variantCount}&perVariant=${perVariant}&shuffleAnswers=${shuffleAnswers}&sameQuestions=${sameQuestions}`,
        "variantlar.zip"
    );
}

// ZIP faylni fetch orqali yuklab olish — oddiy window.location.href
// ISHLATILMAYDI, chunki backend xato bo'lsa (masalan "savol yetmadi")
// {"error": "..."} JSON qaytaradi, buni foydalanuvchiga ko'rsatish uchun
// javobni avval o'qib chiqish kerak (GlobalRestExceptionHandler).
async function downloadWordVariants(url, filename) {
    const btn = document.getElementById("wordExportConfirmBtn");
    const originalText = btn.textContent;
    btn.disabled = true;
    btn.textContent = "Tayyorlanmoqda...";

    try {
        const res = await fetch(url);
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "Eksport qilishda xatolik");
            return;
        }

        const blob = await res.blob();
        const objectUrl = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = objectUrl;
        a.download = filename;
        document.body.appendChild(a);
        a.click();
        a.remove();
        URL.revokeObjectURL(objectUrl);
        closeWordExportModal();
    } catch (e) {
        console.error(e);
        alert("Tarmoq xatoligi");
    } finally {
        btn.disabled = false;
        btn.textContent = originalText;
    }
}

function hasDuplicate(currentIndex, name) {

    return itemBlock.some((topic, index) =>
        index !== currentIndex &&
        topic.name.toLowerCase().trim() === name.toLowerCase().trim()
    );
} //DONE

function onClickKey(event, i) {
    // Nom maydoni endi <textarea> (bir necha qatorli tahrirlash uchun) —
    // Shift+Enter bilan qator ko'chirish (agar kerak bo'lsa) ochiq
    // qoldirilgan, oddiy Enter esa saqlaydi (avvalgi <input>'dagi bilan
    // bir xil xulq-atvor) — preventDefault() shart, aks holda textarea'ga
    // qo'shimcha bo'sh qator ham qo'shilib qolardi.
    if (event.key === "Enter" && !event.shiftKey && itemBlock[i].mode !== "VIEW") {
        event.preventDefault();
        saveOnClientSide(i);
    }

    if (event.key === "Escape" && itemBlock[i].mode !== "VIEW") {
        cancel(i);
    }

    // "Delete" tugmasi ORQALI QATORNI O'CHIRISH endi olib tashlandi —
    // <textarea> ichida matn tahrirlashda "Delete" odatiy (kursordan
    // keyingi belgini o'chirish) ma'noda ishlatiladi, avvalgi <input>'da
    // bo'lgani kabi butun QATORNI o'chirib yubormasligi kerak. Qatorni
    // o'chirish endi faqat 🗑️ tugmasi orqali (buttons()).
} //DONE

function onViewKeyDown(event, index) {
    const s = itemBlock[index];

    // работаем ТОЛЬКО в VIEW
    if (s.mode !== "VIEW") return;

    switch (event.key) {

        case "Enter":
            event.preventDefault();
            openQuestions(s.id);
            break;

        case "ArrowUp":
            event.preventDefault();
            moveFocus(index - 1);
            break;

        case "ArrowDown":
            event.preventDefault();
            moveFocus(index + 1);
            break;

        case "Home":
            event.preventDefault();
            moveFocus(0);
            break;

        case "End":
            event.preventDefault();
            moveFocus(itemBlock.length - 1);
            break;
    }
} //DONE

function moveFocus(newIndex) {
    if (newIndex < 0 || newIndex >= itemBlock.length) return;
    focusIndex = newIndex;
    render();
}//DONE

function cancel(i) {
    const s = itemBlock[i];
    if (s.mode !== "VIEW") {
        if (s.mode === "NEW") {
            itemBlock.splice(i, 1);
        }
        s.name = s.original;
        s.sectionId = s.originalSectionId; // Bo'lim tanlovi ham bekor qilinadi
        s.mode = "VIEW";
        showToast('info', 'Amaliyot bekor qilindi', 2000);
    }
    render();
} //DONE

function undoAll() {
    reloadFromDb(`/api/topic?scienceId=${scienceId}`).then(r => {
        render()
    });
    showToast('info', 'Ma\'lumotlar bazasidan qayta yuklandi ', 4000);
}//DONE

function removeFromUi(i) {
    if (itemBlock[i].mode === "NEW") {
        itemBlock.splice(i, 1);
        render();
        return;
    }
    const topicName = itemBlock[i].name || "Bu dars";
    const confirmDelete = confirm(`⚠️ "${topicName}"ni o'chirishni tasdiqlaysizmi?\n\nKeyin bu amalni bekor qilib bo'lmaydi.`);
    if (confirmDelete) {
        const removedTopic = itemBlock[i];

        if (removedTopic.id > 0) {
            deletedTopicIds.push(removedTopic.id);
        }

        itemBlock.splice(i, 1);
        showToast('success', `"${removedTopic.name || 'Dars'}" o'chirildi`, 2000);
        render();
    } else {
        cancel(i);
    }
} //DONE

// Tugmalar guruhi ".row-actions" ichiga o'raladi (science.css) — mavzu
// nomi qancha uzun bo'lib, bir necha qatorga o'ralib ketmasin, tugmalar
// HECH QACHON torayib/siqilib qolmaydi (flex-shrink:0).
function buttons(s, i) {
    if (s.mode === "VIEW") {
        // Tartib tugmalari (⬆⬇) — bo'lim bo'yicha FILTRLANGAN (ko'rinadigan)
        // ro'yxatdagi o'rniga qarab disabled qilinadi, raw massiv
        // indeksiga emas (chunki qo'shni massiv elementi boshqa bo'limga
        // tegishli bo'lishi mumkin — getVisibleIndices()).
        const visible = getVisibleIndices();
        const pos = visible.indexOf(i);
        const upDisabled = pos <= 0 ? "disabled" : "";
        const downDisabled = pos === -1 || pos === visible.length - 1 ? "disabled" : "";
        return `
            <div class="row-actions">
                <button class="order-move-btn" onclick="moveUp(${i})" ${upDisabled} title="Yuqoriga">⬆</button>
                <button class="order-move-btn" onclick="moveDown(${i})" ${downDisabled} title="Pastga">⬇</button>
                <button onclick="edit(${i})">✏️ Edit</button>
            </div>
        `;
    }
    return `
        <div class="row-actions">
            <button onclick="saveOnClientSide(${i})">💾 Save</button>
            <button onclick="cancel(${i})">↩ Cancel</button>
            <button onclick="removeFromUi(${i})">🗑️ Delete</button>
        </div>
           `;
} //DONE

// Faqat joriy filtrlashda (bo'lim bo'yicha yoki hammasi) KO'RINADIGAN
// qatorlarning raw itemBlock indekslarini qaytaradi — ⬆⬇ tugmalari va
// A-Z/Z-A saralash shu ro'yxat DOIRASIDA ishlashi kerak (boshqa
// bo'limdagi qatorlar aralashib ketmasin).
function getVisibleIndices() {
    return itemBlock
        .map((s, idx) => ({s, idx}))
        .filter(({s}) => !filterSectionId || Number(s.sectionId) === Number(filterSectionId))
        .map(({idx}) => idx);
}

function moveUp(i) {
    const visible = getVisibleIndices();
    const pos = visible.indexOf(i);
    if (pos <= 0) return;
    const otherIdx = visible[pos - 1];
    [itemBlock[otherIdx], itemBlock[i]] = [itemBlock[i], itemBlock[otherIdx]];
    persistOrder();
}

function moveDown(i) {
    const visible = getVisibleIndices();
    const pos = visible.indexOf(i);
    if (pos === -1 || pos >= visible.length - 1) return;
    const otherIdx = visible[pos + 1];
    [itemBlock[otherIdx], itemBlock[i]] = [itemBlock[i], itemBlock[otherIdx]];
    persistOrder();
}

// Yangi tartibni serverga saqlaydi — BUTUN itemBlock (shu Fandagi barcha
// mavzular, filtrlanmagan) joriy massiv tartibida yuboriladi, chunki
// backend (/api/topic/reorder) bitta Fanning TO'LIQ mavzular ro'yxatini
// kutadi (TopicService.reorderTopics — id to'plami mos kelmasa xato
// qaytaradi).
async function persistOrder() {
    render();
    const orderedIds = itemBlock.filter(s => s.id > 0).map(s => s.id);
    if (orderedIds.length < 2) return;
    try {
        const response = await fetch(`/api/topic/reorder?scienceId=${scienceId}`, {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(orderedIds)
        });
        if (!response.ok) throw new Error("Server error: " + response.status);
        showToast('success', 'Tartib saqlandi', 2000);
    } catch (err) {
        console.error(err);
        showToast('error', 'Tartibni saqlashda xatolik', 4000);
    }
}

// A→Z / Z→A — faqat joriy KO'RINADIGAN (bo'lim bo'yicha filtrlangan)
// qatorlar orasida saralanadi; boshqa bo'limdagi qatorlarning massivdagi
// o'rni butunlay tegilmay qoladi (courseDetail.js#sortChapterSections
// bilan bir xil "slot almashtirish" texnikasi).
function sortAllAZ(dir) {
    if (itemBlock.some(s => s.mode !== "VIEW")) {
        alert("❌ Avval tahrirlashni yakuniga yetkazing (yoki saqlang)!");
        return;
    }
    const visibleIndices = getVisibleIndices();
    if (visibleIndices.length < 2) return;
    const visibleItems = visibleIndices.map(idx => itemBlock[idx]);
    const sorted = [...visibleItems].sort((a, b) =>
        dir === "AZ" ? a.name.localeCompare(b.name, "uz") : b.name.localeCompare(a.name, "uz"));
    visibleIndices.forEach((idx, k) => {
        itemBlock[idx] = sorted[k];
    });
    persistOrder();
}

function edit(i) {
    // Kursga bog'langan mavzu — nomi HAM, Bo'limi HAM faqat kurs ichidan
    // o'zgartiriladi (foydalanuvchi so'rovi bo'yicha: "mavzu kursni ichida
    // o'zgartiriladi" — bu yerdan umuman tahrirlash imkoni yo'q, na
    // qisman). Backend ham xuddi shu tekshiruvni qaytaradi
    // (TopicService.updateTopic, TopicSectionService.assignTopicToSection)
    // — bu yerdagi tekshiruv foydalanuvchiga darhol, saqlashga
    // urinmasdan tushuntirish beradi.
    if (itemBlock[i].linkedCourseTitle) {
        alert(`❌ Bu mavzu "${itemBlock[i].linkedCourseTitle}" kursiga bog'langan.\n\nUni (nomini ham, Bo'limini ham) faqat shu kurs ichidan (kurs sahifasidagi mavzu ✏️ tugmasi orqali) tahrirlashingiz mumkin.`);
        return;
    }

    if (itemBlock.some(s => s.mode === "EDIT")) {
        showToast('warning', 'Avval tahrirlashni yakuniga yetkazing!');
        focusIndex = itemBlock.findIndex(s => s.mode !== "VIEW");
        render();
        return;
    }
    itemBlock[i].mode = "EDIT";
    focusIndex = i;

    oldName = itemBlock[i].name;

    render();
} //DONE

function showToast(type, message, duration = 4000) {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;

    const icons = {
        success: '✅',
        error: '❌',
        warning: '⚠️',
        info: 'ℹ️'
    };

    toast.innerHTML = `
               <span class="toast-icon">${icons[type] || ''}</span>
               <span class="toast-message">${message}</span>
               <button class="toast-close" onclick="this.parentElement.remove()">❌</button>
           `;

    const container = document.getElementById('toast-container');
    container.appendChild(toast);

    // Автоматическое удаление через указанное время
    setTimeout(() => {
        if (toast.parentElement) {
            toast.style.opacity = '0';
            setTimeout(() => toast.remove(), 300);
        }
    }, duration);

    return toast;
} //DONE

function add() {
    if (itemBlock.some(s => s.mode === "NEW" || s.mode === "EDIT")) {
        showToast('warning', 'Avval saqlash tugmasini bosing!');
        focusIndex = itemBlock.findIndex(s => s.mode !== "VIEW");
        render();
        return;
    }

    // ИЗМЕНЕНИЕ: Увеличиваем временный ID
    const tempId = Date.now() * -1; // Отрицательный ID для временных записей

    // Bo'lim ustidan kelingan bo'lsa (filterSectionId) — yangi mavzu
    // avtomatik o'sha bo'limga tanlangan holda ochiladi (teacher har safar
    // qo'lda tanlamasin uchun).
    itemBlock.push({
        id: tempId, // Временный ID
        name: "",
        original: "",
        sectionId: filterSectionId ? Number(filterSectionId) : null,
        originalSectionId: null,
        mode: "NEW"
    });

    focusIndex = itemBlock.length - 1;
    render();
} //DONE

function saveOnClientSide(i) {
    const s = itemBlock[i];
    newName = s.name.trim();


    if (newName === "") {
        alert('❌ Dars nomi bo\'sh bo\'lishi mumkin emas!');
        focusIndex = i;
        console.error("Dars nomi bo\'sh bo\'lishi mumkin emas!");

        return;
    }

    // проверка дубликатов на фронте
    if (hasDuplicate(i, newName)) {
        alert('❌ Bu dars nomi allaqachon mavjud!');
        focusIndex = i;
        console.log("hasDuplicate = true");
        return;
    }

    s.name = newName;
    itemBlock[i].mode = "VIEW";

    render();

    // Определяем тип операции
    if (newName === oldName) {
        showToast('info', 'O\'zgarish bo\'lmadi', 3000);
    }

    if (s.id < 0) {
        if (newName === oldName) {
            showToast('info', 'O\'zgarish bo\'lmadi', 3000);
        } else {
            showToast('info', 'Yangi dars o\'zgardi', 3000);
        }
        showToast('success', 'Yangi dars saqlandi \n\n(bazaga saqlash uchun "Bazaga saqlash" tugmasini bosing)', 3000);
    } else {
        // Существующая запись из БД
        if (newName === oldName) {
            showToast('warm', 'O\'zgarish bo\'lmadi', 3000);
        } else {
            showToast('success', 'Dars muvaffaqiyatli saqlandi', 3000);
        }

    }
    oldName = "";
    newName = "";
}//DONE

async function saveToDb() {

    // Запрет: есть незавершённые записи
    if (itemBlock.some(s => s.mode !== "VIEW")) {
        alert('❌ Avval tahrirlashni yakuniga yetkazing!');
        focusIndex = itemBlock.findIndex(s => s.mode !== "VIEW");
        render();
        return;
    }

    // Формируем payload
    const payload = {
        new: itemBlock
            .filter(s => s.id < 0)
            .map(s => (
                {science_id: scienceId, name: s.name, sectionId: s.sectionId || null})),

        // E'tibor bering: nom o'zgarmagan, faqat Bo'lim o'zgargan bo'lsa
        // ham "updated"ga tushishi kerak — shu sabab shart ikkalasini
        // ham tekshiradi (avval faqat nom tekshirilardi).
        updated: itemBlock
            .filter(s => s.id > 0 && (s.name !== s.original || s.sectionId !== s.originalSectionId))
            .map(s => (
                {id: s.id, name: s.name, sectionId: s.sectionId || null}
            )),

        deletedIds: deletedTopicIds
    };

    // Если нечего сохранять — выходим
    if (
        payload.new.length === 0 &&
        payload.updated.length === 0 &&
        deletedTopicIds.length === 0) {
        alert('ℹ️ Saqlash uchun o‘zgarishlar yo‘q');
        return;
    }

    // 5. Подтверждение
    const confirmed = confirm(
        `Yangi: ${payload.new.length} ta\n` +
        `O\'zgartirilgan: ${payload.updated.length} ta\n\n` +
        `O\'chirilgan: ${deletedTopicIds.length} ta\n\n` +
        `Saqlashni xohlaysizmi?`
    );
    if (!confirmed) return;

    try {
        showToast('info', 'Maʼlumotlar bazaga saqlanmoqda...', 5000);

        // 6. Отправка в backend
        const response = await fetch("/api/topic/save",
            {
                method: "POST",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify(payload)
            });

        if (!response.ok) {
            // Backend {"error": "..."} shaklida qaytaradi — avval bu yerda
            // matn o'qib tashlanardi-yu, aniq sabab o'rniga umumiy "Server
            // error (not JSON)" ko'rsatilardi.
            const data = await response.json().catch(() => ({}));
            throw new Error(data.error || "Saqlashda xatolik");
        }

        const data = await response.json();   // теперь это безопасно

        // Успешное сообщение
        showToast(
            'success',
            `Saqlandi: yangi — ${payload.new.length}, \n
            o‘zgartirilgan — ${payload.updated.length}, \n\n
            o'chirilgan - ${deletedTopicIds.length} ta`,
            5000
        );

        // 🔑 КЛЮЧЕВОЕ МЕСТО — ПОЛНАЯ СИНХРОНИЗАЦИЯ С БД
        deletedTopicIds = [];
        await reloadFromDb(`/api/topic?scienceId=${scienceId}`);
        focusIndex = 0;
        render(); // ❗ shu qator yo'q edi — shuning uchun DB yangilangan, lekin ekran eskicha qolardi
        refreshTopicTrashBadge();

    } catch (err) {
        console.error(err);
        showToast('error', err.message || 'Saqlashda xatolik', 7000);
        alert(err.message);
    }
}//DONE

//===========================================================================
//            BACK tugmasini bosganda ishlaydi.
//===========================================================================
document.addEventListener("DOMContentLoaded", () => {
    const btnBack = document.getElementById("btnBack");

    if (!btnBack) return;

    btnBack.onclick = () => {
        const scienceId =
            new URLSearchParams(window.location.search).get("scienceId");

        if (!scienceId) {
            // fallback
            window.location.href = "/science";
            return;
        }

        // Mavzu ichidan kelingan bo'lsa (Bo'lim -> Mavzu -> Dars) — Mavzu
        // guruhlari ro'yxatiga qaytariladi, aks holda to'g'ridan-to'g'ri
        // Bo'limlarga (ikkalasida ham joriy Yo'nalish qamrovi saqlanadi).
        window.location.href = filterSectionId
            ? `/topic-sections?scienceId=${scienceId}${fieldQuery}`
            : `/science?focus=${scienceId}${fieldQuery}`;
    };
});
//===========================================================================






