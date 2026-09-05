const params = new URLSearchParams(window.location.search);
const topicId = params.get("topicId");

let editingRow = null; // хранит текущую редактируемую строку

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
        "<tr><td colspan='12'>❌ topicId yuborilmagan</td></tr>";
} else {
    loadAllQuestions();
    loadTopicName();
    refreshQuestionTrashBadge();
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
            `<tr><td colspan="12">❌ ${e.message}</td></tr>`;
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
        const correctIndex = answers.findIndex(a => a.isTrue);
        const correctLetter = correctIndex !== -1 ? letters[correctIndex] : "-";
        const correctAnswer = answers.find(a => a.isTrue);

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
            <td class="correct-letter"><b>${correctLetter}</b></td>
            
            <td class="comment-col hidden">
                    <input class="comment-input" type="text">
            </td>

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
                    
                    <button class="action-btn edit" 
                    onclick="enableInlineEdit(this, ${q.id})"
                         title="Tahrirlash">✏️</button>
                     
                </div>
                <div class="edit-actions" style="display:none;">
                    <button class="action-btn save" 
                    onclick="saveInlineEdit(this, ${q.id})" 
                    title="Saqlash">💾</button>
                    
                    <button class="action-btn cancel" 
                    style="color: orangered; font-weight: bold;" 
                    onclick="cancelInlineEdit(this)" 
                    title="Bekor qilish">&#8634;</button>
                    
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

        if (editingRow && ids.includes(Number(editingRow.dataset.questionId))) {
            editingRow = null;
        }
        hideCommentColumn();

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

    btn.onclick = () => {
        if (editingRow) {
            showAlertModal("Avval tahrirni yakunlang!");
            return;
        }
        loadQuestions(topicId, page);
    };

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
// Har bir chaqiruvda yangi HTML qaytaradi; joriy URL data-current-url'da saqlanadi,
// fayl tanlansa yuklanadi va shu atributga yangi URL yoziladi (saveInlineEdit shundan o'qiydi).
function buildInlineImageWidget(role, currentUrl, altText) {
    const url = currentUrl || "";
    return `
        <div class="inline-image-upload" data-role="${role}" data-current-url="${url}">
            <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" class="inline-image-input" hidden>
            <button type="button" class="inline-media-btn inline-image-btn">🖼️</button>
            <img class="inline-image-preview ${url ? "" : "hidden"}" src="${url}" alt="${altText}">
            <button type="button" class="inline-media-btn inline-remove-image-btn ${url ? "" : "hidden"}">✖</button>
        </div>
    `;
}

function buildInlineVideoWidget(currentUrl) {
    const url = currentUrl || "";
    return `
        <div class="inline-video-upload" data-current-url="${url}">
            <input type="file" accept="video/mp4,video/webm,video/ogg" class="inline-video-input" hidden>
            <button type="button" class="inline-media-btn inline-video-btn">🎬</button>
            <video class="inline-video-preview ${url ? "" : "hidden"}" src="${url}" controls></video>
            <button type="button" class="inline-media-btn inline-remove-video-btn ${url ? "" : "hidden"}">✖</button>
        </div>
    `;
}

document.addEventListener("click", (e) => {
    if (e.target.classList.contains("inline-image-btn")) {
        e.target.closest(".inline-image-upload").querySelector(".inline-image-input").click();
        return;
    }
    if (e.target.classList.contains("inline-video-btn")) {
        e.target.closest(".inline-video-upload").querySelector(".inline-video-input").click();
        return;
    }
    if (e.target.classList.contains("inline-remove-image-btn")) {
        const container = e.target.closest(".inline-image-upload");
        container.dataset.currentUrl = "";
        const preview = container.querySelector(".inline-image-preview");
        preview.src = "";
        preview.classList.add("hidden");
        e.target.classList.add("hidden");
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

        const uploadBtn = container.querySelector(".inline-image-btn");
        const originalLabel = uploadBtn.textContent;
        uploadBtn.disabled = true;
        uploadBtn.textContent = "⏳";

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
            container.querySelector(".inline-remove-image-btn").classList.remove("hidden");
        } catch (err) {
            console.error(err);
            showAlertModal("❌ Rasmni yuklashda tarmoq xatoligi");
        } finally {
            uploadBtn.disabled = false;
            uploadBtn.textContent = originalLabel;
        }
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

function enableInlineEdit(btn) {
    const row = btn.closest("tr");

    document.querySelectorAll(".comment-col")
        .forEach(c => c.classList.remove("hidden"));

    // запрет на редактирование, если уже редактируется другая строка
    if (editingRow && editingRow !== row) {
        showAlertModal("Avval tahrirlanayotgan satrni yakuniga yetkazing!");
        return;
    }

    editingRow = row; // помечаем эту строку как редактируемую

    row.classList.add("editing");

    toggleButtons(row, true);

    // 🔹 ВОПРОС
    const questionCell = row.querySelector("td[data-editable]");
    const qText = questionCell.innerText;
    const questionImageUrl = row.dataset.imageUrl || "";

    questionCell.innerHTML = `
    <input type="text"
           class="inline-input question-input"
           value="${qText}">
    ${buildInlineImageWidget("question-image", questionImageUrl, "Savol rasmi")}
`;


    const answerCells = row.querySelectorAll(".answer-cell");

    answerCells.forEach((cell, index) => {
        // Bo'sh ("padding") katakcha — bu savolda bunday variant umuman
        // mavjud emas (masalan eski 4 variantli savolning "E" ustuni).
        // Tahrirlash uchun input yaratilmaydi, aks holda saqlashda bo'sh
        // matnli "javob" sifatida yuborilib, validatsiyada xatolikka olib kelardi.
        if (!cell.dataset.answerId) return;

        const text = cell.innerText;
        const id = cell.dataset.answerId;
        const isCorrect = cell.classList.contains("correct");
        const answerImageUrl = cell.dataset.imageUrl || "";

        cell.innerHTML = `
            <label style="display:flex; gap:6px; align-items:center;">
                <input type="radio"
                       name="correct-${row.rowIndex}"
                       class="correct-radio"
                       ${isCorrect ? "checked" : ""}>
                <input type="text"
                       class="inline-input"
                       data-answer-id="${id}"
                       value="${text}">
            </label>
            ${buildInlineImageWidget("answer-image", answerImageUrl, "Javob rasmi")}
        `;
    });
//обработчик radio (КЛЮЧЕВОЕ)
    const radios = row.querySelectorAll(".correct-radio");
    const correctLetterCell = row.querySelector(".correct-letter b");
    const letters = ["A", "B", "C", "D", "E"];

    radios.forEach((radio, index) => {
        radio.addEventListener("change", () => {
            correctLetterCell.innerText = letters[index];
        });
    });


    // комментарий
    // ===== комментарий (ТОЛЬКО из data-comment кнопки) =====
    const commentBtn = row.querySelector(".action-btn.comment");
    const commentText = commentBtn
        ? decodeURIComponent(commentBtn.dataset.comment || "")
        : "";

    const commentCol = row.querySelector(".comment-col");

    // Hozircha to'g'ri deb belgilangan javobga biriktirilgan izoh media'si
    // (foydalanuvchi tahrirlash paytida boshqa variantni to'g'ri qilib belgilasa ham,
    // shu media/matn saqlanayotganda o'sha yangi to'g'ri javobga yoziladi).
    const correctCell = row.querySelector(".answer-cell.correct");
    const commentaryImageUrl = correctCell?.dataset.commentaryImageUrl || "";
    const commentaryVideoUrl = correctCell?.dataset.commentaryVideoUrl || "";

    commentCol.innerHTML = `
    <input type="text"
           class="comment-input"
           placeholder="To'g'ri javob uchun izoh"
           value="${commentText}">
    <div class="inline-commentary-media">
        ${buildInlineImageWidget("commentary-image", commentaryImageUrl, "Izoh rasmi")}
        ${buildInlineVideoWidget(commentaryVideoUrl)}
    </div>
`;

}

function cancelInlineEdit(btn) {
    editingRow = null; // снимаем флаг редактирования

    document.querySelectorAll(".comment-col")
        .forEach(c => c.classList.add("hidden"));//скрываем коммент столбцу

    loadQuestions(topicId, currentPage);
}

function saveInlineEdit(btn, questionId) {
    const row = btn.closest("tr");

    const questionText = row.querySelector('td[data-editable] input').value;
    const questionImageWidget = row.querySelector('.inline-image-upload[data-role="question-image"]');
    const questionImageUrl = questionImageWidget?.dataset.currentUrl || null;

    const answerRows = row.querySelectorAll(".answer-cell");

    const answers = [];
    let correctIndex = -1;

    answerRows.forEach((cell, i) => {
        const input = cell.querySelector(".inline-input");
        // Bo'sh ("padding") katakcha — bu savolda bunday variant mavjud
        // emas, hech narsa tahrirlanmagan (enableInlineEdit'da ham shu
        // katakcha o'tkazib yuborilgan edi). Bo'sh javob sifatida
        // yubormaymiz — aks holda backend'dagi "javob matni bo'sh
        // bo'lmasligi kerak" tekshiruvi butun saqlashni bloklab qo'yardi.
        if (!input) return;

        const radio = cell.querySelector(".correct-radio");
        const imageWidget = cell.querySelector('.inline-image-upload[data-role="answer-image"]');

        if (radio.checked) correctIndex = i;

        answers.push({
            id: Number(input.dataset.answerId),
            answerText: input.value,
            isTrue: radio.checked,
            commentary: "",
            imageUrl: imageWidget?.dataset.currentUrl || null,
            commentaryImageUrl: null,
            commentaryVideoUrl: null
        });
    });

    // комментарий (matn + rasm/video) — ТОЛЬКО правильному
    const comment = row.querySelector(".comment-input")?.value ?? "";
    const commentaryImageWidget = row.querySelector('.inline-commentary-media .inline-image-upload');
    const commentaryVideoWidget = row.querySelector('.inline-commentary-media .inline-video-upload');

    if (correctIndex !== -1) {
        answers[correctIndex].commentary = comment;
        answers[correctIndex].commentaryImageUrl = commentaryImageWidget?.dataset.currentUrl || null;
        answers[correctIndex].commentaryVideoUrl = commentaryVideoWidget?.dataset.currentUrl || null;
    }

    const payload = {
        id: questionId,
        questionText,
        imageUrl: questionImageUrl,
        answers
    };

    fetch("/api/question/update", {
        method: "PUT",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(payload)
    })
        .then(async r => {

            const data = await r.json();

            if (!r.ok) {
                showAlert(data.error || "Saqlashda xatolik yuz berdi.")
                return;
            }

            showAlert("✅ Muvaffaqiyatli saqlandi.", "success");

            editingRow = null; // снимаем флаг после сохранения

            document.querySelectorAll(".comment-col")
                .forEach(c => c.classList.add("hidden"));//скрываем коммент столбцу

            loadQuestions(topicId, currentPage);
        })
        .catch(e => showAlertModal(e.message));
}

function toggleButtons(row, isEditing) {

    row.querySelector(".view-actions").style.display = isEditing ? "none" : "flex";
    row.querySelector(".edit-actions").style.display = isEditing ? "flex" : "none";

    row.querySelector(".edit").style.display = isEditing ? "none" : "inline-block";
    row.querySelector(".comment").style.display = isEditing ? "none" : "inline-block";

    row.querySelector(".save").style.display = isEditing ? "inline-block" : "none";
    row.querySelector(".cancel").style.display = isEditing ? "inline-block" : "none";
    row.querySelector(".delete").style.display = isEditing ? "inline-block" : "none";
}

function hideCommentColumn() {
    document.querySelectorAll(".comment-col")
        .forEach(c => c.classList.add("hidden"));
}

// "O'chirilganlar savati"ga o'tkazadi (soft-delete) — darhol butunlay
// o'chmaydi, "🗑️ O'chirilganlar" panelidan ("♻️ Tiklash") bir zumda
// qaytariladi (QuestionService.deleteQuestion).
async function deleteQuestion(questionId) {

    if (!await showConfirmModal("Rostdan ham savolni o'chirmoqchimisiz?\n\n(Butunlay o'chmaydi — \"🗑️ O'chirilganlar\" panelidan qaytarish mumkin.)", { danger: true })) return;

    try {
        const res = await fetch(`/api/question/${questionId}`, {method: "DELETE"});
        if (!res.ok) throw new Error("O‘chirishda xatolik");

        // Сброс флага редактирования, если удаляем текущую редактируемую строку
        if (editingRow && editingRow.dataset.questionId == questionId) {
            editingRow = null;
        }

        // 🔑 ВАЖНО: скрываем колонку комментариев (th + td)
        hideCommentColumn();

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

function createTest() {
    window.location.href = `/question/${topicId}/create-test-form`;
}

// "📥 Excel'ga eksport" — shu mavzudagi barcha faol savollarni .xlsx
// fayl sifatida yuklab beradi (import shabloni bilan bir xil formatda).
// Oddiy GET + Content-Disposition:attachment orqali — fetch/blob shart
// emas, brauzerning o'zi faylni yuklab beradi.
function exportQuestionsToExcel() {
    window.location.href = `/api/export/questions?topicId=${topicId}`;
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
// Открытие модала
const modal = document.getElementById("commentModal");
const textarea = document.getElementById("modalComment");
const editBtn = document.getElementById("modalEdit");
const saveBtn = document.getElementById("modalSaveBtn");
const linkBtn = document.getElementById("modalLinkBtn");
const closeBtn = modal.querySelector("button[onclick*='closeCommentModal']");

let currentAnswerId = null;
let currentQuestionId = null;
let originalText = "";

// "🔗 Darsga havola qo'shish" — joriy sahifadagi barcha savollar bitta
// darsga tegishli bo'lgani uchun (URL'dagi topicId), bir marta
// yuklanadi (fetchTopicCourseLink / buildTopicLinkHtml / insertTextAtCursor
// — topicLinkButton.js'da, test-form.js bilan umumiy).
let modalTopicCourseLink = null;
fetchTopicCourseLink(topicId).then(link => {
    modalTopicCourseLink = link;
    if (link && linkBtn) linkBtn.classList.remove("hidden");
});

if (linkBtn) {
    linkBtn.onclick = () => {
        if (!modalTopicCourseLink || textarea.readOnly) return;
        insertTextAtCursor(textarea, buildTopicLinkHtml(modalTopicCourseLink));
        saveBtn.disabled = textarea.value === originalText;
    };
}

function openCommentModal(btn) {
    const modal = document.getElementById("commentModal");
    const textarea = document.getElementById("modalComment");
    const saveBtn = document.getElementById("modalSaveBtn");
    const commentImage = document.getElementById("modalCommentImage");
    const commentVideo = document.getElementById("modalCommentVideo");

    if (!modal || !textarea || !saveBtn) {
        console.error("Modal, textarea или saveBtn не найдены!");
        return;
    }

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
    originalText = commentary;

    textarea.value = commentary;
    textarea.readOnly = true;
    saveBtn.disabled = true;
    if (linkBtn) linkBtn.disabled = true;

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
        textarea.value = "";
        textarea.readOnly = true;
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
            textarea.readOnly = false;
            textarea.focus();
            if (linkBtn && modalTopicCourseLink) linkBtn.disabled = false;
        };
    }

    // Включение Save при изменении текста
    if (textarea) {
        textarea.addEventListener("input", () => {
            saveBtn.disabled = textarea.value === originalText;
        });
    }

    // Сохранение комментария
    if (saveBtn) {
        saveBtn.onclick = async () => {
            const newComment = textarea.value;

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

                originalText = newComment;
                textarea.readOnly = true;
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

