let cachedCourse = null;
let clickPaymentEnabled = false;

// Mavzular ro'yxati sahifalanadi — bitta sahifada shuncha karta ko'rsatiladi
// (renderSections/changeSectionsPage). Sahifa raqami 0'dan boshlanadi. Bu —
// hech qaysi mavzu biror Bo'limga biriktirilmagan (eski/oddiy) kurslar
// uchun — bitta tekis grid + bitta umumiy sahifalash.
const SECTIONS_PER_PAGE = 12;
let sectionsPage = 0;

// Kursda Bo'lim(lar) bo'lsa — har bir Bo'lim o'z alohida "box"ida, o'z
// sahifalash tugmalari bilan ko'rsatiladi (renderGroupedSections). Har bir
// Bo'lim uchun joriy sahifa alohida saqlanadi: chapterPages[chapterKey].
// Umumiy .sections-grid javobgar panjarasi 1200px+'da 4 ustunli bo'ladi —
// shu sabab 4 ta tanlangan: har bir sahifa aynan BITTA TO'LIQ qatorni
// tashkil qiladi (qatorni "yorib chiqmaydi", qo'shimcha skroll ham shart
// emas). Torroq ekranlarda (kamroq ustunli) 4 ta mavzu shunchaki 2-4
// qatorga o'z-o'zidan bo'linadi — bu normal, kutilgan holat.
const CHAPTER_SECTIONS_PER_PAGE = 4;
let chapterPages = {};

// Klaviatura bilan mavzu kartochkalari orasida navigatsiya (←/→ — joriy
// sahifa/Bo'lim ichida oldingi-keyingi kartaga, ↑/↓ — oldingi/keyingi
// sahifa, Home — 1-sahifa). Tanlangan kartaning id'si — qayta chizishlar
// (sahifa/Bo'lim almashganda) orasida ham saqlanib qoladi.
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
            alert(data.error || "❌ Rasm yuklashda xatolik");
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
        alert("❌ Rasm yuklashda tarmoq xatoligi");
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
        alert("❌ Video havolasini kiriting yoki video fayl tanlang");
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
            alert(data.error || "❌ Video yuklashda xatolik");
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
        alert("❌ Video yuklashda tarmoq xatoligi");
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
            alert(data.error || "❌ Taqdimotni import qilishda xatolik");
            return;
        }

        const slides = data.slideUrls || [];
        if (!slides.length) {
            alert("❌ Taqdimotda hech qanday slayd topilmadi");
            return;
        }

        restoreEditorSelection(editorId, richInsertSavedRange);
        insertPptSlideshowHtml(editorId, slides);
    } catch (err) {
        console.error(err);
        alert("❌ Taqdimotni import qilishda tarmoq xatoligi");
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
        alert("❌ Import kutubxonasi yuklanmadi. Internet aloqasini tekshirib, sahifani qayta yuklang.");
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
        alert("❌ Faylni import qilishda xatolik: " + err.message);
    } finally {
        fileInput.value = "";
        document.getElementById(actionsId).classList.add('hidden');
    }
}

// "Fan nomi" endi tanlov (select) — mavjud fanlar ro'yxati + "➕ Boshqa..."
// (erkin nom kiritish uchun, agar kerakli fan ro'yxatda bo'lmasa).
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

    // Fan o'zgartirilganda — Bo'lim ro'yxati ham shu YANGI Fanda TEST
    // BOSHQARUVIda mavjud Bo'limlar bilan qayta to'ldiriladi
    // (populateChapterSelect). Joriy tanlov ("id:<id>" bo'lsa — kursning
    // o'z Bo'limi, Fan o'zgarsa ham hamon amal qiladi) saqlanadi.
    const chapterSelectId = mode === "new" ? "newSectionChapterSelect" : "editSectionChapterSelect";
    const currentValue = document.getElementById(chapterSelectId).value;
    const currentChapterId = currentValue.startsWith("id:") ? Number(currentValue.slice(3)) : null;
    populateChapterSelect(chapterSelectId, currentChapterId, mode);
}

// Formani ochishda chaqiriladi — agar berilgan nom (masalan shu kursning
// o'zi nomi, yoki bo'limga allaqachon bog'langan fan) ro'yxatda mavjud
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

// Mavzu nomi — default sifatida bo'lim nomi bilan bir xil bo'lib turadi,
// foydalanuvchi maydonni o'zi qo'lda tahrirlagunga qadar (shundan keyin
// bo'lim nomi o'zgarsa ham, mavzu nomi endi avtomatik qayta yozilmaydi).
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

// "🎯 Mavzuga oid testlar bilan bog'lash" — checkbox belgilanmagan bo'lsa
// fan/mavzu maydonlari yashirin turadi VA saqlashda umuman yuborilmaydi
// (getSelectedScienceName/topicName checkbox holatini submitAddSection /
// submitEditSection'da tekshiradi) — shunda tasodifan (checkbox
// belgilanmasdan) bo'lim boshqa fan/mavzuga bog'lanib qolmaydi.
function onTopicLinkToggle(mode) {
    const checkbox = document.getElementById(mode === "new" ? "newSectionLinkTopic" : "editSectionLinkTopic");
    const fields = document.getElementById(mode === "new" ? "newSectionTopicFields" : "editSectionTopicFields");
    fields.style.display = checkbox.checked ? "block" : "none";
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

    updateSubscribeBanner(course);

    // Boshqarish paneli (tahrirlash/chop etish/bo'lim qo'shish) — OWNER
    // uchun HAR DOIM, ADMIN uchun faqat O'ZI yaratgan kursda (backend
    // shu logikani hisoblab, canManage sifatida qaytaradi).
    const managePanel = document.getElementById("manageCoursePanel");
    managePanel.style.display = course.canManage ? "block" : "none";
    // Umumiy "Saralash" panelining ko'rinish-yo'qligi shu YERDA emas,
    // renderSections() ichida hal qilinadi: guruhlangan (Bo'limli) kursda
    // bu umumiy panel BUTUNLAY yashiriladi — har bir Bo'lim o'zining
    // alohida "Saralash" tugmalariga ega bo'ladi (renderChapterBox —
    // sortChapterSections). Faqat bo'limsiz (flat) kursda ko'rinadi.

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
            alert(data.error || "To'lovni boshlashda xatolik");
            return;
        }

        location.href = data.checkoutUrl;
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
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

// "sections" — HAR DOIM kursning TO'LIQ (sahifalanmagan) mavzular ro'yxati.
// Joriy sahifa (sectionsPage) shu funksiya ichida keyingi chaqiruvlargacha
// eslab qolinadi (loadCourse() -> renderCourse() har safar to'liq
// ro'yxatni qayta beradi, lekin foydalanuvchi qaysi sahifada turgani
// o'zgarmasligi kerak — masalan bo'lim tahrirlangandan keyin).
let allSections = [];

function renderSections(sections) {
    allSections = sections;

    // Kursda kamida bitta mavzu biror Bo'limga biriktirilgan bo'lsagina
    // guruhlangan ("box"li) ko'rinishga o'tiladi — aks holda (standart,
    // hozirgi barcha kurslar) 100% eskidek, bitta tekis grid.
    const hasAnyChapter = sections.some(s => s.chapterId != null);
    const canManage = cachedCourse && cachedCourse.canManage;

    // Umumiy (butun kurs bo'yicha) "Saralash" — faqat GURUHLANMAGAN
    // (flat) ko'rinishda ma'noli, chunki guruhlangan ko'rinishda har bir
    // Bo'lim endi O'ZINING alohida "Saralash" tugmalariga ega
    // (renderChapterBox), bittasi butun kursni aralashtirib yubormasligi
    // uchun.
    document.getElementById("sectionsSortBar").style.display = (canManage && !hasAnyChapter) ? "flex" : "none";

    // "Kurs ichidan mavzu yoritmasi bo'yicha qidiruv" — tahrirlashga
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
    // so'rovi bo'yicha). Keyingi qayta chizishlarda (masalan bo'lim
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

// DOM'dagi (haqiqiy ko'rinadigan) ENG BIRINCHI kartani tanlangan/fokusda
// deb belgilaydi — allSections[0]'ga emas, DOM tartibiga tayanadi, chunki
// guruhlangan ko'rinishda Bo'lim qutilari tartibi allSections'ning xom
// tartibidan farq qilishi mumkin (renderGroupedSections — orderIndex
// bo'yicha saralaydi).
function selectFirstCardByDefault() {
    const firstCardEl = document.querySelector(".section-item");
    if (firstCardEl) selectCard(Number(firstCardEl.dataset.sectionId));
}

// "?focus=<sectionId>" — sahifa birinchi ochilganda, o'sha kartani o'zi
// turgan sahifa/Bo'limga o'tkazib (agar hozirgi sahifada bo'lmasa),
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

// Har bir mavzu-tugmalar-karta shablonini bir joyda ushlab turadi — flat
// va guruhlangan (bo'limli) ko'rinishlar ikkalasi ham shundan foydalanadi.
// globalIndexById — ⬆️/⬇️ chegaralarini (birinchi/oxirgi) TO'LIQ (allSections)
// ro'yxatdagi haqiqiy o'rniga qarab hisoblash uchun (guruhlangan ko'rinishda
// bitta bo'lim ichidagi tartib to'liq ro'yxatdagi tartibning bir qismi,
// xolos — chegara tekshiruvi baribir GLOBAL bo'lishi kerak).
function renderSectionCard(s, globalIndexById) {
    const i = globalIndexById.get(s.id);
    const indexClass = s.completed ? "section-index completed" : "section-index";
    const indexIcon = s.completed ? "✓" : s.orderIndex;
    const typeIcon = s.type === "VIDEO" ? "🎬" : s.type === "MIXED" ? "📄🎬" : "📄";

    // Butun karta bosiladigan qilindi (kurslar katalogidagi kartalar
    // bilan bir xil uslub) — shuning uchun sarlavha endi alohida <a>
    // emas, oddiy matn; hover effekti ham shu tashqi kartada.
    const titleEl = `<span class="section-title-text" title="${escapeHtml(s.title)}">${escapeHtml(s.title)}</span>`;
    // Bosilganda — avval "tanlangan" deb belgilaymiz (selectCard),
    // qulflanmagan bo'lsa keyin darhol o'sha mavzuga o'tadi (qulflangan
    // bo'lsa faqat tanlash — klaviatura navigatsiyasi shu yerdan davom etadi).
    const cardClick = s.locked
        ? ` onclick="selectCard(${s.id})"`
        : ` onclick="selectCard(${s.id}); location.href='/courses/${COURSE_ID}/sections/${s.id}'"`;

    // Shu mavzu TEST BOSHQARUVIga bog'langan bo'lsa — nechta faol savoli
    // borligi (foydalanuvchi so'rovi bo'yicha: har bir mavzu kartochkasida
    // ko'rinishi kerak). linkedTopicQuestionCount backend'da BULK
    // hisoblanadi (CourseService.getDetail).
    const questionCountBadge = s.linkedTopicId != null
        ? `<span class="section-question-count-badge">📝 ${s.linkedTopicQuestionCount} ta test</span>`
        : "";

    // Shu mavzu haqiqiy test tizimidagi bir mavzuga bog'langan bo'lsa —
    // ro'yxatdan turib ham, mavzuni ochmasdan, testlarni yechish tugmasi
    // (faqat ochilgan/qulflanmagan mavzularda — qulflangan bo'lsa
    // mavzuning o'zini ham ko'rib bo'lmaydi).
    const testLink = (!s.locked && s.linkedTopicId)
        ? `<button class="topic-test-btn-inline" onclick="event.stopPropagation(); location.href='/testConfigPage?scienceId=${s.linkedScienceId}&topicId=${s.linkedTopicId}&courseId=${COURSE_ID}'">🎯 Mavzuga oid testlarni yechish</button>`
        : "";

    // Faqat boshqaruvchilar uchun — shu mavzuga (TEST BOSHQARUVIga
    // bog'langan bo'lsa) to'g'ridan-to'g'ri kartochkadan yangi test savoli
    // qo'shish (test-form.html'ga o'tadi, ?courseId= orqali — u yerdagi
    // "🔙 Kursga qaytish" tugmasi kursning UMUMIY (mavzular ro'yxati)
    // sahifasiga qaytaradi, ANIQ shu mavzu ICHIGA emas). "&fromSectionId="
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
               <button onclick="moveSectionUp(${s.id})" title="Yuqoriga" ${i === 0 ? "disabled" : ""}>⬆️</button>
               <button onclick="moveSectionDown(${s.id})" title="Pastga" ${i === allSections.length - 1 ? "disabled" : ""}>⬇️</button>
               <button onclick="openEditSectionForm(${s.id})" title="Tahrirlash">✏️</button>
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

/* ===== Bo'limsiz (standart) — bitta tekis grid + bitta umumiy sahifalash ===== */

function renderFlatSections() {
    const list = document.getElementById("sectionsList");
    const pagination = document.getElementById("sectionsPagination");

    if (!allSections.length) {
        list.innerHTML = `<div class="courses-empty">Hali mavzu yo'q</div>`;
        pagination.style.display = "none";
        return;
    }

    const totalPages = Math.max(1, Math.ceil(allSections.length / SECTIONS_PER_PAGE));
    if (sectionsPage >= totalPages) sectionsPage = totalPages - 1;
    if (sectionsPage < 0) sectionsPage = 0;

    const from = sectionsPage * SECTIONS_PER_PAGE;
    const pageSections = allSections.slice(from, from + SECTIONS_PER_PAGE);
    const globalIndexById = buildGlobalIndexMap();

    // Bo'limlarga guruhlangan ko'rinishdagi (renderChapterBox) "jami mavzu
    // / jami testlar" bilan BIR XIL — bo'limsiz (tekis) kursda ham shu
    // umumiy son ko'rinishi kerak (foydalanuvchi so'rovi: "gridda ham
    // to'g'rila"). Butun KURS bo'yicha (allSections — sahifalanmagan,
    // to'liq ro'yxat), joriy sahifagagina emas.
    const totalQuestions = allSections.reduce(
        (sum, s) => sum + (s.linkedTopicId != null ? (s.linkedTopicQuestionCount || 0) : 0), 0);

    list.innerHTML = `
        <div class="sections-summary">(mavzu — ${allSections.length} ta, jami testlar — ${totalQuestions} ta)</div>
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

/* ===== Bo'limlar bo'yicha guruhlangan — har biri alohida "box", o'z sahifalashi bilan ===== */

function renderGroupedSections() {
    const list = document.getElementById("sectionsList");
    document.getElementById("sectionsPagination").style.display = "none";

    // chapterKey -> {chapterId, name, orderIndex, items[]}. "none" —
    // hali hech qanday Bo'limga biriktirilmagan mavzular (bo'lsa, ro'yxat
    // OXIRIDA, "— Bo'limsiz mavzular —" nomi bilan).
    const groups = new Map();
    for (const s of allSections) {
        const key = s.chapterId != null ? String(s.chapterId) : "none";
        if (!groups.has(key)) {
            groups.set(key, {
                key,
                chapterId: s.chapterId,
                name: s.chapterId != null ? s.chapterName : "— Bo'limsiz mavzular —",
                orderIndex: s.chapterId != null ? s.chapterOrderIndex : Number.MAX_SAFE_INTEGER,
                items: []
            });
        }
        groups.get(key).items.push(s);
    }

    const sortedGroups = [...groups.values()].sort((a, b) => a.orderIndex - b.orderIndex);
    const globalIndexById = buildGlobalIndexMap();

    list.innerHTML = sortedGroups.map(group => renderChapterBox(group, globalIndexById)).join("");
}

function renderChapterBox(group, globalIndexById) {
    // Shu Bo'limdagi BARCHA mavzularning (TEST BOSHQARUVIga bog'langanlari)
    // testlari yig'indisi — bo'lim sarlavhasida "jami testlar" sifatida
    // ko'rsatish uchun (foydalanuvchi so'rovi bo'yicha).
    const totalQuestions = group.items.reduce(
        (sum, s) => sum + (s.linkedTopicId != null ? (s.linkedTopicQuestionCount || 0) : 0), 0);

    const totalPages = Math.max(1, Math.ceil(group.items.length / CHAPTER_SECTIONS_PER_PAGE));
    let page = chapterPages[group.key] || 0;
    if (page >= totalPages) page = totalPages - 1;
    if (page < 0) page = 0;
    chapterPages[group.key] = page;

    const from = page * CHAPTER_SECTIONS_PER_PAGE;
    const pageItems = group.items.slice(from, from + CHAPTER_SECTIONS_PER_PAGE);

    const cardsHtml = pageItems.map(s => renderSectionCard(s, globalIndexById)).join("");
    const paginationHtml = totalPages > 1
        ? buildPaginationHtml(totalPages, page, (p) => `changeChapterPage('${group.key}', ${p})`)
        : "";

    // "✏️" — faqat haqiqiy bo'limlarda (group.chapterId != null), "—
    // Bo'limsiz mavzular —" psevdo-guruhida ko'rsatilmaydi (uni "qayta
    // nomlash" mantiqsiz — u umuman CourseChapter yozuvi emas).
    const renameBtn = (cachedCourse && cachedCourse.canManage && group.chapterId != null)
        ? `<button class="chapter-rename-btn" onclick="renameChapterPrompt(${group.chapterId})" title="Bo'lim nomini tahrirlash">✏️</button>`
        : "";

    // "🗑️ Bo'lim + mavzular" — deleteSelectedChapter (Bo'lim tanlash
    // select'i yonida) dan farqli, bo'sh bo'lishi shart emas: shu Bo'lim
    // ICHIDAGI barcha kurs mavzularini (va bog'langan bo'lsa, TEST
    // BOSHQARUVIdagi mos mavzu+savollarni ham) birga o'chiradi. Foydalanuvchi
    // so'rovi bo'yicha ATAYLAB shu yerda (TEST BOSHQARUVIda EMAS).
    const deleteWithTopicsBtn = (cachedCourse && cachedCourse.canManage && group.chapterId != null)
        ? `<button class="chapter-rename-btn danger-btn" onclick="deleteChapterWithLinkedTopics(${group.chapterId}, ${JSON.stringify(group.name).replace(/"/g, "&quot;")})" title="Bo'lim va ichidagi barcha mavzularni (bog'langan bo'lsa, TEST BOSHQARUVIdagi savollari bilan) butunlay o'chirish">🗑️</button>`
        : "";

    // Har bir Bo'lim — o'zining ALOHIDA "Saralash: A→Z / Z→A" tugmalariga
    // ega (sortChapterSections) — faqat SHU bo'lim ichidagi mavzularni
    // qayta tartiblaydi, boshqa bo'limlarga (yoki bo'limsiz mavzularga)
    // hech qanday ta'sir qilmaydi.
    const sortBar = (cachedCourse && cachedCourse.canManage && group.items.length > 1)
        ? `<div class="chapter-box-sort" onclick="event.stopPropagation()">
               <span>Saralash:</span>
               <button onclick="sortChapterSections('${group.key}', 'AZ')">A→Z</button>
               <button onclick="sortChapterSections('${group.key}', 'ZA')">Z→A</button>
           </div>`
        : "";

    return `
        <div class="chapter-box">
            <h3 class="chapter-box-title">📂 ${escapeHtml(group.name)} <span class="chapter-box-count">(mavzu — ${group.items.length} ta, jami testlar — ${totalQuestions} ta)</span><span class="chapter-box-actions">${renameBtn}${deleteWithTopicsBtn}</span></h3>
            ${sortBar}
            <div class="sections-grid">${cardsHtml}</div>
            ${paginationHtml ? `<div class="sections-pagination chapter-box-pagination">${paginationHtml}</div>` : ""}
        </div>
    `;
}

// Faqat "chapterKey" bo'limiga (yoki "none" — bo'limsiz mavzular
// psevdo-guruhiga) tegishli mavzularni A-Z/Z-A tartibga soladi — boshqa
// bo'limlardagi (yoki bo'limsiz) mavzularning nisbiy tartibi BUTUNLAY
// o'zgarishsiz qoladi. Backend /sections/reorder har doim TO'LIQ (butun
// kurs bo'yicha) yangi tartibdagi id ro'yxatini kutadi (orderIndex —
// bitta umumiy, ketma-ket raqam, Bo'lim bo'yicha alohida emas) — shu
// sabab shu bo'limga tegishli o'rinlarga, ular TURGAN JOYLARIDA, faqat
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

/* ===== Mavzu kartochkalari orasida klaviatura navigatsiyasi ===== */

// Tanlangan kartaning "navigatsiya guruhi" — bo'limsiz (flat) ko'rinishda
// BUTUN kurs; guruhlangan ko'rinishda esa FAQAT shu kartaning Bo'limi
// (yoki "— Bo'limsiz mavzular —" psevdo-guruhi). ←/→/↑/↓/Home
// navigatsiyasi ANIQ shu guruh (o'z sahifalashi) DOIRASIDA ishlaydi —
// boshqa Bo'limdagi kartalarga "sakrab" ketmaydi.
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

// ← / → — joriy sahifadagi (yoki joriy Bo'lim qutisidagi) oldingi/keyingi
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
            moveCardPage(sectionId, 1);
            break;
        case "ArrowUp":
            event.preventDefault();
            moveCardPage(sectionId, -1);
            break;
        case "Home":
            event.preventDefault();
            moveCardPage(sectionId, "home");
            break;
        case "End":
            event.preventDefault();
            moveCardToLast(sectionId);
            break;
    }
}

// Kartani "tanlangan" deb belgilaydi — vizual ajratib ko'rsatish
// (".selected" klassi) + klaviatura fokusi (keyingi Strelka/Home
// tugmalari shu kartadan davom etishi uchun). "scroll" — sahifa/Bo'lim
// almashganda yangi kartani ekranga ko'rinadigan joyga olib kelish uchun.
function selectCard(sectionId, { scroll = false } = {}) {
    selectedSectionId = sectionId;
    document.querySelectorAll(".section-item.selected").forEach(el => el.classList.remove("selected"));
    const el = document.querySelector(`.section-item[data-section-id="${sectionId}"]`);
    if (el) {
        el.classList.add("selected");
        el.focus({ preventScroll: !scroll });
        if (scroll) el.scrollIntoView({ behavior: "smooth", block: "center" });
    }
}

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

/* ===== Mavzuni Bo'limga biriktirish — TANLOV (select), erkin matn emas =====
   Erkin matn (avvalgi versiya) yozuvdagi eng kichik farqda ham (bo'sh joy,
   katta-kichik harf, imlo xatosi) yangi DUBLIKAT bo'lim yaratib yuborardi,
   va bitta bo'limni "qayta nomlash" uchun unga tegishli HAR BIR mavzuni
   birma-bir tahrirlab, qo'lda bir xil yangi nomni terish kerak bo'lardi.
   Endi: mavjud bo'limlar ANIQ id bo'yicha tanlanadi (CourseSectionSaveDto.
   chapterId); faqat "➕ Yangi bo'lim yaratish..." tanlanganda nom kiritiladi
   (newChapterName) — va nomni o'zgartirish alohida renameChapterPrompt()
   orqali, BITTA umumiy CourseChapter yozuvini o'zgartiradi (barcha unga
   biriktirilgan mavzularda darhol, avtomatik aks etadi). */

const NEW_CHAPTER_OPTION = "__new__";

// selectId — "newSectionChapterSelect" | "editSectionChapterSelect".
// selectedChapterId — oldindan tanlangan bo'lim id'si (tahrirlashda), yoki null.
// mode — "new" | "edit" — tanlangan Fan (Science)ni topish uchun (shu
// Fanda TEST BOSHQARUVIda ALLAQACHON mavjud Bo'limlarni ham ro'yxatga
// qo'shish uchun, pastga qarang). Berilmasa (yoki Fan hali tanlanmagan/
// yangi bo'lsa) — faqat shu KURSNING o'z Bo'limlari ko'rsatiladi
// (avvalgi xulq-atvor).
// chapterId -> shu bo'limda nechta mavzu borligi — deleteSelectedChapter()
// va onChapterSelectChange() "🗑️" tugmasini ko'rsatish/yashirish uchun
// shu yerdan o'qiydi (har safar populateChapterSelect chaqirilganda
// yangilanadi).
let chapterCountsById = {};

async function populateChapterSelect(selectId, selectedChapterId, mode) {
    const select = document.getElementById(selectId);
    if (!select) return;

    // 1) Shu KURSNING BARCHA Bo'limlari (CourseChapter) — hozircha BO'SH
    //    (hech qanday mavzuga biriktirilmaganlari) ham shu jumladan
    //    (backend'dan, sectionCount bilan birga — bo'sh bo'limni
    //    o'chirish imkoniyati uchun). "id:<id>" bilan tanlanadi,
    //    saqlashda ANIQ shu bo'lim ishlatiladi (chapterId).
    let courseChapters = [];
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/chapters`);
        if (res.ok) courseChapters = await res.json();
    } catch (err) {
        console.error(err);
    }
    chapterCountsById = {};
    courseChapters.forEach(c => { chapterCountsById[c.id] = c.sectionCount; });

    // 2) TEST BOSHQARUVIDA (tanlangan Fan bo'yicha) ALLAQACHON mavjud
    //    Bo'limlar — kursning o'z Bo'limlari ro'yxatida hali bo'lmagan
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

    const options = ['<option value="">— Bo\'limsiz —</option>'];
    for (const c of courseChapters) {
        const emptyMark = c.sectionCount === 0 ? " (bo'sh)" : "";
        options.push(`<option value="id:${c.id}">${escapeHtml(c.name)}${emptyMark}</option>`);
    }
    for (const name of externalNames) {
        options.push(`<option value="name:${encodeURIComponent(name)}">${escapeHtml(name)}</option>`);
    }
    options.push(`<option value="${NEW_CHAPTER_OPTION}">➕ Yangi bo'lim yaratish...</option>`);

    select.innerHTML = options.join("");
    select.value = selectedChapterId != null ? `id:${selectedChapterId}` : "";

    if (mode) onChapterSelectChange(mode);
}

// Tanlangan (yoki "Boshqa"da qo'lda yozilgan) Fan nomini cachedSciences
// ro'yxatidan id'siga o'giradi — hali mavjud bo'lmagan (yangi kiritilgan)
// Fan uchun albatta null qaytadi (test boshqaruvida hali hech qanday
// Bo'lim bo'lishi ham mumkin emas, shuning uchun bu to'g'ri xulq-atvor).
function getSelectedScienceId(mode) {
    const name = getSelectedScienceName(mode);
    if (!name) return null;
    const match = cachedSciences.find(s => s.name.toLowerCase() === name.toLowerCase());
    return match ? match.id : null;
}

// "➕ Yangi bo'lim yaratish..." tanlansa — yangi nom kiritish maydoni
// ochiladi; "🗑️" esa faqat hozir tanlangan Bo'lim kursning O'Z Bo'limi
// ("id:" bilan) VA hech qanday mavzuga biriktirilmagan (bo'sh) bo'lsa
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

// "🗑️" tugmasi — faqat BO'SH (hech qanday mavzuga biriktirilmagan)
// Bo'limni o'chiradi (backend ham xuddi shu tekshiruvni qaytaradi,
// himoya sifatida). Bo'lim tanlash ro'yxatini qayta yuklaydi.
async function deleteSelectedChapter(mode) {
    const deleteBtn = document.getElementById(mode + "SectionChapterDeleteBtn");
    const chapterId = deleteBtn.dataset.chapterId;
    if (!chapterId) return;

    if (!confirm("Bu bo'sh bo'limni o'chirmoqchimisiz? Bu amalni bekor qilib bo'lmaydi.")) return;

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/chapters/${chapterId}`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "Bo'limni o'chirishda xatolik");
            return;
        }
        await populateChapterSelect(mode + "SectionChapterSelect", null, mode);
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

// "🗑️ Bo'shlarni tozalash" — shu KURSDA hech qanday mavzuga
// biriktirilmagan BARCHA Bo'limlarni bir yo'la o'chiradi (topic-sections
// sahifasidagi "Bo'sh bo'limlarni o'chirish" bilan bir xil g'oya, faqat
// bu yerda CourseChapter uchun). Ochiq turgan har ikkala forma
// (yangi/tahrirlash) select'i ham qayta yuklanadi.
async function deleteEmptyChapters() {
    if (!confirm("Kursdagi barcha bo'sh (hech qanday mavzuga biriktirilmagan) bo'limlarni o'chirmoqchimisiz?\n\nBu amalni bekor qilib bo'lmaydi.")) {
        return;
    }

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/chapters/empty`, { method: "DELETE" });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            alert(data.error || "O'chirishda xatolik");
            return;
        }

        alert(data.deleted > 0
            ? `✅ ${data.deleted} ta bo'sh bo'lim o'chirildi.`
            : "ℹ️ Bo'sh bo'lim topilmadi.");

        if (document.getElementById("newSectionChapterSelect")) {
            await populateChapterSelect("newSectionChapterSelect", null, "new");
        }
        if (document.getElementById("editSectionChapterSelect")) {
            await populateChapterSelect("editSectionChapterSelect", null, "edit");
        }
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

// submitAddSection/submitEditSection payload'iga to'g'ridan-to'g'ri
// qo'shiladigan {chapterId, newChapterName} juftligi — select qiymati
// "id:<id>" (kursning o'z Bo'limi) yoki "name:<nom>" (TEST BOSHQARUVIdan
// olingan, hali kursga biriktirilmagan Bo'lim nomi) bo'lishi mumkin.
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

// "✏️" — bo'lim (chapter-box) sarlavhasidagi tahrirlash tugmasi. Nom BITTA
// umumiy CourseChapter yozuvida saqlanadi — shu yerda o'zgartirilishi bilan
// unga biriktirilgan BARCHA mavzularda avtomatik yangilanadi.
function renameChapterPrompt(chapterId) {
    const current = allSections.find(s => s.chapterId === chapterId);
    const newName = prompt("Bo'lim nomini kiriting:", current ? current.chapterName : "");
    if (newName === null) return; // bekor qilindi

    const trimmed = newName.trim();
    if (!trimmed) {
        alert("❌ Bo'lim nomi bo'sh bo'lishi mumkin emas.");
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
            alert(data.error || "Bo'lim nomini o'zgartirishda xatolik");
            return;
        }
        loadCourse();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

// "🗑️ Bo'lim + mavzular" (chapter-box sarlavhasidagi ✏️ yonida) —
// deleteSelectedChapter'dan farqli, bo'sh bo'lishi shart emas: shu
// Bo'lim ICHIDAGI barcha kurs mavzularini (CourseSection) soft-delete
// qiladi ("🗑️ O'chirilgan mavzular" panelidan "♻️ Tiklash" bilan
// qaytariladi). TEST BOSHQARUVIdagi Topic/Question'ga HECH QACHON
// tegilmaydi — bog'langan mavzu bo'lsa ham, faqat bog'lanishning o'zi
// (avtomatik) uziladi, savollar o'z joyida, butun holda qolaveradi.
async function deleteChapterWithLinkedTopics(chapterId, chapterName) {
    if (!confirm(`"${chapterName}" bo'limini ICHIDAGI barcha mavzulari bilan o'chirmoqchimisiz?\n\n(Butunlay o'chmaydi — "🗑️ O'chirilgan mavzular" panelidan qaytarish mumkin. TEST BOSHQARUVIdagi savollarga tegilmaydi.)`)) {
        return;
    }

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/chapters/${chapterId}/with-topics`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "O'chirishda xatolik");
            return;
        }
        loadCourse();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

// "🔄 Bo'lim-Mavzu bog'lanishini sinxronlash" — Bo'lim-Mavzu bog'lanishi
// odatda avtomatik sinxron turadi (mavzu saqlanganda), lekin vaqt o'tishi
// bilan farq (drift) paydo bo'lib qolishi mumkin — shu tugma BARCHA kurs
// mavzularini joriy Bo'lim holatiga qarab qayta to'g'rilaydi (kurs —
// "haqiqiy manba", TEST BOSHQARUVI shunga moslashtiriladi).
async function syncChapterTopics() {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/chapters/sync-topics`, { method: "POST" });
        const data = await res.json().catch(() => ({}));
        if (!res.ok) {
            alert(data.error || "Sinxronlashda xatolik");
            return;
        }
        alert(data.updated > 0
            ? `✅ ${data.updated} ta mavzuning Bo'limi TEST BOSHQARUVIDA to'g'rilandi.`
            : "✅ Hammasi allaqachon sinxron edi — o'zgarish kerak bo'lmadi.");
        loadCourse();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
}

// ========================================================================
//     Kurs ichidan mavzu yoritmasi bo'yicha qidiruv
// ========================================================================
// Shu kursdagi (allSections'da linkedTopicId'i bor) mavzular qaysi
// kurs(lar)ga bog'langan bo'lsa (odatda faqat shu kurs, lekin bitta mavzu
// boshqa kursga ham bog'langan bo'lsa — o'sha ham), o'sha kurs(lar)ning
// BARCHA mavzuga bog'langan bo'limlaridagi matn darsi ("mavzu yoritmasi" —
// CourseSection.textContent) ichidan qidiradi (backend: CourseService.
// searchTopicExplanations). Topilgan natijaga bosilsa — o'sha kurs
// bo'limining o'ziga o'tadi.
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
        resultsEl.innerHTML = `<div class="explanation-search-empty">Bu kursda mavzuga bog'langan bo'lim yo'q</div>`;
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
    document.getElementById("editCoursePrice").value = (cachedCourse && cachedCourse.price) || "";
    onEditCourseFreeToggle();

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
        const priceValue = document.getElementById("editCoursePrice").value;
        const price = !free && priceValue ? Number(priceValue) : null;

        const res = await fetch(`/api/courses/${COURSE_ID}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                title, description, coverImageUrl, free, price,
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

// Kursni "O'chirilganlar savati"ga o'tkazadi (soft-delete) — DARHOL
// butunlay o'chmaydi, /courses/trash sahifasidan "♻️ Tiklash" bilan
// bir zumda qaytariladi (CourseService.deleteCourse).
async function deleteCourse() {
    if (!confirm("Kursni \"O'chirilganlar savati\"ga o'tkazmoqchimisiz?\n\n(Butunlay o'chmaydi — /courses/trash sahifasidan istalgan payt qaytarish mumkin.)")) return;

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

// "✏️ Tahrirlash" tugmasi HAR BIR mavzu kartasida (panel TASHQARISIDA)
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

function openAddSectionForm() {
    document.getElementById("newSectionTextEditor").innerHTML = "";
    document.getElementById("newSectionTopicName").value = "";
    newTopicNameManuallyEdited = false;
    attachImageResizeHandlers("newSectionTextEditor");
    // Default — shu kursning o'zi nomi (odatda kurs mavzusi = fan nomi) —
    // checkbox belgilansa shu tayyor turadi, lekin checkbox o'zi
    // boshlanishda O'CHIRILGAN (bog'lash ixtiyoriy, avtomatik emas).
    applyScienceSelection("new", cachedCourse ? cachedCourse.title : "");
    document.getElementById("newSectionLinkTopic").checked = false;
    onTopicLinkToggle("new");
    populateChapterSelect("newSectionChapterSelect", null, "new");
    onChapterSelectChange("new");
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
        alert("❌ Mavzu nomini kiriting");
        return;
    }

    if (!includeText && !includeVideo) {
        alert("❌ Kamida bittasini tanlang: Matn yoki Video");
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
            alert("❌ Matn kontentini kiriting");
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
            alert(data.error || "Mavzu qo'shishda xatolik");
            return;
        }

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
        alert("Tarmoq xatoligi");
    }
}

// "O'chirilganlar savati"ga o'tkazadi (soft-delete) — darhol butunlay
// o'chmaydi, "🗑️ O'chirilgan mavzular" panelidan ("♻️ Tiklash") bir
// zumda qaytariladi (CourseService.deleteSection).
async function deleteSection(sectionId) {
    if (!confirm("Mavzuni o'chirmoqchimisiz?\n\n(Butunlay o'chmaydi — \"🗑️ O'chirilgan mavzular\" panelidan qaytarish mumkin.)")) return;

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

// "🗑️ O'chirilgan mavzular" paneli — soft-delete qilingan CourseSection'lar
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
            list.innerHTML = "<p>O'chirilgan mavzu yo'q</p>";
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
    if (!confirm("Bu mavzuni tiklamoqchimisiz?")) return;

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/${sectionId}/restore`, { method: "POST" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "Tiklashda xatolik");
            return;
        }
        loadSectionTrash();
        loadCourse();
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}

async function permanentlyDeleteSection(sectionId, title) {
    if (!confirm(`⚠️ "${title}" mavzusini BUTUNLAY o'chirmoqchimisiz?\n\nBu amalni HECH QANDAY tarzda bekor qilib bo'lmaydi.`)) return;
    if (!confirm("Haqiqatan ham ishonchingiz komilmi?")) return;

    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/${sectionId}/permanent`, { method: "DELETE" });
        if (!res.ok) {
            const data = await res.json().catch(() => ({}));
            alert(data.error || "O'chirishda xatolik");
            return;
        }
        loadSectionTrash();
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
            alert("Mavzu ma'lumotlarini yuklab bo'lmadi");
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

        // Fan — allaqachon bog'langan bo'lsa o'sha, aks holda kurs nomi
        // default sifatida tanlanadi. Mavzu nomi — bog'langan bo'lsa o'sha
        // (endi "qo'lda kiritilgan" deb hisoblanadi, bo'lim nomi keyinroq
        // o'zgarsa ham qayta yozilmaydi); aks holda bo'lim nomining o'zi
        // (bo'lim nomi o'zgarsa, bu ham birga yangilanaveradi).
        applyScienceSelection("edit", section.linkedScienceName || (cachedCourse ? cachedCourse.title : ""));
        editTopicNameManuallyEdited = !!section.linkedTopicName;
        document.getElementById("editSectionTopicName").value = section.linkedTopicName || section.title || "";
        // Checkbox — bo'lim ALLAQACHON biror mavzuga bog'langan bo'lsagina
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

// keepValue=true faqat bo'lim yuklanganda (openEditSectionForm) ishlatiladi
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
        alert("❌ Mavzu nomini kiriting");
        return;
    }

    if (!includeText && !includeVideo) {
        alert("❌ Kamida bittasini tanlang: Matn yoki Video");
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
            alert("❌ Matn kontentini kiriting");
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
            alert(data.error || "Mavzuni saqlashda xatolik");
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
