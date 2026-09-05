// "Mavzu" (TopicSection — Bo'lim ICHIDAGI guruh, "Bo'lim -> Mavzu -> Dars"
// ierarxiyasida) CRUD — topic.js bilan bir xil andoza (itemBlock[] + mode
// VIEW/NEW/EDIT, saveOnClientSide() DARHOL bazaga yozadi), + tartib
// o'zgartirish (yuqoriga/pastga) tugmalari.
// ========================================================================
//                     Global fields
// ========================================================================

let itemBlock = [];
let focusIndex = null;

// Haqiqiy Excel ilovasi belgisiga o'xshash SVG (yashil hujjat + oq "X") —
// "Excel'ga eksport" tugmalarida emoji o'rniga ishlatiladi (foydalanuvchi
// so'rovi bo'yicha — barcha eksport tugmalarida bir xil belgi, topic.js
// bilan bir xil).
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
// ========================================================================

const scienceId = getScienceId();

// science.js#openTopics'dan "&fieldId=..." bilan kelgan bo'lsa — shu
// Yo'nalish qamrovi "Orqaga"/"📋 Barcha darslar" havolalarida saqlab
// qolinadi (topic.js'ga ham shu tarzda uzatiladi).
const pageFieldId = new URLSearchParams(window.location.search).get("fieldId");
const fieldQuery = pageFieldId != null ? `&fieldId=${pageFieldId}` : "";

if (!scienceId) {
    showAlertModal("❌ scienceId topilmadi (HTML dan)");
} else {
    afterStartPage(`/api/topic-section?scienceId=${scienceId}`);
    refreshSectionTrashBadge();
    applyScopeBar();
}

// science.html'dagi "← Yo'nalishlar / <nomi>" bilan bir xil ko'rinish —
// "← Bo'limlar / <shu Bo'lim nomi>" (foydalanuvchi so'rovi, 2026-09-05:
// "iyerarxiyaning boshqa qismlariga ham qo'sh"). "/science/{id}" —
// ScienceIdAndNameDto (name + fieldId/fieldName) qaytaradi.
async function applyScopeBar() {
    const bar = document.getElementById("sectionScopeBar");
    const backLink = document.getElementById("sectionScopeBackLink");
    const nameEl = document.getElementById("sectionScopeName");
    if (!bar) return;

    try {
        const res = await fetch(`/science/${scienceId}`);
        if (!res.ok) return;
        const science = await res.json();

        backLink.href = `/science?focus=${scienceId}${fieldQuery}`;
        nameEl.textContent = science.name;
        bar.classList.remove("hidden");
    } catch (err) {
        console.error(err);
    }
}

// Badge'ni (".notif-badge" — navbar.js#refreshUnreadCount bilan bir xil
// uslub) sonini yangilaydi — 0 bo'lsa yashiradi. Bir nechta sahifada
// (question.js/topic.js/...) bir xil andoza bilan takrorlanadi — mustaqil
// kichik JS fayllar bo'lgani uchun ataylab nusxalangan.
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

function refreshSectionTrashBadge() {
    fetch(`/api/topic-section/deleted?scienceId=${scienceId}`)
        .then(r => r.ok ? r.json() : [])
        .then(items => setTrashBadgeCount("sectionTrashBadge", items.length))
        .catch(err => console.error(err));
}

// ========================================================================
//                      Functions
// ========================================================================

function getScienceId() {
    const element = document.getElementById("scienceId");
    return element ? element.value : null;
}

// "🗑️ Bo'sh bo'limlarni o'chirish" — shu Fandagi hech qanday mavzuga
// biriktirilmagan (topicCount==0) BARCHA bo'limlarni bir yo'la o'chiradi.
// Avval saqlanmagan o'zgarishlar bo'lsa (yangi/tahrirlanayotgan qatorlar)
// — chalkashmasin deb, ular haqida ogohlantiriladi.
async function deleteEmptySections() {
    if (itemBlock.some(s => s.mode !== "VIEW")) {
        showAlertModal("❌ Avval tahrirlashni yakuniga yetkazing (yoki saqlang)!");
        return;
    }

    const emptyCount = itemBlock.filter(s => s.id > 0 && (s.topicCount || 0) === 0).length;
    if (emptyCount === 0) {
        showAlertModal("ℹ️ Bo'sh mavzu topilmadi.");
        return;
    }

    if (!await showConfirmModal(`⚠️ ${emptyCount} ta bo'sh mavzuni o'chirmoqchimisiz?\n\nBu amalni bekor qilib bo'lmaydi.`, { danger: true })) {
        return;
    }

    try {
        const res = await fetch(`/api/topic-section/empty?scienceId=${getScienceId()}`, { method: "DELETE" });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "O'chirishda xatolik");
            return;
        }
        showToast('success', `✅ ${data.deleted} ta bo'sh mavzu o'chirildi`, 4000);
        await reloadFromDb(`/api/topic-section?scienceId=${getScienceId()}`);
        focusIndex = 0;
        render();
        refreshSectionTrashBadge();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text ?? "";
    return div.innerHTML;
}

// "🗑️ O'chirilgan bo'limlar" paneli — soft-delete qilingan
// TopicSection'lar ro'yxati (bir zumda "♻️ Tiklash" qilinadigan).
let sectionTrashOpen = false;

function toggleSectionTrash() {
    sectionTrashOpen = !sectionTrashOpen;
    document.getElementById("sectionTrashPanel").style.display = sectionTrashOpen ? "block" : "none";
    if (sectionTrashOpen) {
        loadSectionTrash();
    }
}

async function loadSectionTrash() {
    const list = document.getElementById("sectionTrashList");
    list.innerHTML = "<p>Yuklanmoqda...</p>";

    try {
        const res = await fetch(`/api/topic-section/deleted?scienceId=${getScienceId()}`);
        if (!res.ok) {
            list.innerHTML = "<p>Yuklashda xatolik</p>";
            return;
        }
        const items = await res.json();
        setTrashBadgeCount("sectionTrashBadge", items.length);
        if (!items.length) {
            list.innerHTML = "<p>O'chirilgan mavzu yo'q</p>";
            return;
        }
        list.innerHTML = items.map(s => `
            <div class="row">
                <div>${escapeHtml(s.name)} — ${formatSectionTrashDate(s.deletedAt)}da o'chirilgan</div>
                <div class="row-actions">
                    <button onclick="restoreSectionFromTrash(${s.id})">♻️ Tiklash</button>
                    <button class="danger-btn" onclick="permanentlyDeleteSectionFromTrash(${s.id}, ${JSON.stringify(s.name).replace(/"/g, "&quot;")})">🗑️ Butunlay o'chirish</button>
                </div>
            </div>
        `).join("");
    } catch (err) {
        console.error(err);
        list.innerHTML = "<p>Tarmoq xatoligi</p>";
    }
}

function formatSectionTrashDate(isoString) {
    if (!isoString) return "";
    const d = new Date(isoString);
    return d.toLocaleDateString("uz-UZ") + " " + d.toLocaleTimeString("uz-UZ", { hour: "2-digit", minute: "2-digit" });
}

async function restoreSectionFromTrash(sectionId) {
    if (!await showConfirmModal("Bu mavzuni tiklamoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/topic-section/${sectionId}/restore`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Tiklashda xatolik");
            return;
        }
        loadSectionTrash();
        await reloadFromDb(`/api/topic-section?scienceId=${getScienceId()}`);
        render();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

async function permanentlyDeleteSectionFromTrash(sectionId, name) {
    if (!await showConfirmModal(`⚠️ "${name}" mavzusini BUTUNLAY o'chirmoqchimisiz?\n\nBu amalni HECH QANDAY tarzda bekor qilib bo'lmaydi. (Darslari o'chmaydi, faqat "mavzusiz" bo'lib qoladi.)`, { danger: true })) return;

    try {
        const res = await fetch(`/api/topic-section/${sectionId}/permanent`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "O'chirishda xatolik");
            return;
        }
        loadSectionTrash();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

function afterStartPage(mapping) {
    reloadFromDb(mapping).then(() => {
        focusIndex = 0;
        render();
    });
}

async function reloadFromDb(mapping) {
    const response = await fetch(mapping);

    try {
        if (!response.ok) {
            throw new Error(`Server error: ${response.status}`);
        }
    } catch (err) {
        console.error('Yuklash xatosi:', err);
        showToast('error', `Bo'limlarni yuklashda xatolik`, 4000);
    }

    const data = await response.json();

    itemBlock = data.map(s => ({
        id: s.id,
        name: s.name,
        original: s.name,
        // Shu bo'lim biror KURSga bog'langan bo'lsa — o'sha kursning nomi
        // (render()'da "🔗 Kurs: ..." belgisi VA edit()'da tahrirlashni
        // bloklash uchun — bunday bo'limning nomi kurs Bo'limi bilan
        // sinxronlangan, faqat kurs ichidan o'zgartiriladi).
        linkedCourseTitle: s.linkedCourseTitle || null,
        // Shu bo'limda nechta mavzu borligi — render()'da "(N ta mavzu)"
        // ko'rsatish uchun.
        topicCount: s.topicCount || 0,
        mode: "VIEW"
    }));
}

function render() {
    const list = document.getElementById("list");
    list.innerHTML = "";

    itemBlock.forEach((s, i) => {
        const row = document.createElement("div");
        row.className = "row";

        const isView = s.mode === "VIEW";
        const isLink = isView && s.id !== null;
        const isNew = s.mode === "NEW";
        const placeholder = isNew ? 'placeholder="Yangi mavzu nomini kiriting"' : '';

        const hasDup = !isView && hasDuplicate(i, s.name);
        const inputClass = `
                                    ${isView ? 'view' : ''}
                                    ${isLink ? 'link' : ''}
                                    ${hasDup ? 'duplicate' : ''}
                                    `;

        // Kursga bog'langan bo'lim — kichik belgi (topic.js'dagi "🔗 Kurs"
        // belgisi bilan bir xil uslub/rang). O'z alohida qatorida (bo'lim
        // NOMI har doim YANGI qatordan boshlanishi uchun — item-badges/
        // item-title-row, science.css).
        const courseBadge = s.linkedCourseTitle
            ? `<span class="topic-course-badge" title="Bu mavzu kursga bog'langan, faqat kurs ichidan tahrirlanadi">🔗 Kurs: ${escapeHtml(s.linkedCourseTitle)}</span>`
            : '';

        // "📊 Excel'ga eksport" — shu Mavzudagi BARCHA darslarning
        // savollarini BITTA faylga yig'ib yuklab beradi. Kurs belgisi
        // bo'lmasa ham HAR DOIM ko'rinadi (topic.js'dagi dars-darajali
        // eksport bilan bir xil uslub).
        const exportBtn = `<button class="topic-export-btn" onclick="event.stopPropagation(); exportSectionQuestions(${s.id})" title="Shu mavzudagi barcha darslarning testlarini Excel'ga eksport qilish">${EXCEL_ICON_SVG}</button>`;

        // "📝 Word'ga eksport" — shu Mavzudagi barcha darslarning
        // savollarini chop etishga tayyor .docx faylga (Excel eksport
        // tugmasi yonida).
        const wordExportBtn = `<button class="topic-export-btn" onclick="event.stopPropagation(); openWordExportModal(${s.id})" title="Shu mavzudagi barcha darslarning testlarini Word'ga eksport qilish">${WORD_ICON_SVG}</button>`;

        const badgesRow = `<div class="item-badges">${courseBadge}<div class="topic-export-btn-group">${exportBtn}${wordExportBtn}</div></div>`;

        row.innerHTML = `
    ${
            isView
                ? `
            <div
            class="row-view"
            tabindex="0"
            onclick="openTopics(${s.id})"
            onkeydown="onViewKeyDown(event, ${i})"
            title="Enter — Darslarni ochish | ↑ ↓ — navigatsiya | Home/End — birinchi/oxirgi"
        >
            <div
                id="input-${i}"
                class="topic-name ${inputClass}"
                tabindex="-1"
            >${badgesRow}<div class="item-title-row"><span class="item-title-text">${escapeHtml(s.name)}</span><span class="item-count-badge">${s.topicCount} ta dars</span></div></div>
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
            `
        }
    ${buttons(s, i)}
`;

        list.appendChild(row);
    });

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

    updateSectionsSummary();
}

// "Bo'limlar" sarlavhasi yonida — nechta bo'lim bor, nechtasi kursga
// bog'langan, nechtasi bog'lanmagan (topics.html/topic.js#
// updateTopicsSummary bilan bir xil g'oya — bo'lim darajasida).
function updateSectionsSummary() {
    const el = document.getElementById("sectionsSummary");
    if (!el) return;

    const relevant = itemBlock.filter(s => s.id > 0);
    if (relevant.length === 0) {
        el.textContent = "";
        return;
    }

    const linked = relevant.filter(s => s.linkedCourseTitle).length;
    const unlinked = relevant.length - linked;
    el.textContent = `(${relevant.length} ta — ${linked} tasi kursga bog'langan, ${unlinked} tasi bog'lanmagan)`;
}

function openTopics(sectionId) {
    if (!sectionId || sectionId < 0) {
        showAlertModal("❗ Avval mavzuni bazaga saqlang");
        return;
    }
    // Faqat shu mavzuga tegishli darslarni ko'rsatadigan holatda ochiladi.
    window.location.href = `/topics?scienceId=${scienceId}&sectionId=${sectionId}${fieldQuery}`;
}

// "📊 Excel'ga eksport" — shu Bo'limdagi BARCHA mavzularning savollarini
// BITTA .xlsx faylga yig'ib yuklab beradi (topic.js#exportTopicQuestions
// bilan bir xil andoza, faqat butun Bo'lim miqyosida).
function exportSectionQuestions(sectionId) {
    window.location.href = `/api/export/questions/section?sectionId=${sectionId}`;
}

// "📝 Word'ga eksport" oynasi — galochka qo'yilmasa oddiy bitta faylli
// eksport, qo'yilsa "🎲 Variantlar yaratish" — har biri BOSHQA
// savollardan iborat bir nechta imtihon varianti (ExamVariantService),
// ZIP + javoblar kaliti holida. Qator tugmasi bosilganda openWordExportModal
// aynan qaysi BO'LIM uchun ekanini saqlab qo'yadi (wordExportSectionId).
let wordExportSectionId = null;

function openWordExportModal(sectionId) {
    wordExportSectionId = sectionId;
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
    const textDifferent = "Savollar shu mavzudagi BARCHA darslar bo'yicha TENG taqsimlanadi (darsda savol yetmasa, qolgan qismi boshqa darslarga teng bo'lib beriladi). Natija — har biri alohida .docx fayl bo'lgan ZIP arxiv + javoblar kaliti (Excel).";
    const textSame = "Savollar shu mavzudagi BARCHA darslar bo'yicha TENG taqsimlanib BIR MARTA tanlanadi va BARCHA nusxada bir xil bo'ladi — faqat savollar (va javob variantlari) tartibi har bir nusxada alohida aralashtiriladi.";

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
        window.location.href = `/api/export/questions/word/section?sectionId=${wordExportSectionId}`;
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
        `/api/export/questions/word/variants/section?sectionId=${wordExportSectionId}&variantCount=${variantCount}&perVariant=${perVariant}&shuffleAnswers=${shuffleAnswers}&sameQuestions=${sameQuestions}`,
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

function hasDuplicate(currentIndex, name) {
    return itemBlock.some((s, index) =>
        index !== currentIndex &&
        s.name.toLowerCase().trim() === name.toLowerCase().trim()
    );
}

function onClickKey(event, i) {
    // Nom maydoni endi <textarea> — oddiy Enter saqlaydi (preventDefault
    // shart, aks holda qo'shimcha bo'sh qator ham qo'shilib qolardi),
    // Shift+Enter esa qator ko'chirish uchun ochiq qoldirilgan. "Delete"
    // tugmasi orqali QATORNI o'chirish olib tashlandi — <textarea> ichida
    // bu tugma endi odatiy (kursordan keyingi belgini o'chirish) ma'noda
    // ishlaydi; qatorni o'chirish faqat 🗑️ tugmasi orqali.
    if (event.key === "Enter" && !event.shiftKey && itemBlock[i].mode !== "VIEW") {
        event.preventDefault();
        saveOnClientSide(i);
    }
    if (event.key === "Escape" && itemBlock[i].mode !== "VIEW") {
        cancel(i);
    }
}

function onViewKeyDown(event, index) {
    const s = itemBlock[index];
    if (s.mode !== "VIEW") return;

    switch (event.key) {
        case "Enter":
            event.preventDefault();
            openTopics(s.id);
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
}

function moveFocus(newIndex) {
    if (newIndex < 0 || newIndex >= itemBlock.length) return;
    focusIndex = newIndex;
    render();
}

function cancel(i) {
    const s = itemBlock[i];
    if (s.mode !== "VIEW") {
        if (s.mode === "NEW") {
            itemBlock.splice(i, 1);
        }
        s.name = s.original;
        s.mode = "VIEW";
        showToast('info', 'Amaliyot bekor qilindi', 2000);
    }
    render();
}

function undoAll() {
    reloadFromDb(`/api/topic-section?scienceId=${scienceId}`).then(() => render());
    showToast('info', 'Ma\'lumotlar bazasidan qayta yuklandi ', 4000);
}

// Foydalanuvchi so'rovi, 2026-09-05: "Save to DB" tugmasi olib
// tashlandi — o'chirish DARHOL bazaga yoziladi (alohida DELETE endpoint
// yo'q, shu sabab /api/topic-section/save'ga BITTA elementli
// deletedIds bilan murojaat qilinadi).
async function removeFromUi(i) {
    if (itemBlock[i].mode === "NEW") {
        itemBlock.splice(i, 1);
        render();
        return;
    }
    const s = itemBlock[i];
    const sectionName = s.name || "Bu mavzu";
    const confirmDelete = await showConfirmModal(`⚠️ "${sectionName}"ni o'chirishni tasdiqlaysizmi?\n\nMavzudagi darslar O'CHMAYDI — faqat mavzusiz bo'lib qoladi.\n\nKeyin bu amalni bekor qilib bo'lmaydi.`, { danger: true });
    if (!confirmDelete) {
        cancel(i);
        return;
    }

    try {
        const res = await fetch("/api/topic-section/save", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify({new: [], updated: [], deletedIds: [s.id]})
        });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "O'chirishda xatolik");
            return;
        }
        showToast('success', `"${sectionName}" o'chirildi`, 2000);
        await reloadFromDb(`/api/topic-section?scienceId=${scienceId}`);
        render();
        refreshSectionTrashBadge();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// Tugmalar guruhi ".row-actions" ichiga o'raladi (science.css) — shu
// tufayli bo'lim nomi qancha uzun bo'lib, bir necha qatorga o'ralib
// ketmasin, tugmalar HECH QACHON torayib/siqilib qolmaydi (flex-shrink:0).
function buttons(s, i) {
    if (s.mode === "VIEW") {
        const upDisabled = i === 0 ? "disabled" : "";
        const downDisabled = i === itemBlock.length - 1 ? "disabled" : "";
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
}

// "✏️ Edit" tugmasi shu funksiyani chaqiradi — topic.js'da bor, bu yerga
// klonlanganda (topicSection.js yaratilganda) tushib qolgan edi, shu
// sabab "✏️ Edit" bosilganda hech narsa sodir bo'lmasdi (browser konsolida
// "edit is not defined" xatosi bilan).
function edit(i) {
    // Kursga bog'langan bo'lim — nomi kurs Bo'limi bilan (bir tomonlama)
    // sinxronlangan (CourseService.renameChapter), shu sabab bu yerdan
    // tahrirlash BLOKLANADI — aks holda qo'lda kiritilgan o'zgarish
    // keyingi kurs tomonidagi rename'da ustidan yozilib, "yo'qolib
    // qolar" edi. Backend ham xuddi shu tekshiruvni qaytaradi
    // (TopicSectionService.updateSectionName) — bu yerdagi tekshiruv
    // foydalanuvchiga darhol, saqlashga urinmasdan tushuntirish beradi.
    if (itemBlock[i].linkedCourseTitle) {
        showAlertModal(`❌ Bu bo'lim "${itemBlock[i].linkedCourseTitle}" kursiga bog'langan.\n\nUni faqat shu kurs ichidan (kurs sahifasidagi Bo'lim ✏️ tugmasi orqali) tahrirlashingiz mumkin.`);
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

    render();
}

// Faqat DB'da mavjud (id > 0) bo'limlar orasida joy almashtiradi va
// darhol serverga (reorder endpoint) yuboradi — yangi (hali saqlanmagan)
// bo'limlar bilan aralashtirmaslik uchun oddiy holatda saqlanadi.
function moveUp(i) {
    if (i <= 0) return;
    [itemBlock[i - 1], itemBlock[i]] = [itemBlock[i], itemBlock[i - 1]];
    persistOrder();
}

function moveDown(i) {
    if (i >= itemBlock.length - 1) return;
    [itemBlock[i], itemBlock[i + 1]] = [itemBlock[i + 1], itemBlock[i]];
    persistOrder();
}

async function persistOrder() {
    render();

    const orderedIds = itemBlock.filter(s => s.id > 0).map(s => s.id);
    if (orderedIds.length < 2) return;

    try {
        const response = await fetch(`/api/topic-section/reorder?scienceId=${scienceId}`, {
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

// "Saralash: A→Z / Z→A" — hali saqlanmagan (NEW/EDIT) qatorlar bo'lsa
// avval ularni yakunlash so'raladi.
function sortAllAZ(dir) {
    if (itemBlock.some(s => s.mode !== "VIEW")) {
        showAlertModal("❌ Avval tahrirlashni yakuniga yetkazing (yoki saqlang)!");
        return;
    }

    itemBlock.sort((a, b) =>
        dir === "AZ" ? a.name.localeCompare(b.name, "uz") : b.name.localeCompare(a.name, "uz"));

    persistOrder();
}

function showToast(type, message, duration = 4000) {
    const toast = document.createElement('div');
    toast.className = `toast ${type}`;

    const icons = {success: '✅', error: '❌', warning: '⚠️', info: 'ℹ️'};

    toast.innerHTML = `
               <span class="toast-icon">${icons[type] || ''}</span>
               <span class="toast-message">${message}</span>
               <button class="toast-close" onclick="this.parentElement.remove()">❌</button>
           `;

    const container = document.getElementById('toast-container');
    container.appendChild(toast);

    setTimeout(() => {
        if (toast.parentElement) {
            toast.style.opacity = '0';
            setTimeout(() => toast.remove(), 300);
        }
    }, duration);

    return toast;
}

function add() {
    if (itemBlock.some(s => s.mode === "NEW" || s.mode === "EDIT")) {
        showToast('warning', 'Avval saqlash tugmasini bosing!');
        focusIndex = itemBlock.findIndex(s => s.mode !== "VIEW");
        render();
        return;
    }

    const tempId = Date.now() * -1;

    itemBlock.push({
        id: tempId,
        name: "",
        original: "",
        mode: "NEW"
    });

    focusIndex = itemBlock.length - 1;
    render();
}

// Foydalanuvchi so'rovi, 2026-09-05: "Save to DB" tugmasi olib
// tashlandi — "💾 Save" bosilganda (yoki Enter) o'zgarish DARHOL bazaga
// yoziladi (/api/topic-section/save'ga BITTA elementli new/updated bilan).
async function saveOnClientSide(i) {
    const s = itemBlock[i];
    const newNameVal = s.name.trim();

    if (newNameVal === "") {
        showAlertModal('❌ Bo\'lim nomi bo\'sh bo\'lishi mumkin emas!');
        focusIndex = i;
        return;
    }

    if (hasDuplicate(i, newNameVal)) {
        showAlertModal('❌ Bu bo\'lim nomi allaqachon mavjud!');
        focusIndex = i;
        return;
    }

    const isNew = s.id < 0;
    if (!isNew && newNameVal === s.original) {
        s.mode = "VIEW";
        render();
        showToast('info', "O'zgarish bo'lmadi", 2000);
        return;
    }

    const payload = isNew
        ? {new: [{science_id: scienceId, name: newNameVal}], updated: [], deletedIds: []}
        : {new: [], updated: [{id: s.id, name: newNameVal}], deletedIds: []};

    try {
        const res = await fetch("/api/topic-section/save", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(payload)
        });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Saqlashda xatolik");
            return;
        }
        showToast('success', isNew ? `"${newNameVal}" saqlandi` : "Bo'lim saqlandi", 2000);

        await reloadFromDb(`/api/topic-section?scienceId=${scienceId}`);
        focusIndex = itemBlock.findIndex(x => x.name === newNameVal);
        render();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

//===========================================================================
//            BACK tugmasini bosganda ishlaydi.
//===========================================================================
document.addEventListener("DOMContentLoaded", () => {
    const btnBack = document.getElementById("btnBack");

    if (!btnBack) return;

    btnBack.onclick = () => {
        // Mavzu guruhlari (TopicSection) sahifasi Bo'lim va Darslar
        // o'rtasida turadi (Bo'lim -> Mavzu -> Dars) — shu sabab "Orqaga"
        // Bo'limlar ro'yxatiga (kelingan Yo'nalish qamrovini saqlagan
        // holda) qaytaradi.
        const scienceId =
            new URLSearchParams(window.location.search).get("scienceId");

        window.location.href = scienceId ? `/science?focus=${scienceId}${fieldQuery}` : `/science${pageFieldId != null ? "?fieldId=" + pageFieldId : ""}`;
    };
});
