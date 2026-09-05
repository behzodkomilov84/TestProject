const params = new URLSearchParams(window.location.search);
const topicId = params.get("topicId");

// science.js'dagi "🔍 Bo'lim ichida qidiruv" natijalaridagi "👁️ Ko'rish"/
// "✏️ Tahrirlash" tugmalaridan kelinganda — aynan qaysi savolga
// e'tibor qaratish kerakligini bildiradi (foydalanuvchi so'rovi,
// 2026-09-05). "focus" — qatorga scroll+yorqinlashtirish (view),
// "edit" — to'g'ridan-to'g'ri "Savol formasi" tahrirlash rejimida ochiladi.
const focusQuestionId = params.get("focus");
const editQuestionId = params.get("edit");

let questions = null;

let currentPage = 0;
let pageSize = 10;
// Standart (default) holatda "Hammasi" tanlangan bo'ladi (pageSizeSelect'da
// ham "all" — selected) — o'qituvchi sahifaga kirishi bilan mavzuning
// BARCHA savollarini bir zumda ko'radi, sahifalashni qo'lda tanlashi shart emas.
let isAllMode = true;
let searchQuery = "";
let totalPages = 1;
let isServerPaging = false;

if (!topicId) {
    document.querySelector("#questionsTable tbody").innerHTML =
        "<tr><td colspan='11'>❌ topicId yuborilmagan</td></tr>";
} else {
    // ".then" — DASTLABKI yuklashdan KEYIN, jadval allaqachon chizilgan
    // bo'lganda ISHGA TUSHIRILISHI kerak (aks holda tr[data-question-id]
    // hali DOM'da yo'q bo'ladi). Faqat BIR MARTA (sahifa birinchi
    // ochilganda) — keyingi qayta yuklashlarda (o'chirish, qidiruv va h.k.)
    // takrorlanmaydi.
    loadAllQuestions().then(() => handleIncomingFocusOrEdit());
    loadTopicName();
    refreshQuestionTrashBadge();
}

// science.js qidiruv natijasidan "?focus=<id>" yoki "?edit=<id>" bilan
// kelinganda — DOMContentLoaded ichida "Savol formasi" modali
// (buildQuestionFormModalHtml) ALLAQACHON DOM'ga qo'shilgan bo'lishi kerak
// (bu funksiya loadAllQuestions() ning fetch'i tugagandan KEYIN chaqiriladi,
// bu esa DOMContentLoaded'dan doim KEYINROQ sodir bo'ladi).
function handleIncomingFocusOrEdit() {
    if (editQuestionId) {
        openQuestionFormModal("edit", Number(editQuestionId));
        return;
    }
    if (focusQuestionId) {
        const row = document.querySelector(`tr[data-question-id="${focusQuestionId}"]`);
        if (!row) return;
        row.scrollIntoView({ behavior: "smooth", block: "center" });
        row.classList.add("question-row-flash");
        setTimeout(() => row.classList.remove("question-row-flash"), 2600);
    }
}

// "🗑️ O'chirilganlar" tugmasidagi hisoblagich (bildirishnoma belgisi bilan
// bir xil uslub — ".notif-badge", navbar.js#refreshUnreadCount) — savatda
// nechta savol borligini, panelni ochmasdan ham ko'rsatib turadi. Har bir
// tiklash/o'chirish amalidan keyin qayta chaqiriladi (loadQuestionTrash,
// deleteQuestion va h.k.).
function refreshQuestionTrashBadge() {
    fetch(`/api/question/deleted?topicId=${topicId}`)
        .then(r => r.ok ? r.json() : [])
        .then(items => setTrashBadgeCount("questionTrashBadge", items.length))
        .catch(err => console.error(err));
}

// Badge'ni (".notif-badge") sonini yangilaydi — 0 bo'lsa yashiradi, aks
// holda ko'rsatadi (99+dan katta bo'lsa "99+" deb yozadi). Bir nechta
// sahifada (question.js/topic.js/science.js/...) bir xil andoza bilan
// takrorlanadi — mustaqil kichik JS fayllar bo'lgani uchun ataylab
// nusxalangan (umumiy modul yo'q).
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

// Sarlavhada ("Mavzuga oid testlar: <nomi>") aynan qaysi mavzu ekanini
// ko'rsatish uchun (foydalanuvchi so'rovi — bir nechta mavzu sahifasi
// ochilganda adashib qolmaslik uchun).
async function loadTopicName() {
    const heading = document.getElementById("topicNameHeading");
    if (!heading) return;
    try {
        const res = await fetch(`/api/topic/${topicId}/name`);
        if (!res.ok) throw new Error();
        const data = await res.json();
        heading.textContent = `📋 ${data.name}`;
    } catch (err) {
        heading.textContent = "";
    }
}

function normalizeApiResponse(data) {
    // CASE 1: Page<T>
    if (data && Array.isArray(data.content)) {
        return {
            items: data.content,
            totalPages: data.totalPages ?? 1,
            totalElements: data.totalElements ?? data.content.length,
            page: data.number ?? 0,
            isPaged: true
        };
    }

    // CASE 2: List<T>
    if (Array.isArray(data)) {
        return {
            items: data,
            totalPages: 1,
            totalElements: data.length,
            page: 0,
            isPaged: false
        };
    }

    throw new Error("Неподдерживаемый формат ответа API");
}

async function loadQuestions(topicId, page = 0) {
    try {
        const url = `/api/question?topicId=${topicId}&page=${page}&size=${pageSize}`;

        const res = await fetch(url);

        if (!res.ok) throw new Error("Testlarni yuklashda xatolik yuz berdi.");

        const data = await res.json();

        const normalized = normalizeApiResponse(data);

        isServerPaging = normalized.isPaged;
        currentPage = normalized.page;
        totalPages = normalized.totalPages;

        // Faqat EKRANDA ko'rsatish uchun — ⬆⬇ / A-Z / Z-A saralash bunga
        // emas, har doim /api/question/all orqali qayta yuklangan TO'LIQ
        // ro'yxatga nisbatan ishlaydi (fetchAllActiveQuestions).
        questions = normalized.items;

        renderQuestionsTable(questions);

        renderPagination({
            totalPages: normalized.totalPages,
            number: normalized.page,
            first: normalized.page === 0,
            last: normalized.page === normalized.totalPages - 1
        });

        updateSortButtonsVisibility();

    } catch (e) {
        document.querySelector("#questionsTable tbody").innerHTML =
            `<tr><td colspan="11">❌ ${e.message}</td></tr>`;
    }
}

// Belgilangan (checkbox orqali) savol ID'lari — guruh holatida o'chirish
// uchun (toggleSelectAll/onRowCheckboxChange/deleteSelectedQuestions).
// Jadval QAYTA chizilganda (saralash, sahifa almashtirish, tahrirlashdan
// keyin qayta yuklash va h.k.) ATAYLAB TOZALANADI — ekranda
// ko'rinmayotgan qatorlar tasodifan o'chib ketmasligi uchun (xavfsiz,
// oldindan aytib bo'ladigan xulq-atvor).
let selectedQuestionIds = new Set();

function renderQuestionsTable(rows) {
    const tbody = document.querySelector("#questionsTable tbody");
    tbody.innerHTML = "";

    selectedQuestionIds.clear();
    updateBulkDeleteButton();
    const selectAllCheckbox = document.getElementById("selectAllCheckbox");
    if (selectAllCheckbox) selectAllCheckbox.checked = false;

    const letters = ["A", "B", "C", "D", "E"];

    // ⬆⬇ tugmalari sahifalash holatidan (10/20/.../Hammasi) QAT'I NAZAR
    // ishlaydi — bosilganda ekrandagi (ehtimol qisman) ro'yxatga emas,
    // HAR DOIM /api/question/all orqali qayta yuklangan TO'LIQ faol
    // ro'yxatga nisbatan amal qiladi (moveQuestionUp/moveQuestionDown/
    // sortAllAZ — reorderAgainstFullList). Faqat qidiruv FAOL bo'lganda
    // o'chirilgan — aks holda foydalanuvchi qidiruv natijasi ustida
    // sortlayotganini o'ylab qolishi mumkin, holbuki amal HAR DOIM butun
    // mavzu bo'yicha bajariladi.
    const canReorder = !searchQuery;

    rows.forEach((q, index) => {
        const answers = q.answers.slice(0, 5);
        // Ko'p to'g'ri javobli savollar (foydalanuvchi so'rovi, 2026-09-05,
        // 3-bosqich) — BARCHA to'g'ri javoblar (ilgari faqat BIRINCHISI,
        // findIndex/find). "💬" tugmasi va izoh-standart-qiymati hamon
        // faqat BIRINCHI to'g'ri javobga tayanadi (bitta umumiy izoh
        // maydoni — Excel importdagi bilan bir xil qoida).
        const correctAnswers = answers.filter(a => a.isTrue);
        const correctLetter = correctAnswers.length > 0
            ? correctAnswers.map(a => letters[answers.indexOf(a)]).join(",")
            : "-";
        const correctAnswer = correctAnswers[0];

        const row = document.createElement("tr");
        row.dataset.questionId = q.id;  // <-- это ключевое
        // Rasm URL'lari saqlab qo'yiladi (bu sahifada rasmni tahrirlash yo'q,
        // lekin savol matnini saqlashda mavjud rasm o'chib ketmasligi uchun kerak).
        row.dataset.imageUrl = q.imageUrl || "";
        row.innerHTML = `
            <td><input type="checkbox" class="row-select-checkbox" onchange="onRowCheckboxChange(${q.id}, this)"></td>
            <td class="enumeration">${index + 1}</td>
            <td data-editable>
                ${q.questionText}
                ${q.imageUrl ? `<br><img class="question-thumb" src="${q.imageUrl}" alt="Savol rasmi">` : ""}
            </td>
            ${letters.map((letter, i) => {
                const a = answers[i];

                // Eski (5-variant qo'shilishidan OLDIN yaratilgan) savollarda
                // faqat 4 ta javob bor — shu savol uchun "E" ustuni bo'sh
                // qoladi. Bo'sh katakcha ham chizilishi SHART, aks holda
                // keyingi ustunlar (✅, izoh, amallar) chapga surilib,
                // sarlavha bilan mos kelmay qoladi.
                if (!a) {
                    return `<td class="answer-cell"></td>`;
                }

                return `
            <td data-editable
                data-answer-id="${a.id}"
                data-image-url="${a.imageUrl || ""}"
                data-commentary-image-url="${a.commentaryImageUrl || ""}"
                data-commentary-video-url="${a.commentaryVideoUrl || ""}"
                class="answer-cell ${a.isTrue ? "correct" : ""}">
                ${a.answerText}
                ${a.imageUrl ? `<br><img class="answer-thumb" src="${a.imageUrl}" alt="Javob rasmi">` : ""}
            </td>
            `;
            }).join("")}
            <td class="correct-letter">${correctAnswers.length > 1 ? '<span title="Ko\'p to\'g\'ri javobli savol">🔀</span> ' : ''}<b>${correctLetter}</b></td>

            <td class="order-cell">
                ${canReorder ? `
                    <button class="order-move-btn" onclick="moveQuestionUp(${q.id})" title="Yuqoriga">⬆</button>
                    <button class="order-move-btn" onclick="moveQuestionDown(${q.id})" title="Pastga">⬇</button>
                ` : `<span style="color:#94a3b8; font-size:11px;">—</span>`}
            </td>

            <td class="actions-cell">
                <div class="view-actions">

                    <button class="action-btn comment"
                        data-question-id="${q.id}"
                        data-answer-id="${correctAnswer?.id ?? ''}"
                        data-comment="${encodeURIComponent(correctAnswer?.commentary ?? '')}"
                        data-comment-image="${encodeURIComponent(correctAnswer?.commentaryImageUrl ?? '')}"
                        data-comment-video="${encodeURIComponent(correctAnswer?.commentaryVideoUrl ?? '')}"

                        onclick="openCommentModal(this)"
                        title="Izoh ko‘rsatish">
                    💬
                    </button>

                    <!-- Ilgari qatorning O'ZINI tahrirlanadigan (inline)
                         holatga o'tkazardi (enableInlineEdit/saveInlineEdit/
                         cancelInlineEdit) — endi to'liq "Savol formasi"
                         modalini ochadi (foydalanuvchi so'rovi, 2026-09-05:
                         "testni tahrirlashni ham modalda ochiladigan qilib
                         mosla"). -->
                    <button class="action-btn edit"
                        onclick="openQuestionFormModal('edit', ${q.id})"
                        title="Tahrirlash">✏️</button>

                    <button class="action-btn delete"
                        onclick="deleteQuestion(${q.id})"
                        title="O‘chirish">❌</button>

                </div>
            </td>
        `;
        tbody.appendChild(row);
    });
}

// ========================================================================
//     Guruh holatida belgilash / o'chirish
// ========================================================================

function onRowCheckboxChange(questionId, checkbox) {
    if (checkbox.checked) {
        selectedQuestionIds.add(questionId);
    } else {
        selectedQuestionIds.delete(questionId);
        // Bitta qator bekor qilinsa — "hammasini belgilash" ham endi
        // to'g'ri emas (barchasi belgilangan degani emas).
        const selectAllCheckbox = document.getElementById("selectAllCheckbox");
        if (selectAllCheckbox) selectAllCheckbox.checked = false;
    }
    updateBulkDeleteButton();
}

// "Hammasini belgilash" — faqat EKRANDAGI (joriy sahifadagi yoki
// "Hammasi" rejimida — barcha) qatorlarga taalluqli.
function toggleSelectAll(selectAllCheckbox) {
    document.querySelectorAll("#questionsTable tbody .row-select-checkbox").forEach((cb) => {
        cb.checked = selectAllCheckbox.checked;
        const questionId = Number(cb.closest("tr").dataset.questionId);
        if (selectAllCheckbox.checked) {
            selectedQuestionIds.add(questionId);
        } else {
            selectedQuestionIds.delete(questionId);
        }
    });
    updateBulkDeleteButton();
}

function updateBulkDeleteButton() {
    const btn = document.getElementById("bulkDeleteBtn");
    if (!btn) return;
    const count = selectedQuestionIds.size;
    document.getElementById("bulkDeleteCount").textContent = String(count);
    btn.classList.toggle("hidden", count === 0);
}

// "🗑️ Tanlanganlarni o'chirish" — BARCHA belgilangan savollarni BITTA
// so'rovda soft-delete qiladi (QuestionService.deleteQuestions,
// /api/question/bulk) — darhol butunlay o'chmaydi, "🗑️ O'chirilganlar"
// panelidan qaytarish mumkin (bitta-bitta o'chirish bilan bir xil g'oya).
async function deleteSelectedQuestions() {
    const ids = [...selectedQuestionIds];
    if (!ids.length) return;

    if (!await showConfirmModal(`⚠️ ${ids.length} ta savolni o'chirmoqchimisiz?\n\n(Butunlay o'chmaydi — "🗑️ O'chirilganlar" panelidan qaytarish mumkin.)`, { danger: true })) {
        return;
    }

    try {
        const res = await fetch("/api/question/bulk", {
            method: "DELETE",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(ids)
        });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            throw new Error(data.error || "O'chirishda xatolik");
        }

        const data = await res.json().catch(() => ({}));
        showAlert(`✅ ${data.deleted ?? ids.length} ta savol o'chirildi`, "success");

        await reloadCurrentQuestionsView();
        refreshQuestionTrashBadge();
    } catch (err) {
        console.error(err);
        showAlert(err.message || "O'chirishda xatolik");
    }
}

function renderPagination(page) {
    const container = document.getElementById("pagination");
    container.innerHTML = "";

    if (!page || page.totalPages <= 1) return;

    const prev = document.createElement("button");
    prev.textContent = "←";
    prev.title = "Oldingi sahifa";
    prev.disabled = page.first;
    prev.onclick = () => {
        currentPage--;
        loadPage();
    };

    container.appendChild(prev);

    for (let i = 0; i < page.totalPages; i++) {
        const btn = document.createElement("button");
        btn.textContent = i + 1;
        btn.classList.toggle("active", i === page.number);

        btn.onclick = () => {
            currentPage = i;
            loadPage();
        };

        container.appendChild(btn);
    }

    const next = document.createElement("button");
    next.textContent = "→";
    next.title = "Keyingi sahifa";
    next.disabled = page.last;
    next.onclick = () => {
        currentPage++;
        loadPage();
    };

    container.appendChild(next);
}

function pageBtn(text, page) {
    const btn = document.createElement("button");
    btn.textContent = text;
    btn.onclick = () => loadQuestions(topicId, page);
    return btn;
}

document.getElementById("pageSizeSelect").addEventListener("change", (e) => {

    const value = e.target.value;
    if (value === "all") {
        isAllMode = true;
        currentPage = 0;
        loadAllQuestions();
        return;
    }

    const size = Number(value);

    if (!Number.isFinite(size)) return;

    isAllMode = false;
    pageSize = size;
    currentPage = 0;

    loadPage();
});

async function loadPage() {
    if (!topicId || isAllMode) return;

    const params = new URLSearchParams({
        topicId,
        page: currentPage,
        size: pageSize
    });

    if (searchQuery) {
        params.append("searchQuestionText", searchQuery); /*Controllerdagi parametr bilan bir xil bo'lishi kk.*/
    }

    const res = await fetch(`/api/question?${params}`);

    if (!res.ok) {
        showAlertModal("Xatolik yuz berdi");
        return;
    }

    const page = await res.json();

    currentPage = page.number ?? currentPage;
    totalPages = page.totalPages ?? 1;
    // Faqat EKRANDA ko'rsatish uchun (izoh yuqorida, loadQuestions'da).
    questions = page.content;

    renderQuestionsTable(questions);
    renderPagination(page);
    updateSortButtonsVisibility();
}


async function loadAllQuestions() {
    if (!topicId) return;

    const params = new URLSearchParams({ topicId });

    if (searchQuery) {
        params.append("q", searchQuery);
    }

    const res = await fetch(`/api/question/all?${params}`);

    if (!res.ok) {
        showError("Xatolik yuz berdi");
        return;
    }

    const data = await res.json();

    // Faqat EKRANDA ko'rsatish uchun (izoh yuqorida, loadQuestions'da).
    questions = data;

    renderQuestionsTable(questions);
    hidePagination();
    updateSortButtonsVisibility();
}

// renderQuestionsTable ichidagi "canReorder" bilan bir xil shart —
// qidiruv FAOL bo'lmasa (sahifalash holatidan qat'i nazar).
function updateSortButtonsVisibility() {
    const shouldShow = !searchQuery;
    const el = document.getElementById("questionSortButtons");
    if (el) el.classList.toggle("hidden", !shouldShow);
}

// Mavzuning TO'LIQ (sahifalanmagan, faol) savollar ro'yxatini har safar
// yangidan yuklaydi — ⬆⬇/A-Z/Z-A amallari ekrandagi (ehtimol qisman)
// ro'yxatga emas, HAR DOIM shu TO'LIQ ro'yxatga nisbatan bajarilishi
// uchun (aks holda 10/20 tadan ko'rsatilayotganda faqat o'sha sahifa
// ichida "aralashib", boshqa sahifadagilarga umuman tegilmasdi).
async function fetchAllActiveQuestions() {
    const res = await fetch(`/api/question/all?topicId=${topicId}`);
    if (!res.ok) throw new Error("Savollarni yuklashda xatolik");
    return await res.json();
}

async function submitQuestionOrder(orderedIds) {
    const response = await fetch(`/api/question/reorder?topicId=${topicId}`, {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(orderedIds)
    });
    if (!response.ok) {
        const data = await response.json().catch(() => ({}));
        throw new Error(data.error || ("Server error: " + response.status));
    }
}

// Joriy ko'rinishni (sahifalangan yoki "Hammasi") qayta yuklaydi —
// reorderdan keyin ekranda YANGI tartib ko'rinishi uchun.
async function reloadCurrentQuestionsView() {
    if (isAllMode) {
        await loadAllQuestions();
    } else {
        await loadPage();
    }
}

// ⬆⬇ tugmalari — bosilgan savolning ID'si TO'LIQ ro'yxat ichidagi haqiqiy
// o'rnini topib, qo'shni savol bilan almashtiradi (ekrandagi ko'rinishga
// emas — QuestionService.reorderQuestions bilan bir xil andoza).
function moveQuestionUp(questionId) {
    reorderQuestionRelativeToNeighbor(questionId, -1);
}

function moveQuestionDown(questionId) {
    reorderQuestionRelativeToNeighbor(questionId, 1);
}

async function reorderQuestionRelativeToNeighbor(questionId, direction) {
    try {
        const all = await fetchAllActiveQuestions();
        const pos = all.findIndex(q => q.id === questionId);
        const neighborPos = pos + direction;
        if (pos === -1 || neighborPos < 0 || neighborPos >= all.length) {
            return; // Allaqachon birinchi/oxirgi — jim o'tkazib yuboriladi.
        }
        [all[pos], all[neighborPos]] = [all[neighborPos], all[pos]];
        await submitQuestionOrder(all.map(q => q.id));
        showAlert("✅ Tartib saqlandi", "success");
        await reloadCurrentQuestionsView();
    } catch (err) {
        console.error(err);
        showAlert(err.message || "Tartibni saqlashda xatolik");
    }
}

// A→Z / Z→A — sahifalash holatidan qat'i nazar mavzuning TO'LIQ faol
// ro'yxati bo'yicha saralaydi (updateSortButtonsVisibility faqat
// qidiruv faol bo'lganda yashiradi).
async function sortAllAZ(dir) {
    try {
        const all = await fetchAllActiveQuestions();
        if (all.length < 2) return;
        all.sort((a, b) =>
            dir === "AZ"
                ? a.questionText.localeCompare(b.questionText, "uz")
                : b.questionText.localeCompare(a.questionText, "uz"));
        await submitQuestionOrder(all.map(q => q.id));
        showAlert("✅ Tartib saqlandi", "success");
        await reloadCurrentQuestionsView();
    } catch (err) {
        console.error(err);
        showAlert(err.message || "Tartibni saqlashda xatolik");
    }
}


function hidePagination() {
    document.querySelector(".pagination")?.classList.add("hidden");
}


// ================= Tahrirlash rejimida rasm/video widget'lari =================
// Har bir chaqiruvda yangi HTML qaytaradi; joriy URL data-current-url'da
// saqlanadi, fayl tanlansa yuklanadi va shu atributga yangi URL yoziladi
// (saveQuestionForm shundan o'qiydi). "Eni"/"Bo'yi" (px) — foydalanuvchi
// so'rovi, 2026-09-05: "Эни ва бўйи полялари мунтазам кўриниб турсин,
// расм импорт қилинмаса ҳам эни ва бўйига эга тўртбурчак турсин
// (расмсиз). Расм импорт қилганда ичи расмга тўлиб қолсин" — shu sabab
// ".inline-image-box" (chegarali to'rtburchak) rasm bor-yo'qligidan
// qat'i nazar HAR DOIM ko'rinadi, o'lchami "Eni"/"Bo'yi" maydonlariga
// mos (standart 140x100) — rasm yuklansa shu quti ICHINI to'liq
// qoplaydi (object-fit:cover). O'chirish (✖) tugmasi rasmning O'NG-YUQORI
// burchagida (bazada saqlanadi — Answer/Question entity#imageWidth/imageHeight).
function buildInlineImageWidget(role, currentUrl, altText, width, height) {
    const url = currentUrl || "";
    const w = width || 140;
    const h = height || 100;
    return `
        <div class="inline-image-upload" data-role="${role}" data-current-url="${url}" data-width="${width || ""}" data-height="${height || ""}">
            <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" class="inline-image-input" hidden>
            <div class="inline-image-box" style="width:${w}px;height:${h}px;" title="${altText} qo'shish/almashtirish">
                <img class="inline-image-preview ${url ? "" : "hidden"}" src="${url}" alt="${altText}">
                <span class="inline-image-placeholder ${url ? "hidden" : ""}">🖼️</span>
                <button type="button" class="inline-remove-image-btn ${url ? "" : "hidden"}" title="${altText}ni olib tashlash">✖</button>
            </div>
            <div class="inline-image-size">
                <label>Eni <input type="number" class="inline-image-width" min="10" max="2000" value="${width || ""}" placeholder="px"></label>
                <label>Bo'yi <input type="number" class="inline-image-height" min="10" max="2000" value="${height || ""}" placeholder="px"></label>
            </div>
        </div>
    `;
}

function buildInlineVideoWidget(currentUrl) {
    const url = currentUrl || "";
    return `
        <div class="inline-video-upload" data-current-url="${url}">
            <input type="file" accept="video/mp4,video/webm,video/ogg" class="inline-video-input" hidden>
            <button type="button" class="inline-media-btn inline-video-btn" title="Video qo'shish">🎬</button>
            <video class="inline-video-preview ${url ? "" : "hidden"}" src="${url}" controls></video>
            <button type="button" class="inline-media-btn inline-remove-video-btn ${url ? "" : "hidden"}" title="Videoni olib tashlash">✖</button>
        </div>
    `;
}

document.addEventListener("click", (e) => {
    // Tekshiruv TARTIBI muhim: ✖ tugmasi ".inline-image-box" ICHIDA
    // joylashgan (o'ng-yuqori burchakda) — shu sabab AVVAL ✖ ni
    // tekshiramiz, aks holda uni bosish ham qutini "bosilgandek" fayl
    // tanlash oynasini ochib yuborardi.
    if (e.target.closest(".inline-remove-image-btn")) {
        const container = e.target.closest(".inline-image-upload");
        container.dataset.currentUrl = "";
        container.dataset.width = "";
        container.dataset.height = "";
        const box = container.querySelector(".inline-image-box");
        const preview = container.querySelector(".inline-image-preview");
        preview.src = "";
        preview.classList.add("hidden");
        container.querySelector(".inline-image-placeholder").classList.remove("hidden");
        container.querySelector(".inline-remove-image-btn").classList.add("hidden");
        // Rasm o'chirilgandan keyin ham quti standart o'lchamda (140x100)
        // ko'rinishda qoladi ("расм импорт қилинмаса ҳам ... тўртбурчак
        // турсин") — foydalanuvchi Eni/Bo'yi maydonlarini ham tozalaydi.
        box.style.width = "140px";
        box.style.height = "100px";
        container.querySelector(".inline-image-width").value = "";
        container.querySelector(".inline-image-height").value = "";
        return;
    }
    if (e.target.closest(".inline-image-box")) {
        e.target.closest(".inline-image-upload").querySelector(".inline-image-input").click();
        return;
    }
    if (e.target.classList.contains("inline-video-btn")) {
        e.target.closest(".inline-video-upload").querySelector(".inline-video-input").click();
        return;
    }
    if (e.target.classList.contains("inline-remove-video-btn")) {
        const container = e.target.closest(".inline-video-upload");
        container.dataset.currentUrl = "";
        const preview = container.querySelector(".inline-video-preview");
        preview.src = "";
        preview.classList.add("hidden");
        e.target.classList.add("hidden");
    }
});

document.addEventListener("change", async (e) => {
    if (e.target.classList.contains("inline-image-input")) {
        const container = e.target.closest(".inline-image-upload");
        const file = e.target.files[0];
        if (!file) return;

        const endpoint = container.dataset.role === "commentary-image"
            ? "/api/question/upload-commentary-image"
            : "/api/question/upload-image";

        const formData = new FormData();
        formData.append("image", file);

        const box = container.querySelector(".inline-image-box");
        const placeholder = container.querySelector(".inline-image-placeholder");
        const originalPlaceholder = placeholder.textContent;
        placeholder.textContent = "⏳";

        try {
            const res = await fetch(endpoint, { method: "POST", body: formData });
            const data = await res.json();

            if (!res.ok) {
                showAlertModal(data.error || "❌ Rasmni yuklab bo'lmadi");
                return;
            }

            container.dataset.currentUrl = data.url;
            const preview = container.querySelector(".inline-image-preview");
            preview.src = data.url;
            preview.classList.remove("hidden");
            placeholder.classList.add("hidden");
            container.querySelector(".inline-remove-image-btn").classList.remove("hidden");

            // Agar Eni/Bo'yi maydonlari ALLAQACHON to'ldirilgan bo'lsa
            // (foydalanuvchi rasm yuklashdan OLDIN o'zi belgilagan bo'lsa)
            // — quti o'sha o'lchamda QOLADI, rasm shunchaki ICHINI to'ldiradi
            // (object-fit:cover, CSS). Aks holda — tabiiy o'lchamidan
            // (320px enigacha, nisbatni saqlab) avtomatik hisoblanadi
            // (foydalanuvchi so'rovi, 2026-09-05).
            const widthInput = container.querySelector(".inline-image-width");
            const heightInput = container.querySelector(".inline-image-height");
            if (!widthInput.value && !heightInput.value) {
                await new Promise((resolve) => {
                    if (preview.complete && preview.naturalWidth) { resolve(); return; }
                    preview.onload = resolve;
                    preview.onerror = resolve;
                });
                if (preview.naturalWidth && preview.naturalHeight) {
                    const maxW = 320;
                    const scale = Math.min(1, maxW / preview.naturalWidth);
                    const w = Math.round(preview.naturalWidth * scale);
                    const h = Math.round(preview.naturalHeight * scale);
                    container.dataset.width = w;
                    container.dataset.height = h;
                    box.style.width = w + "px";
                    box.style.height = h + "px";
                    widthInput.value = w;
                    heightInput.value = h;
                }
            }
        } catch (err) {
            console.error(err);
            showAlertModal("❌ Rasmni yuklashda tarmoq xatoligi");
        } finally {
            placeholder.textContent = originalPlaceholder;
        }
    }

    if (e.target.classList.contains("inline-image-width") || e.target.classList.contains("inline-image-height")) {
        const container = e.target.closest(".inline-image-upload");
        const box = container.querySelector(".inline-image-box");
        const widthInput = container.querySelector(".inline-image-width");
        const heightInput = container.querySelector(".inline-image-height");

        // Quti ("тўртбурчак") HAR DOIM Eni/Bo'yi qiymatlariga mos —
        // rasm bor-yo'qligidan qat'i nazar (bo'sh qoldirilsa — standart
        // 140x100'ga qaytadi). Rasm bo'lsa, ICHINI to'liq qoplaydi
        // (".inline-image-preview{width:100%;height:100%}", CSS).
        const w = widthInput.value ? Number(widthInput.value) : 140;
        const h = heightInput.value ? Number(heightInput.value) : 100;
        container.dataset.width = widthInput.value ? w : "";
        container.dataset.height = heightInput.value ? h : "";
        box.style.width = w + "px";
        box.style.height = h + "px";
        return;
    }

    if (e.target.classList.contains("inline-video-input")) {
        const container = e.target.closest(".inline-video-upload");
        const file = e.target.files[0];
        if (!file) return;

        const formData = new FormData();
        formData.append("video", file);

        const uploadBtn = container.querySelector(".inline-video-btn");
        const originalLabel = uploadBtn.textContent;
        uploadBtn.disabled = true;
        uploadBtn.textContent = "⏳";

        try {
            const res = await fetch("/api/question/upload-commentary-video", { method: "POST", body: formData });
            const data = await res.json();

            if (!res.ok) {
                showAlertModal(data.error || "❌ Videoni yuklab bo'lmadi");
                return;
            }

            container.dataset.currentUrl = data.url;
            const preview = container.querySelector(".inline-video-preview");
            preview.src = data.url;
            preview.classList.remove("hidden");
            container.querySelector(".inline-remove-video-btn").classList.remove("hidden");
        } catch (err) {
            console.error(err);
            showAlertModal("❌ Videoni yuklashda tarmoq xatoligi");
        } finally {
            uploadBtn.disabled = false;
            uploadBtn.textContent = originalLabel;
        }
    }
});

// ========================================================================
// "Savol formasi" — yangi savol qo'shish VA mavjudini tahrirlash, ENDI
// BITTA A4 kenglikdagi modalda (foydalanuvchi so'rovi, 2026-09-05:
// "testni tahrirlashni ham modalda ochiladigan qilib mosla, yangi test
// yaratishni ham"). Ilgari: tahrirlash — qatorning O'ZINI (inline)
// tahrirlanadigan holatga o'tkazardi (enableInlineEdit/saveInlineEdit/
// cancelInlineEdit, endi butunlay OLIB TASHLANDI); yaratish — butunlay
// boshqa sahifaga (test-form.html) o'tkazardi. To'g'ri javob(lar) uchun
// izoh — test-form.js'dagi BILAN BIR XIL rich-text (boy matn) muharriri
// bilan, lekin BITTA, UMUMIY sub-modal orqali (buildCommentaryModalHtml,
// pastda) — nechta javob to'g'ri deb belgilanishidan qat'i nazar, bitta
// izoh BARCHASIGA baravar qo'llanadi (foydalanuvchi so'rovi, 2026-09-05).
// Modal BIR MARTA (DOMContentLoaded'da) yaratiladi, har safar ochilishda
// resetQuestionFormModal() orqali tozalanadi/qayta to'ldiriladi.
// ========================================================================

const ANSWER_LETTERS = ["A", "B", "C", "D", "E"];

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text ?? "";
    return div.innerHTML;
}

// Joriy modal rejimi: 'create' | 'edit' (+ tahrirlanayotgan savol ID'si).
let qformMode = "create";
let qformQuestionId = null;

function buildQuestionFormModalHtml() {
    return `
        <div id="questionFormModal" class="modal-overlay">
            <div class="question-form-modal">
                <h2 class="modal-h2" id="qformTitle">➕ Yangi savol qo'shish</h2>

                <!-- "📥 Import from Excel" / "📄 Shablon" — FAQAT "create"
                     rejimida (bitta savol qo'shish o'rniga BUTUN Excel
                     fayldan bir yo'la ko'p savol qo'shishning muqobil
                     yo'li) ko'rinadi — tahrirlashda ma'nosiz (foydalanuvchi
                     so'rovi, 2026-09-05: "импорт ва шаблон кнопкаларини
                     тест яратиш модалини ичига жойлаштир"). -->
                <div class="qform-import-row" id="qformImportRow">
                    <button type="button" class="export-excel-btn" onclick="importExcel()" title="Avval shablonni yuklab oling, to'ldirib qayta import qiling.">📥 Import from Excel</button>
                    <input type="file" id="excelFile" accept=".xlsx" hidden>
                    <button type="button" class="export-excel-btn" onclick="downloadTemplate()" title="Excel import shablonini yuklab olish">📄 Shablon</button>
                </div>

                <div class="qform-group">
                    <label for="qformQuestionText">Savol</label>
                    <!-- Rasm CHAPDA, matn O'NGDA (yonma-yon) — foydalanuvchi
                         so'rovi, 2026-09-05: "Расм ва текстни ёнма-ён
                         жойлаш мумкин бўлиши керак ... курсор расмни ўнг
                         томонида энг тепадан бошланиши керак" (javob
                         qatorlari — .qform-answer — bilan bir xil andoza). -->
                    <div class="qform-question-row">
                        <div id="qformQuestionImageWidget"></div>
                        <textarea id="qformQuestionText" class="auto-textarea" placeholder="Savol matnini kiriting"></textarea>
                    </div>
                </div>

                <div class="qform-answers">
                    <h3>Javob variantlari</h3>
                    <p class="qform-multi-correct-hint">☑️ Bitta yoki bir nechta to'g'ri javobni belgilang</p>
                    <div id="qformAnswersContainer"></div>
                    <!-- Izoh — BITTA, BARCHA to'g'ri belgilangan javoblar uchun UMUMIY
                         (foydalanuvchi so'rovi, 2026-09-05: "бирдан ортиқ жавоб
                         белгиланганда, изоҳ фақат 1 та кўриниши керак. Фақат 1
                         марта қўшилади ҳаммаси учун умумий"). Ilgari har bir
                         to'g'ri javobning O'ZINING alohida izoh sub-modali bor
                         edi (5 tagacha) — endi BITTA tugma, kamida bitta javob
                         to'g'ri deb belgilansa ko'rinadi. -->
                    <button type="button" class="qform-comment-btn hidden" id="qformSharedCommentBtn" onclick="openCommentaryModal()">✏️ Izoh (to'g'ri javob(lar) uchun)</button>
                </div>

                <div class="modal-footer">
                    <button type="button" class="qform-delete-btn hidden" id="qformDeleteBtn" onclick="deleteFromQuestionForm()">🗑️ O'chirish</button>
                    <button type="button" class="qform-cancel-btn" onclick="closeQuestionFormModal()">Bekor qilish</button>
                    <button type="button" class="qform-save-btn" id="qformSaveBtn" onclick="saveQuestionForm()">💾 Saqlash</button>
                </div>
            </div>
        </div>
        <div id="qformCommentaryModalsContainer"></div>
    `;
}

function buildQformAnswerRowHtml(index) {
    return `
        <div class="qform-answer" data-answer-index="${index}">
            <input type="checkbox" class="qform-correct-checkbox" data-answer-index="${index}">
            <div class="qform-answer-image-slot" id="qformAnswerImageSlot-${index}"></div>
            <textarea class="auto-textarea qform-answer-text" placeholder="${ANSWER_LETTERS[index]} variantni kiriting..."></textarea>
        </div>
    `;
}

// To'g'ri javob(lar) uchun izoh — test-form.js'dagi BILAN BIR XIL A4
// kenglikdagi rich-text modal. BITTA, UMUMIY (foydalanuvchi so'rovi,
// 2026-09-05: "бирдан ортиқ жавоб белгиланганда, изоҳ фақат 1 та
// кўриниши керак ... ҳаммаси учун умумий") — ilgari har bir javob
// variantining O'ZINING alohida sub-modali bor edi (5 tagacha), endi
// BITTASI (id="commentaryModal-shared"), qaysi javob(lar) to'g'ri deb
// belgilanishidan qat'i nazar.
function buildCommentaryModalHtml() {
    const editorId = `commentaryRichEditor-shared`;
    return `
        <div id="commentaryModal-shared" class="modal-overlay">
            <div class="commentary-form-modal qform-commentary-modal">
                <h2 class="modal-h2">✏️ To'g'ri javob uchun izoh</h2>

                <div class="rich-toolbar">
                    <button type="button" onclick="richExec('${editorId}','bold')" title="Qalin (Ctrl+B)"><b>B</b></button>
                    <button type="button" onclick="richExec('${editorId}','italic')" title="Kursiv (Ctrl+I)"><i>I</i></button>
                    <button type="button" onclick="richExec('${editorId}','underline')" title="Tagi chizilgan (Ctrl+U)"><u>U</u></button>
                    <button type="button" onclick="richExec('${editorId}','strikeThrough')" title="Chizib o'tilgan"><s>S</s></button>
                    <button type="button" onclick="richExec('${editorId}','subscript')" title="Indeks (pastki, masalan H₂O)">X<sub>2</sub></button>
                    <button type="button" onclick="richExec('${editorId}','superscript')" title="Daraja (yuqori, masalan x²)">X<sup>2</sup></button>
                    <span class="rich-toolbar-sep"></span>
                    <select class="rich-toolbar-select" title="Shrift turi" onmousedown="saveRichSelection('${editorId}')" onchange="richFontName('${editorId}', this.value); this.selectedIndex=0;">
                        <option value="" selected disabled>🔤 Shrift</option>
                        <option value="Arial">Arial</option>
                        <option value="Georgia">Georgia</option>
                        <option value="Times New Roman">Times New Roman</option>
                        <option value="Courier New">Courier New</option>
                        <option value="Verdana">Verdana</option>
                    </select>
                    <select class="rich-toolbar-select" title="Shrift o'lchami" onmousedown="saveRichSelection('${editorId}')" onchange="richFontSize('${editorId}', this.value); this.selectedIndex=0;">
                        <option value="" selected disabled>🔠 O'lcham</option>
                        <option value="12">12</option>
                        <option value="14">14</option>
                        <option value="16">16</option>
                        <option value="18">18</option>
                        <option value="20">20</option>
                        <option value="24">24</option>
                    </select>
                    <span class="rich-toolbar-sep"></span>
                    <button type="button" class="rich-color-trigger" title="🎨 Harf rangi" onclick="toggleColorPalette(this, '${editorId}', 'fore')"><span class="rich-color-preview" style="background:#000000"></span></button>
                    <button type="button" class="rich-color-trigger" title="🖊️ Fon (bo'yash) rangi" onclick="toggleColorPalette(this, '${editorId}', 'hilite')"><span class="rich-color-preview" style="background:#fff59d"></span></button>
                    <span class="rich-toolbar-sep"></span>
                    <button type="button" onclick="richExec('${editorId}','justifyLeft')" title="Chapga tekislash"><svg viewBox="0 0 16 16" fill="currentColor"><rect x="0" y="1" width="16" height="2"/><rect x="0" y="5" width="10" height="2"/><rect x="0" y="9" width="16" height="2"/><rect x="0" y="13" width="10" height="2"/></svg></button>
                    <button type="button" onclick="richExec('${editorId}','justifyCenter')" title="Markazga tekislash"><svg viewBox="0 0 16 16" fill="currentColor"><rect x="0" y="1" width="16" height="2"/><rect x="3" y="5" width="10" height="2"/><rect x="0" y="9" width="16" height="2"/><rect x="3" y="13" width="10" height="2"/></svg></button>
                    <button type="button" onclick="richExec('${editorId}','justifyRight')" title="O'ngga tekislash"><svg viewBox="0 0 16 16" fill="currentColor"><rect x="0" y="1" width="16" height="2"/><rect x="6" y="5" width="10" height="2"/><rect x="0" y="9" width="16" height="2"/><rect x="6" y="13" width="10" height="2"/></svg></button>
                    <button type="button" onclick="richExec('${editorId}','justifyFull')" title="Ikki tomonga tekislash"><svg viewBox="0 0 16 16" fill="currentColor"><rect x="0" y="1" width="16" height="2"/><rect x="0" y="5" width="16" height="2"/><rect x="0" y="9" width="16" height="2"/><rect x="0" y="13" width="16" height="2"/></svg></button>
                    <span class="rich-toolbar-sep"></span>
                    <button type="button" onclick="richExec('${editorId}','outdent')" title="Chekinishni kamaytirish"><svg viewBox="0 0 16 16" fill="currentColor"><rect x="7" y="1" width="9" height="2"/><rect x="7" y="7" width="9" height="2"/><rect x="7" y="13" width="9" height="2"/><path d="M5 4 L1 8 L5 12 Z"/></svg></button>
                    <button type="button" onclick="richExec('${editorId}','indent')" title="Chekinishni oshirish"><svg viewBox="0 0 16 16" fill="currentColor"><rect x="7" y="1" width="9" height="2"/><rect x="7" y="7" width="9" height="2"/><rect x="7" y="13" width="9" height="2"/><path d="M1 4 L5 8 L1 12 Z"/></svg></button>
                    <select class="rich-toolbar-select" title="Qator oralig'i" onmousedown="saveRichSelection('${editorId}')" onchange="richLineSpacing('${editorId}', this.value); this.selectedIndex=0;">
                        <option value="" selected disabled>↕️ Oraliq</option>
                        <option value="1">1.0</option>
                        <option value="1.15">1.15</option>
                        <option value="1.5">1.5</option>
                        <option value="2">2.0</option>
                    </select>
                    <span class="rich-toolbar-sep"></span>
                    <button type="button" onclick="richExec('${editorId}','insertUnorderedList')" title="Ro'yxat">☰•</button>
                    <button type="button" onclick="richExec('${editorId}','insertOrderedList')" title="Raqamlangan ro'yxat">☰1</button>
                    <button type="button" onclick="triggerImageInsert('${editorId}')" title="Rasm qo'shish">🖼</button>
                    <input type="file" id="${editorId}-imageInput" accept="image/*" style="display:none;" onchange="richInsertImage('${editorId}', this)">
                    <button type="button" onclick="openVideoInsertModal('${editorId}')" title="Video qo'shish">🎬</button>
                    <span class="rich-toolbar-sep"></span>
                    <button type="button" onclick="richExec('${editorId}','removeFormat')" title="Formatni tozalash">🧹</button>
                </div>
                <div id="${editorId}" class="rich-text-editor commentary" contenteditable="true"
                     data-placeholder="To'g'ri javob uchun izoh kiriting..."></div>

                <button type="button" class="qform-link-btn hidden">🔗 Darsga havola qo'shish</button>

                <div class="modal-footer">
                    <button type="button" class="qform-save-btn" onclick="closeCommentaryModal()">✅ Yopish</button>
                </div>
            </div>
        </div>
    `;
}

function openCommentaryModal() {
    const editor = document.getElementById(`commentaryRichEditor-shared`);
    cleanupEmptyCaptions(`commentaryRichEditor-shared`);
    document.getElementById(`commentaryModal-shared`).classList.add("show");
    editor.focus();
}

function closeCommentaryModal() {
    cleanupEmptyCaptions(`commentaryRichEditor-shared`);
    document.getElementById(`commentaryModal-shared`).classList.remove("show");
}

// ========================================================================
// Rich-text muharrir — courseDetail.js ("Kursga dars qo'shish/tahrirlash")
// va test-form.js bilan BIR XIL funksiyalar (video/PPT/.docx import
// KIRITILMAGAN — "Matn formatlash + rasm" darajasi). Barcha funksiyalar
// "editorId" ni parametr sifatida qabul qiladi, shu sabab HAM "Savol
// formasi"dagi 5 ta izoh sub-modali, HAM alohida "💬 Izoh ko'rsatish"
// (#commentModal, id="modalCommentEditor") — BITTA nusxada, umumiy
// ishlatiladi.
// ========================================================================

function richExec(editorId, command) {
    document.getElementById(editorId).focus();
    document.execCommand(command, false, null);
}

// <input type="color"> yoki <select> bosilganda brauzer o'z (native)
// rang tanlash oynasi/dropdown'ini ochadi — bu FOKUSNI contenteditable'dan
// olib qo'yadi va shu bilan birga tanlangan matn (selection/Range) ham
// yo'qoladi. Yechim: shu boshqaruv elementi hali fokusni OLMASDAN turib
// ("mousedown" paytida), joriy selection'ni saqlab qo'yamiz, so'ng
// "onchange"da (editor.focus()'dan KEYIN) uni qayta tiklaymiz.
let savedRichSelection = { editorId: null, range: null };

function saveRichSelection(editorId) {
    const editor = document.getElementById(editorId);
    const sel = window.getSelection();
    if (sel.rangeCount > 0 && editor.contains(sel.anchorNode)) {
        savedRichSelection = { editorId, range: sel.getRangeAt(0).cloneRange() };
    }
}

function restoreRichSelection(editorId) {
    if (savedRichSelection.editorId !== editorId || !savedRichSelection.range) return;
    const sel = window.getSelection();
    sel.removeAllRanges();
    sel.addRange(savedRichSelection.range);
}

function richFontName(editorId, fontName) {
    if (!fontName) return;
    document.getElementById(editorId).focus();
    restoreRichSelection(editorId);
    document.execCommand('fontName', false, fontName);
}

// execCommand('fontSize', ...) haqiqiy piksel emas, faqat shartli 1-7
// oralig'idagi o'lchamlarni qabul qiladi — shuning uchun standart hiyla
// qo'llanadi: eng katta shartli o'lcham (7) qo'yiladi, so'ng natijadagi
// <font size="7"> teglari haqiqiy piksel o'lchamli <span>ga almashtiriladi.
function richFontSize(editorId, sizePx) {
    if (!sizePx) return;
    const editor = document.getElementById(editorId);
    editor.focus();
    restoreRichSelection(editorId);
    document.execCommand('fontSize', false, '7');
    editor.querySelectorAll('font[size="7"]').forEach(el => {
        const span = document.createElement('span');
        span.style.fontSize = sizePx + 'px';
        span.innerHTML = el.innerHTML;
        el.replaceWith(span);
    });
}

function richForeColor(editorId, color) {
    document.getElementById(editorId).focus();
    restoreRichSelection(editorId);
    document.execCommand('foreColor', false, color);
}

// Fon (bo'yash) rangi — ba'zi brauzerlar 'hiliteColor'ni qo'llab-
// quvvatlamaydi, shu sabab muvaffaqiyatsiz bo'lsa 'backColor'ga o'tiladi.
function richHiliteColor(editorId, color) {
    const editor = document.getElementById(editorId);
    editor.focus();
    restoreRichSelection(editorId);
    if (!document.execCommand('hiliteColor', false, color)) {
        document.execCommand('backColor', false, color);
    }
}

// ================= Rang palette (Word'dagi kabi tayyor ranglar) =================
const FORE_COLOR_PRESETS = ['#000000', '#FFFFFF', '#7F7F7F', '#C00000', '#FF0000', '#FFC000',
    '#FFFF00', '#92D050', '#00B050', '#00B0F0', '#0070C0', '#7030A0'];
const HILITE_COLOR_PRESETS = ['#FFFF00', '#00FF00', '#00FFFF', '#FF00FF', '#0000FF', '#FF0000',
    '#C00000', '#FFC000', '#92D050', '#ADD8E6', '#7030A0', '#FFFFFF'];

let colorPaletteEl = null;
let colorPaletteState = null; // { editorId, mode, triggerBtn }

function toggleColorPalette(triggerBtn, editorId, mode) {
    if (!colorPaletteEl) {
        colorPaletteEl = document.createElement('div');
        colorPaletteEl.className = 'rich-color-palette';
        colorPaletteEl.style.display = 'none';
        document.body.appendChild(colorPaletteEl);
    }

    const alreadyOpenForThis = colorPaletteEl.style.display === 'block'
        && colorPaletteState && colorPaletteState.triggerBtn === triggerBtn;
    if (alreadyOpenForThis) {
        closeColorPalette();
        return;
    }

    saveRichSelection(editorId);
    colorPaletteState = { editorId, mode, triggerBtn };

    const presets = mode === 'fore' ? FORE_COLOR_PRESETS : HILITE_COLOR_PRESETS;
    colorPaletteEl.innerHTML = '';

    const grid = document.createElement('div');
    grid.className = 'rich-color-grid';
    presets.forEach(color => {
        const sw = document.createElement('button');
        sw.type = 'button';
        sw.className = 'rich-color-swatch-btn';
        sw.style.background = color;
        sw.title = color;
        sw.onclick = () => applyColorFromPalette(color);
        grid.appendChild(sw);
    });
    colorPaletteEl.appendChild(grid);

    const customLabel = document.createElement('label');
    customLabel.className = 'rich-color-custom-label';
    customLabel.appendChild(document.createTextNode('🎨 Boshqa rang...'));
    const customInput = document.createElement('input');
    customInput.type = 'color';
    customInput.onmousedown = () => saveRichSelection(editorId);
    customInput.onchange = (e) => applyColorFromPalette(e.target.value);
    customLabel.appendChild(customInput);
    colorPaletteEl.appendChild(customLabel);

    const rect = triggerBtn.getBoundingClientRect();
    colorPaletteEl.style.top = (rect.bottom + 4) + 'px';
    colorPaletteEl.style.left = rect.left + 'px';
    colorPaletteEl.style.display = 'block';
}

function applyColorFromPalette(color) {
    if (!colorPaletteState) return;
    const { editorId, mode, triggerBtn } = colorPaletteState;

    if (mode === 'fore') {
        richForeColor(editorId, color);
    } else {
        richHiliteColor(editorId, color);
    }

    const preview = triggerBtn.querySelector('.rich-color-preview');
    if (preview) preview.style.background = color;
    closeColorPalette();
}

function closeColorPalette() {
    if (colorPaletteEl) colorPaletteEl.style.display = 'none';
    colorPaletteState = null;
}

document.addEventListener('click', (e) => {
    if (!colorPaletteEl || colorPaletteEl.style.display === 'none') return;
    if (colorPaletteEl.contains(e.target)) return;
    if (colorPaletteState && colorPaletteState.triggerBtn.contains(e.target)) return;
    closeColorPalette();
});

function isBlockElement(el) {
    return ['P', 'DIV', 'LI', 'H1', 'H2', 'H3', 'H4', 'BLOCKQUOTE'].includes(el.tagName);
}

// Qator oralig'i — bunday funksiya uchun tayyor execCommand yo'q, shuning
// uchun tanlangan matnga eng yaqin blok elementi qidirib topilib, unga
// line-height qo'yiladi.
function richLineSpacing(editorId, value) {
    if (!value) return;
    const editor = document.getElementById(editorId);
    editor.focus();
    restoreRichSelection(editorId);

    const selection = window.getSelection();
    let node = selection.rangeCount ? selection.getRangeAt(0).commonAncestorContainer : null;
    if (node && node.nodeType === Node.TEXT_NODE) node = node.parentElement;

    while (node && node !== editor && !isBlockElement(node)) {
        node = node.parentElement;
    }

    if (!node || node === editor) {
        editor.querySelectorAll('p, li, div, h1, h2, h3, h4, blockquote').forEach(el => el.style.lineHeight = value);
        editor.style.lineHeight = value;
    } else {
        node.style.lineHeight = value;
    }
}

// ================= Rasm qo'shish (izoh ichiga) =================
// Fayl tanlash oynasi ochilganda kursor tahrirlagichdan "chiqib ketadi" —
// shu sabab fayl tanlash OLDIN joriy kursor o'rnini (Range) saqlab
// qo'yamiz, keyin insert vaqtida O'SHA joyga qaytaramiz.
let richInsertSavedRange = null;

function captureEditorSelection(editorId) {
    const editor = document.getElementById(editorId);
    const sel = window.getSelection();
    if (!editor || !sel || sel.rangeCount === 0) return null;
    const range = sel.getRangeAt(0);
    if (!editor.contains(range.commonAncestorContainer)) return null;
    return range.cloneRange();
}

function restoreEditorSelection(editorId, savedRange) {
    const editor = document.getElementById(editorId);
    editor.focus();
    const sel = window.getSelection();
    sel.removeAllRanges();
    const range = savedRange ? savedRange.cloneRange() : document.createRange();
    if (!savedRange) {
        range.selectNodeContents(editor);
        range.collapse(false);
    }
    sel.addRange(range);
}

function triggerImageInsert(editorId) {
    richInsertSavedRange = captureEditorSelection(editorId);
    document.getElementById(`${editorId}-imageInput`).click();
}

// "🖼 Rasm qo'shish" — fayl tanlangach serverga yuklanadi, qaytgan URL
// kursor turgan joyga qo'yiladi ("rich-img-wrap" ichida, pastki-o'ng
// burchakdagi sudraladigan tutqich bilan birga — kattaligini
// kichraytirish mumkin bo'lishi uchun). /api/question/upload-image
// UMUMIY endpoint ishlatiladi (test-form.js bilan bir xil — savol/izoh
// rasmlari kurslarga bog'liq emas).
async function richInsertImage(editorId, fileInput) {
    const file = fileInput.files[0];
    if (!file) return;

    attachImageResizeHandlers(editorId);

    try {
        const formData = new FormData();
        formData.append("image", file);
        const res = await fetch("/api/question/upload-image", {
            method: "POST", body: formData
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "❌ Rasm yuklashda xatolik");
            return;
        }
        const url = escapeHtml(data.url);
        const html = `<span class="rich-img-wrap" contenteditable="false">`
            + `<img src="${url}">`
            + `<span class="rich-img-handle" title="Sudrab o'lchamini o'zgartiring"></span>`
            + `</span>&nbsp;`;
        restoreEditorSelection(editorId, richInsertSavedRange);
        document.execCommand('insertHTML', false, html);
        injectAlignBars(editorId);
        injectCaptions(editorId);
    } catch (err) {
        console.error(err);
        showAlertModal("❌ Rasm yuklashda tarmoq xatoligi");
    } finally {
        fileInput.value = "";
    }
}

// ========================================================================
// "🎬 Video qo'shish" — courseDetail.js ("Kursga dars qo'shish/tahrirlash")
// bilan BIR XIL (foydalanuvchi so'rovi, 2026-09-05: "Форматларни ичига
// курст таҳрирлашдаги каби видео қўйишни қўш. Размерини бошқариш мумкин
// бўлсин"). YouTube/Vimeo/Facebook/Instagram havolasi YOKI kompyuterdan
// fayl — kursor turgan joyga qo'yiladi, rasm kabi tutqichni sudrab
// o'lchamini o'zgartirish mumkin (startImageResize — "img, video, iframe"
// hammasini birdek qamrab oladi). Endpoint — /api/question/upload-
// commentary-video (bu sahifada COURSE_ID yo'q, umumiy endpoint).
// ========================================================================
let videoInsertTargetEditorId = null;

function openVideoInsertModal(editorId) {
    videoInsertTargetEditorId = editorId;
    richInsertSavedRange = captureEditorSelection(editorId);
    document.getElementById("videoInsertUrlInput").value = "";
    document.getElementById("videoInsertFileInput").value = "";
    document.getElementById("videoInsertWidthInput").value = "480";
    document.getElementById("videoInsertModal").classList.remove("hidden");
}

function closeVideoInsertModal() {
    document.getElementById("videoInsertModal").classList.add("hidden");
    videoInsertTargetEditorId = null;
}

function confirmVideoInsert() {
    const editorId = videoInsertTargetEditorId;
    if (!editorId) return;

    const url = document.getElementById("videoInsertUrlInput").value.trim();
    const fileInput = document.getElementById("videoInsertFileInput");
    const hasFile = fileInput.files && fileInput.files.length > 0;
    const width = normalizeVideoWidth(document.getElementById("videoInsertWidthInput").value);

    if (!url && !hasFile) {
        showAlertModal("❌ Video havolasini kiriting yoki video fayl tanlang");
        return;
    }

    closeVideoInsertModal();

    if (url) {
        insertVideoEmbedHtml(editorId, url, width);
    } else {
        fileInput.dataset.pendingWidth = width;
        richInsertUploadedVideo(editorId, fileInput);
    }
}

function normalizeVideoWidth(raw) {
    const trimmed = (raw || "").trim();
    if (!trimmed) return "480px";
    if (trimmed.endsWith("%") || trimmed.endsWith("px")) return trimmed;
    return trimmed + "px";
}

// YouTube pleyeri "videoId" sifatida FAQAT xom ID'ni qabul qiladi, to'liq
// URL emas — courseDetail.js bilan bir xil.
function extractYouTubeId(input) {
    if (!input) return input;
    const trimmed = input.trim();
    if (!trimmed.includes("/") && !trimmed.includes("?")) return trimmed;

    try {
        const url = new URL(trimmed);
        if (url.hostname.includes("youtu.be")) {
            return url.pathname.slice(1);
        }
        if (url.searchParams.get("v")) {
            return url.searchParams.get("v");
        }
        const embedMatch = url.pathname.match(/\/embed\/([^/?]+)/);
        if (embedMatch) return embedMatch[1];
    } catch (e) {
        // URL sifatida parse bo'lmadi — ehtimol shunchaki ID, o'zgarishsiz qoldiramiz.
    }
    return trimmed;
}

function isYouTubeSource(source) {
    const trimmed = (source || "").trim();
    if (!trimmed) return false;
    if (/youtube\.com|youtu\.be/i.test(trimmed)) return true;
    return !trimmed.includes("/") && !trimmed.includes(".") && !trimmed.includes(" ");
}

// Instagram ODDIY <iframe> orqali ko'rsatilmaydi (ularning xavfsizlik
// siyosati bloklaydi) — rasmiy blockquote+embed.js usuli kerak
// (courseDetail.js bilan bir xil).
let instagramEmbedScriptState = "idle"; // idle | loading | loaded

function ensureInstagramEmbedProcessed() {
    if (instagramEmbedScriptState === "loaded") {
        if (window.instgrm && window.instgrm.Embeds) window.instgrm.Embeds.process();
        return;
    }
    if (instagramEmbedScriptState === "loading") return;
    instagramEmbedScriptState = "loading";
    const script = document.createElement("script");
    script.src = "https://www.instagram.com/embed.js";
    script.async = true;
    script.onload = () => {
        instagramEmbedScriptState = "loaded";
        if (window.instgrm && window.instgrm.Embeds) window.instgrm.Embeds.process();
    };
    document.body.appendChild(script);
}

function insertVideoEmbedHtml(editorId, source, width) {
    restoreEditorSelection(editorId, richInsertSavedRange);
    attachImageResizeHandlers(editorId);

    const trimmed = source.trim();
    let mediaHtml;
    let isInstagram = false;

    if (isYouTubeSource(trimmed)) {
        const videoId = escapeHtml(extractYouTubeId(trimmed));
        mediaHtml = `<iframe src="https://www.youtube.com/embed/${videoId}" style="width:${width};max-width:100%;aspect-ratio:16/9;border:0;display:block" allowfullscreen></iframe>`;
    } else if (/facebook\.com|fb\.watch/i.test(trimmed)) {
        const encodedUrl = encodeURIComponent(trimmed);
        mediaHtml = `<iframe src="https://www.facebook.com/plugins/video.php?href=${encodedUrl}&show_text=false" style="width:${width};max-width:100%;aspect-ratio:16/9;border:0;display:block" allowfullscreen></iframe>`;
    } else if (/instagram\.com/i.test(trimmed)) {
        isInstagram = true;
        const url = escapeHtml(trimmed);
        mediaHtml = `<blockquote class="instagram-media" data-instgrm-permalink="${url}" data-instgrm-version="14" style="width:${width};max-width:100%;min-width:220px;margin:0 auto;"></blockquote>`;
    } else if (/\.(mp4|webm|ogg|ogv|mov)(\?|$)/i.test(trimmed)) {
        const url = escapeHtml(trimmed);
        mediaHtml = `<video src="${url}" controls style="width:${width};max-width:100%;display:block"></video>`;
    } else {
        const url = escapeHtml(trimmed);
        mediaHtml = `<iframe src="${url}" style="width:${width};max-width:100%;aspect-ratio:16/9;border:0;display:block" allowfullscreen></iframe>`;
    }

    const html = `<span class="rich-img-wrap" contenteditable="false">`
        + mediaHtml
        + `<span class="rich-img-handle" title="Sudrab o'lchamini o'zgartiring"></span>`
        + `</span>&nbsp;`;
    document.execCommand('insertHTML', false, html);
    injectAlignBars(editorId);
    injectCaptions(editorId);
    if (isInstagram) ensureInstagramEmbedProcessed();
}

async function richInsertUploadedVideo(editorId, fileInput) {
    const file = fileInput.files[0];
    if (!file) return;

    const width = fileInput.dataset.pendingWidth || "480px";
    delete fileInput.dataset.pendingWidth;

    attachImageResizeHandlers(editorId);

    try {
        const formData = new FormData();
        formData.append("video", file);
        const res = await fetch("/api/question/upload-commentary-video", {
            method: "POST", body: formData
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "❌ Video yuklashda xatolik");
            return;
        }
        const url = escapeHtml(data.url);
        const html = `<span class="rich-img-wrap" contenteditable="false">`
            + `<video src="${url}" controls style="width:${width};max-width:100%;display:block"></video>`
            + `<span class="rich-img-handle" title="Sudrab o'lchamini o'zgartiring"></span>`
            + `</span>&nbsp;`;
        restoreEditorSelection(editorId, richInsertSavedRange);
        document.execCommand('insertHTML', false, html);
        injectAlignBars(editorId);
        injectCaptions(editorId);
    } catch (err) {
        console.error(err);
        showAlertModal("❌ Video yuklashda tarmoq xatoligi");
    } finally {
        fileInput.value = "";
    }
}

// ================= Rasmni chapga/markazga/o'ngga surish =================
function injectAlignBars(editorId) {
    const editor = document.getElementById(editorId);
    if (!editor) return;
    editor.querySelectorAll('.rich-img-wrap').forEach((wrap) => {
        if (wrap.querySelector('.rich-img-align-bar')) return;
        const bar = document.createElement('span');
        bar.className = 'rich-img-align-bar';
        bar.setAttribute('contenteditable', 'false');
        bar.innerHTML =
            `<button type="button" title="Chapga surish" onclick="setMediaAlign(event,'left')">⬅</button>`
            + `<button type="button" title="Markazga surish" onclick="setMediaAlign(event,'center')">⏺</button>`
            + `<button type="button" title="O'ngga surish" onclick="setMediaAlign(event,'right')">➡</button>`;
        wrap.appendChild(bar);
    });
}

function setMediaAlign(evt, align) {
    evt.preventDefault();
    evt.stopPropagation();
    const wrap = evt.currentTarget.closest('.rich-img-wrap');
    if (!wrap) return;
    wrap.classList.remove('align-left', 'align-center', 'align-right');
    wrap.classList.add('align-' + align);
}

// ================= Rasm ostiga (ixtiyoriy) sarlavha =================
function injectCaptions(editorId) {
    const editor = document.getElementById(editorId);
    if (!editor) return;
    editor.querySelectorAll('.rich-img-wrap').forEach((wrap) => {
        if (wrap.querySelector('.rich-img-caption')) return;
        const caption = document.createElement('div');
        caption.className = 'rich-img-caption';
        caption.setAttribute('contenteditable', 'true');
        caption.setAttribute('data-placeholder', 'Sarlavha (ixtiyoriy)');
        wrap.appendChild(caption);
    });
}

// Saqlashdan (yoki modalni yopishdan) oldin chaqiriladi — foydalanuvchi
// yozmagan (bo'sh) sarlavha qatorlarini butunlay olib tashlaydi.
function cleanupEmptyCaptions(editorId) {
    const editor = document.getElementById(editorId);
    if (!editor) return;
    editor.querySelectorAll('.rich-img-caption').forEach((caption) => {
        if (!caption.textContent.trim()) {
            caption.remove();
        }
    });
}

// ================= Rasm o'lchamini sudrab o'zgartirish =================
let richResizeState = null;

function attachImageResizeHandlers(editorId) {
    const editor = document.getElementById(editorId);
    if (!editor || editor.dataset.resizeAttached) return;
    editor.dataset.resizeAttached = "1";

    editor.addEventListener('mousedown', (e) => startImageResize(e, e.clientX));
    editor.addEventListener('touchstart', (e) => {
        if (!e.touches[0]) return;
        startImageResize(e, e.touches[0].clientX);
    }, { passive: true });
}

// "rich-img-wrap" nomiga qaramay — rasm bilan bir qatorda video
// (<iframe>/<video>, openVideoInsertModal orqali qo'shilgan) ham shu
// tutqich orqali sudrab o'lchamini o'zgartirishi mumkin (foydalanuvchi
// so'rovi, 2026-09-05: "Размерини бошқариш мумкин бўлсин").
function startImageResize(e, clientX) {
    if (!e.target.classList || !e.target.classList.contains('rich-img-handle')) return;
    const wrap = e.target.closest('.rich-img-wrap');
    const media = wrap ? wrap.querySelector('img, video, iframe') : null;
    const editor = e.currentTarget;
    if (!media) return;

    if (e.cancelable) e.preventDefault();
    const rect = media.getBoundingClientRect();
    richResizeState = {
        media,
        editor,
        startX: clientX,
        startWidth: rect.width,
        ratio: rect.height / rect.width
    };
}

function updateImageResize(clientX) {
    if (!richResizeState) return;
    const { media, editor, startX, startWidth, ratio } = richResizeState;
    const delta = clientX - startX;
    const maxWidth = editor.getBoundingClientRect().width;
    const newWidth = Math.min(maxWidth, Math.max(40, startWidth + delta));
    media.style.width = newWidth + 'px';
    media.style.height = (newWidth * ratio) + 'px';
}

document.addEventListener('mousemove', (e) => updateImageResize(e.clientX));
document.addEventListener('mouseup', () => { richResizeState = null; });
document.addEventListener('touchmove', (e) => {
    if (!richResizeState || !e.touches[0]) return;
    updateImageResize(e.touches[0].clientX);
}, { passive: true });
document.addEventListener('touchend', () => { richResizeState = null; });

// Ilgari izoh sub-modalida rasm/video UCHUN ALOHIDA ("izohdan tashqari")
// yuklash tugmalari bor edi (buildInlineImageWidget/buildInlineVideoWidget,
// endi olib tashlandi — rich-toolbar'ning O'ZIDA rasm/video qo'shish
// tugmasi mavjud, foydalanuvchi so'rovi, 2026-09-05: "изоҳда ўзи расм
// қўшадиган кнопка бор, ташқаридагини олиб ташла"). Ammo ESKI (masalan
// test-form.js'da HAMON mavjud bo'lgan alohida widget orqali) saqlangan
// commentaryImageUrl/commentaryVideoUrl bo'lishi mumkin — ular BUTUNLAY
// YO'QOTILMASIN deb, tahrirlashda o'zgarishsiz shu yerda saqlab
// qo'yiladi va saveQuestionForm shundan o'qiydi (endi UI'da ko'rish/
// o'zgartirish imkoni yo'q — faqat mavjud qiymat saqlanib qoladi).
// BITTA obyekt — izoh o'zi BITTA, UMUMIY bo'lgani uchun (pastga qarang).
let qformLegacyCommentaryMedia = { image: null, video: null };

// Modalni bo'sh holatga qaytaradi — ochilishidan OLDIN (create) yoki
// to'ldirishdan OLDIN (edit) chaqiriladi.
function resetQuestionFormModal() {
    document.getElementById("qformQuestionText").value = "";
    document.getElementById("qformQuestionImageWidget").innerHTML =
        buildInlineImageWidget("question-image", "", "Savol rasmi");

    const answersContainer = document.getElementById("qformAnswersContainer");
    answersContainer.innerHTML = ANSWER_LETTERS.map((_, i) => buildQformAnswerRowHtml(i)).join("");

    ANSWER_LETTERS.forEach((_, i) => {
        document.getElementById(`qformAnswerImageSlot-${i}`).innerHTML =
            buildInlineImageWidget("answer-image", "", `${ANSWER_LETTERS[i]} javob rasmi`);
    });

    qformLegacyCommentaryMedia = { image: null, video: null };
    document.getElementById("commentaryRichEditor-shared").innerHTML = "";
    document.getElementById("commentaryModal-shared").classList.remove("show");
    document.getElementById("qformSharedCommentBtn").classList.add("hidden");
    // Rasm sudrab-o'lchamini-o'zgartirish — odatda faqat richInsertImage
    // paytida biriktiriladi (yangi rasm qo'shilganda), lekin TAHRIRLASH
    // rejimida muharrir ALLAQACHON (avval saqlangan) rasmlar bilan
    // to'ldiriladi — shu sabab bu yerda OLDINDAN biriktirilib qo'yiladi.
    attachImageResizeHandlers("commentaryRichEditor-shared");

    document.querySelectorAll(".qform-link-btn").forEach(btn => {
        btn.classList.toggle("hidden", !modalTopicCourseLink);
    });

    document.querySelectorAll(".qform-answer textarea").forEach(t => {
        t.style.height = "auto";
        t.style.height = t.scrollHeight + "px";
    });
}

async function openQuestionFormModal(mode, questionId) {
    qformMode = mode;
    qformQuestionId = questionId ?? null;

    resetQuestionFormModal();

    document.getElementById("qformTitle").textContent =
        mode === "edit" ? "✏️ Savolni tahrirlash" : "➕ Yangi savol qo'shish";
    document.getElementById("qformDeleteBtn").classList.toggle("hidden", mode !== "edit");
    // "📥 Import from Excel" / "📄 Shablon" — faqat "create" rejimida
    // (bitta mavjud savolni tahrirlashda ma'nosiz).
    document.getElementById("qformImportRow").classList.toggle("hidden", mode !== "create");

    if (mode === "edit") {
        try {
            const res = await fetch(`/question/${questionId}`);
            if (!res.ok) throw new Error("Savolni yuklashda xatolik");
            const q = await res.json();
            fillQuestionFormModal(q);
        } catch (err) {
            console.error(err);
            showAlertModal("❌ Savolni yuklashda xatolik yuz berdi");
            return;
        }
    }

    document.getElementById("questionFormModal").classList.add("show");
}

function fillQuestionFormModal(q) {
    document.getElementById("qformQuestionText").value = q.questionText || "";
    if (q.imageUrl) {
        document.getElementById("qformQuestionImageWidget").innerHTML =
            buildInlineImageWidget("question-image", q.imageUrl, "Savol rasmi", q.imageWidth, q.imageHeight);
    }

    const answers = (q.answers || []).slice(0, 5);

    // Eski (5-variant qo'shilishidan OLDIN yaratilgan) savollarda faqat
    // 4 ta javob bor — ortiqcha (bu savolda umuman mavjud bo'lmagan)
    // qatorlar YASHIRILADI, aks holda saqlashda "bo'sh javob" sifatida
    // talab qilinib qolardi (renderQuestionsTable'dagi bilan bir xil
    // g'oya — "E" ustuni bo'sh bo'lishi mumkin).
    ANSWER_LETTERS.forEach((_, i) => {
        const row = document.querySelector(`.qform-answer[data-answer-index="${i}"]`);
        row?.classList.toggle("qform-answer-unused", i >= answers.length);
    });

    // Izoh BITTA, UMUMIY — BARCHA to'g'ri javoblar bir xil izohga ega
    // deb qaraladi, shu sabab shu FUNKSIYA ICHIDA emas, pastda, BIR
    // MARTA (birinchi to'g'ri javobdan) to'ldiriladi (foydalanuvchi
    // so'rovi, 2026-09-05: "изоҳ фақат 1 та кўриниши керак ... ҳаммаси
    // учун умумий" — question.js/renderQuestionsTable'dagi
    // "correctAnswers[0]" konvensiyasi bilan bir xil).
    const firstCorrect = answers.find(a => a.isTrue);

    answers.forEach((a, i) => {
        const row = document.querySelector(`.qform-answer[data-answer-index="${i}"]`);
        if (!row) return;

        row.querySelector(".qform-answer-text").value = a.answerText || "";
        row.dataset.answerId = a.id;

        if (a.imageUrl) {
            document.getElementById(`qformAnswerImageSlot-${i}`).innerHTML =
                buildInlineImageWidget("answer-image", a.imageUrl, `${ANSWER_LETTERS[i]} javob rasmi`, a.imageWidth, a.imageHeight);
        }

        if (a.isTrue) {
            row.querySelector(".qform-correct-checkbox").checked = true;
        }
    });

    if (firstCorrect) {
        document.getElementById("qformSharedCommentBtn").classList.remove("hidden");
        document.getElementById("commentaryRichEditor-shared").innerHTML = firstCorrect.commentary || "";
        // Eski, ALOHIDA (rich-toolbar'dan tashqarida) yuklangan izoh
        // rasmi/videosi — endi UI'da ko'rinmaydi/o'zgartirilmaydi, lekin
        // saqlashda yo'qotib qo'yilmasin deb qiymati saqlab qo'yiladi.
        qformLegacyCommentaryMedia = {
            image: firstCorrect.commentaryImageUrl || null,
            video: firstCorrect.commentaryVideoUrl || null
        };
        // Mavjud izohdagi rasmlarga (agar bo'lsa) tekislash tugmalari/
        // sarlavha qatorini qayta biriktiradi — bular innerHTML orqali
        // saqlangan HTML'da YO'Q (faqat runtime'da qo'shiladi).
        injectAlignBars("commentaryRichEditor-shared");
        injectCaptions("commentaryRichEditor-shared");
    }

    document.querySelectorAll(".qform-answer textarea").forEach(t => {
        t.style.height = "auto";
        t.style.height = t.scrollHeight + "px";
    });
}

function closeQuestionFormModal() {
    document.getElementById("questionFormModal").classList.remove("show");
    document.getElementById("commentaryModal-shared")?.classList.remove("show");
}

// Checkbox belgilansa/bekor qilinsa — umumiy "✏️Izoh" tugmasi ko'rinadi/
// yashiriladi, KAMIDA BITTA javob to'g'ri deb belgilanishiga qarab (izoh
// BITTA, BARCHA to'g'ri javoblar uchun UMUMIY — foydalanuvchi so'rovi,
// 2026-09-05).
document.addEventListener("change", (e) => {
    if (!e.target.classList.contains("qform-correct-checkbox")) return;
    const anyChecked = document.querySelectorAll(".qform-correct-checkbox:checked").length > 0;
    document.getElementById("qformSharedCommentBtn").classList.toggle("hidden", !anyChecked);
});

// "🔗 Darsga havola qo'shish" — umumiy izoh sub-modali ichida, standalone
// #commentModal bilan BIR XIL topicCourseLink'dan foydalanadi (pastda,
// "MODAL commentary" bo'limida bir marta yuklanadi).
document.addEventListener("click", (e) => {
    if (!e.target.classList.contains("qform-link-btn") || !modalTopicCourseLink) return;
    const editor = document.getElementById("commentaryRichEditor-shared");
    if (!editor) return;
    editor.focus();
    document.execCommand("insertHTML", false, buildTopicLinkHtml(modalTopicCourseLink));
});

// Savol/javob matni auto-height (test-form.js'dagi bilan bir xil).
document.addEventListener("input", (e) => {
    if (!e.target.classList.contains("auto-textarea")) return;
    e.target.style.height = "auto";
    e.target.style.height = e.target.scrollHeight + "px";
});

async function saveQuestionForm() {
    const questionText = document.getElementById("qformQuestionText").value.trim();
    if (!questionText) {
        showAlertModal("❌ Savol matnini kiriting");
        return;
    }

    // Eski (4 javobli) savollarni tahrirlashda "qform-answer-unused" deb
    // belgilangan qator (bu savolda umuman mavjud emas) o'tkazib
    // yuboriladi — bo'sh javob sifatida talab qilinmaydi/yuborilmaydi
    // (fillQuestionFormModal bilan bir xil g'oya).
    const answerRows = [...document.querySelectorAll(".qform-answer")]
        .filter(row => !row.classList.contains("qform-answer-unused"));
    const texts = [];
    const answers = [];

    // Izoh — BITTA, BARCHA to'g'ri belgilangan javoblar uchun UMUMIY
    // (foydalanuvchi so'rovi, 2026-09-05: "изоҳ фақат 1 та кўриниши
    // керак ... ҳаммаси учун умумий"). Shu sabab bir marta, tashqarida
    // o'qiladi — har bir javob uchun ALOHIDA emas.
    cleanupEmptyCaptions("commentaryRichEditor-shared");
    const sharedEditor = document.getElementById("commentaryRichEditor-shared");
    const sharedCommentaryHtml = sharedEditor.innerHTML.trim();
    // To'g'ri javob uchun izoh BO'SH BO'LISHI MUMKIN EMAS
    // (QuestionService#updateQuestion — validation.textFieldMustNotBeEmpty).
    // Muharrir bo'sh qoldirilgan bo'lsa — /api/question/save (yaratish)
    // qanday standart qo'yishini takrorlaymiz.
    const sharedCommentary = sharedEditor.textContent.trim() ? sharedCommentaryHtml : "To'g'ri javob";

    for (const row of answerRows) {
        const index = Number(row.dataset.answerIndex);
        const textInput = row.querySelector(".qform-answer-text");
        const value = textInput.value.trim();

        if (!value) {
            showAlertModal("❌ Barcha javoblarni to'ldiring");
            textInput.focus();
            return;
        }
        texts.push(value.toLowerCase());

        const isCorrect = row.querySelector(".qform-correct-checkbox").checked;
        const imageWidget = document.getElementById(`qformAnswerImageSlot-${index}`)
            .querySelector(".inline-image-upload");

        answers.push({
            id: row.dataset.answerId ? Number(row.dataset.answerId) : undefined,
            answerText: value,
            isTrue: isCorrect,
            commentary: isCorrect ? sharedCommentary : null,
            imageUrl: imageWidget?.dataset.currentUrl || null,
            imageWidth: imageWidget?.dataset.width ? Number(imageWidget.dataset.width) : null,
            imageHeight: imageWidget?.dataset.height ? Number(imageWidget.dataset.height) : null,
            // Rasm/video endi rich-toolbar orqali TO'G'RIDAN-TO'G'RI izoh
            // matni ICHIGA joylashadi — alohida commentaryImageUrl/
            // commentaryVideoUrl maydonlariga ENDI hech narsa yozilmaydi,
            // faqat ESKI (masalan test-form.js'dagi hamon mavjud alohida
            // widget orqali) saqlangan qiymat bo'lsa — o'zgarishsiz
            // saqlanib qoladi (qformLegacyCommentaryMedia, BARCHA to'g'ri
            // javoblarga bir xil qo'llanadi).
            commentaryImageUrl: isCorrect ? qformLegacyCommentaryMedia.image : null,
            commentaryVideoUrl: isCorrect ? qformLegacyCommentaryMedia.video : null
        });
    }

    if (new Set(texts).size !== texts.length) {
        showAlertModal("❌ Javob variantlari bir xil bo'lishi mumkin emas");
        return;
    }

    if (!answers.some(a => a.isTrue)) {
        showAlertModal("❌ Kamida bitta to'g'ri javobni belgilang");
        return;
    }

    const questionImageWidget = document.getElementById("qformQuestionImageWidget")
        .querySelector(".inline-image-upload");
    const questionImageUrl = questionImageWidget?.dataset.currentUrl || null;
    const questionImageWidth = questionImageWidget?.dataset.width ? Number(questionImageWidget.dataset.width) : null;
    const questionImageHeight = questionImageWidget?.dataset.height ? Number(questionImageWidget.dataset.height) : null;

    const saveBtn = document.getElementById("qformSaveBtn");
    saveBtn.disabled = true;

    try {
        let res;
        if (qformMode === "edit") {
            const payload = { id: qformQuestionId, questionText, imageUrl: questionImageUrl, imageWidth: questionImageWidth, imageHeight: questionImageHeight, answers };
            res = await fetch("/api/question/update", {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });
        } else {
            const payload = { topicId: Number(topicId), questionText, imageUrl: questionImageUrl, imageWidth: questionImageWidth, imageHeight: questionImageHeight, answers };
            res = await fetch("/api/question/save", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });
        }

        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            throw new Error(data.message || data.error || "Saqlashda xatolik");
        }

        showAlert(qformMode === "edit" ? "✅ Muvaffaqiyatli saqlandi." : "✅ Test muvaffaqiyatli saqlandi", "success");
        closeQuestionFormModal();
        await reloadCurrentQuestionsView();
    } catch (err) {
        console.error(err);
        showAlertModal("❌ " + (err.message || "Saqlashda xatolik"));
    } finally {
        saveBtn.disabled = false;
    }
}

// Tahrirlash modalidan to'g'ridan-to'g'ri o'chirish — mavjud deleteQuestion
// (soft-delete, "🗑️ O'chirilganlar" panelidan qaytariladi) bilan bir xil.
async function deleteFromQuestionForm() {
    if (qformMode !== "edit" || !qformQuestionId) return;
    closeQuestionFormModal();
    await deleteQuestion(qformQuestionId);
}

// "O'chirilganlar savati"ga o'tkazadi (soft-delete) — darhol butunlay
// o'chmaydi, "🗑️ O'chirilganlar" panelidan ("♻️ Tiklash") bir zumda
// qaytariladi (QuestionService.deleteQuestion).
async function deleteQuestion(questionId) {

    if (!await showConfirmModal("Rostdan ham savolni o'chirmoqchimisiz?\n\n(Butunlay o'chmaydi — \"🗑️ O'chirilganlar\" panelidan qaytarish mumkin.)", { danger: true })) return;

    try {
        const res = await fetch(`/api/question/${questionId}`, {method: "DELETE"});
        if (!res.ok) throw new Error("O‘chirishda xatolik");

        await loadQuestions(topicId, currentPage);
        refreshQuestionTrashBadge();
    } catch (e) {
        showAlertModal(e.message);
    }
}

// "← DARSGA QAYTISH" — TEST BOSHQARUVI'dagi ("/topics") ANIQ shu dars
// qatoriga qaytaradi (oldin history.back() edi — ba'zan foydalanuvchi
// kutgan joyga emas, tasodifiy oldingi sahifaga olib borardi; test-form.js
// #backBtn bilan bir xil yechim). "?focus=" orqali topic.js'ga qaysi
// darsga fokus tushishi kerakligi ham beriladi (topic.js#afterStartPage).
// Fetch muvaffaqiyatsiz bo'lsa — history.back() eskicha fallback.
// (Tugma matni ilgari "Mavzuga qaytish" edi — bu noto'g'ri edi, chunki
// bu yerdan haqiqatda Darsga (Topic) qaytiladi, Mavzuga (TopicSection)
// emas — foydalanuvchi so'rovi, 2026-09-05.)
function goBack() {
    fetch(`/api/topic/${topicId}/location`)
        .then(r => r.ok ? r.json() : null)
        .then(loc => {
            if (!loc) {
                history.back();
                return;
            }
            window.location.href = loc.sectionId
                ? `/topics?scienceId=${loc.scienceId}&sectionId=${loc.sectionId}&focus=${topicId}`
                : `/topics?scienceId=${loc.scienceId}&focus=${topicId}`;
        })
        .catch(() => history.back());
}

// "🗑️ O'chirilganlar" paneli — soft-delete qilingan savollar ro'yxati
// (bir zumda "♻️ Tiklash" qilinadigan). Panel yopiq holatda boshlanadi,
// bosilganda ochilib ro'yxatni yuklaydi.
let questionTrashOpen = false;

function toggleQuestionTrash() {
    questionTrashOpen = !questionTrashOpen;
    document.getElementById("questionTrashPanel").style.display = questionTrashOpen ? "block" : "none";
    if (questionTrashOpen) {
        loadQuestionTrash();
    }
}

function escapeHtmlTrash(text) {
    const div = document.createElement("div");
    div.textContent = text ?? "";
    return div.innerHTML;
}

// O'chirilganlar savatida belgilangan savol ID'lari — guruh holatida
// BUTUNLAY o'chirish uchun (asosiy jadvaldagi selectedQuestionIds'dan
// ALOHIDA — ikkalasi butunlay boshqa ro'yxatlar). Panel har safar qayta
// yuklanganda (loadQuestionTrash) tozalanadi.
let selectedTrashQuestionIds = new Set();

async function loadQuestionTrash() {
    const list = document.getElementById("questionTrashList");
    list.innerHTML = "<p>Yuklanmoqda...</p>";
    selectedTrashQuestionIds.clear();

    try {
        const res = await fetch(`/api/question/deleted?topicId=${topicId}`);
        if (!res.ok) {
            list.innerHTML = "<p>Yuklashda xatolik</p>";
            return;
        }
        const items = await res.json();
        setTrashBadgeCount("questionTrashBadge", items.length);
        if (!items.length) {
            list.innerHTML = "<p>O'chirilgan savol yo'q</p>";
            return;
        }
        list.innerHTML = `
            <div class="trash-bulk-actions">
                <label><input type="checkbox" id="selectAllTrashCheckbox" onchange="toggleSelectAllTrash(this)"> Hammasini belgilash</label>
                <button id="bulkRestoreBtn" class="restore-bulk-btn hidden" onclick="restoreSelectedQuestions()">♻️ Tanlanganlarni tiklash (<span id="bulkRestoreCount">0</span>)</button>
                <button id="bulkPermanentDeleteBtn" class="bulk-delete-btn hidden" onclick="permanentlyDeleteSelectedQuestions()">🗑️ Tanlanganlarni BUTUNLAY o'chirish (<span id="bulkPermanentDeleteCount">0</span>)</button>
            </div>
            ${items.map(q => `
            <div class="row">
                <input type="checkbox" class="trash-select-checkbox" data-question-id="${q.id}" onchange="onTrashCheckboxChange(${q.id}, this)">
                <div>${escapeHtmlTrash(q.questionText)} — ${formatQuestionTrashDate(q.deletedAt)}da o'chirilgan</div>
                <div class="row-actions">
                    <button onclick="restoreQuestion(${q.id})">♻️ Tiklash</button>
                    <button class="danger-btn" onclick="permanentlyDeleteQuestion(${q.id})">🗑️ Butunlay o'chirish</button>
                </div>
            </div>
        `).join("")}`;
        updateBulkPermanentDeleteButton();
    } catch (err) {
        console.error(err);
        list.innerHTML = "<p>Tarmoq xatoligi</p>";
    }
}

function onTrashCheckboxChange(questionId, checkbox) {
    if (checkbox.checked) {
        selectedTrashQuestionIds.add(questionId);
    } else {
        selectedTrashQuestionIds.delete(questionId);
        const selectAll = document.getElementById("selectAllTrashCheckbox");
        if (selectAll) selectAll.checked = false;
    }
    updateBulkPermanentDeleteButton();
}

// "Hammasini belgilash" (savatda) — faqat EKRANDAGI (shu mavzuning
// BARCHA o'chirilgan savollari — sahifalash yo'q) qatorlarga taalluqli.
function toggleSelectAllTrash(selectAllCheckbox) {
    document.querySelectorAll("#questionTrashList .trash-select-checkbox").forEach((cb) => {
        cb.checked = selectAllCheckbox.checked;
        const questionId = Number(cb.dataset.questionId);
        if (selectAllCheckbox.checked) {
            selectedTrashQuestionIds.add(questionId);
        } else {
            selectedTrashQuestionIds.delete(questionId);
        }
    });
    updateBulkPermanentDeleteButton();
}

function updateBulkPermanentDeleteButton() {
    const count = selectedTrashQuestionIds.size;

    const deleteBtn = document.getElementById("bulkPermanentDeleteBtn");
    if (deleteBtn) {
        document.getElementById("bulkPermanentDeleteCount").textContent = String(count);
        deleteBtn.classList.toggle("hidden", count === 0);
    }

    // "♻️ Tanlanganlarni tiklash" tugmasi ham xuddi shu tanlov (Set)
    // asosida ko'rsatiladi/yashiriladi — ikkalasi bir xil checkbox'larga
    // tegishli, faqat amal boshqacha (savatdan chiqarish vs butunlay o'chirish).
    const restoreBtn = document.getElementById("bulkRestoreBtn");
    if (restoreBtn) {
        document.getElementById("bulkRestoreCount").textContent = String(count);
        restoreBtn.classList.toggle("hidden", count === 0);
    }
}

// "♻️ Tanlanganlarni tiklash" — BARCHA belgilangan savollarni BITTA
// so'rovda savatdan qaytaradi (QuestionService.restoreQuestions,
// /api/question/bulk/restore) — bitta-bitta "♻️ Tiklash" bilan bir xil
// g'oya, faqat guruh holatida.
async function restoreSelectedQuestions() {
    const ids = [...selectedTrashQuestionIds];
    if (!ids.length) return;

    if (!await showConfirmModal(`${ids.length} ta savolni tiklamoqchimisiz?`)) return;

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
        selectedTrashQuestionIds.clear();
        loadQuestionTrash();
        await loadQuestions(topicId, currentPage);
    } catch (err) {
        console.error(err);
        showAlertModal(err.message || "Tarmoq xatoligi");
    }
}

// "🗑️ Tanlanganlarni BUTUNLAY o'chirish" — BARCHA belgilangan savollarni
// BITTA so'rovda, QAYTARIB BO'LMAYDIGAN tarzda o'chiradi
// (QuestionService.permanentlyDeleteQuestions, /api/question/bulk/permanent).
// FAQAT allaqachon savatda (soft-delete qilingan) turgan savollarga
// nisbatan ishlaydi — bitta-bitta "Butunlay o'chirish" bilan bir xil g'oya.
async function permanentlyDeleteSelectedQuestions() {
    const ids = [...selectedTrashQuestionIds];
    if (!ids.length) return;

    if (!await showConfirmModal(`⚠️ ${ids.length} ta savolni BUTUNLAY o'chirmoqchimisiz?\n\nBu amalni HECH QANDAY tarzda bekor qilib bo'lmaydi.`, { danger: true })) {
        return;
    }
    if (!await showConfirmModal("Haqiqatan ham ishonchingiz komilmi?", { danger: true })) {
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
        selectedTrashQuestionIds.clear();
        loadQuestionTrash();
    } catch (err) {
        console.error(err);
        showAlertModal(err.message || "Tarmoq xatoligi");
    }
}

function formatQuestionTrashDate(isoString) {
    if (!isoString) return "";
    const d = new Date(isoString);
    return d.toLocaleDateString("uz-UZ") + " " + d.toLocaleTimeString("uz-UZ", { hour: "2-digit", minute: "2-digit" });
}

async function restoreQuestion(questionId) {
    if (!await showConfirmModal("Bu savolni tiklamoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/question/${questionId}/restore`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Tiklashda xatolik");
            return;
        }
        loadQuestionTrash();
        await loadQuestions(topicId, currentPage);
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

async function permanentlyDeleteQuestion(questionId) {
    if (!await showConfirmModal("⚠️ Bu savolni BUTUNLAY o'chirmoqchimisiz?\n\nBu amalni HECH QANDAY tarzda bekor qilib bo'lmaydi.", { danger: true })) return;
    if (!await showConfirmModal("Haqiqatan ham ishonchingiz komilmi?", { danger: true })) return;

    try {
        const res = await fetch(`/api/question/${questionId}/permanent`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "O'chirishda xatolik");
            return;
        }
        loadQuestionTrash();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// Ilgari test-form.html'ga (butunlay boshqa sahifa) o'tkazardi — endi shu
// sahifaning o'zida, "Savol formasi" modalini "create" rejimida ochadi
// (foydalanuvchi so'rovi, 2026-09-05: "yangi test yaratishni ham modalda
// ochiladigan qilib mosla"). test-form.html sahifasining o'zi hamon
// mavjud — boshqa kirish nuqtalari (masalan courseDetail.js'dagi "➕
// Testga savol qo'shish") o'sha sahifaga hamon o'tkazadi, faqat SHU
// ro'yxat sahifasidagi "➕ TEST YARATISH" tugmasi o'zgardi.
function createTest() {
    openQuestionFormModal("create");
}

// "📥 Excel'ga eksport" — shu mavzudagi barcha faol savollarni .xlsx
// fayl sifatida yuklab beradi (import shabloni bilan bir xil formatda).
// Oddiy GET + Content-Disposition:attachment orqali — fetch/blob shart
// emas, brauzerning o'zi faylni yuklab beradi.
function exportQuestionsToExcel() {
    window.location.href = `/api/export/questions?topicId=${topicId}`;
}

// "📥 Import from Excel" / "📄 Shablon" — test-form.js'dagi BILAN BIR XIL
// (/api/import/excel, /api/export/template). Ilgari FAQAT test-form.html
// sahifasida bor edi — "➕ TEST YARATISH" endi shu sahifaning o'zida
// modalda ochilgani uchun (o'sha sahifaga o'tish o'rniga), bu ikkala
// funksiya foydalanuvchining odatiy oqimidan "yo'qolib qolgan" edi
// (haqiqiy xabar, 2026-09-05: "Тест импорт ва шаблон кўринмаяпти").
function importExcel() {
    document.getElementById("excelFile").click();
}

// "#excelFile" endi "Savol formasi" modali ICHIDA (statik HTML'da EMAS —
// buildQuestionFormModalHtml orqali DINAMIK yaratiladi, DOMContentLoaded'da
// BIR MARTA), shu sabab bu listener ham O'SHA elementlar DOM'ga
// qo'shilgandan KEYIN (pastda, "Savol formasi"ni yaratuvchi
// DOMContentLoaded handler ICHIDA) biriktiriladi — aks holda hali mavjud
// bo'lmagan elementga addEventListener chaqirilib, butun skript xato
// bilan to'xtab qolardi.
async function onExcelFileChange() {
    const fileInput = document.getElementById("excelFile");
    const file = fileInput.files[0];
    if (!file) return;

    const formData = new FormData();
    formData.append("file", file);
    formData.append("topicId", topicId);

    try {
        const res = await fetch("/api/import/excel", {
            method: "POST",
            body: formData
        });
        const data = await res.json();
        showImportResult(data);
    } catch (err) {
        console.error(err);
        showImportResult({ error: "Tarmoq xatoligi" });
    } finally {
        fileInput.value = "";
    }
}

function showImportResult(data) {
    const modal = document.getElementById("importModal");
    const title = document.getElementById("importTitle");
    const body = document.getElementById("importBody");

    if (data.success) {
        title.textContent = "✅ Import muvaffaqiyatli";
        body.textContent = `Import qilindi: ${data.imported} ta savol`;
    } else {
        title.textContent = "❌ Import xatoliklari";
        if (Array.isArray(data.errors) && data.errors.length) {
            const importedCount = typeof data.imported === "number" ? data.imported : 0;
            body.textContent = `Import qilindi: ${importedCount}\n\n` + data.errors.join("\n");
        } else if (data.error) {
            body.textContent = data.error;
        } else {
            body.textContent = "Noma'lum xatolik yuz berdi.";
        }
    }

    modal.classList.add("show");
    reloadCurrentQuestionsView();
}

function closeModal() {
    document.getElementById("importModal").classList.remove("show");
}

function downloadTemplate() {
    window.location.href = "/api/export/template";
}

// "📝 Word'ga eksport" oynasi — galochka qo'yilmasa oddiy bitta faylli
// eksport (WordService#exportQuestionsToWord), qo'yilsa "🎲 Variantlar
// yaratish" — ikki rejim: "different" (har biri BOSHQA savollardan
// iborat, eski/default) yoki "same" (BARCHASIDA bir xil savollar, faqat
// tartibi aralashtirilgan) — ExamVariantService, ZIP + javoblar kaliti.
function openWordExportModal() {
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
        window.location.href = `/api/export/questions/word?topicId=${topicId}`;
        closeWordExportModal();
        return;
    }

    const variantCount = Number(document.getElementById("wordExportVariantCount").value);
    const perVariant = Number(document.getElementById("wordExportPerVariant").value);
    const shuffleAnswers = document.getElementById("wordExportShuffleAnswers").checked;
    const sameQuestions = document.querySelector('input[name="wordExportVariantMode"]:checked').value === "same";

    if (!variantCount || variantCount < 1) {
        showAlertModal("Nechta variant kerakligini kiriting");
        return;
    }
    if (!perVariant || perVariant < 1) {
        showAlertModal("Har bir variantga nechta savol kerakligini kiriting");
        return;
    }

    await downloadWordVariants(
        `/api/export/questions/word/variants?topicId=${topicId}&variantCount=${variantCount}&perVariant=${perVariant}&shuffleAnswers=${shuffleAnswers}&sameQuestions=${sameQuestions}`,
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
            showAlertModal(data.error || "Eksport qilishda xatolik");
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
        showAlertModal("Tarmoq xatoligi");
    } finally {
        btn.disabled = false;
        btn.textContent = originalText;
    }
}

function showAlert(message, type = "error") {
    const box = document.getElementById("alertBox");
    box.textContent = message;
    box.className = `alert ${type}`;
    box.classList.remove("hidden");

    setTimeout(() => {
        box.classList.add("hidden");
    }, 4000);
}


//=============================================================================
//                      MODAL commentary
//=============================================================================
// "To'g'ri javob uchun izoh" ko'rish/tahrirlash oynasi — ilgari oddiy
// <textarea readonly> edi, endi rich-text (boy matn) muharrir, "Savol
// formasi"dagi 5 ta izoh sub-modali bilan BIR XIL richExec-oilasidan
// foydalanadi (pastda, "Rich-text muharrir" bo'limida — umumiy, editorId
// parametr sifatida uzatiladi). Ko'rish rejimida asboblar paneli
// (".rich-toolbar") CSS orqali yashirin (question.css#commentModal:not(.editing)),
// "✏️ Tahrirlash" bosilganda "#commentModal"ga ".editing" klassi qo'shiladi.
const modal = document.getElementById("commentModal");
const modalEditor = document.getElementById("modalCommentEditor");
const editBtn = document.getElementById("modalEdit");
const saveBtn = document.getElementById("modalSaveBtn");
const linkBtn = document.getElementById("modalLinkBtn");
const closeBtn = modal.querySelector("button[onclick*='closeCommentModal']");

let currentAnswerId = null;
let currentQuestionId = null;
let originalCommentHtml = "";

function isCommentModalEditable() {
    return modal.classList.contains("editing");
}

// "🔗 Darsga havola qo'shish" — joriy sahifadagi barcha savollar bitta
// darsga tegishli bo'lgani uchun (URL'dagi topicId), bir marta
// yuklanadi (fetchTopicCourseLink / buildTopicLinkHtml — topicLinkButton.js'da,
// test-form.js bilan umumiy; endi execCommand('insertHTML') orqali
// qo'yiladi — contenteditable, insertTextAtCursor EMAS — u textarea'ga
// mo'ljallangan, question.js'dagi boshqa joyda — bu HAM umumiy fayl,
// o'zgartirilmagan). "Savol formasi"dagi izoh sub-modallari ham SHU
// o'zgaruvchidan foydalanadi.
let modalTopicCourseLink = null;
fetchTopicCourseLink(topicId).then(link => {
    modalTopicCourseLink = link;
    if (link && linkBtn) linkBtn.classList.remove("hidden");
    document.querySelectorAll(".qform-link-btn").forEach(btn => {
        if (link) btn.classList.remove("hidden");
    });
});

if (linkBtn) {
    linkBtn.onclick = () => {
        if (!modalTopicCourseLink || !isCommentModalEditable()) return;
        modalEditor.focus();
        document.execCommand("insertHTML", false, buildTopicLinkHtml(modalTopicCourseLink));
    };
}

function openCommentModal(btn) {
    const commentImage = document.getElementById("modalCommentImage");
    const commentVideo = document.getElementById("modalCommentVideo");

    const answerId = btn.dataset.answerId;
    const questionId = btn.dataset.questionId;
    const commentary = decodeURIComponent(btn.dataset.comment || "");
    const commentImageUrl = decodeURIComponent(btn.dataset.commentImage || "");
    const commentVideoUrl = decodeURIComponent(btn.dataset.commentVideo || "");

    if (!answerId) {
        showAlertModal("❌ Правильный ответ не найден, комментарий отсутствует.");
        return;
    }

    currentAnswerId = answerId;
    currentQuestionId = questionId;
    originalCommentHtml = commentary;

    modalEditor.innerHTML = commentary;
    modalEditor.setAttribute("contenteditable", "false");
    modal.classList.remove("editing");
    saveBtn.disabled = true;
    if (linkBtn) linkBtn.disabled = true;

    injectAlignBars("modalCommentEditor");
    injectCaptions("modalCommentEditor");
    attachImageResizeHandlers("modalCommentEditor");

    // Rasm/video faqat ko'rish uchun (bu sahifada tahrirlanmaydi)
    if (commentImageUrl) {
        commentImage.src = commentImageUrl;
        commentImage.classList.remove("hidden");
    } else {
        commentImage.classList.add("hidden");
    }

    if (commentVideoUrl) {
        commentVideo.src = commentVideoUrl;
        commentVideo.classList.remove("hidden");
    } else {
        commentVideo.classList.add("hidden");
    }

    modal.classList.add("show");
}


document.addEventListener("DOMContentLoaded", () => {
    // Закрытие модала
    window.closeCommentModal = function () {
        modal.classList.remove("show");
        modal.classList.remove("editing");
        modalEditor.innerHTML = "";
        modalEditor.setAttribute("contenteditable", "false");
        saveBtn.disabled = true;
        if (linkBtn) linkBtn.disabled = true;

        const commentImage = document.getElementById("modalCommentImage");
        const commentVideo = document.getElementById("modalCommentVideo");
        commentImage?.classList.add("hidden");
        if (commentVideo) commentVideo.src = "";
        commentVideo?.classList.add("hidden");
    };

    // Режим редактирования
    if (editBtn) {
        editBtn.onclick = () => {
            modalEditor.setAttribute("contenteditable", "true");
            modal.classList.add("editing");
            modalEditor.focus();
            if (linkBtn && modalTopicCourseLink) linkBtn.disabled = false;
        };
    }

    // Включение Save при изменении текста
    if (modalEditor) {
        modalEditor.addEventListener("input", () => {
            saveBtn.disabled = modalEditor.innerHTML === originalCommentHtml;
        });
    }

    // Сохранение комментария
    if (saveBtn) {
        saveBtn.onclick = async () => {
            cleanupEmptyCaptions("modalCommentEditor");
            const newComment = modalEditor.innerHTML;

            const payload = {
                questionId: Number(currentQuestionId),
                trueAnswer: {
                    id: Number(currentAnswerId),
                    commentary: newComment,
                    isTrue: true
                }
            };

            try {
                const res = await fetch("/api/question/updateComment", {
                    method: "PATCH",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify(payload)
                });

                const data = await res.json();
                if (!res.ok) {
                    showAlertModal(data.error || "Ошибка сохранения");
                    return;
                }
                showAlert(data.message, "success");

                originalCommentHtml = newComment;
                modalEditor.setAttribute("contenteditable", "false");
                modal.classList.remove("editing");
                saveBtn.disabled = true;
                closeCommentModal();

                // Обновляем таблицу
                const params = new URLSearchParams(window.location.search);
                const topicId = params.get("topicId");
                if (topicId) await loadQuestions(topicId, currentPage);

            } catch (e) {
                showAlertModal("Ошибка сети");
            }
        };
    }

    // Кнопка закрытия
    if (closeBtn) {
        closeBtn.onclick = () => closeCommentModal();
    }

    // "Savol formasi" (create/edit) — bir marta yaratiladi va DOM'ga
    // biriktiriladi (foydalanuvchi so'rovi, 2026-09-05).
    document.body.insertAdjacentHTML("beforeend", buildQuestionFormModalHtml());
    document.getElementById("qformCommentaryModalsContainer").innerHTML = buildCommentaryModalHtml();
    document.getElementById("excelFile").addEventListener("change", onExcelFileChange);
});

//===========================================================================
//                      Savollarni qidirish

const searchInput = document.getElementById("searchInput");

let searchTimeout = null;

searchInput.addEventListener("input", (e) => {
    clearTimeout(searchTimeout);

    searchTimeout = setTimeout(() => {
        searchQuery = e.target.value.trim();
        currentPage = 0;

        if (isAllMode) {
            loadAllQuestions();
        } else {
            loadPage();
        }
    }, 400);
});

