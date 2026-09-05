let cachedCourse = null;
let clickPaymentEnabled = false;

// Darslar ro'yxati sahifalanadi — bitta sahifada shuncha karta ko'rsatiladi
// (renderSections/changeSectionsPage). Sahifa raqami 0'dan boshlanadi. Bu —
// hech qaysi dars biror Mavzuga biriktirilmagan (eski/oddiy) kurslar
// uchun — bitta tekis grid + bitta umumiy sahifalash.
const SECTIONS_PER_PAGE = 12;
let sectionsPage = 0;

// Kursda Mavzu(lar) bo'lsa — har bir Mavzu o'z alohida "box"ida, o'z
// sahifalash tugmalari bilan ko'rsatiladi (renderGroupedSections). Har bir
// Mavzu uchun joriy sahifa alohida saqlanadi: chapterPages[chapterKey].
// Umumiy .sections-grid javobgar panjarasi 1200px+'da 4 ustunli bo'ladi —
// shu sabab 4 ta tanlangan: har bir sahifa aynan BITTA TO'LIQ qatorni
// tashkil qiladi (qatorni "yorib chiqmaydi", qo'shimcha skroll ham shart
// emas). Torroq ekranlarda (kamroq ustunli) 4 ta dars shunchaki 2-4
// qatorga o'z-o'zidan bo'linadi — bu normal, kutilgan holat.
const CHAPTER_SECTIONS_PER_PAGE = 4;
let chapterPages = {};

// Mavzu ro'yxati endi accordion (yig'ma) ko'rinishida — sarlavhaga
// bosilganda O'SHA mavzuning darslari ("box" ichidagi hammasi — saralash,
// kartochkalar, sahifalash) ochiladi/yopiladi. Bir nechtasi bir vaqtda
// ochiq turishi mumkin. FAQAT shu sahifa (sessiya) davomida eslab qolinadi
// (chapterKey -> true) — sahifa qayta yuklansa hammasi yana yopiq holatdan
// boshlanadi (selectCard'dagi avtomatik ochish bundan mustasno).
let expandedChapterKeys = new Set();

// "🔍 Mavzu qidirish" (onChapterSearchInput) — mavzu nomi bo'yicha
// filtr, katta-kichik harfga sezgir emas. Bo'sh bo'lsa — filtr yo'q.
let chapterSearchQuery = "";

// Klaviatura bilan dars kartochkalari orasida navigatsiya (←/→ — joriy
// sahifa/Mavzu ichida oldingi-keyingi kartaga, ↑/↓ — oldingi/keyingi
// sahifa, Home — 1-sahifa). Tanlangan kartaning id'si — qayta chizishlar
// (sahifa/Mavzu almashganda) orasida ham saqlanib qoladi.
let selectedSectionId = null;

// "🔙 Kursga qaytish" (test-form.js) dan "?focus=<sectionId>" bilan
// qaytilganda — ANIQ shu kartani ekranga chiqarib, "tanlangan" holatda
// belgilash uchun. FAQAT birinchi yuklanishda qo'llaniladi (keyingi
// qayta chizishlarda — masalan bo'lim tahrirlangandan keyin — takrorlanmaydi).
const focusSectionIdFromUrl = Number(new URLSearchParams(window.location.search).get("focus")) || null;
let pendingFocusApplied = false;

// YouTube pleyeri (courseSectionView.js) "videoId" sifatida FAQAT xom
// ID'ni qabul qiladi, to'liq URL emas — shu sabab o'qituvchi to'liq
// havolani joylashtirsa ham, saqlashdan oldin shu yerda tozalab olamiz
// (aks holda video keyinchalik qora ekran bo'lib chiqadi).
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

document.addEventListener("DOMContentLoaded", () => {
    loadCourse();
    loadScienceNamesList();
    fetch("/api/payments/config")
        .then(r => r.ok ? r.json() : { clickEnabled: false })
        .then(cfg => {
            clickPaymentEnabled = !!cfg.clickEnabled;
            // Kurs ma'lumoti bu vaqtga qadar allaqachon kelib, banner
            // chizilgan bo'lishi mumkin (ikkala fetch parallel ketadi) —
            // "💳 Click orqali to'lash" tugmasi ko'rinishini shu payt
            // aniq bo'lgan clickPaymentEnabled bilan qayta hisoblaymiz.
            if (cachedCourse) updateSubscribeBanner(cachedCourse);
        })
        .catch(() => { clickPaymentEnabled = false; });
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

// ================= Rich-toolbar: shrift, rang, tekislash, rasm =================

// <input type="color"> yoki <select> bosilganda brauzer o'z (native)
// rang tanlash oynasi/dropdown'ini ochadi — bu FOKUSNI contenteditable'dan
// olib qo'yadi va shu bilan birga tanlangan matn (selection/Range) ham
// yo'qoladi. Natijada "onchange" ishga tushganda (rang/qiymat
// tanlangandan keyin) execCommand'ga berish uchun HECH QANDAY tanlangan
// matn qolmaydi — shuning uchun rang/shrift o'zgarishi ko'rinmas edi.
// Yechim: shu boshqaruv elementi hali fokusni OLMASDAN turib ("mousedown"
// paytida), joriy selection'ni saqlab qo'yamiz, so'ng "onchange"da
// (editor.focus()'dan KEYIN) uni qayta tiklaymiz — shundagina execCommand
// haqiqatan tanlangan matnga qo'llanadi.
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
// <input type="color"> har safar tasodifiy (oxirgi tanlangan bo'lmagan)
// holatdan ochilib, bir xil rangni qayta-qayta tanlashni noqulay qilardi
// — endi Word/Google Docs'dagi kabi tayyor ranglar to'plami (grid)
// chiqadi, tagida esa istalgan boshqa rang uchun native tanlovchi ham bor.
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

    // Palette hali biror boshqa elementga fokus o'tkazmasdan turib,
    // joriy tanlangan matnni saqlab qo'yamiz (aks holda rang tanlanganda
    // qo'llash uchun hech narsa qolmaydi — xuddi native <input type="color">
    // bilan bo'lgani kabi).
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
// uchun tanlangan matnga eng yaqin blok elementi (paragraf, ro'yxat band
// va h.k.) qidirib topilib, unga line-height qo'yiladi. Hech narsa
// tanlanmagan bo'lsa (yoki blok topilmasa) — butun matnga qo'llanadi.
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

// Fayl tanlash oynasi (native, 🖼) yoki video-qo'shish modali ochilganda
// kursor tahrirlagichdan "chiqib ketadi" — keyin oddiy editor.focus()
// chaqirilsa, brauzer avvalgi joyni ESLAB QOLMAYDI, balki kursorni
// tahrirlagich BOSHIGA qo'yadi (shu sabab video har doim matn boshida
// paydo bo'lardi). Yechim: fayl tanlash/modal ochilishidan OLDIN joriy
// kursor o'rnini (Range) saqlab qo'yamiz, keyin insert vaqtida O'SHA
// joyga qaytaramiz.
let richInsertSavedRange = null;

function captureEditorSelection(editorId) {
    const editor = document.getElementById(editorId);
    const sel = window.getSelection();
    if (!editor || !sel || sel.rangeCount === 0) return null;
    const range = sel.getRangeAt(0);
    if (!editor.contains(range.commonAncestorContainer)) return null;
    return range.cloneRange();
}

// Saqlangan joyga kursorni qaytaradi; agar hech narsa saqlanmagan bo'lsa
// (masalan tahrirlagich hali bo'sh edi) — kontent oxiriga qo'yiladi.
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
    const inputId = editorId === "newSectionTextEditor" ? "newSectionImageInput" : "editSectionImageInput";
    document.getElementById(inputId).click();
}

// "🖼 Rasm qo'shish" — fayl tanlangach serverga yuklanadi (virus/tur
// tekshiruvi bilan, boshqa fayl yuklashlar kabi), qaytgan URL kursor
// turgan joyga qo'yiladi. Oddiy <img> emas — "rich-img-wrap" ichiga
// pastki-o'ng burchakdagi sudraladigan tutqich (handle) bilan birga
// qo'yiladi, shu orqali rasm katta bo'lsa ham kichraytirish mumkin
// (attachImageResizeHandlers() shu tutqichni ushlaydi).
async function richInsertImage(editorId, fileInput) {
    const file = fileInput.files[0];
    if (!file) return;

    attachImageResizeHandlers(editorId);

    try {
        const formData = new FormData();
        formData.append("image", file);
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/upload-image`, {
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
        // Yuklash tugagach — endi kursorni saqlangan joyga qaytaramiz
        // (yuklash paytida boshqa joy bosilmagan bo'lsa deb umid qilib
        // emas, aynan shu payt uchun saqlangan Range'ni ishlatamiz).
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

// "🎬 Video qo'shish" (rich-toolbar, matn ICHIGA) — rasm bilan bir xil
// tamoyilda ishlaydi: video havolasi (YouTube, Vimeo, Instagram, Facebook
// va h.k.) YOKI
// kompyuterdan fayl, kursor
// turgan joyga qo'yiladi (matndan oldin/o'rtasida/keyin — cursor qayerda
// bo'lsa, shu yerga; xohlagancha marta chaqirib bir nechta video
// qo'shish mumkin). Kenglik qo'shishda so'raladi, keyin esa — rasm kabi
// — pastki-o'ng burchakdagi tutqichni sudrab ham o'zgartirish mumkin
// (attachImageResizeHandlers umumiy, img/video/iframe barchasini qamrab
// oladi). Bu — "🎬 Video qo'shish" checkbox orqali qo'shiladigan YAGONA,
// "ko'rib chiqilganda avtomatik tugatiladigan" rasmiy videodan MUSTAQIL —
// shu sabab bu yerdagi videolar tugatish (completion) holatiga ta'sir
// qilmaydi, xuddi rasm kabi shunchaki kontent hisoblanadi.
// window.prompt() o'rniga — ba'zi muhitlarda (masalan avtomatlashtirilgan
// brauzerlar) umuman qo'llab-quvvatlanmaydi, bundan tashqari bir nechta
// ketma-ket prompt() oynasi qulay emas. Shu sabab oddiy modal ishlatiladi:
// qaysi tahrirlagichga qo'yilishi videoInsertTargetEditorId'da eslab qolinadi.
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

// Havola YouTube'gami yoki boshqa manbagami — shunga qarab boshqacha
// ishlov beriladi (pastda, insertVideoEmbedHtml). YouTube uchun bare ID
// kerak (youtube.com/embed/{id}), boshqa manbalar esa o'z holicha (yoki
// to'g'ridan-to'g'ri video fayl bo'lsa <video> orqali) qo'yiladi.
function isYouTubeSource(source) {
    const trimmed = (source || "").trim();
    if (!trimmed) return false;
    if (/youtube\.com|youtu\.be/i.test(trimmed)) return true;
    // Havola/nuqta/probel yo'q qisqa satr — ehtimol xom YouTube ID
    // (masalan "dQw4w9WgXcQ").
    return !trimmed.includes("/") && !trimmed.includes(".") && !trimmed.includes(" ");
}

// Instagram (post/reels) ODDIY <iframe> orqali ko'rsatilmaydi — Instagram
// buni maxsus xavfsizlik siyosati bilan bloklaydi ("www.instagram.com не
// позволяет установить соединение" xatosi shundan). Ko'rsatish uchun
// Instagram'ning RASMIY embed usuli kerak: <blockquote class="instagram-
// media" data-instgrm-permalink="..."> + ularning "embed.js" skripti, u
// keyin shu blockquote'ni haqiqiy pleyerga almashtiradi. API kalit shart
// emas — faqat OMMAVIY (public) postlar uchun ishlaydi.
let instagramEmbedScriptState = "idle"; // idle | loading | loaded

function ensureInstagramEmbedProcessed() {
    if (instagramEmbedScriptState === "loaded") {
        if (window.instgrm && window.instgrm.Embeds) window.instgrm.Embeds.process();
        return;
    }
    if (instagramEmbedScriptState === "loading") return; // skript yuklangach o'zi process() chaqiradi (pastda)
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

// "🎬 Video qo'shish" oynasiga YouTube, Vimeo, Facebook, Instagram va
// CDN'dagi to'g'ridan-to'g'ri video fayl (.mp4 va h.k.) havolalarini
// qo'yish mumkin — manba turiga qarab boshqacha ishlov beriladi.
function insertVideoEmbedHtml(editorId, source, width) {
    restoreEditorSelection(editorId, richInsertSavedRange);
    attachImageResizeHandlers(editorId);

    const trimmed = source.trim();
    let mediaHtml;
    let isInstagram = false;

    if (isYouTubeSource(trimmed)) {
        // YouTube — bare ID kerak (extractYouTubeId orqali).
        const videoId = escapeHtml(extractYouTubeId(trimmed));
        mediaHtml = `<iframe src="https://www.youtube.com/embed/${videoId}" style="width:${width};max-width:100%;aspect-ratio:16/9;border:0;display:block" allowfullscreen></iframe>`;
    } else if (/facebook\.com|fb\.watch/i.test(trimmed)) {
        // Facebook — o'zining "video plugin"i orqali (API kalit shart
        // emas): oddiy iframe, faqat manzil ularning plugin URL'iga
        // ulanadi (href sifatida asl havola beriladi).
        const encodedUrl = encodeURIComponent(trimmed);
        mediaHtml = `<iframe src="https://www.facebook.com/plugins/video.php?href=${encodedUrl}&show_text=false" style="width:${width};max-width:100%;aspect-ratio:16/9;border:0;display:block" allowfullscreen></iframe>`;
    } else if (/instagram\.com/i.test(trimmed)) {
        // Instagram — oddiy iframe ishlamaydi (yuqoridagi izohga qarang),
        // rasmiy blockquote+embed.js usuli ishlatiladi.
        isInstagram = true;
        const url = escapeHtml(trimmed);
        mediaHtml = `<blockquote class="instagram-media" data-instgrm-permalink="${url}" data-instgrm-version="14" style="width:${width};max-width:100%;min-width:220px;margin:0 auto;"></blockquote>`;
    } else if (/\.(mp4|webm|ogg|ogv|mov)(\?|$)/i.test(trimmed)) {
        // To'g'ridan-to'g'ri video fayl havolasi (masalan CDN'dan .mp4).
        const url = escapeHtml(trimmed);
        mediaHtml = `<video src="${url}" controls style="width:${width};max-width:100%;display:block"></video>`;
    } else {
        // Boshqa manba (Vimeo va h.k.) — umumiy iframe embed sifatida
        // qo'yiladi. E'tibor bering: havola aynan "embed" uchun mo'ljallangan
        // bo'lishi kerak (masalan Vimeo uchun oddiy vimeo.com/XXXXX emas,
        // https://player.vimeo.com/video/XXXXX ko'rinishida) — aks holda
        // ba'zi saytlar iframe orqali ko'rsatishni bloklashi mumkin.
        const url = escapeHtml(trimmed);
        mediaHtml = `<iframe src="${url}" style="width:${width};max-width:100%;aspect-ratio:16/9;border:0;display:block" allowfullscreen></iframe>`;
    }

    // E'tibor bering: kenglik WRAP'ga emas, to'g'ridan-to'g'ri media
    // elementga qo'yiladi — xuddi rasmdagi kabi, shunda tutqichni sudrash
    // ham (startImageResize/updateImageResize) o'zgarishsiz ishlayveradi.
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
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/upload-video`, {
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

// ================= "🎞 PPT qo'shish" (matn ICHIGA, slaydlar sifatida) =================
// Rasm/video bilan bir xil tamoyilda: fayl serverga yuklanadi (LibreOffice
// orqali har bir slayd alohida rasmga aylantiriladi — FileStorageService.
// storeCoursePptSlides), qaytgan slayd URL'lari ro'yxati kursor turgan
// joyga qo'yiladi. MUHIM: bu HAM "rich-img-wrap" texnikasidan foydalanadi
// (birinchi slayd oddiy <img> sifatida) — shu sabab sudrab-o'lchamini-
// o'zgartirish tutqichi HECH QANDAY qo'shimcha kodsiz o'zi ishlayveradi
// (startImageResize wrap ichidan "img, video, iframe" qidiradi). Ustiga
// qo'shilgan ◀ N/M ▶ tugmalari (pptSlideNav) shu <img>ning src'ini
// slaydlar orasida almashtiradi — <img> elementining o'zi almashtirilmagani
// uchun, qo'lda o'zgartirilgan o'lcham slaydlar orasida o'tilganda ham saqlanadi.
function triggerPptInsert(editorId) {
    richInsertSavedRange = captureEditorSelection(editorId);
    const inputId = editorId === "newSectionTextEditor" ? "newSectionPptInput" : "editSectionPptInput";
    document.getElementById(inputId).click();
}

async function richInsertPpt(editorId, fileInput) {
    const file = fileInput.files[0];
    if (!file) return;

    attachImageResizeHandlers(editorId);

    try {
        const formData = new FormData();
        formData.append("ppt", file);
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/upload-ppt`, {
            method: "POST", body: formData
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "❌ Taqdimotni import qilishda xatolik");
            return;
        }

        const slides = data.slideUrls || [];
        if (!slides.length) {
            showAlertModal("❌ Taqdimotda hech qanday slayd topilmadi");
            return;
        }

        restoreEditorSelection(editorId, richInsertSavedRange);
        insertPptSlideshowHtml(editorId, slides);
    } catch (err) {
        console.error(err);
        showAlertModal("❌ Taqdimotni import qilishda tarmoq xatoligi");
    } finally {
        fileInput.value = "";
    }
}

function insertPptSlideshowHtml(editorId, slides) {
    // JSON.stringify natijasi doim qo'sh tirnoq (") bilan — shu sabab
    // atribut ichiga qo'yishdan oldin ular &quot;ga almashtiriladi (aks
    // holda atribut vaqtidan oldin yopilib, HTML buziladi — bu loyihada
    // bir necha marta uchragan onclick+JSON.stringify bugi bilan bir xil turkum).
    const slidesAttr = JSON.stringify(slides).replace(/"/g, "&quot;");
    const firstUrl = escapeHtml(slides[0]);
    const html = `<span class="rich-img-wrap rich-ppt-wrap" contenteditable="false" data-slides="${slidesAttr}" data-slide-index="0">`
        + `<img src="${firstUrl}">`
        + `<span class="rich-ppt-nav" contenteditable="false">`
        + `<button type="button" class="rich-ppt-nav-btn" onclick="pptSlideNav(event,-1)" title="Oldingi slayd">◀</button>`
        + `<span class="rich-ppt-counter">1 / ${slides.length}</span>`
        + `<button type="button" class="rich-ppt-nav-btn" onclick="pptSlideNav(event,1)" title="Keyingi slayd">▶</button>`
        + `</span>`
        + `<span class="rich-img-handle" title="Sudrab o'lchamini o'zgartiring"></span>`
        + `</span>&nbsp;`;
    document.execCommand('insertHTML', false, html);
    injectAlignBars(editorId);
    injectCaptions(editorId);
}

// ◀/▶ tugmalari — shu wrap ichidagi <img>ning src'ini data-slides
// ro'yxatidagi keyingi/oldingi slaydga almashtiradi (aylanma: oxiridan
// birinchiga, aksincha). E'TIBOR: xuddi shu funksiya courseSectionView.js'da
// ham bor (talaba/kursni ko'rish sahifasida) — chunki bu HTML saqlangan
// matn ichida, ikkala sahifada ham (tahrirlashda VA o'qishda) ishlashi kerak.
function pptSlideNav(evt, direction) {
    evt.preventDefault();
    evt.stopPropagation();
    const wrap = evt.currentTarget.closest('.rich-ppt-wrap');
    if (!wrap) return;

    let slides;
    try {
        slides = JSON.parse(wrap.dataset.slides || "[]");
    } catch (e) {
        slides = [];
    }
    if (!slides.length) return;

    let index = Number(wrap.dataset.slideIndex || 0);
    index = (index + direction + slides.length) % slides.length;
    wrap.dataset.slideIndex = String(index);

    const img = wrap.querySelector('img');
    if (img) img.src = slides[index];

    const counter = wrap.querySelector('.rich-ppt-counter');
    if (counter) counter.textContent = `${index + 1} / ${slides.length}`;
}

// ================= Rasm/video'ni chapga/markazga/o'ngga surish =================
// Har bir "rich-img-wrap" (rasm ham, video ham) ustida — resize tutqichi
// kabi — kichik surish tugmalari (⬅ ⏺ ➡) chiqadi. Ular bosilganda wrap'ga
// "align-left/center/right" klassi qo'shiladi (courses.css'da wrap'ni
// display:block qilib, margin orqali chap/markaz/o'ngga suradi — hech
// qanday klass bo'lmasa standart holat: matn bilan bir qatorda, chapdan
// boshlanadi). Yangi qo'shilgan rasm/video uchun ham (richInsertImage/
// insertVideoEmbedHtml/richInsertUploadedVideo — insertHTML'dan keyin),
// oldin saqlangan (bazadan yuklangan eski) rasm/video uchun ham
// (openEditSectionForm — kontent yuklangandan keyin) chaqiriladi, shu
// sabab ESKI rasmlarga ham bu imkoniyat qo'shiladi.
function injectAlignBars(editorId) {
    const editor = document.getElementById(editorId);
    if (!editor) return;
    editor.querySelectorAll('.rich-img-wrap').forEach((wrap) => {
        if (wrap.querySelector('.rich-img-align-bar')) return; // Allaqachon bor
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

// ================= Rasm/video ostiga (ixtiyoriy) sarlavha =================
// Har bir "rich-img-wrap" ichiga rasm/video OSTIDA kichik, ALOHIDA
// tahrirlanadigan (contenteditable="true") matn qatori qo'shiladi — o'zi
// contenteditable="false" bo'lgan wrap ICHIDA shu bitta joy yana
// tahrirlanadigan qilib qo'yilgan ("orol" texnikasi, brauzerlar qo'llab-
// quvvatlaydi). Bo'sh bo'lsa — tahrirlashda kulrang "Sarlavha (ixtiyoriy)"
// ko'rinadi, o'qish sahifasida esa umuman ko'rinmaydi (courses.css).
// Majburiy emas — foydalanuvchi yozmasa, saqlashda butunlay olib
// tashlanadi (cleanupEmptyCaptions, submitAddSection/submitEditSection).
function injectCaptions(editorId) {
    const editor = document.getElementById(editorId);
    if (!editor) return;
    editor.querySelectorAll('.rich-img-wrap').forEach((wrap) => {
        if (wrap.querySelector('.rich-img-caption')) return; // Allaqachon bor
        const caption = document.createElement('div');
        caption.className = 'rich-img-caption';
        caption.setAttribute('contenteditable', 'true');
        caption.setAttribute('data-placeholder', 'Sarlavha (ixtiyoriy)');
        wrap.appendChild(caption);
    });
}

// Saqlashdan oldin chaqiriladi — foydalanuvchi yozmagan (bo'sh) sarlavha
// qatorlarini butunlay olib tashlaydi, shunda bazada keraksiz bo'sh
// "<div class=rich-img-caption></div>" saqlanib qolmaydi.
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
// Kontenteditable'ning o'z (native) rasm resize funksiyasi ko'p
// brauzerlarda ishonchli ishlamaydi (eski execCommand asosidagi
// standart, asta-sekin olib tashlanmoqda) — shuning uchun oddiy, o'zimiz
// yozgan JS orqali ishlaydigan sudrash amalga oshirilgan. Holat bitta
// global o'zgaruvchida saqlanadi — bir vaqtning o'zida faqat bitta rasm
// sudralishi mumkin.
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
// tutqich orqali sudrab o'lchamini o'zgartirishi mumkin.
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

// Fayl tanlanishi bilan DARHOL import qilinmaydi — foydalanuvchi avval
// qaysi faylni tanlaganini ko'rishi (brauzerning o'zi nomini ko'rsatadi),
// keyin "📥 Import qilish"ni bosishi (yoki "Bekor qilish" bilan tanlovni
// bekor qilishi) kerak. Shu sabab bu yerda faqat import/bekor qilish
// tugmalarini ko'rsatish/yashirish bilan cheklanamiz — haqiqiy import
// faqat importDocxFile() chaqirilganda (tugma bosilganda) sodir bo'ladi.
function onImportFileSelected(fileInput, actionsId) {
    const actions = document.getElementById(actionsId);
    if (fileInput.files && fileInput.files[0]) {
        actions.classList.remove('hidden');
    } else {
        actions.classList.add('hidden');
    }
}

function cancelDocxImport(fileInputId, actionsId) {
    document.getElementById(fileInputId).value = "";
    document.getElementById(actionsId).classList.add('hidden');
}

// .docx faylni mammoth.js orqali HTML'ga aylantiradi — abzatslar,
// qalin/kursiv, sarlavhalar, ro'yxatlar kabi formatlash saqlanadi (fayl
// ichidagi shriftlar/uslublar o'zgartirilmaydi, faqat saytning umumiy
// dizayniga moslashtiriladi). Natija to'g'ridan-to'g'ri tahrirlash
// maydoniga qo'yiladi — kerak bo'lsa qo'lda ham tahrirlash mumkin.
async function importDocxFile(fileInput, editorId) {
    const file = fileInput.files[0];
    if (!file) return;

    const actionsId = editorId === "newSectionTextEditor" ? "newSectionImportActions" : "editSectionImportActions";

    if (typeof mammoth === "undefined") {
        showAlertModal("❌ Import kutubxonasi yuklanmadi. Internet aloqasini tekshirib, sahifani qayta yuklang.");
        fileInput.value = "";
        document.getElementById(actionsId).classList.add('hidden');
        return;
    }

    try {
        const arrayBuffer = await file.arrayBuffer();
        const result = await mammoth.convertToHtml({ arrayBuffer });
        // Word faylida muallif Shift+Enter bilan qo'ygan qator ko'chirishlar
        // (haqiqiy abzats emas — ko'pincha shunchaki Word'ning O'Z sahifa
        // enida chiroyli ko'rinishi uchun qo'yilgan) mammoth tomonidan
        // <br> qilib saqlanadi. Bu yerda saqlansa, matn hali QATOR
        // TO'LMASDAN erta ko'chib ketadi. Shu sabab olib tashlanadi —
        // matn endi shu tahrirlagichning o'z enida tabiiy ravishda,
        // qator to'lgach avtomatik ko'chadi (haqiqiy abzatslar — <p>
        // teglari — bunga taalluqli emas, ular saqlanadi).
        document.getElementById(editorId).innerHTML = result.value.replace(/<br\s*\/?>/gi, ' ');
    } catch (err) {
        console.error(err);
        showAlertModal("❌ Faylni import qilishda xatolik: " + err.message);
    } finally {
        fileInput.value = "";
        document.getElementById(actionsId).classList.add('hidden');
    }
}

// "Bo'lim" endi tanlov (select) — mavjud bo'limlar ro'yxati + "➕ Boshqa..."
// (erkin nom kiritish uchun, agar kerakli bo'lim ro'yxatda bo'lmasa).
let cachedSciences = [];
const OTHER_SCIENCE_VALUE = "__other__";

function loadScienceNamesList() {
    fetch("/api/science")
        .then(r => r.ok ? r.json() : [])
        .then(sciences => {
            cachedSciences = sciences;
            populateScienceSelect("newSectionScienceSelect");
            populateScienceSelect("editSectionScienceSelect");
        })
        .catch(err => console.error(err));
}

function populateScienceSelect(selectId) {
    const select = document.getElementById(selectId);
    const currentValue = select.value; // qayta to'ldirilganda joriy tanlov yo'qolmasin
    select.innerHTML = cachedSciences.map(s => `<option value="${escapeHtml(s.name)}">${escapeHtml(s.name)}</option>`).join("")
        + `<option value="${OTHER_SCIENCE_VALUE}">➕ Boshqa...</option>`;
    if (currentValue) select.value = currentValue;
}

function onScienceSelectChange(mode) {
    const select = document.getElementById(mode === "new" ? "newSectionScienceSelect" : "editSectionScienceSelect");
    const otherInput = document.getElementById(mode === "new" ? "newSectionScienceOther" : "editSectionScienceOther");
    otherInput.style.display = select.value === OTHER_SCIENCE_VALUE ? "block" : "none";

    // Bo'lim o'zgartirilganda — Mavzu ro'yxati ham shu YANGI Bo'limda TEST
    // BOSHQARUVIda mavjud Mavzular bilan qayta to'ldiriladi
    // (populateChapterSelect). Joriy tanlov ("id:<id>" bo'lsa — kursning
    // o'z Mavzusi, Bo'lim o'zgarsa ham hamon amal qiladi) saqlanadi.
    const chapterSelectId = mode === "new" ? "newSectionChapterSelect" : "editSectionChapterSelect";
    const currentValue = document.getElementById(chapterSelectId).value;
    const currentChapterId = currentValue.startsWith("id:") ? Number(currentValue.slice(3)) : null;
    populateChapterSelect(chapterSelectId, currentChapterId, mode);
}

// Formani ochishda chaqiriladi — agar berilgan nom (masalan shu kursning
// o'zi nomi, yoki darsga allaqachon bog'langan bo'lim) ro'yxatda mavjud
// bo'lsa, o'sha tanlanadi; aks holda "Boshqa" tanlanib, erkin maydonga
// o'sha nom qo'yiladi (foydalanuvchi kerak bo'lsa o'zgartirishi mumkin).
function applyScienceSelection(mode, preferredName) {
    const select = document.getElementById(mode === "new" ? "newSectionScienceSelect" : "editSectionScienceSelect");
    const otherInput = document.getElementById(mode === "new" ? "newSectionScienceOther" : "editSectionScienceOther");

    const trimmed = (preferredName || "").trim();
    const match = trimmed && cachedSciences.find(s => s.name.toLowerCase() === trimmed.toLowerCase());

    if (match) {
        select.value = match.name;
        otherInput.style.display = "none";
        otherInput.value = "";
    } else {
        select.value = OTHER_SCIENCE_VALUE;
        otherInput.style.display = "block";
        otherInput.value = trimmed;
    }
}

function getSelectedScienceName(mode) {
    const select = document.getElementById(mode === "new" ? "newSectionScienceSelect" : "editSectionScienceSelect");
    const otherInput = document.getElementById(mode === "new" ? "newSectionScienceOther" : "editSectionScienceOther");
    if (select.value === OTHER_SCIENCE_VALUE) {
        return otherInput.value.trim() || null;
    }
    return select.value || null;
}

// Dars nomi (Topic) — default sifatida dars nomi (CourseSection title)
// bilan bir xil bo'lib turadi, foydalanuvchi maydonni o'zi qo'lda
// tahrirlagunga qadar (shundan keyin dars nomi o'zgarsa ham, Topic nomi
// endi avtomatik qayta yozilmaydi).
let newTopicNameManuallyEdited = false;
let editTopicNameManuallyEdited = false;

function onSectionTitleInput(mode) {
    const titleField = document.getElementById(mode === "new" ? "newSectionTitle" : "editSectionTitle");
    const topicField = document.getElementById(mode === "new" ? "newSectionTopicName" : "editSectionTopicName");
    const manuallyEdited = mode === "new" ? newTopicNameManuallyEdited : editTopicNameManuallyEdited;

    if (!manuallyEdited) {
        topicField.value = titleField.value;
    }
}

function onTopicNameInput(mode) {
    if (mode === "new") newTopicNameManuallyEdited = true;
    else editTopicNameManuallyEdited = true;
}

// "🎯 Darsga oid testlar bilan bog'lash" — checkbox belgilanmagan bo'lsa
// bo'lim/dars maydonlari yashirin turadi VA saqlashda umuman yuborilmaydi
// (getSelectedScienceName/topicName checkbox holatini submitAddSection /
// submitEditSection'da tekshiradi) — shunda tasodifan (checkbox
// belgilanmasdan) dars boshqa bo'lim/Topic'ga bog'lanib qolmaydi.
function onTopicLinkToggle(mode) {
    const checkbox = document.getElementById(mode === "new" ? "newSectionLinkTopic" : "editSectionLinkTopic");
    const fields = document.getElementById(mode === "new" ? "newSectionTopicFields" : "editSectionTopicFields");
    fields.style.display = checkbox.checked ? "block" : "none";
}

// Promise QAYTARADI — chaqiruvchilar render tugashini kutib, keyin
// selectCard() bilan kerakli kartaga fokus qaytarishi mumkin bo'lishi
// uchun (masalan submitEditSection() — tahrirlab saqlagandan keyin
// AYNAN o'sha kartaga qaytarish, haqiqiy foydalanuvchi shikoyati).
function loadCourse() {
    return fetch(`/api/courses/${COURSE_ID}`)
        .then(r => {
            if (!r.ok) throw new Error("Kurs topilmadi yoki ruxsat yo'q");
            return r.json();
        })
        .then(course => {
            // "/chapters" — faqat OWNER/ADMIN uchun ruxsat etilgan (backend
            // @PreAuthorize) — oddiy talaba (canManage=false) uchun umuman
            // chaqirilmaydi (403 bo'lardi, va bo'sh Mavzu ko'rsatish ham
            // faqat boshqaruvchilar uchun ma'noli).
            if (!course.canManage) {
                allChapters = [];
                return renderCourse(course);
            }
            return fetch(`/api/courses/${COURSE_ID}/chapters`)
                .then(r => r.ok ? r.json() : [])
                .catch(() => [])
                .then(chapters => {
                    allChapters = chapters;
                    renderCourse(course);
                });
        })
        .catch(err => {
            console.error(err);
            document.getElementById("courseTitle").textContent = "Kurs topilmadi";
        });
}

function renderCourse(course) {
    cachedCourse = course;

    document.getElementById("courseTitle").textContent = course.title;
    document.getElementById("courseDescription").textContent = course.description || "";

    updateSubscribeBanner(course);

    // Boshqarish paneli (tahrirlash/chop etish/bo'lim qo'shish) — OWNER
    // uchun HAR DOIM, ADMIN uchun faqat O'ZI yaratgan kursda (backend
    // shu logikani hisoblab, canManage sifatida qaytaradi).
    const managePanel = document.getElementById("manageCoursePanel");
    managePanel.style.display = course.canManage ? "block" : "none";
    // Umumiy "Saralash" panelining ko'rinish-yo'qligi shu YERDA emas,
    // renderSections() ichida hal qilinadi: guruhlangan (Mavzuli) kursda
    // bu umumiy panel BUTUNLAY yashiriladi — har bir Mavzu o'zining
    // alohida "Saralash" tugmalariga ega bo'ladi (renderChapterBox —
    // sortChapterSections). Faqat mavzusiz (flat) kursda ko'rinadi.

    // Panel yig'ilgan/ochiq holati — saqlangan tanlov bo'lsa o'shanga
    // qarab, aks holda HTML'dagi standart (yig'ilgan) holatda qoladi.
    if (course.canManage) {
        const savedCollapsed = localStorage.getItem(MANAGE_PANEL_COLLAPSED_KEY);
        if (savedCollapsed === "0") {
            managePanel.classList.remove("collapsed");
        } else if (savedCollapsed === "1") {
            managePanel.classList.add("collapsed");
        }
    }

    if (course.canManage) {
        document.getElementById("togglePublishBtn").textContent =
            course.published ? "📕 Qoralamaga o'tkazish" : "📗 Chop etish";
        document.getElementById("editCourseTitle").value = course.title;
        document.getElementById("editCourseDescription").value = course.description || "";
        // Faqat boshqaruvchilarga ko'rinadigan tugma (#sectionTrashBtn) —
        // badge ham shu shart bilan yangilanadi (aks holda oddiy
        // o'quvchida keraksiz 403 so'rov ketardi).
        refreshCourseSectionTrashBadge();
    }

    renderSections(course.sections);
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

function refreshCourseSectionTrashBadge() {
    fetch(`/api/courses/${COURSE_ID}/sections/deleted`)
        .then(r => r.ok ? r.json() : [])
        .then(items => setTrashBadgeCount("courseSectionTrashBadge", items.length))
        .catch(err => console.error(err));
}

// Obuna banner'i ("🔒 ... obuna kerak" / "⏳ so'rov yuborilgan") — alohida
// funksiyaga chiqarilgan, chunki uni ikki joydan (kurs yuklanganda VA
// to'lov config'i keyinroq kelganda) qayta chizish kerak bo'ladi.
function updateSubscribeBanner(course) {
    const showBanner = !course.subscribed && !course.canManage;
    document.getElementById("subscribeBanner").style.display = showBanner ? "flex" : "none";

    if (!showBanner) return;

    const requestBtn = document.getElementById("requestSubscriptionBtn");
    const payBtn = document.getElementById("payWithClickBtn");

    if (course.requestPending) {
        document.getElementById("subscribeBannerText").textContent =
            "⏳ Obunaga so'rovingiz yuborilgan — administrator (OWNER) javobini kuting.";
        requestBtn.style.display = "none";
        payBtn.style.display = "none";
        return;
    }

    const priceText = course.price ? ` Narxi: ${formatPrice(course.price)} so'm.` : "";
    document.getElementById("subscribeBannerText").textContent =
        "🔒 Bu kursning to'liq mazmuniga kirish uchun obuna kerak." + priceText;
    requestBtn.style.display = "";

    // Onlayn to'lov faqat Click ulangan VA kurs narxi belgilangan bo'lsa
    // ko'rinadi (narxsiz kursda avtomatik summani hisoblab bo'lmaydi —
    // bunday holda faqat "so'rov yuborish" orqali, OWNER summani qo'lda
    // belgilaydi).
    payBtn.style.display = (clickPaymentEnabled && course.price) ? "" : "none";
}

async function payWithClick() {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/subscriptions/pay`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ durationMonths: 1, provider: "CLICK" })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            showAlertModal(data.error || "To'lovni boshlashda xatolik");
            return;
        }

        location.href = data.checkoutUrl;
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

async function requestSubscription() {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/subscriptions/request`, { method: "POST" });
        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }

        showAlertModal("✅ So'rovingiz yuborildi. Administrator (OWNER) ko'rib chiqib, obunani tasdiqlaydi.");
        loadCourse();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "sections" — HAR DOIM kursning TO'LIQ (sahifalanmagan) darslar ro'yxati.
// Joriy sahifa (sectionsPage) shu funksiya ichida keyingi chaqiruvlargacha
// eslab qolinadi (loadCourse() -> renderCourse() har safar to'liq
// ro'yxatni qayta beradi, lekin foydalanuvchi qaysi sahifada turgani
// o'zgarmasligi kerak — masalan mavzu tahrirlangandan keyin).
let allSections = [];

// Kursning BARCHA Mavzulari (CourseChapter) — hozircha BO'SH (hech qanday
// darsga biriktirilmagan) bo'limlar ham shu jumladan. Faqat canManage
// bo'lganda yuklanadi (loadCourse — endpoint OWNER/ADMIN uchun) va
// getSortedChapterGroups()'da "bo'sh mavzu" quti sifatida ko'rsatiladi
// (foydalanuvchi so'rovi, 2026-09-05: "bo'sh bo'limlar bu yerda ham
// ko'rinsin" — aks holda katalog kartasidagi "N ta mavzu" soni bilan bu
// sahifada ko'rinayotgan mavzular soni mos kelmasdi).
let allChapters = [];

function renderSections(sections) {
    allSections = sections;

    // Kursda kamida bitta dars biror Mavzuga biriktirilgan BO'LSA, YOKI
    // (hozircha bo'sh bo'lsa ham) kamida bitta Mavzu (CourseChapter) yaratib
    // qo'yilgan bo'lsa — guruhlangan ("box"li) ko'rinishga o'tiladi, aks
    // holda (standart, mavzular umuman yo'q kurslar) 100% eskidek, bitta
    // tekis grid.
    const hasAnyChapter = sections.some(s => s.chapterId != null) || allChapters.length > 0;
    const canManage = cachedCourse && cachedCourse.canManage;

    // Umumiy (butun kurs bo'yicha) "Saralash" — faqat GURUHLANMAGAN
    // (flat) ko'rinishda ma'noli, chunki guruhlangan ko'rinishda har bir
    // Mavzu endi O'ZINING alohida "Saralash" tugmalariga ega
    // (renderChapterBox), bittasi butun kursni aralashtirib yubormasligi
    // uchun.
    document.getElementById("sectionsSortBar").style.display = (canManage && !hasAnyChapter) ? "flex" : "none";

    // Sahifa sarlavhasi — Mavzuli kursda endi pastda TO'G'RIDAN-TO'G'RI
    // dars kartalari emas, Mavzu "box"lari ko'rinadi (📋 Darslar
    // sarlavhasi endi har bir OCHILGAN mavzu ICHIDA, renderChapterBox).
    document.getElementById("curriculumTitle").textContent = hasAnyChapter ? "📂 Mavzular" : "📋 Darslar";

    // "🔍 Mavzu qidirish" — faqat guruhlangan (Mavzuli) ko'rinishda ma'noli.
    document.getElementById("chapterSearchBox").style.display = hasAnyChapter ? "block" : "none";

    // "Kurs ichidan dars yoritmasi bo'yicha qidiruv" — tahrirlashga
    // aloqasi yo'q, oddiy o'qish/qidiruv, shuning uchun sortBar'dan
    // farqli faqat boshqaruvchilarga emas — istalgan foydalanuvchiga
    // (OWNER/ADMIN/USER) ko'rinadi (flat/guruhlangan ko'rinishdan qat'i nazar).
    document.getElementById("explanationSearchBox").style.display = "block";

    if (hasAnyChapter) {
        renderGroupedSections();
    } else {
        renderFlatSections();
    }

    // Sahifa birinchi ochilganda — bir marta: "?focus=" bo'lsa o'sha
    // kartaga, bo'lmasa DEFAULT holatda birinchi (ekranda ko'rinadigan
    // eng birinchi) kartaga fokus/belgilash qo'yiladi (foydalanuvchi
    // so'rovi bo'yicha). Keyingi qayta chizishlarda (masalan mavzu
    // tahrirlangandan keyin) takrorlanmaydi.
    if (!pendingFocusApplied) {
        pendingFocusApplied = true;
        if (focusSectionIdFromUrl) {
            applyFocusFromUrl(focusSectionIdFromUrl);
        } else {
            selectFirstCardByDefault();
        }
    }
}

// ENG BIRINCHI (orderIndex bo'yicha) kartani tanlangan/fokusda deb
// belgilaydi.
function selectFirstCardByDefault() {
    const hasAnyChapter = allSections.some(s => s.chapterId != null) || allChapters.length > 0;

    if (!hasAnyChapter) {
        // Flat (mavzusiz) ko'rinish — barcha kartalar har doim DOM'da,
        // shu sabab oddiy DOM tartibiga tayanish kifoya.
        const firstCardEl = document.querySelector(".section-item");
        if (firstCardEl) selectCard(Number(firstCardEl.dataset.sectionId));
        return;
    }

    // Guruhlangan (Mavzuli) ko'rinishda BARCHA Mavzu qutilari sahifa
    // birinchi ochilganda YOPIQ (accordion, expandedChapterKeys bo'sh) —
    // shu sabab ".section-item" DOM'da UMUMAN bo'lmasligi mumkin, va
    // oldingi (DOM'ga tayangan) usul hech narsa topa olmay, default
    // fokus butunlay qo'yilmay qolardi (foydalanuvchi so'rovi, 2026-09-05:
    // "kurslardagi focuslarni ham ko'rib chiq" — topic.js'dagi Mavzu-
    // filtrlangan Darslar bilan bir xil sinf muammo). Endi
    // getSortedChapterGroups()'dan (allChapters bilan BIRGA — bo'sh
    // Mavzular ham kiradi, lekin items.length===0 bo'lgani uchun
    // avtomatik o'tkazib yuboriladi) birinchi DARSI BOR guruh topilib,
    // o'sha guruhning birinchi darsi selectCard() orqali tanlanadi — u
    // kerak bo'lsa mavzuni o'zi avtomatik ochadi ("?focus=" va Ctrl+↑/↓
    // navigatsiyasi bilan BIR XIL, allaqachon sinovdan o'tgan yo'l).
    const firstGroupWithItems = getSortedChapterGroups().find(g => g.items.length > 0);
    const firstItem = firstGroupWithItems ? firstGroupWithItems.items[0] : null;
    if (firstItem) selectCard(firstItem.id);
}

// "?focus=<sectionId>" — sahifa birinchi ochilganda, o'sha kartani o'zi
// turgan sahifa/Mavzuga o'tkazib (agar hozirgi sahifada bo'lmasa),
// ekranga chiqarib, "tanlangan" holatda belgilaydi (selectCard).
function applyFocusFromUrl(sectionId) {
    const section = allSections.find(s => s.id === sectionId);
    if (!section) return; // topilmadi (o'chirilgan/boshqa kurs) — jim o'tkazib yuboriladi

    const hasAnyChapter = allSections.some(s => s.chapterId != null);
    if (hasAnyChapter) {
        const key = section.chapterId != null ? String(section.chapterId) : "none";
        const items = allSections.filter(s => (s.chapterId != null ? String(s.chapterId) : "none") === key);
        const idx = items.findIndex(s => s.id === sectionId);
        chapterPages[key] = Math.floor(idx / CHAPTER_SECTIONS_PER_PAGE);
        renderGroupedSections();
    } else {
        const idx = allSections.findIndex(s => s.id === sectionId);
        sectionsPage = Math.floor(idx / SECTIONS_PER_PAGE);
        renderFlatSections();
    }

    selectCard(sectionId, { scroll: true });
}

// Har bir dars-tugmalar-karta shablonini bir joyda ushlab turadi — flat
// va guruhlangan (mavzuli) ko'rinishlar ikkalasi ham shundan foydalanadi.
// globalIndexById — ⬆️/⬇️ chegaralarini (birinchi/oxirgi) TO'LIQ (allSections)
// ro'yxatdagi haqiqiy o'rniga qarab hisoblash uchun (guruhlangan ko'rinishda
// bitta mavzu ichidagi tartib to'liq ro'yxatdagi tartibning bir qismi,
// xolos — chegara tekshiruvi baribir GLOBAL bo'lishi kerak).
//
// "displayNumber" — kartochkada KO'RINADIGAN raqam (ko'k doira ichida).
// s.orderIndex — BUTUN kurs bo'yicha bitta umumiy, ketma-ket son (backend
// /sections/reorder shuni kutadi, Mavzu bo'yicha alohida EMAS — CourseService
// izohiga qarang), shu sabab 2-mavzu kartalari "44, 45, 46..." kabi
// 1-mavzuning davomi bo'lib ko'rinardi (haqiqiy foydalanuvchi shikoyati).
// Endi har bir chaqiruvchi (renderChapterBox) shu mavzu ICHIDAGI o'rnini
// (1'dan boshlab) alohida hisoblab beradi — berilmasa (masalan mavzusiz
// tekis ro'yxatda, renderFlatSections), eskicha s.orderIndex ishlatiladi.
// "groupBounds" — {isFirst, isLast}: shu karta o'z guruhi (guruhlangan
// ko'rinishda — mavzu, sahifalashdan qat'i nazar TO'LIQ mavzu ro'yxati
// bo'yicha; berilmasa — GLOBAL, butun kurs bo'yicha) ichida birinchi/oxirgi
// o'rindami — ⬆️/⬇️ tugmalarini shunga qarab o'chiradi (disabled). Avval
// har doim GLOBAL (butun kurs) chegara ishlatilardi — guruhlangan
// ko'rinishda bu chalkash edi: masalan 2-mavzuning BIRINCHI darsi
// "yuqoriga" tugmasi yoqilgan ko'rinardi (global birinchi emasligi uchun),
// lekin bosilganda 1-mavzu bilan chegarada hech qanday KO'RINADIGAN
// o'zgarish bermасdi (haqiqiy foydalanuvchi shikoyati).
function renderSectionCard(s, globalIndexById, displayNumber, groupBounds) {
    const i = globalIndexById.get(s.id);
    const isFirstInGroup = groupBounds ? groupBounds.isFirst : (i === 0);
    const isLastInGroup = groupBounds ? groupBounds.isLast : (i === allSections.length - 1);
    // "Tugallandi" (completed) holati endi RAQAMNI TO'SIB QO'YMAYDI — faqat
    // doiraning fonini yashilga o'zgartiradi, tartib raqamining o'zi doim
    // ko'rinib turadi (foydalanuvchi: "galochka tartib raqamini to'sib
    // qo'ygan").
    const indexClass = s.completed ? "section-index completed" : "section-index";
    const indexIcon = displayNumber ?? s.orderIndex;
    const typeIcon = s.type === "VIDEO" ? "🎬" : s.type === "MIXED" ? "📄🎬" : "📄";

    // Butun karta bosiladigan qilindi (kurslar katalogidagi kartalar
    // bilan bir xil uslub) — shuning uchun sarlavha endi alohida <a>
    // emas, oddiy matn; hover effekti ham shu tashqi kartada.
    const titleEl = `<span class="section-title-text" title="${escapeHtml(s.title)}">${escapeHtml(s.title)}</span>`;
    // Bosilganda — avval "tanlangan" deb belgilaymiz (selectCard),
    // qulflanmagan bo'lsa keyin darhol o'sha darsga o'tadi (qulflangan
    // bo'lsa faqat tanlash — klaviatura navigatsiyasi shu yerdan davom etadi).
    const cardClick = s.locked
        ? ` onclick="selectCard(${s.id})"`
        : ` onclick="selectCard(${s.id}); location.href='/courses/${COURSE_ID}/sections/${s.id}'"`;

    // Shu dars TEST BOSHQARUVIga bog'langan bo'lsa — nechta faol savoli
    // borligi (foydalanuvchi so'rovi bo'yicha: har bir dars kartochkasida
    // ko'rinishi kerak). linkedTopicQuestionCount backend'da BULK
    // hisoblanadi (CourseService.getDetail).
    const questionCountBadge = s.linkedTopicId != null
        ? `<span class="section-question-count-badge">📝 ${s.linkedTopicQuestionCount} ta test</span>`
        : "";

    // Shu dars haqiqiy test tizimidagi bir Topic'ga (Dars) bog'langan bo'lsa —
    // ro'yxatdan turib ham, darsni ochmasdan, testlarni yechish tugmasi
    // (faqat ochilgan/qulflanmagan darslarda — qulflangan bo'lsa
    // darsning o'zini ham ko'rib bo'lmaydi).
    const testLink = (!s.locked && s.linkedTopicId)
        ? `<button class="topic-test-btn-inline" onclick="event.stopPropagation(); location.href='/testConfigPage?scienceId=${s.linkedScienceId}&topicId=${s.linkedTopicId}&courseId=${COURSE_ID}&fromSectionId=${s.id}'">🎯 Darsga oid testlarni yechish</button>`
        : "";

    // Faqat boshqaruvchilar uchun — shu darsga (TEST BOSHQARUVIga
    // bog'langan bo'lsa) to'g'ridan-to'g'ri kartochkadan yangi test savoli
    // qo'shish (test-form.html'ga o'tadi, ?courseId= orqali — u yerdagi
    // "🔙 Kursga qaytish" tugmasi kursning UMUMIY (darslar ro'yxati)
    // sahifasiga qaytaradi, ANIQ shu dars ICHIGA emas). "&fromSectionId="
    // — aynan shu kartochkadan ketilganini eslab qolish uchun: test-form.js
    // "🔙 Kursga qaytish"da buni "?focus=" sifatida qaytarib beradi, shunda
    // bu sahifaga qaytilganda ANIQ shu karta avtomatik ko'rsatiladi/tanlanadi.
    const addTestBtn = (cachedCourse && cachedCourse.canManage && s.linkedTopicId)
        ? `<button class="topic-test-btn-inline" onclick="event.stopPropagation(); location.href='/question/${s.linkedTopicId}/create-test-form?courseId=${COURSE_ID}&fromSectionId=${s.id}'">➕ Testga savol qo'shish</button>`
        : "";

    // Ichidagi tugmalar (test, boshqarish) bosilganda kartaning o'zi
    // ham navigatsiya qilib yubormasligi uchun — shu wrapper'larga
    // event.stopPropagation() qo'yiladi.
    const manageActions = cachedCourse && cachedCourse.canManage
        ? `<div class="section-manage-actions" onclick="event.stopPropagation()">
               <button onclick="moveSectionUp(${s.id})" title="Yuqoriga" ${isFirstInGroup ? "disabled" : ""}>⬆️</button>
               <button onclick="moveSectionDown(${s.id})" title="Pastga" ${isLastInGroup ? "disabled" : ""}>⬇️</button>
               <button onclick="openEditSectionForm(${s.id})" title="Tahrirlash">✏️</button>
               <button onclick="openSectionWordExportModal(${s.id}, ${JSON.stringify(s.title).replace(/"/g, "&quot;")})" title="Shu darsni Word (.docx) faylga eksport qilish"><svg width="14" height="14" viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg" style="vertical-align:-2px;"><rect x="4" y="4" width="40" height="40" rx="7" fill="#185ABD"/><rect x="4" y="4" width="18" height="40" rx="7" fill="#103F91"/><text x="31" y="30" font-family="Arial, sans-serif" font-size="20" font-weight="bold" fill="#fff" text-anchor="middle">W</text></svg></button>
               <button onclick="deleteSection(${s.id})" title="O'chirish">🗑️</button>
           </div>`
        : "";

    // Test tugmasi va boshqarish tugmalari — kartaning ENG PASTIGA
    // (test tugmasi boshqarish tugmalarining USTIGA) yig'ilgan, sarlavha
    // qatoridan alohida. Bo'sh joy bo'lsa (.section-item flex-column),
    // shu blok margin-top:auto orqali pastga "yopishadi".
    const bottomGroup = (testLink || addTestBtn || manageActions)
        ? `<div class="section-item-bottom" onclick="event.stopPropagation()">
               ${testLink}
               ${addTestBtn}
               ${manageActions}
           </div>`
        : "";

    // Klaviatura navigatsiyasi (←/→/↑/↓/Home — onCardKeyDown) uchun karta
    // fokus oladigan (tabindex) va o'zining sectionId'sini bilishi kerak
    // (data-section-id — selectCard shu orqali DOM elementini topadi).
    const isSelected = s.id === selectedSectionId;

    return `
        <div class="section-item ${s.locked ? "locked" : ""} ${isSelected ? "selected" : ""}"
             data-section-id="${s.id}"
             tabindex="0"
             onkeydown="onCardKeyDown(event, ${s.id})"${cardClick}>
            <span class="kbd-hint-badge" onclick="event.stopPropagation(); toggleKbdHint(this)" title="Klaviatura yorliqlari">⌨️</span>
            <div class="section-item-top">
                <div class="section-item-badges">
                    <div class="${indexClass}">${indexIcon}</div>
                    <span class="section-type-icon">${typeIcon}</span>
                    ${s.locked ? '<span class="section-type-icon">🔒</span>' : ""}
                    ${questionCountBadge}
                </div>
                ${titleEl}
            </div>
            ${bottomGroup}
        </div>
    `;
}

function buildGlobalIndexMap() {
    const map = new Map();
    allSections.forEach((s, idx) => map.set(s.id, idx));
    return map;
}

/* ===== Mavzusiz (standart) — bitta tekis grid + bitta umumiy sahifalash ===== */

function renderFlatSections() {
    const list = document.getElementById("sectionsList");
    const pagination = document.getElementById("sectionsPagination");

    if (!allSections.length) {
        list.innerHTML = `<div class="courses-empty">Hali dars yo'q</div>`;
        pagination.style.display = "none";
        return;
    }

    const totalPages = Math.max(1, Math.ceil(allSections.length / SECTIONS_PER_PAGE));
    if (sectionsPage >= totalPages) sectionsPage = totalPages - 1;
    if (sectionsPage < 0) sectionsPage = 0;

    const from = sectionsPage * SECTIONS_PER_PAGE;
    const pageSections = allSections.slice(from, from + SECTIONS_PER_PAGE);
    const globalIndexById = buildGlobalIndexMap();

    // Mavzularga guruhlangan ko'rinishdagi (renderChapterBox) "jami dars
    // / jami testlar" bilan BIR XIL — mavzusiz (tekis) kursda ham shu
    // umumiy son ko'rinishi kerak (foydalanuvchi so'rovi: "gridda ham
    // to'g'rila"). Butun KURS bo'yicha (allSections — sahifalanmagan,
    // to'liq ro'yxat), joriy sahifagagina emas.
    const totalQuestions = allSections.reduce(
        (sum, s) => sum + (s.linkedTopicId != null ? (s.linkedTopicQuestionCount || 0) : 0), 0);

    list.innerHTML = `
        <div class="sections-summary">(dars — ${allSections.length} ta, jami testlar — ${totalQuestions} ta)</div>
        <div class="sections-grid">
        ${pageSections.map(s => renderSectionCard(s, globalIndexById)).join("")}
    </div>`;

    renderPaginationInto(pagination, totalPages, sectionsPage, (p) => `changeSectionsPage(${p})`);
}

function changeSectionsPage(page) {
    sectionsPage = page;
    renderFlatSections();
    document.getElementById("sectionsList").scrollIntoView({ behavior: "smooth", block: "start" });

    // Sahifa (sichqon bilan pagination tugmasi orqali) almashganda ham —
    // klaviatura bilan (↑/↓/Home) almashgandagi kabi — yangi sahifaning
    // BIRINCHI kartasi default holatda tanlangan/fokusda bo'ladi.
    const firstOnPage = allSections.slice(page * SECTIONS_PER_PAGE, page * SECTIONS_PER_PAGE + SECTIONS_PER_PAGE)[0];
    if (firstOnPage) selectCard(firstOnPage.id);
}

/* ===== Mavzular bo'yicha guruhlangan — har biri alohida "box", o'z sahifalashi bilan ===== */

// allSections'ni Mavzu (chapter) bo'yicha guruhlab, tartib bo'yicha
// saralab qaytaradi. chapterKey -> {chapterId, name, orderIndex, items[]}.
// "none" — hali hech qanday Mavzuga biriktirilmagan darslar (bo'lsa,
// ro'yxat OXIRIDA, "— Mavzusiz darslar —" nomi bilan). renderGroupedSections
// VA Ctrl+↑/↓ (moveToAdjacentChapter — mavzular orasida o'tish) ikkalasi
// ham shundan foydalanadi.
function getSortedChapterGroups() {
    const groups = new Map();

    // AVVAL — kursning BARCHA Mavzulari (allChapters, faqat canManage
    // bo'lganda yuklanadi) qo'shiladi, hozircha bo'sh (0 ta darsi bor)
    // bo'lsa ham — shu bilan katalog kartasidagi "N ta mavzu" soni bilan
    // bu yerda ko'rinayotgan mavzular soni mos keladi (foydalanuvchi
    // so'rovi, 2026-09-05).
    for (const c of allChapters) {
        groups.set(String(c.id), {
            key: String(c.id),
            chapterId: c.id,
            name: c.name,
            orderIndex: c.orderIndex,
            items: []
        });
    }

    for (const s of allSections) {
        const key = s.chapterId != null ? String(s.chapterId) : "none";
        if (!groups.has(key)) {
            groups.set(key, {
                key,
                chapterId: s.chapterId,
                name: s.chapterId != null ? s.chapterName : "— Mavzusiz darslar —",
                orderIndex: s.chapterId != null ? s.chapterOrderIndex : Number.MAX_SAFE_INTEGER,
                items: []
            });
        }
        groups.get(key).items.push(s);
    }
    return [...groups.values()].sort((a, b) => a.orderIndex - b.orderIndex);
}

function renderGroupedSections() {
    const list = document.getElementById("sectionsList");
    document.getElementById("sectionsPagination").style.display = "none";

    const sortedGroups = getSortedChapterGroups();
    const globalIndexById = buildGlobalIndexMap();
    // "⬆⬇" tugmalari faqat HAQIQIY Mavzular orasida ishlaydi — "—
    // Mavzusiz darslar —" psevdo-guruhi (chapterId == null) CourseChapter
    // yozuvi emas, "surib" bo'lmaydi, shu sabab hisobga olinmaydi. TO'LIQ
    // (filtrlanmagan) ro'yxatdan hisoblanadi — qidiruv faqat KO'RINISHNI
    // toraytiradi, haqiqiy tartibga ta'sir qilmaydi.
    const realChapterGroups = sortedGroups.filter(g => g.chapterId != null);

    const query = chapterSearchQuery.trim().toLowerCase();
    const visibleGroups = query
        ? sortedGroups.filter(g => g.name.toLowerCase().includes(query))
        : sortedGroups;

    if (query && visibleGroups.length === 0) {
        list.innerHTML = `<div class="courses-empty">"${escapeHtml(chapterSearchQuery)}" bo'yicha mavzu topilmadi</div>`;
        return;
    }

    list.innerHTML = visibleGroups.map(group => renderChapterBox(group, globalIndexById, realChapterGroups)).join("");
}

// "🔍 Mavzu qidirish" — teriladigan har harfda chaqiriladi (input
// statik, qayta chizilmaydi — shu sabab fokus/kursor yo'qolmaydi).
// Qidiruv FAOL bo'lganda — mos kelgan mavzular avtomatik OCHIQ holda
// ko'rsatiladi (renderChapterBox), qo'shimcha bosish shart emas.
function onChapterSearchInput(value) {
    chapterSearchQuery = value;
    renderGroupedSections();
}

// Mavzu sarlavhasiga bosilganda — shu mavzuning darslar ro'yxati
// ochiladi/yopiladi (accordion). Bir nechtasi bir vaqtda ochiq turishi
// mumkin (faqat bittasi bilan cheklanmagan).
function toggleChapterBox(key) {
    if (expandedChapterKeys.has(key)) {
        expandedChapterKeys.delete(key);
    } else {
        expandedChapterKeys.add(key);
    }
    renderGroupedSections();
}

function renderChapterBox(group, globalIndexById, realChapterGroups) {
    // Endi accordion — sarlavha bosilganda ochiladi/yopiladi (toggleChapterBox).
    // Qidiruv FAOL bo'lsa (chapterSearchQuery) — mos kelgan mavzular
    // avtomatik OCHIQ ko'rsatiladi (natijani ko'rish uchun qo'shimcha
    // bosish shart emas).
    const isExpanded = expandedChapterKeys.has(group.key) || chapterSearchQuery.trim() !== "";

    // Shu Mavzudagi BARCHA darslarning (TEST BOSHQARUVIga bog'langanlari)
    // testlari yig'indisi — mavzu sarlavhasida "jami testlar" sifatida
    // ko'rsatish uchun (foydalanuvchi so'rovi bo'yicha) — YOPIQ holatda ham
    // ko'rinadi, ochmasdan turib ham asosiy ma'lumot bilinishi uchun.
    const totalQuestions = group.items.reduce(
        (sum, s) => sum + (s.linkedTopicId != null ? (s.linkedTopicQuestionCount || 0) : 0), 0);

    // Kartochkalar/sahifalash — FAQAT ochiq bo'lsa hisoblanadi (yopiq
    // mavzularda keraksiz DOM/CPU sarflanmasin, ayniqsa ko'p mavzuli
    // kurslarda).
    let bodyHtml = "";
    if (isExpanded) {
        const totalPages = Math.max(1, Math.ceil(group.items.length / CHAPTER_SECTIONS_PER_PAGE));
        let page = chapterPages[group.key] || 0;
        if (page >= totalPages) page = totalPages - 1;
        if (page < 0) page = 0;
        chapterPages[group.key] = page;

        const from = page * CHAPTER_SECTIONS_PER_PAGE;
        const pageItems = group.items.slice(from, from + CHAPTER_SECTIONS_PER_PAGE);

        const cardsHtml = pageItems.map((s, idx) => renderSectionCard(s, globalIndexById, from + idx + 1, {
            isFirst: from + idx === 0,
            isLast: from + idx === group.items.length - 1
        })).join("");
        const paginationHtml = totalPages > 1
            ? buildPaginationHtml(totalPages, page, (p) => `changeChapterPage('${group.key}', ${p})`)
            : "";

        // Har bir Mavzu — o'zining ALOHIDA "Saralash: A→Z / Z→A" tugmalariga
        // ega (sortChapterSections) — faqat SHU mavzu ichidagi darslarni
        // qayta tartiblaydi, boshqa mavzularga (yoki mavzusiz darslarga)
        // hech qanday ta'sir qilmaydi.
        const sortBar = (cachedCourse && cachedCourse.canManage && group.items.length > 1)
            ? `<div class="chapter-box-sort" onclick="event.stopPropagation()">
                   <span>Saralash:</span>
                   <button onclick="sortChapterSections('${group.key}', 'AZ')">A→Z</button>
                   <button onclick="sortChapterSections('${group.key}', 'ZA')">Z→A</button>
               </div>`
            : "";

        bodyHtml = `
            <div class="chapter-box-body">
                ${sortBar}
                <h4 class="chapter-box-mavzular-title">📋 Darslar</h4>
                <div class="sections-grid">${cardsHtml}</div>
                ${paginationHtml ? `<div class="sections-pagination chapter-box-pagination">${paginationHtml}</div>` : ""}
            </div>`;
    }

    // "➕" — ANIQ shu Mavzuga dars qo'shish (openAddSectionForm forceChapterId
    // bilan) — bosilganda "Mavzu" tanlovi avtomatik shu mavzuga o'rnatiladi,
    // qayta tanlash shart emas (foydalanuvchi ANIQ shuni so'ragan).
    const addTopicBtn = (cachedCourse && cachedCourse.canManage && group.chapterId != null)
        ? `<button class="chapter-rename-btn" onclick="event.stopPropagation(); openAddSectionForm(${group.chapterId})" title="Shu mavzuga dars qo'shish">➕</button>`
        : "";

    // "✏️" — faqat haqiqiy mavzularda (group.chapterId != null), "—
    // Mavzusiz darslar —" psevdo-guruhida ko'rsatilmaydi (uni "qayta
    // nomlash" mantiqsiz — u umuman CourseChapter yozuvi emas).
    const renameBtn = (cachedCourse && cachedCourse.canManage && group.chapterId != null)
        ? `<button class="chapter-rename-btn" onclick="event.stopPropagation(); renameChapterPrompt(${group.chapterId})" title="Mavzu nomini tahrirlash">✏️</button>`
        : "";

    // "🗑️ Mavzu + darslar" — deleteSelectedChapter (Mavzu tanlash
    // select'i yonida) dan farqli, bo'sh bo'lishi shart emas: shu Mavzu
    // ICHIDAGI barcha kurs darslarini (va bog'langan bo'lsa, TEST
    // BOSHQARUVIdagi mos Topic+savollarni ham) birga o'chiradi. Foydalanuvchi
    // so'rovi bo'yicha ATAYLAB shu yerda (TEST BOSHQARUVIda EMAS).
    const deleteWithTopicsBtn = (cachedCourse && cachedCourse.canManage && group.chapterId != null)
        ? `<button class="chapter-rename-btn danger-btn" onclick="event.stopPropagation(); deleteChapterWithLinkedTopics(${group.chapterId}, ${JSON.stringify(group.name).replace(/"/g, "&quot;")})" title="Mavzu va ichidagi barcha darslarni (bog'langan bo'lsa, TEST BOSHQARUVIdagi savollari bilan) butunlay o'chirish">🗑️</button>`
        : "";

    // Rasmiy Word ikonkasi — faqat SHU Mavzuni Word'ga eksport qilish
    // (courseWordExportModal — butun kurs eksporti bilan bir xil oyna,
    // faqat ko'lami boshqacha; ikonka question.html'dagi Word eksport
    // tugmasi bilan bir xil SVG).
    const exportChapterBtn = (group.chapterId != null)
        ? `<button class="chapter-rename-btn" onclick="event.stopPropagation(); openCourseWordExportModal(${group.chapterId}, ${JSON.stringify(group.name).replace(/"/g, "&quot;")})" title="Shu mavzuni Word (.docx) faylga eksport qilish"><svg width="14" height="14" viewBox="0 0 48 48" xmlns="http://www.w3.org/2000/svg" style="vertical-align:-2px;"><rect x="4" y="4" width="40" height="40" rx="7" fill="#185ABD"/><rect x="4" y="4" width="18" height="40" rx="7" fill="#103F91"/><text x="31" y="30" font-family="Arial, sans-serif" font-size="20" font-weight="bold" fill="#fff" text-anchor="middle">W</text></svg></button>`
        : "";

    // "⬆⬇" — shu Mavzu "box"ini boshqa Mavzu bilan o'rin almashtiradi
    // (moveChapter) — faqat haqiqiy Mavzularda (group.chapterId != null)
    // va kamida 2 ta Mavzu bo'lganda ko'rinadi. Ro'yxat cheti — tegishli
    // tugma disabled.
    let moveBtns = "";
    if (cachedCourse && cachedCourse.canManage && group.chapterId != null && realChapterGroups.length > 1) {
        const pos = realChapterGroups.findIndex(g => g.chapterId === group.chapterId);
        const upDisabled = pos <= 0 ? "disabled" : "";
        const downDisabled = pos === realChapterGroups.length - 1 ? "disabled" : "";
        moveBtns = `
            <button class="chapter-move-btn" onclick="event.stopPropagation(); moveChapter(${group.chapterId}, -1)" ${upDisabled} title="Mavzuni yuqoriga surish">⬆</button>
            <button class="chapter-move-btn" onclick="event.stopPropagation(); moveChapter(${group.chapterId}, 1)" ${downDisabled} title="Mavzuni pastga surish">⬇</button>
        `;
    }

    return `
        <div class="chapter-box ${isExpanded ? "expanded" : "collapsed"}">
            <h3 class="chapter-box-title" onclick="toggleChapterBox('${group.key}')" title="${isExpanded ? "Yig'ish" : "Ochish"}">
                <span class="chapter-box-chevron">▸</span>
                📂 ${escapeHtml(group.name)}
                <span class="chapter-box-count">(dars — ${group.items.length} ta, jami testlar — ${totalQuestions} ta)</span>
                <span class="chapter-box-actions">${moveBtns}${addTopicBtn}${exportChapterBtn}${renameBtn}${deleteWithTopicsBtn}</span>
            </h3>
            ${bodyHtml}
        </div>
    `;
}

// Faqat "chapterKey" mavzusiga (yoki "none" — mavzusiz darslar
// psevdo-guruhiga) tegishli darslarni A-Z/Z-A tartibga soladi — boshqa
// mavzulardagi (yoki mavzusiz) darslarning nisbiy tartibi BUTUNLAY
// o'zgarishsiz qoladi. Backend /sections/reorder har doim TO'LIQ (butun
// kurs bo'yicha) yangi tartibdagi id ro'yxatini kutadi (orderIndex —
// bitta umumiy, ketma-ket raqam, Mavzu bo'yicha alohida emas) — shu
// sabab shu mavzuga tegishli o'rinlarga, ular TURGAN JOYLARIDA, faqat
// yangi (saralangan) tartibda qo'yiladi.
function sortChapterSections(chapterKey, dir) {
    if (!cachedCourse) return;

    const targetChapterId = chapterKey === "none" ? null : Number(chapterKey);
    const targetItems = cachedCourse.sections.filter(s => (s.chapterId ?? null) === targetChapterId);
    const sortedTarget = [...targetItems].sort((a, b) =>
        dir === "AZ" ? a.title.localeCompare(b.title, "uz") : b.title.localeCompare(a.title, "uz"));

    const result = [...cachedCourse.sections];
    let sortedIdx = 0;
    for (let i = 0; i < result.length; i++) {
        if ((result[i].chapterId ?? null) === targetChapterId) {
            result[i] = sortedTarget[sortedIdx++];
        }
    }

    reorderTo(result.map(s => s.id));
}

function changeChapterPage(chapterKey, page) {
    chapterPages[chapterKey] = page;
    renderGroupedSections();

    // Sahifa (sichqon bilan pagination tugmasi orqali) almashganda ham —
    // klaviatura bilan (↑/↓/Home) almashgandagi kabi — yangi sahifaning
    // BIRINCHI kartasi default holatda tanlangan/fokusda bo'ladi.
    const items = allSections.filter(s => (s.chapterId != null ? String(s.chapterId) : "none") === chapterKey);
    const firstOnPage = items.slice(page * CHAPTER_SECTIONS_PER_PAGE, page * CHAPTER_SECTIONS_PER_PAGE + CHAPTER_SECTIONS_PER_PAGE)[0];
    if (firstOnPage) selectCard(firstOnPage.id);
}

/* ===== Dars kartochkalari orasida klaviatura navigatsiyasi ===== */

// Tanlangan kartaning "navigatsiya guruhi" — mavzusiz (flat) ko'rinishda
// BUTUN kurs; guruhlangan ko'rinishda esa FAQAT shu kartaning Mavzusi
// (yoki "— Mavzusiz darslar —" psevdo-guruhi). ←/→/↑/↓/Home
// navigatsiyasi ANIQ shu guruh (o'z sahifalashi) DOIRASIDA ishlaydi —
// boshqa Mavzudagi kartalarga "sakrab" ketmaydi.
function getCardGroup(sectionId) {
    const section = allSections.find(s => s.id === sectionId);
    if (!section) return null;

    const hasAnyChapter = allSections.some(s => s.chapterId != null);
    if (!hasAnyChapter) {
        return {
            items: allSections,
            perPage: SECTIONS_PER_PAGE,
            getPage: () => sectionsPage,
            setPage: (p) => { sectionsPage = p; renderFlatSections(); }
        };
    }

    const key = section.chapterId != null ? String(section.chapterId) : "none";
    const items = allSections.filter(s => (s.chapterId != null ? String(s.chapterId) : "none") === key);
    return {
        items,
        perPage: CHAPTER_SECTIONS_PER_PAGE,
        getPage: () => chapterPages[key] || 0,
        setPage: (p) => { chapterPages[key] = p; renderGroupedSections(); }
    };
}

// ← / → — joriy sahifadagi (yoki joriy Mavzu qutisidagi) oldingi/keyingi
// kartaga o'tadi. Sahifa chegarasiga yetganda (→ bilan OXIRGI kartadan,
// yoki ← bilan BIRINCHI kartadan) — agar keyingi/oldingi sahifa mavjud
// bo'lsa, avtomatik o'sha sahifaga o'tadi (→ — keyingi sahifaning
// BIRINCHISI, ← — oldingi sahifaning OXIRGISI). Butun ro'yxatning eng
// boshida/oxirida bo'lsa (o'tadigan sahifa yo'q) — hech narsa qilmaydi.
function moveCardSelection(sectionId, delta) {
    const group = getCardGroup(sectionId);
    if (!group) return;

    const totalPages = Math.max(1, Math.ceil(group.items.length / group.perPage));
    const page = group.getPage();
    const pageItems = group.items.slice(page * group.perPage, page * group.perPage + group.perPage);
    const idx = pageItems.findIndex(s => s.id === sectionId);
    if (idx === -1) return;
    const newIdx = idx + delta;

    if (newIdx >= 0 && newIdx < pageItems.length) {
        selectCard(pageItems[newIdx].id);
        return;
    }

    if (delta > 0 && page < totalPages - 1) {
        const newPage = page + 1;
        group.setPage(newPage);
        const firstOnPage = group.items.slice(newPage * group.perPage, newPage * group.perPage + group.perPage)[0];
        if (firstOnPage) selectCard(firstOnPage.id, { scroll: true });
    } else if (delta < 0 && page > 0) {
        const newPage = page - 1;
        group.setPage(newPage);
        const itemsOnNewPage = group.items.slice(newPage * group.perPage, newPage * group.perPage + group.perPage);
        const lastOnPage = itemsOnNewPage[itemsOnNewPage.length - 1];
        if (lastOnPage) selectCard(lastOnPage.id, { scroll: true });
    }
    // aks holda — ro'yxatning eng boshi/oxiri, o'tadigan joy yo'q.
}

// ↑ — bir oldingi sahifa, ↓ — bir keyingi sahifa, Home — 1-sahifa (BIRINCHI
// kartasi tanlanadi). Yangi sahifaga o'tgach, o'sha sahifaning BIRINCHI
// kartasi avtomatik tanlanadi (va ekranga scroll qilinadi).
function moveCardPage(sectionId, dir) {
    const group = getCardGroup(sectionId);
    if (!group) return;

    const totalPages = Math.max(1, Math.ceil(group.items.length / group.perPage));
    const curPage = group.getPage();
    const newPage = dir === "home" ? 0 : curPage + dir;
    if (newPage < 0 || newPage >= totalPages || newPage === curPage) return;

    group.setPage(newPage);

    const firstOnPage = group.items.slice(newPage * group.perPage, newPage * group.perPage + group.perPage)[0];
    if (firstOnPage) selectCard(firstOnPage.id, { scroll: true });
}

// End — ENG OXIRGI sahifaning ENG OXIRGI kartasiga o'tadi (Home'ning
// aksi — Home 1-sahifa/1-kartaga, End oxirgi sahifa/oxirgi kartaga).
function moveCardToLast(sectionId) {
    const group = getCardGroup(sectionId);
    if (!group) return;

    const totalPages = Math.max(1, Math.ceil(group.items.length / group.perPage));
    const lastPage = totalPages - 1;
    if (group.getPage() !== lastPage) {
        group.setPage(lastPage);
    }

    const itemsOnLastPage = group.items.slice(lastPage * group.perPage, lastPage * group.perPage + group.perPage);
    const lastCard = itemsOnLastPage[itemsOnLastPage.length - 1];
    if (lastCard) selectCard(lastCard.id, { scroll: true });
}

// Ctrl+↑ / Ctrl+↓ — MAVZULAR (chapter box'lar) orasida o'tadi — oddiy
// ↑/↓ (bitta sahifa) dan farqli, butun Mavzuni almashtiradi. Mavzusiz
// (flat) ko'rinishda hech narsa qilmaydi — Mavzu tushunchasi umuman yo'q.
// Yangi Mavzuning 1-sahifasidagi BIRINCHI kartasi tanlanadi.
function moveToAdjacentChapter(sectionId, dir) {
    const hasAnyChapter = allSections.some(s => s.chapterId != null);
    if (!hasAnyChapter) return;

    const section = allSections.find(s => s.id === sectionId);
    if (!section) return;

    const groups = getSortedChapterGroups();
    const currentKey = section.chapterId != null ? String(section.chapterId) : "none";
    const idx = groups.findIndex(g => g.key === currentKey);
    const newIdx = idx + dir;
    if (idx === -1 || newIdx < 0 || newIdx >= groups.length) return;

    const targetGroup = groups[newIdx];
    chapterPages[targetGroup.key] = 0;
    renderGroupedSections();

    const firstItem = targetGroup.items[0];
    if (firstItem) selectCard(firstItem.id, { scroll: true });
}

// Enter — tanlangan kartaga "kirish" (sichqon bilan bosgandagi bilan bir
// xil xulq-atvor: qulflangan bo'lsa hech narsa qilmaydi).
function openSelectedCard(sectionId) {
    const section = allSections.find(s => s.id === sectionId);
    if (!section || section.locked) return;
    location.href = `/courses/${COURSE_ID}/sections/${sectionId}`;
}

function onCardKeyDown(event, sectionId) {
    switch (event.key) {
        case "ArrowRight":
            event.preventDefault();
            moveCardSelection(sectionId, 1);
            break;
        case "ArrowLeft":
            event.preventDefault();
            moveCardSelection(sectionId, -1);
            break;
        case "ArrowDown":
            event.preventDefault();
            if (event.ctrlKey || event.metaKey) {
                moveToAdjacentChapter(sectionId, 1);
            } else {
                moveCardPage(sectionId, 1);
            }
            break;
        case "ArrowUp":
            event.preventDefault();
            if (event.ctrlKey || event.metaKey) {
                moveToAdjacentChapter(sectionId, -1);
            } else {
                moveCardPage(sectionId, -1);
            }
            break;
        case "Home":
            event.preventDefault();
            moveCardPage(sectionId, "home");
            break;
        case "End":
            event.preventDefault();
            moveCardToLast(sectionId);
            break;
        case "Enter":
            event.preventDefault();
            openSelectedCard(sectionId);
            break;
    }
}

// Kartani "tanlangan" deb belgilaydi — vizual ajratib ko'rsatish
// (".selected" klassi) + klaviatura fokusi (keyingi Strelka/Home
// tugmalari shu kartadan davom etishi uchun). "scroll" — sahifa/Mavzu
// almashganda yangi kartani ekranga ko'rinadigan joyga olib kelish uchun.
function selectCard(sectionId, { scroll = false } = {}) {
    selectedSectionId = sectionId;

    // Karta hozir YOPIQ (collapsed) Mavzu ichida bo'lishi mumkin — endi
    // mavzular accordion, shu sabab DOM'da bo'lmasligi mumkin. Shu
    // mavzuni ochib, qayta chizib, keyin yana qidiramiz — bu bitta joyda
    // qilingani uchun (fokus qaytariladigan BARCHA joylar: "?focus=",
    // tahrirlab saqlagandan keyin, Ctrl+↑/↓ mavzu navigatsiyasi va h.k.)
    // ularning har birini alohida o'zgartirish shart emas.
    let el = document.querySelector(`.section-item[data-section-id="${sectionId}"]`);
    if (!el) {
        const section = allSections.find(s => s.id === sectionId);
        const hasAnyChapter = allSections.some(s => s.chapterId != null);
        if (section && hasAnyChapter) {
            const key = section.chapterId != null ? String(section.chapterId) : "none";
            if (!expandedChapterKeys.has(key)) {
                expandedChapterKeys.add(key);
                renderGroupedSections();
                el = document.querySelector(`.section-item[data-section-id="${sectionId}"]`);
            }
        }
    }

    document.querySelectorAll(".section-item.selected").forEach(x => x.classList.remove("selected"));
    if (el) {
        el.classList.add("selected");
        el.focus({ preventScroll: !scroll });
        if (scroll) el.scrollIntoView({ behavior: "smooth", block: "center" });
    }
}

// "⌨️" belgisi bosilganda — klaviatura-yo'riqnoma pufakchasini
// ochadi/yopadi (".kbd-hint-open" klassi, courses.css). Avval hover'da
// avtomatik ochilardi, lekin ekranni to'sib xalaqit berardi (haqiqiy
// foydalanuvchi shikoyati) — endi FAQAT shu belgi bosilganda ko'rinadi.
// Bir vaqtning o'zida faqat BITTA karta yo'riqnomasi ochiq turadi.
function toggleKbdHint(badgeEl) {
    const card = badgeEl.closest(".section-item");
    if (!card) return;
    const wasOpen = card.classList.contains("kbd-hint-open");
    document.querySelectorAll(".section-item.kbd-hint-open").forEach(el => el.classList.remove("kbd-hint-open"));
    if (!wasOpen) card.classList.add("kbd-hint-open");
}

// Kartadan tashqariga (yoki boshqa joyga) bosilsa — ochiq turgan
// yo'riqnoma yopiladi.
document.addEventListener("click", (e) => {
    if (!e.target.closest(".kbd-hint-badge")) {
        document.querySelectorAll(".section-item.kbd-hint-open").forEach(el => el.classList.remove("kbd-hint-open"));
    }
});

/* ===== Sahifalash tugmalari — flat va guruhlangan ko'rinishlar bir xil ishlatadi ===== */

// Ko'p sahifali ro'yxatlarda (masalan 18 sahifa) BARCHA raqamni birma-bir
// chizish o'rniga — "oynali" sahifalash: doim 1-, OXIRGI sahifa va joriy
// sahifa atrofidagi ±PAGE_WINDOW_DELTA raqamlar ko'rinadi, orada uzilgan
// joyda "…" chiqadi. "…" statik EMAS — bosilsa kichik raqam maydoniga
// aylanadi (togglePageJumpInput) va Enter bosilganda YOZILGAN sahifaga
// BEVOSITA o'tkazadi (masalan 2-sahifadan 15-sahifaga — oradagi barcha
// sahifalarni birma-bir bosib o'tirmasdan).
const PAGE_WINDOW_DELTA = 2;

function buildPaginationHtml(totalPages, currentPage, onClickFor) {
    const isFirst = currentPage === 0;
    const isLast = currentPage === totalPages - 1;

    const buttons = [];
    // «/» — bevosita BIRINCHI/OXIRGI sahifaga sakrash (ko'p sahifali
    // ro'yxatlarda ‹Oldingi/Keyingi› bilan bittalab bosib borish noqulay).
    buttons.push(`<button ${isFirst ? "disabled" : ""} onclick="${onClickFor(0)}" title="Birinchi sahifa">«</button>`);
    buttons.push(`<button ${isFirst ? "disabled" : ""} onclick="${onClickFor(currentPage - 1)}">‹ Oldingi</button>`);

    // Ko'rsatiladigan sahifa raqamlari: birinchi, oxirgi, va joriy atrofi.
    const pagesToShow = new Set([0, totalPages - 1]);
    for (let p = currentPage - PAGE_WINDOW_DELTA; p <= currentPage + PAGE_WINDOW_DELTA; p++) {
        if (p >= 0 && p < totalPages) pagesToShow.add(p);
    }
    const sortedPages = [...pagesToShow].sort((a, b) => a - b);

    // "…" bosilganda qaysi funksiyani (changeSectionsPage yoki
    // changeChapterPage — closure bo'yicha bo'limga xos) chaqirish
    // kerakligini keyinroq (runtime'da, foydalanuvchi raqam kiritgach)
    // bilish uchun — onClickFor'ni "__JUMP__" placeholder bilan chaqirib,
    // natijani shablon sifatida data-atributga yozib qo'yamiz (aks holda
    // bir nechta bo'lim-box'i bir vaqtda ekranda bo'lsa, qaysi bo'limning
    // sahifalashiga tegishli ekanini bilib bo'lmas edi).
    const jumpTemplate = onClickFor("__JUMP__").replace(/"/g, "&quot;");

    let prevPage = null;
    sortedPages.forEach((p) => {
        if (prevPage !== null && p - prevPage > 1) {
            buttons.push(`<button class="page-ellipsis" data-jump-template="${jumpTemplate}" data-max="${totalPages}" onclick="togglePageJumpInput(this)" title="Sahifaga o'tish">…</button>`);
        }
        buttons.push(`<button class="${p === currentPage ? "active" : ""}" onclick="${onClickFor(p)}">${p + 1}</button>`);
        prevPage = p;
    });

    buttons.push(`<button ${isLast ? "disabled" : ""} onclick="${onClickFor(currentPage + 1)}">Keyingi ›</button>`);
    buttons.push(`<button ${isLast ? "disabled" : ""} onclick="${onClickFor(totalPages - 1)}" title="Oxirgi sahifa">»</button>`);
    return buttons.join("");
}

// "…" bosilganda — o'sha o'rniga kichik raqam kiritish maydoni chiqadi.
// Enter — yozilgan sahifaga bevosita o'tkazadi; Escape/fokusdan chiqish
// (bo'sh holda) — yana "…" belgisiga qaytaradi.
function togglePageJumpInput(ellipsisBtn) {
    const template = ellipsisBtn.dataset.jumpTemplate;
    const max = Number(ellipsisBtn.dataset.max);

    const input = document.createElement("input");
    input.type = "number";
    input.className = "page-jump-input";
    input.min = "1";
    input.max = String(max);
    input.placeholder = "№";

    const commit = () => {
        const value = Number(input.value);
        if (Number.isInteger(value) && value >= 1 && value <= max) {
            // "__JUMP__" — build vaqtida onClickFor'dan olingan shablon
            // ichidagi joy egallovchi, endi haqiqiy (0-indeksli) sahifa
            // raqami bilan almashtiriladi va xuddi oddiy sahifalash
            // tugmasidagi onclick kabi bajariladi.
            new Function(template.replace("__JUMP__", String(value - 1)))();
        }
    };

    input.addEventListener("keydown", (e) => {
        if (e.key === "Enter") commit();
        if (e.key === "Escape") input.replaceWith(ellipsisBtn);
    });
    input.addEventListener("blur", () => {
        if (!input.value) input.replaceWith(ellipsisBtn);
    });

    ellipsisBtn.replaceWith(input);
    input.focus();
}

function renderPaginationInto(container, totalPages, currentPage, onClickFor) {
    if (totalPages <= 1) {
        container.style.display = "none";
        container.innerHTML = "";
        return;
    }
    container.innerHTML = buildPaginationHtml(totalPages, currentPage, onClickFor);
    container.style.display = "flex";
}

/* ===== Darsni Mavzuga biriktirish — TANLOV (select), erkin matn emas =====
   Erkin matn (avvalgi versiya) yozuvdagi eng kichik farqda ham (bo'sh joy,
   katta-kichik harf, imlo xatosi) yangi DUBLIKAT mavzu yaratib yuborardi,
   va bitta mavzuni "qayta nomlash" uchun unga tegishli HAR BIR darsni
   birma-bir tahrirlab, qo'lda bir xil yangi nomni terish kerak bo'lardi.
   Endi: mavjud mavzular ANIQ id bo'yicha tanlanadi (CourseSectionSaveDto.
   chapterId); faqat "➕ Yangi mavzu yaratish..." tanlanganda nom kiritiladi
   (newChapterName) — va nomni o'zgartirish alohida renameChapterPrompt()
   orqali, BITTA umumiy CourseChapter yozuvini o'zgartiradi (barcha unga
   biriktirilgan darslarda darhol, avtomatik aks etadi). */

const NEW_CHAPTER_OPTION = "__new__";

// selectId — "newSectionChapterSelect" | "editSectionChapterSelect".
// selectedChapterId — oldindan tanlangan mavzu id'si (tahrirlashda), yoki null.
// mode — "new" | "edit" — tanlangan Bo'lim (Science)ni topish uchun (shu
// Bo'limda TEST BOSHQARUVIda ALLAQACHON mavjud Mavzularni ham ro'yxatga
// qo'shish uchun, pastga qarang). Berilmasa (yoki Bo'lim hali tanlanmagan/
// yangi bo'lsa) — faqat shu KURSNING o'z Mavzulari ko'rsatiladi
// (avvalgi xulq-atvor).
// chapterId -> shu mavzuda nechta dars borligi — deleteSelectedChapter()
// va onChapterSelectChange() "🗑️" tugmasini ko'rsatish/yashirish uchun
// shu yerdan o'qiydi (har safar populateChapterSelect chaqirilganda
// yangilanadi).
let chapterCountsById = {};

async function populateChapterSelect(selectId, selectedChapterId, mode) {
    const select = document.getElementById(selectId);
    if (!select) return;

    // 1) Shu KURSNING BARCHA Mavzulari (CourseChapter) — hozircha BO'SH
    //    (hech qanday darsga biriktirilmaganlari) ham shu jumladan
    //    (backend'dan, sectionCount bilan birga — bo'sh mavzuni
    //    o'chirish imkoniyati uchun). "id:<id>" bilan tanlanadi,
    //    saqlashda ANIQ shu mavzu ishlatiladi (chapterId).
    let courseChapters = [];
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/chapters`);
        if (res.ok) courseChapters = await res.json();
    } catch (err) {
        console.error(err);
    }
    chapterCountsById = {};
    courseChapters.forEach(c => { chapterCountsById[c.id] = c.sectionCount; });

    // 2) TEST BOSHQARUVIDA (tanlangan Bo'lim bo'yicha) ALLAQACHON mavjud
    //    Mavzular — kursning o'z Mavzulari ro'yxatida hali bo'lmagan
    //    (nom bo'yicha, katta-kichik harfga sezgir emas) nomlar UNIKAL
    //    holda qo'shib qo'yiladi. "name:<nom>" bilan tanlanadi, saqlashda
    //    newChapterName sifatida yuboriladi (backend resolveChapter — shu
    //    nomli TopicSection allaqachon bor bo'lsa, aynan o'shanga
    //    ulanadi, YANGI dublikat yaratilmaydi).
    let externalNames = [];
    const scienceId = mode ? getSelectedScienceId(mode) : null;
    if (scienceId != null) {
        try {
            const res = await fetch(`/api/topic-section?scienceId=${scienceId}`);
            if (res.ok) {
                const sections = await res.json();
                const existingNamesLower = new Set(courseChapters.map(c => c.name.toLowerCase()));
                externalNames = sections
                    .filter(s => !existingNamesLower.has(s.name.toLowerCase()))
                    .sort((a, b) => a.orderIndex - b.orderIndex)
                    .map(s => s.name);
            }
        } catch (err) {
            console.error(err);
        }
    }

    const options = ['<option value="">— Mavzusiz —</option>'];
    for (const c of courseChapters) {
        const emptyMark = c.sectionCount === 0 ? " (bo'sh)" : "";
        options.push(`<option value="id:${c.id}">${escapeHtml(c.name)}${emptyMark}</option>`);
    }
    for (const name of externalNames) {
        options.push(`<option value="name:${encodeURIComponent(name)}">${escapeHtml(name)}</option>`);
    }
    options.push(`<option value="${NEW_CHAPTER_OPTION}">➕ Yangi mavzu yaratish...</option>`);

    select.innerHTML = options.join("");
    select.value = selectedChapterId != null ? `id:${selectedChapterId}` : "";

    if (mode) onChapterSelectChange(mode);
}

// Tanlangan (yoki "Boshqa"da qo'lda yozilgan) Bo'lim nomini cachedSciences
// ro'yxatidan id'siga o'giradi — hali mavjud bo'lmagan (yangi kiritilgan)
// Bo'lim uchun albatta null qaytadi (test boshqaruvida hali hech qanday
// Mavzu bo'lishi ham mumkin emas, shuning uchun bu to'g'ri xulq-atvor).
function getSelectedScienceId(mode) {
    const name = getSelectedScienceName(mode);
    if (!name) return null;
    const match = cachedSciences.find(s => s.name.toLowerCase() === name.toLowerCase());
    return match ? match.id : null;
}

// "➕ Yangi mavzu yaratish..." tanlansa — yangi nom kiritish maydoni
// ochiladi; "🗑️" esa faqat hozir tanlangan Mavzu kursning O'Z Mavzusi
// ("id:" bilan) VA hech qanday darsga biriktirilmagan (bo'sh) bo'lsa
// ko'rinadi (chapterCountsById — populateChapterSelect'da to'ldiriladi).
function onChapterSelectChange(mode) {
    const select = document.getElementById(mode + "SectionChapterSelect");
    const newInput = document.getElementById(mode + "SectionChapterNewInput");
    const deleteBtn = document.getElementById(mode + "SectionChapterDeleteBtn");
    const value = select.value;

    const isNew = value === NEW_CHAPTER_OPTION;
    newInput.style.display = isNew ? "block" : "none";
    if (isNew) {
        newInput.value = "";
        newInput.focus();
    }

    if (deleteBtn) {
        const chapterId = value.startsWith("id:") ? Number(value.slice(3)) : null;
        const isEmpty = chapterId != null && (chapterCountsById[chapterId] || 0) === 0;
        deleteBtn.style.display = isEmpty ? "inline-flex" : "none";
        deleteBtn.dataset.chapterId = chapterId != null ? String(chapterId) : "";
    }
}

// "🗑️" tugmasi — faqat BO'SH (hech qanday darsga biriktirilmagan)
// Mavzuni o'chiradi (backend ham xuddi shu tekshiruvni qaytaradi,
// himoya sifatida). Mavzu tanlash ro'yxatini qayta yuklaydi.
async function deleteSelectedChapter(mode) {
    const deleteBtn = document.getElementById(mode + "SectionChapterDeleteBtn");
    const chapterId = deleteBtn.dataset.chapterId;
    if (!chapterId) return;

    if (!await showConfirmModal("Bu bo'sh mavzuni o'chirmoqchimisiz? Bu amalni bekor qilib bo'lmaydi.", { danger: true })) return;

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/chapters/${chapterId}`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Mavzuni o'chirishda xatolik");
            return;
        }
        await populateChapterSelect(mode + "SectionChapterSelect", null, mode);
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "🗑️ Bo'shlarni tozalash" — shu KURSDA hech qanday darsga
// biriktirilmagan BARCHA Mavzularni bir yo'la o'chiradi (topic-sections
// sahifasidagi "Bo'sh mavzularni o'chirish" bilan bir xil g'oya, faqat
// bu yerda CourseChapter uchun). Ochiq turgan har ikkala forma
// (yangi/tahrirlash) select'i ham qayta yuklanadi.
async function deleteEmptyChapters() {
    if (!await showConfirmModal("Kursdagi barcha bo'sh (hech qanday darsga biriktirilmagan) mavzularni o'chirmoqchimisiz?\n\nBu amalni bekor qilib bo'lmaydi.", { danger: true })) {
        return;
    }

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/chapters/empty`, { method: "DELETE" });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "O'chirishda xatolik");
            return;
        }

        showAlertModal(data.deleted > 0
            ? `✅ ${data.deleted} ta bo'sh mavzu o'chirildi.`
            : "ℹ️ Bo'sh mavzu topilmadi.");

        if (document.getElementById("newSectionChapterSelect")) {
            await populateChapterSelect("newSectionChapterSelect", null, "new");
        }
        if (document.getElementById("editSectionChapterSelect")) {
            await populateChapterSelect("editSectionChapterSelect", null, "edit");
        }
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// submitAddSection/submitEditSection payload'iga to'g'ridan-to'g'ri
// qo'shiladigan {chapterId, newChapterName} juftligi — select qiymati
// "id:<id>" (kursning o'z Mavzusi) yoki "name:<nom>" (TEST BOSHQARUVIdan
// olingan, hali kursga biriktirilmagan Mavzu nomi) bo'lishi mumkin.
function getChapterPayload(mode) {
    const select = document.getElementById(mode + "SectionChapterSelect");
    const value = select.value;

    if (value === NEW_CHAPTER_OPTION) {
        const name = document.getElementById(mode + "SectionChapterNewInput").value.trim();
        return { chapterId: null, newChapterName: name || null };
    }
    if (!value) {
        return { chapterId: null, newChapterName: null };
    }
    if (value.startsWith("id:")) {
        return { chapterId: Number(value.slice(3)), newChapterName: null };
    }
    if (value.startsWith("name:")) {
        return { chapterId: null, newChapterName: decodeURIComponent(value.slice(5)) };
    }
    return { chapterId: null, newChapterName: null };
}

// "✏️" — mavzu (chapter-box) sarlavhasidagi tahrirlash tugmasi. Nom BITTA
// umumiy CourseChapter yozuvida saqlanadi — shu yerda o'zgartirilishi bilan
// unga biriktirilgan BARCHA darslarda avtomatik yangilanadi.
async function renameChapterPrompt(chapterId) {
    const current = allSections.find(s => s.chapterId === chapterId);
    const newName = await showPromptModal("Mavzu nomini kiriting:", current ? current.chapterName : "");
    if (newName === null) return; // bekor qilindi

    const trimmed = newName.trim();
    if (!trimmed) {
        showAlertModal("❌ Mavzu nomi bo'sh bo'lishi mumkin emas.");
        return;
    }

    renameChapter(chapterId, trimmed);
}

async function renameChapter(chapterId, newName) {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/chapters/${chapterId}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name: newName })
        });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Mavzu nomini o'zgartirishda xatolik");
            return;
        }
        loadCourse();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "➕ Mavzu qo'shish" ("Kursni boshqarish" paneli) — bo'sh (darssiz)
// Mavzuni to'g'ridan-to'g'ri yaratadi (createChapter). Yaratilgandan
// keyin — shu Mavzu darhol OCHIQ holatda ko'rsatiladi (hozircha bo'sh
// bo'lsa ham — foydalanuvchi darhol "➕" ikonkasi orqali
// ichiga dars qo'sha boshlashi mumkin).
async function createChapterPrompt() {
    const name = await showPromptModal("Yangi mavzu nomini kiriting:");
    if (name === null) return; // bekor qilindi

    const trimmed = name.trim();
    if (!trimmed) {
        showAlertModal("❌ Mavzu nomi bo'sh bo'lishi mumkin emas.");
        return;
    }

    createChapter(trimmed);
}

async function createChapter(name) {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/chapters`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ name })
        });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "Mavzu yaratishda xatolik");
            return;
        }

        // Mavzu hozircha BO'SH — hech qanday darsga ega emas, shu sabab
        // asosiy ro'yxatda ("box" sifatida) ko'rinmaydi (guruhlar FAQAT
        // mavjud darslardan hisoblanadi). Shu sabab foydalanuvchini
        // darhol ANIQ shu mavzuga dars qo'shish formasiga yo'naltiramiz
        // — "mavzu yaratildimi?" degan noaniqlik qolmaydi.
        await loadCourse();
        openAddSectionForm(data.id);
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "🗑️ Mavzu + darslar" (chapter-box sarlavhasidagi ✏️ yonida) —
// deleteSelectedChapter'dan farqli, bo'sh bo'lishi shart emas: shu
// Mavzu ICHIDAGI barcha kurs darslarini (CourseSection) soft-delete
// qiladi ("🗑️ O'chirilgan darslar" panelidan "♻️ Tiklash" bilan
// qaytariladi). TEST BOSHQARUVIdagi Topic/Question'ga HECH QACHON
// tegilmaydi — bog'langan Topic bo'lsa ham, faqat bog'lanishning o'zi
// (avtomatik) uziladi, savollar o'z joyida, butun holda qolaveradi.
async function deleteChapterWithLinkedTopics(chapterId, chapterName) {
    if (!await showConfirmModal(`"${chapterName}" mavzusini ICHIDAGI barcha darslari bilan o'chirmoqchimisiz?\n\n(Butunlay o'chmaydi — "🗑️ O'chirilgan darslar" panelidan qaytarish mumkin. TEST BOSHQARUVIdagi savollarga tegilmaydi.)`, { danger: true })) {
        return;
    }

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/chapters/${chapterId}/with-topics`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "O'chirishda xatolik");
            return;
        }
        loadCourse();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "⬆⬇" — Mavzu "box"ini qo'shni HAQIQIY Mavzu bilan (mavzusiz darslar
// psevdo-guruhi bilan EMAS) o'rin almashtiradi, so'ng yangi tartibni
// serverga saqlaydi (/api/courses/{id}/chapters/reorder — topic.js#
// persistOrder/TopicService#reorderTopics bilan bir xil andoza: backend
// BUTUN yangi tartibdagi id ro'yxatini kutadi).
async function moveChapter(chapterId, direction) {
    const groups = getSortedChapterGroups().filter(g => g.chapterId != null);
    const pos = groups.findIndex(g => g.chapterId === chapterId);
    const newPos = pos + direction;
    if (pos === -1 || newPos < 0 || newPos >= groups.length) return;

    [groups[pos], groups[newPos]] = [groups[newPos], groups[pos]];
    const orderedChapterIds = groups.map(g => g.chapterId);

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/chapters/reorder`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(orderedChapterIds)
        });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Mavzular tartibini saqlashda xatolik");
            return;
        }
        loadCourse();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "🔄 Mavzu nomlarini TEST BOSHQARUVI bilan sinxronlash" — kurs Mavzusi
// (CourseChapter) nomi bilan TEST BOSHQARUVIdagi Mavzu (TopicSection)
// nomi odatda avtomatik sinxron turadi (dars saqlanganda), lekin vaqt
// o'tishi bilan farq (drift) paydo bo'lib qolishi mumkin — shu tugma
// BARCHA darslarni joriy Mavzu holatiga qarab qayta to'g'rilaydi (kurs —
// "haqiqiy manba", TEST BOSHQARUVI shunga moslashtiriladi).
async function syncChapterTopics() {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/chapters/sync-topics`, { method: "POST" });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "Sinxronlashda xatolik");
            return;
        }
        showAlertModal(data.updated > 0
            ? `✅ ${data.updated} ta darsning Mavzusi TEST BOSHQARUVIDA to'g'rilandi.`
            : "✅ Hammasi allaqachon sinxron edi — o'zgarish kerak bo'lmadi.");
        loadCourse();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// ========================================================================
//     "📝 Kursni Word'ga eksport qilish" — butun kursni BITTA .docx
//     faylga: kurs darslari, testlar va (eng oxirida, alohida bo'lim)
//     javoblar — har biri mustaqil checkbox orqali (default — barchasi
//     yoqilgan).
// ========================================================================

// Bitta modal — BUTUN kurs ("📝 Kursni Word'ga eksport qilish" tugmasi) va
// ALOHIDA mavzu ("📝" — renderChapterBox) ikkalasi ham shu modaldan
// foydalanadi. Qaysi ko'lamda ekanini shu 2 ta o'zgaruvchi eslab qoladi
// (confirmCourseWordExport shu bo'yicha to'g'ri URL yasaydi).
let wordExportChapterId = null;
let wordExportChapterName = "";
// "📝" — bitta ANIQ darsni (CourseSection) eksport qilish uchun — mavzu
// (chapter) bilan bir xilda, lekin mustaqil (ikkalasi bir vaqtda tanlangan bo'lmaydi).
let wordExportSectionId = null;
let wordExportSectionName = "";

function openCourseWordExportModal(chapterId, chapterName) {
    wordExportChapterId = chapterId != null ? chapterId : null;
    wordExportChapterName = chapterName || "";
    wordExportSectionId = null;
    wordExportSectionName = "";

    showWordExportModal(wordExportChapterId != null
        ? `📝 "${wordExportChapterName}" mavzusini Word'ga eksport qilish`
        : "📝 Kursni Word'ga eksport qilish");
}

// Bitta dars kartochkasidagi "📝" ikonkasidan — renderSectionCard.
function openSectionWordExportModal(sectionId, sectionName) {
    wordExportSectionId = sectionId;
    wordExportSectionName = sectionName || "";
    wordExportChapterId = null;
    wordExportChapterName = "";

    showWordExportModal(`📝 "${wordExportSectionName}" darsini Word'ga eksport qilish`);
}

function showWordExportModal(title) {
    document.getElementById("courseWordExportModalTitle").textContent = title;

    document.getElementById("courseExportContent").checked = true;
    document.getElementById("courseExportTests").checked = true;

    const answersCheckbox = document.getElementById("courseExportAnswers");
    answersCheckbox.checked = true;
    answersCheckbox.disabled = false;

    document.getElementById("courseWordExportModal").classList.add("show");
}

function closeCourseWordExportModal() {
    document.getElementById("courseWordExportModal").classList.remove("show");
}

// "Testlar" o'chirilsa — "Test javoblari" ma'nosiz bo'lib qoladi
// (testsiz javob bo'lmaydi), shu sabab avtomatik o'chadi va bloklanadi.
function onCourseExportTestsToggle() {
    const testsChecked = document.getElementById("courseExportTests").checked;
    const answersCheckbox = document.getElementById("courseExportAnswers");
    answersCheckbox.disabled = !testsChecked;
    if (!testsChecked) {
        answersCheckbox.checked = false;
    }
}

// Oddiy GET + Content-Disposition:attachment orqali — fetch/blob shart
// emas, brauzerning o'zi faylni yuklab beradi (question.js#
// exportQuestionsToExcel bilan bir xil andoza).
function confirmCourseWordExport() {
    const includeContent = document.getElementById("courseExportContent").checked;
    const includeTests = document.getElementById("courseExportTests").checked;
    const includeAnswers = document.getElementById("courseExportAnswers").checked;

    const params = new URLSearchParams({ includeContent, includeTests, includeAnswers });
    const url = wordExportSectionId != null
        ? `/api/courses/${COURSE_ID}/sections/${wordExportSectionId}/export/word?${params.toString()}`
        : wordExportChapterId != null
            ? `/api/courses/${COURSE_ID}/chapters/${wordExportChapterId}/export/word?${params.toString()}`
            : `/api/courses/${COURSE_ID}/export/word?${params.toString()}`;
    window.location.href = url;
    closeCourseWordExportModal();
}

// ========================================================================
//     "🔗 Havolalarni tekshirish" — savol izohlaridagi dars havolalari
// ========================================================================
// Har bir savolning to'g'ri javob izohida "🔗 Darsga havola qo'shish"
// orqali qo'shilgan havola bo'lishi mumkin — bu FAQAT KO'RISH uchun
// tekshiruv: o'sha havola O'ZINING darsiga to'g'ri bog'langanmi
// (CourseService.auditTopicLinks). Hech narsa avtomatik o'zgartirilmaydi
// — faqat ➖ (havola yo'q) uchun bulk-qo'shish, ⚠️ (boshqa darsga
// bog'langan) uchun har biriga alohida "✅ To'g'irlash" tugmasi beriladi.
let topicLinkAuditOpen = false;

function toggleTopicLinkAudit() {
    topicLinkAuditOpen = !topicLinkAuditOpen;
    document.getElementById("topicLinkAuditPanel").style.display = topicLinkAuditOpen ? "block" : "none";
    if (topicLinkAuditOpen) {
        loadTopicLinkAudit();
    }
}

async function loadTopicLinkAudit() {
    const list = document.getElementById("topicLinkAuditList");
    list.innerHTML = "<p>Tekshirilmoqda...</p>";

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/topic-links/audit`);
        if (!res.ok) {
            list.innerHTML = "<p>Tekshirishda xatolik</p>";
            return;
        }
        const topics = await res.json();
        if (!topics.length) {
            list.innerHTML = "<p>Bu kursda TEST BOSHQARUVIga bog'langan dars yo'q</p>";
            return;
        }

        // Muammosi bor darslar ALOHIDA-ALOHIDA (batafsil) ko'rsatiladi,
        // hammasi to'g'ri bo'lgan darslar esa (ko'pchilik holatda —
        // asosiy qism) BITTA qisqa izoh qatoriga yig'iladi — foydalanuvchi
        // so'rovi bo'yicha: o'nlab-yuzlab "hammasi joyida" qatorni birma-bir
        // ko'rsatish shart emas, faqat muammoli joylar ko'zga tashlansin.
        const okTopics = topics.filter(t => t.missingCount === 0 && t.wrongItems.length === 0);
        const issueTopics = topics.filter(t => t.missingCount > 0 || t.wrongItems.length > 0);

        const okSummary = okTopics.length > 0
            ? `<div class="topic-link-audit-ok-summary">
                   ✅ ${okTopics.length} ta darsda hammasi joyida (jami ${okTopics.reduce((s, t) => s + t.okCount, 0)} ta savol to'g'ri bog'langan)
               </div>`
            : "";

        if (issueTopics.length === 0) {
            list.innerHTML = okSummary || "<p>✅ Barcha darslarda havolalar to'g'ri!</p>";
            return;
        }

        // Katta kurslarda o'nlab darsda yuzlab havola (xato yoki umuman
        // yo'q) bir yo'la topilishi mumkin (haqiqiy holat: 290 ta darsli
        // kursda o'nlab yangi darsda havola umuman yo'q edi, birma-bir
        // "➕ Havola qo'shish"ni bosib chiqish amaliy emas) — har birini
        // alohida bosish o'rniga, BUTUN kurs bo'yicha bittada tugmalar.
        const totalWrong = topics.reduce((sum, t) => sum + t.wrongItems.length, 0);
        const totalMissing = topics.reduce((sum, t) => sum + t.missingCount, 0);
        const addAllMissingInCourseBtn = totalMissing > 0
            ? `<button class="topic-link-add-missing-btn" onclick="addMissingTopicLinks()">➕ Butun kursda BARCHASIGA havola qo'shish (${totalMissing} ta)</button>`
            : "";
        const fixAllWrongInCourseBtn = totalWrong > 0
            ? `<button class="topic-link-fix-all-course-btn" onclick="fixAllWrongTopicLinksInCourse()">🛠️ Butun kursdagi BARCHA xato havolalarni tuzatish (${totalWrong} ta)</button>`
            : "";
        const fixAllInCourseBtn = (totalMissing > 0 || totalWrong > 0)
            ? `<div class="topic-link-audit-fix-all">${addAllMissingInCourseBtn}${fixAllWrongInCourseBtn}</div>`
            : "";

        const issueRows = issueTopics.map(t => {
            const addMissingBtn = t.missingCount > 0
                ? `<button class="topic-link-add-missing-btn" onclick="addMissingTopicLinks(${t.topicId})">➕ Havola qo'shish (${t.missingCount} ta)</button>`
                : "";
            const fixAllWrongBtn = t.wrongItems.length > 0
                ? `<button class="topic-link-fix-all-btn" onclick="fixAllWrongTopicLinks(${t.topicId})">✅ Barchasini to'g'irlash (${t.wrongItems.length} ta)</button>`
                : "";
            const wrongRows = t.wrongItems.map(item => `
                <div class="topic-link-wrong-row">
                    <div class="topic-link-wrong-text">${escapeHtml(item.questionTextSnippet)}</div>
                    <div class="topic-link-wrong-meta">
                        ⚠️ Bog'langan: <code>${escapeHtml(item.actualHref)}</code>
                        — bo'lishi kerak: <code>${escapeHtml(item.expectedHref)}</code>
                    </div>
                    <button class="topic-link-fix-one-btn" onclick="fixWrongTopicLink(${item.questionId})">✅ To'g'irlash</button>
                </div>
            `).join("");

            return `
                <div class="row topic-link-audit-row">
                    <div class="topic-link-audit-summary">
                        <b>${escapeHtml(t.topicName)}</b>
                        — ✅ ${t.okCount} ta to'g'ri
                        ${t.missingCount > 0 ? `, ➖ ${t.missingCount} ta havolasiz` : ""}
                        ${t.wrongItems.length > 0 ? `, ⚠️ ${t.wrongItems.length} ta xato havolali` : ""}
                        ${addMissingBtn}
                        ${fixAllWrongBtn}
                    </div>
                    ${wrongRows}
                </div>
            `;
        }).join("");

        list.innerHTML = okSummary + fixAllInCourseBtn + issueRows;
    } catch (err) {
        console.error(err);
        list.innerHTML = "<p>Tarmoq xatoligi</p>";
    }
}

// topicId berilsa — FAQAT shu darsdagi (dars qatoridagi tugma),
// berilmasa — BUTUN kursdagi barcha havolasiz savollarga (yuqoridagi
// "➕ Butun kursda BARCHASIGA havola qo'shish" tugmasi).
async function addMissingTopicLinks(topicId) {
    const url = topicId
        ? `/api/courses/${COURSE_ID}/topic-links/add-missing?topicId=${topicId}`
        : `/api/courses/${COURSE_ID}/topic-links/add-missing`;

    try {
        const res = await fetch(url, { method: "POST" });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "Havola qo'shishda xatolik");
            return;
        }
        showAlertModal(`✅ ${data.added} ta savolga havola qo'shildi`);
        loadTopicLinkAudit();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

async function fixWrongTopicLink(questionId) {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/topic-links/fix-wrong?questionId=${questionId}`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "To'g'irlashda xatolik");
            return;
        }
        loadTopicLinkAudit();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "✅ Barchasini to'g'irlash" (bitta dars) — shu darsdagi barcha XATO
// havolali savollarni bitta so'rovda to'g'irlaydi.
async function fixAllWrongTopicLinks(topicId) {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/topic-links/fix-all-wrong?topicId=${topicId}`, { method: "POST" });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "To'g'irlashda xatolik");
            return;
        }
        showAlertModal(`✅ ${data.fixed} ta savolning havolasi to'g'irlandi`);
        loadTopicLinkAudit();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "🛠️ Butun kursdagi BARCHA xato havolalarni tuzatish" — kattaroq amal
// (yuzlab savolga tegishi mumkin), shuning uchun tasdiqlash so'raladi.
async function fixAllWrongTopicLinksInCourse() {
    if (!await showConfirmModal("⚠️ Butun kursdagi BARCHA xato havolali savollarni to'g'irlamoqchimisiz?\n\nBu amalni bekor qilib bo'lmaydi (lekin har bir havola o'zining darsiga to'g'irlanadi, xavfsiz).")) {
        return;
    }
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/topic-links/fix-all-wrong`, { method: "POST" });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "To'g'irlashda xatolik");
            return;
        }
        showAlertModal(`✅ ${data.fixed} ta savolning havolasi to'g'irlandi`);
        loadTopicLinkAudit();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "🧹 Takroriy havolalarni tozalash" — bitta savolda bir nechta dars
// havola belgisi qolib ketgan bo'lsa (auditTopicLinks buni ko'rsatmaydi —
// faqat BIRINCHI havolani tekshiradi), hammasini bittaga tushiradi.
async function dedupeTopicLinks() {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/topic-links/dedupe`, { method: "POST" });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            showAlertModal(data.error || "Tozalashda xatolik");
            return;
        }
        showAlertModal(data.deduped > 0
            ? `✅ ${data.deduped} ta savoldagi takroriy havola tozalandi`
            : "✅ Takroriy havola topilmadi — hammasi allaqachon toza edi.");
        if (topicLinkAuditOpen) loadTopicLinkAudit();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
}

// ========================================================================
//     Kurs ichidan dars yoritmasi bo'yicha qidiruv
// ========================================================================
// Shu kursdagi (allSections'da linkedTopicId'i bor) darslar qaysi
// kurs(lar)ga bog'langan bo'lsa (odatda faqat shu kurs, lekin bitta dars
// boshqa kursga ham bog'langan bo'lsa — o'sha ham), o'sha kurs(lar)ning
// BARCHA darsga bog'langan darslaridagi matn ("dars yoritmasi" —
// CourseSection.textContent) ichidan qidiradi (backend: CourseService.
// searchTopicExplanations). Topilgan natijaga bosilsa — o'sha kurs
// darsining o'ziga o'tadi.
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

    const topicIds = allSections
        .filter(s => s.linkedTopicId)
        .map(s => s.linkedTopicId);

    if (topicIds.length === 0) {
        resultsEl.classList.remove("hidden");
        resultsEl.innerHTML = `<div class="explanation-search-empty">Bu kursda TEST BOSHQARUVIga bog'langan dars yo'q</div>`;
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
// highlightSearchQuery esa dars matni ichida shu so'zni topib fonini
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
// "Natijalarga qaytish" tugmalarini ko'rsatadi VA dars matni ichida shu
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

// "150000" -> "150 000" — minglik ajratkichi doim bo'shliq bo'lishi uchun
// (toLocaleString brauzer/OS lokaliga qarab boshqa belgi ishlatishi mumkin).
function formatPrice(price) {
    return String(Math.round(Number(price))).replace(/\B(?=(\d{3})+(?!\d))/g, " ");
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
                price: cachedCourse.price,
                published: !cachedCourse.published,
                fieldId: cachedCourse.fieldId
            })
        });

        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }

        loadCourse();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

function openEditCourseForm() {
    const preview = document.getElementById("editCourseCoverPreview");
    document.getElementById("editCourseCoverFile").value = "";
    document.getElementById("editCourseCoverStatus").textContent = "";
    document.getElementById("editCourseFree").checked = !!(cachedCourse && cachedCourse.free);
    document.getElementById("editCoursePrice").value = (cachedCourse && cachedCourse.price) || "";
    onEditCourseFreeToggle();
    loadFieldSelectOptions("editCourseField", cachedCourse && cachedCourse.fieldId);

    if (cachedCourse && cachedCourse.coverImageUrl) {
        preview.src = cachedCourse.coverImageUrl;
        preview.style.display = "block";
    } else {
        preview.style.display = "none";
    }

    document.getElementById("editCourseForm").classList.add("show");
}

function closeEditCourseForm() {
    document.getElementById("editCourseForm").classList.remove("show");
}

// "🆓 Bepul kurs" belgilansa — narx maydoni keraksiz, yashiriladi.
function onEditCourseFreeToggle() {
    const free = document.getElementById("editCourseFree").checked;
    document.getElementById("editCoursePriceField").style.display = free ? "none" : "block";
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

// Yo'nalish tanlash select'ini (kurs yaratish/tahrirlash formalari, ikkalasi
// ham shu funksiyani ishlatadi) /api/course-fields'dan to'ldiradi.
// selectedFieldId berilsa (tahrirlash formasi) — o'sha variant oldindan
// tanlangan holda ko'rsatiladi.
async function loadFieldSelectOptions(selectElementId, selectedFieldId) {
    const select = document.getElementById(selectElementId);
    if (!select) return;

    try {
        const res = await fetch("/api/course-fields");
        const fields = await res.json();
        select.innerHTML = `<option value="">--Yo'nalishni tanlang--</option>` +
            fields.map(f => `<option value="${f.id}">${escapeHtml(f.name)}</option>`).join("");
        if (selectedFieldId != null) {
            select.value = String(selectedFieldId);
        }
    } catch (err) {
        console.error("Yo'nalishlarni yuklashda xatolik:", err);
    }
}

async function submitEditCourse() {
    const title = document.getElementById("editCourseTitle").value.trim();
    const description = document.getElementById("editCourseDescription").value.trim();
    const fieldId = document.getElementById("editCourseField").value;

    if (!title) {
        showAlertModal("❌ Kurs nomini kiriting");
        return;
    }
    if (!fieldId) {
        showAlertModal("❌ Yo'nalishni tanlang");
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
                showAlertModal(uploadData.error || "Rasm yuklashda xatolik");
                document.getElementById("editCourseCoverStatus").textContent = "";
                return;
            }
            coverImageUrl = uploadData.url;
            document.getElementById("editCourseCoverStatus").textContent = "";
        }

        const free = document.getElementById("editCourseFree").checked;
        const priceValue = document.getElementById("editCoursePrice").value;
        const price = !free && priceValue ? Number(priceValue) : null;

        const res = await fetch(`/api/courses/${COURSE_ID}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                title, description, coverImageUrl, free, price,
                published: cachedCourse.published,
                fieldId: Number(fieldId)
            })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }

        closeEditCourseForm();
        loadCourse();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// Kursni "O'chirilganlar savati"ga o'tkazadi (soft-delete) — DARHOL
// butunlay o'chmaydi, /courses/trash sahifasidan "♻️ Tiklash" bilan
// bir zumda qaytariladi (CourseService.deleteCourse).
async function deleteCourse() {
    if (!await showConfirmModal("Kursni \"O'chirilganlar savati\"ga o'tkazmoqchimisiz?\n\n(Butunlay o'chmaydi — /courses/trash sahifasidan istalgan payt qaytarish mumkin.)", { danger: true })) return;

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }
        location.href = "/courses";
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

/* ===== OWNER: bo'lim qo'shish ===== */

// "⚙️ Kursni boshqarish" paneli — sarlavha bosilganda yig'iladi/ochiladi
// (ekranning katta qismini band qilib qo'ymasligi uchun). Holati
// localStorage'da saqlanadi — admin panelni ochiq qoldirib qayta
// kirsa, har safar qayta ochish shart bo'lmaydi.
const MANAGE_PANEL_COLLAPSED_KEY = "courseManagePanelCollapsed";

function toggleManagePanel() {
    const panel = document.getElementById("manageCoursePanel");
    const collapsed = panel.classList.toggle("collapsed");
    localStorage.setItem(MANAGE_PANEL_COLLAPSED_KEY, collapsed ? "1" : "0");
}

// "✏️ Tahrirlash" tugmasi HAR BIR dars kartasida (panel TASHQARISIDA)
// turadi, lekin tahrirlash formasi (editSectionForm) panel ICHIDA. Panel
// yig'ilgan bo'lsa (standart holat), forma "display:flex" qilinsa ham
// yig'ilgan ota-element (.manage-panel-body{display:none}) uni baribir
// yashirib turaverardi — tashqaridan bosilganda hech narsa ko'rinmasdi.
// Shuning uchun forma ko'rsatilishidan OLDIN panel avtomatik ochiladi.
function expandManagePanel() {
    const panel = document.getElementById("manageCoursePanel");
    if (panel && panel.classList.contains("collapsed")) {
        panel.classList.remove("collapsed");
        localStorage.setItem(MANAGE_PANEL_COLLAPSED_KEY, "0");
    }
}

// Ketma-ket bir nechta dars qo'shilganda — odatda HAMMASI bir xil
// Bo'lim+Mavzuga bog'lanadi (masalan bitta imtihon mavzusining barcha
// savollarini kiritish) — shu sabab har safar Bo'lim/Mavzu/bog'lash
// checkbox'ini qaytadan qo'lda tanlash noqulay (foydalanuvchi shikoyati).
// Oxirgi MUVAFFAQIYATLI qo'shilgan darsning tanlovi shu yerda eslab
// qolinadi (submitAddSection) va KEYINGI "➕ Dars qo'shish" formasi
// ochilganda standart sifatida qo'llaniladi (openAddSectionForm).
// Sahifa yangilanguncha amal qiladi (sessiya davomida, backendga
// saqlanmaydi — shunchaki qo'l qisqartirish).
let lastLinkTopicChoice = null; // {scienceName, chapterName} | null

// "forceChapterId" — bitta ANIQ Mavzuning "➕" (kartochka sarlavhasidagi)
// ikonkasidan ochilganda beriladi: Mavzu tanlash avtomatik ANIQ shu
// mavzuga o'rnatiladi (Bo'lim/bog'lash checkbox esa hamon lastLinkTopicChoice
// bo'yicha — bular mustaqil narsalar). Argumentsiz chaqirilsa (tepadagi
// umumiy "➕ Dars qo'shish" tugmasi) — avvalgidek.
async function openAddSectionForm(forceChapterId) {
    document.getElementById("newSectionTextEditor").innerHTML = "";
    document.getElementById("newSectionTopicName").value = "";
    newTopicNameManuallyEdited = false;
    attachImageResizeHandlers("newSectionTextEditor");

    if (lastLinkTopicChoice) {
        // Oxirgi marta tanlangan Bo'lim+Mavzu+bog'lash holati standart
        // qilib qo'yiladi — foydalanuvchi ketma-ket dars qo'shsa, har
        // safar qaytadan tanlashi shart emas.
        applyScienceSelection("new", lastLinkTopicChoice.scienceName || (cachedCourse ? cachedCourse.title : ""));
        document.getElementById("newSectionLinkTopic").checked = true;
        onTopicLinkToggle("new");
        await populateChapterSelect("newSectionChapterSelect", null, "new");
        selectChapterOptionByName("newSectionChapterSelect", lastLinkTopicChoice.chapterName);
    } else {
        // Default — shu kursning o'zi nomi (odatda kurs nomi bo'lim
        // nomi bilan bir xil bo'ladi) — checkbox belgilansa shu tayyor turadi, lekin checkbox
        // o'zi boshlanishda O'CHIRILGAN (bog'lash ixtiyoriy, avtomatik emas).
        applyScienceSelection("new", cachedCourse ? cachedCourse.title : "");
        document.getElementById("newSectionLinkTopic").checked = false;
        onTopicLinkToggle("new");
        await populateChapterSelect("newSectionChapterSelect", null, "new");
    }

    if (forceChapterId != null) {
        // "id:<id>" — populateChapterSelect'da HAR BIR kurs Mavzusi
        // (bo'sh bo'lganlari ham) shu formatda ro'yxatda bor, Bo'lim
        // tanlovidan qat'i nazar (courseChapters — kurs bo'yicha,
        // Bo'limga bog'liq emas).
        document.getElementById("newSectionChapterSelect").value = `id:${forceChapterId}`;
    }

    onChapterSelectChange("new");

    // "Kursni boshqarish" paneli yig'ilgan (collapsed) holatda bo'lsa —
    // addSectionForm o'sha panelning ICHIDA joylashgani uchun "show"
    // klassi qo'shilsa ham yig'ilgan ota-element (.manage-panel-body{
    // display:none}) uni baribir yashirib turardi (real bug: chapter-box
    // sarlavhasidagi "➕" — forceChapterId bilan — bosilganda hech narsa
    // ko'rinmasdi). openEditSectionForm bilan bir xil yechim.
    expandManagePanel();
    document.getElementById("addSectionForm").classList.add("show");
    document.getElementById("addSectionForm").scrollIntoView({ behavior: "smooth", block: "center" });
}

// Mavzu select'i nomi bo'yicha tanlanadi ("id:"/"name:" qiymatlaridan
// qat'i nazar — populateChapterSelect har safar qaytadan to'ldirilgani
// uchun eski xom qiymat endi noto'g'ri bo'lishi mumkin, shu sabab NOM
// orqali qidiriladi). Ro'yxatda topilmasa (masalan mavzu orada
// o'chirilgan bo'lsa) — "➕ Yangi mavzu yaratish..." orqali xuddi shu
// nom bilan xavfsiz qayta tiklanadi.
function selectChapterOptionByName(selectId, name) {
    if (!name) return;
    const select = document.getElementById(selectId);
    const normalized = name.trim().toLowerCase();

    for (const opt of select.options) {
        const optText = opt.textContent.replace(/\s*\(bo'sh\)\s*$/, "").trim().toLowerCase();
        if (optText === normalized) {
            select.value = opt.value;
            return;
        }
    }

    select.value = NEW_CHAPTER_OPTION;
    const newInput = document.getElementById(selectId.replace("Select", "NewInput"));
    if (newInput) newInput.value = name;
}

// Hozir formada tanlangan Mavzuning NOMINI qaytaradi (id/name qiymatidan
// qat'i nazar) — lastLinkTopicChoice'ga saqlash uchun (submitAddSection).
function currentChapterSelectionName(mode) {
    const select = document.getElementById(mode + "SectionChapterSelect");
    const value = select.value;

    if (value === NEW_CHAPTER_OPTION) {
        return document.getElementById(mode + "SectionChapterNewInput").value.trim() || null;
    }
    if (!value) return null;

    const text = select.selectedOptions[0] ? select.selectedOptions[0].textContent : "";
    return text.replace(/\s*\(bo'sh\)\s*$/, "").trim() || null;
}

function closeAddSectionForm() {
    document.getElementById("addSectionForm").classList.remove("show");
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
    // Manba turi o'zgarganda eski qiymat (masalan boshqa turga tegishli
    // ID/havola) qolib ketmasin — aks holda foydalanuvchi yangi havolani
    // maydonni tozalamasdan ustiga qo'shib yozib qo'yishi (tasodifan
    // qo'shilib ketishi) mumkin.
    document.getElementById("newSectionVideoUrl").value = "";
}

async function submitAddSection() {
    const title = document.getElementById("newSectionTitle").value.trim();
    const includeText = document.getElementById("includeText").checked;
    const includeVideo = document.getElementById("includeVideo").checked;

    if (!title) {
        showAlertModal("❌ Dars nomini kiriting");
        return;
    }

    if (!includeText && !includeVideo) {
        showAlertModal("❌ Kamida bittasini tanlang: Matn yoki Video");
        return;
    }

    const linkTopic = document.getElementById("newSectionLinkTopic").checked;
    const type = includeText && includeVideo ? "MIXED" : includeText ? "TEXT" : "VIDEO";
    const payload = {
        title, type, textContent: null, videoSourceType: null, videoUrl: null, videoDurationSeconds: null,
        scienceName: linkTopic ? getSelectedScienceName("new") : null,
        topicName: linkTopic ? (document.getElementById("newSectionTopicName").value.trim() || null) : null,
        ...getChapterPayload("new"),
        textContentFormat: "HTML"
    };

    if (includeText) {
        const editor = document.getElementById("newSectionTextEditor");
        if (!editor.innerText.trim()) {
            showAlertModal("❌ Matn kontentini kiriting");
            return;
        }
        cleanupEmptyCaptions("newSectionTextEditor");
        payload.textContent = editor.innerHTML;
    }

    if (includeVideo) {
        const source = document.getElementById("newSectionVideoSource").value;
        payload.videoSourceType = source;

        if (source === "UPLOAD") {
            const fileInput = document.getElementById("newSectionVideoFile");
            if (!fileInput.files[0]) {
                showAlertModal("❌ Video faylni tanlang");
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
                    showAlertModal(uploadData.error || "Video yuklashda xatolik");
                    return;
                }
                payload.videoUrl = uploadData.url;
            } catch (err) {
                console.error(err);
                showAlertModal("Video yuklashda tarmoq xatoligi");
                return;
            }
        } else {
            payload.videoUrl = document.getElementById("newSectionVideoUrl").value.trim();
            if (!payload.videoUrl) {
                showAlertModal("❌ Video URL/ID ni kiriting");
                return;
            }
            if (source === "YOUTUBE") {
                payload.videoUrl = extractYouTubeId(payload.videoUrl);
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
            showAlertModal(data.error || "Dars qo'shishda xatolik");
            return;
        }

        // Keyingi "➕ Dars qo'shish" uchun standart sifatida eslab qolinadi
        // (openAddSectionForm) — pastdagi tozalashdan OLDIN, hali forma
        // qiymatlari qo'lda o'chirilmagan holatda.
        lastLinkTopicChoice = linkTopic
            ? { scienceName: getSelectedScienceName("new"), chapterName: currentChapterSelectionName("new") }
            : null;

        document.getElementById("newSectionTitle").value = "";
        document.getElementById("newSectionTextEditor").innerHTML = "";
        document.getElementById("newSectionVideoUrl").value = "";
        document.getElementById("newSectionScienceOther").value = "";
        document.getElementById("newSectionTopicName").value = "";
        populateChapterSelect("newSectionChapterSelect", null, "new");
        onChapterSelectChange("new");
        newTopicNameManuallyEdited = false;
        document.getElementById("newSectionLinkTopic").checked = false;
        onTopicLinkToggle("new");
        document.getElementById("includeText").checked = true;
        document.getElementById("includeVideo").checked = false;
        onContentToggle(document.getElementById("includeText"));
        closeAddSectionForm();
        loadCourse();
        loadScienceNamesList();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "O'chirilganlar savati"ga o'tkazadi (soft-delete) — darhol butunlay
// o'chmaydi, "🗑️ O'chirilgan darslar" panelidan ("♻️ Tiklash") bir
// zumda qaytariladi (CourseService.deleteSection).
async function deleteSection(sectionId) {
    if (!await showConfirmModal("Darsni o'chirmoqchimisiz?\n\n(Butunlay o'chmaydi — \"🗑️ O'chirilgan darslar\" panelidan qaytarish mumkin.)", { danger: true })) return;

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/${sectionId}`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }
        loadCourse();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

// "🗑️ O'chirilgan darslar" paneli — soft-delete qilingan CourseSection'lar
// ro'yxati (bir zumda "♻️ Tiklash" qilinadigan). Panel yopiq holatda
// boshlanadi, bosilganda ochilib ro'yxatni yuklaydi.
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
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/deleted`);
        if (!res.ok) {
            list.innerHTML = "<p>Yuklashda xatolik</p>";
            return;
        }
        const items = await res.json();
        setTrashBadgeCount("courseSectionTrashBadge", items.length);
        if (!items.length) {
            list.innerHTML = "<p>O'chirilgan dars yo'q</p>";
            return;
        }
        list.innerHTML = items.map(s => `
            <div class="row">
                <div>${escapeHtml(s.title)} — ${formatSectionTrashDate(s.deletedAt)}da o'chirilgan</div>
                <div class="row-actions">
                    <button onclick="restoreSection(${s.id})">♻️ Tiklash</button>
                    <button class="danger-btn" onclick="permanentlyDeleteSection(${s.id}, ${JSON.stringify(s.title).replace(/"/g, "&quot;")})">🗑️ Butunlay o'chirish</button>
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

async function restoreSection(sectionId) {
    if (!await showConfirmModal("Bu darsni tiklamoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/${sectionId}/restore`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Tiklashda xatolik");
            return;
        }
        loadSectionTrash();
        loadCourse();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

async function permanentlyDeleteSection(sectionId, title) {
    if (!await showConfirmModal(`⚠️ "${title}" darsini BUTUNLAY o'chirmoqchimisiz?\n\nBu amalni HECH QANDAY tarzda bekor qilib bo'lmaydi.`, { danger: true })) return;
    if (!await showConfirmModal("Haqiqatan ham ishonchingiz komilmi?", { danger: true })) return;

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/${sectionId}/permanent`, { method: "DELETE" });
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

/* ===== OWNER/ADMIN: darsni tahrirlash ===== */

let editingSectionId = null;

async function openEditSectionForm(sectionId) {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/${sectionId}`);
        if (!res.ok) {
            showAlertModal("Dars ma'lumotlarini yuklab bo'lmadi");
            return;
        }
        const section = await res.json();
        editingSectionId = sectionId;

        document.getElementById("editSectionTitle").value = section.title;

        const hasText = section.type === "TEXT" || section.type === "MIXED";
        const hasVideo = section.type === "VIDEO" || section.type === "MIXED";
        document.getElementById("editIncludeText").checked = hasText;
        document.getElementById("editIncludeVideo").checked = hasVideo;

        // Eski PLAIN (qo'lda yozilgan, hali WYSIWYG'gacha) darslar xom
        // matn sifatida saqlangan — tahrirlash oynasida to'g'ri ko'rinishi
        // uchun xavfsiz escape qilib, qatorlarni <br>'ga aylantiramiz.
        // Saqlashda esa hammasi HTML formatga o'tadi (orqaga qaytish shart
        // emas — bu shunchaki bir martalik yaxshilanish).
        const editor = document.getElementById("editSectionTextEditor");
        editor.innerHTML = section.textContentFormat === "HTML"
            ? (section.textContent || "")
            : escapeHtml(section.textContent || "").replace(/\n/g, "<br>");
        attachImageResizeHandlers("editSectionTextEditor");
        injectAlignBars("editSectionTextEditor");
        injectCaptions("editSectionTextEditor");

        onEditContentToggle(document.getElementById("editIncludeText"));

        if (hasVideo) {
            document.getElementById("editSectionVideoSource").value = section.videoSourceType || "YOUTUBE";
            document.getElementById("editSectionVideoUrl").value = section.videoUrl || "";
            document.getElementById("editSectionVideoDuration").value = section.videoDurationSeconds || "";
            onEditVideoSourceChange(true); // keepValue — yuqorida qo'yilgan mavjud URL/ID'ni tozalamaslik uchun
        }

        // Bo'lim — allaqachon bog'langan bo'lsa o'sha, aks holda kurs nomi
        // default sifatida tanlanadi. Dars nomi — bog'langan bo'lsa o'sha
        // (endi "qo'lda kiritilgan" deb hisoblanadi, dars nomi keyinroq
        // o'zgarsa ham qayta yozilmaydi); aks holda dars nomining o'zi
        // (dars nomi o'zgarsa, bu ham birga yangilanaveradi).
        applyScienceSelection("edit", section.linkedScienceName || (cachedCourse ? cachedCourse.title : ""));
        editTopicNameManuallyEdited = !!section.linkedTopicName;
        document.getElementById("editSectionTopicName").value = section.linkedTopicName || section.title || "";
        // Checkbox — dars ALLAQACHON biror Topic'ga (Dars) bog'langan bo'lsagina
        // boshlanishda belgilangan holda ochiladi; aks holda o'chirilgan
        // (bog'lash hamon ixtiyoriy bo'lib qoladi).
        document.getElementById("editSectionLinkTopic").checked = !!section.linkedTopicName;
        onTopicLinkToggle("edit");

        populateChapterSelect("editSectionChapterSelect", section.chapterId, "edit");
        onChapterSelectChange("edit");

        expandManagePanel();
        document.getElementById("editSectionForm").style.display = "flex";
        document.getElementById("editSectionForm").scrollIntoView({ behavior: "smooth", block: "center" });
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
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

// keepValue=true faqat dars yuklanganda (openEditSectionForm) ishlatiladi
// — o'sha payt editSectionVideoUrl'ga ALLAQACHON mavjud video URL/ID
// qo'yilgan bo'ladi, uni yo'qotmaslik kerak. Foydalanuvchi dropdown'ni
// O'ZI o'zgartirganda (onchange) esa keepValue berilmaydi — o'sha holatda
// maydon TOZALANADI, aks holda eski turga tegishli qiymat (masalan eski
// YouTube ID) qolib, foydalanuvchi yangi havolani ustiga qo'shib
// yozib qo'yishi (maydonni oldin tanlamasdan paste qilishi) natijasida
// ikkalasi qo'shilib, DB ustuni sig'imidan oshib ketishi mumkin edi
// ("Kiritilgan matn juda uzun" xatosi shundan kelib chiqqan edi).
function onEditVideoSourceChange(keepValue) {
    const source = document.getElementById("editSectionVideoSource").value;
    document.getElementById("editSectionVideoUrl").style.display = source === "UPLOAD" ? "none" : "block";
    document.getElementById("editSectionVideoFile").style.display = source === "UPLOAD" ? "block" : "none";
    document.getElementById("editSectionVideoDuration").style.display = source === "EXTERNAL" ? "block" : "none";

    document.getElementById("editSectionVideoUrl").placeholder =
        source === "YOUTUBE" ? "YouTube video ID (masalan: dQw4w9WgXcQ)" : "Video URL";

    if (!keepValue) {
        document.getElementById("editSectionVideoUrl").value = "";
    }
}

async function submitEditSection() {
    if (!editingSectionId) return;

    const title = document.getElementById("editSectionTitle").value.trim();
    const includeText = document.getElementById("editIncludeText").checked;
    const includeVideo = document.getElementById("editIncludeVideo").checked;

    if (!title) {
        showAlertModal("❌ Dars nomini kiriting");
        return;
    }

    if (!includeText && !includeVideo) {
        showAlertModal("❌ Kamida bittasini tanlang: Matn yoki Video");
        return;
    }

    const linkTopic = document.getElementById("editSectionLinkTopic").checked;
    const type = includeText && includeVideo ? "MIXED" : includeText ? "TEXT" : "VIDEO";
    const payload = {
        title, type, textContent: null, videoSourceType: null, videoUrl: null, videoDurationSeconds: null,
        scienceName: linkTopic ? getSelectedScienceName("edit") : null,
        topicName: linkTopic ? (document.getElementById("editSectionTopicName").value.trim() || null) : null,
        ...getChapterPayload("edit"),
        textContentFormat: "HTML"
    };

    if (includeText) {
        const editor = document.getElementById("editSectionTextEditor");
        if (!editor.innerText.trim()) {
            showAlertModal("❌ Matn kontentini kiriting");
            return;
        }
        cleanupEmptyCaptions("editSectionTextEditor");
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
                        showAlertModal(uploadData.error || "Video yuklashda xatolik");
                        return;
                    }
                    payload.videoUrl = uploadData.url;
                } catch (err) {
                    console.error(err);
                    showAlertModal("Video yuklashda tarmoq xatoligi");
                    return;
                }
            } else {
                // Yangi fayl tanlanmagan — eski video URL saqlanib qoladi.
                payload.videoUrl = document.getElementById("editSectionVideoUrl").value.trim();
            }
        } else {
            payload.videoUrl = document.getElementById("editSectionVideoUrl").value.trim();
            if (!payload.videoUrl) {
                showAlertModal("❌ Video URL/ID ni kiriting");
                return;
            }
            if (source === "YOUTUBE") {
                payload.videoUrl = extractYouTubeId(payload.videoUrl);
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
            showAlertModal(data.error || "Darsni saqlashda xatolik");
            return;
        }

        // "editingSectionId" closeEditSectionForm() ichida null qilib
        // qo'yiladi — shu sabab qayta chizishdan (loadCourse) keyin, AYNAN
        // shu kartaga fokus qaytarish uchun oldindan saqlab olinadi
        // (haqiqiy foydalanuvchi shikoyati: tahrirlab saqlagandan keyin
        // fokus o'sha darsga kelmayotgan edi).
        const savedSectionId = editingSectionId;
        closeEditSectionForm();
        await loadCourse();
        selectCard(savedSectionId, { scroll: true });
        loadScienceNamesList();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}

/* ===== OWNER/ADMIN: darslarni saralash ===== */

async function reorderTo(sectionIds) {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/reorder`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(sectionIds)
        });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            showAlertModal(data.error || "Tartibni saqlashda xatolik");
            return;
        }
        loadCourse();
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
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

// dir: "AZ" | "ZA" — dars nomlari bo'yicha to'liq qayta saralash.
function sortSections(dir) {
    if (!cachedCourse) return;
    const sorted = [...cachedCourse.sections].sort((a, b) =>
        dir === "AZ" ? a.title.localeCompare(b.title, "uz") : b.title.localeCompare(a.title, "uz"));
    reorderTo(sorted.map(s => s.id));
}

/* Obuna berish/tasdiqlash/bekor qilish — endi /courses/subscriptions
   sahifasida (courseSubscriptions.js), barcha kurslar uchun yagona joyda. */
