let cachedCourse = null;
let clickPaymentEnabled = false;

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

// "🖼 Rasm qo'shish" — fayl tanlangach serverga yuklanadi (virus/tur
// tekshiruvi bilan, boshqa fayl yuklashlar kabi), qaytgan URL kursor
// turgan joyga qo'yiladi. Oddiy <img> emas — "rich-img-wrap" ichiga
// pastki-o'ng burchakdagi sudraladigan tutqich (handle) bilan birga
// qo'yiladi, shu orqali rasm katta bo'lsa ham kichraytirish mumkin
// (attachImageResizeHandlers() shu tutqichni ushlaydi).
async function richInsertImage(editorId, fileInput) {
    const file = fileInput.files[0];
    if (!file) return;

    const editor = document.getElementById(editorId);
    editor.focus();
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
        document.execCommand('insertHTML', false, html);
        injectAlignBars(editorId);
    } catch (err) {
        console.error(err);
        alert("❌ Rasm yuklashda tarmoq xatoligi");
    } finally {
        fileInput.value = "";
    }
}

// "🎬 Video qo'shish" (rich-toolbar, matn ICHIGA) — rasm bilan bir xil
// tamoyilda ishlaydi: YouTube havola/ID YOKI kompyuterdan fayl, kursor
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
        alert("❌ YouTube havolasini kiriting yoki video fayl tanlang");
        return;
    }

    closeVideoInsertModal();

    if (url) {
        insertYouTubeEmbedHtml(editorId, url, width);
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

function insertYouTubeEmbedHtml(editorId, source, width) {
    const editor = document.getElementById(editorId);
    editor.focus();
    attachImageResizeHandlers(editorId);

    // E'tibor bering: kenglik WRAP'ga emas, to'g'ridan-to'g'ri <iframe>'ga
    // qo'yiladi — xuddi rasmdagi kabi, shunda tutqichni sudrash ham
    // (startImageResize/updateImageResize) o'zgarishsiz ishlayveradi.
    const videoId = escapeHtml(extractYouTubeId(source));
    const html = `<span class="rich-img-wrap" contenteditable="false">`
        + `<iframe src="https://www.youtube.com/embed/${videoId}" style="width:${width};max-width:100%;aspect-ratio:16/9;border:0;display:block" allowfullscreen></iframe>`
        + `<span class="rich-img-handle" title="Sudrab o'lchamini o'zgartiring"></span>`
        + `</span>&nbsp;`;
    document.execCommand('insertHTML', false, html);
    injectAlignBars(editorId);
}

async function richInsertUploadedVideo(editorId, fileInput) {
    const file = fileInput.files[0];
    if (!file) return;

    const width = fileInput.dataset.pendingWidth || "480px";
    delete fileInput.dataset.pendingWidth;

    const editor = document.getElementById(editorId);
    editor.focus();
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
        document.execCommand('insertHTML', false, html);
        injectAlignBars(editorId);
    } catch (err) {
        console.error(err);
        alert("❌ Video yuklashda tarmoq xatoligi");
    } finally {
        fileInput.value = "";
    }
}

// ================= Rasm/video'ni chapga/markazga/o'ngga surish =================
// Har bir "rich-img-wrap" (rasm ham, video ham) ustida — resize tutqichi
// kabi — kichik surish tugmalari (⬅ ⏺ ➡) chiqadi. Ular bosilganda wrap'ga
// "align-left/center/right" klassi qo'shiladi (courses.css'da wrap'ni
// display:block qilib, margin orqali chap/markaz/o'ngga suradi — hech
// qanday klass bo'lmasa standart holat: matn bilan bir qatorda, chapdan
// boshlanadi). Yangi qo'shilgan rasm/video uchun ham (richInsertImage/
// insertYouTubeEmbedHtml/richInsertUploadedVideo — insertHTML'dan keyin),
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

// .docx faylni mammoth.js orqali HTML'ga aylantiradi — abzatslar,
// qalin/kursiv, sarlavhalar, ro'yxatlar kabi formatlash saqlanadi (fayl
// ichidagi shriftlar/uslublar o'zgartirilmaydi, faqat saytning umumiy
// dizayniga moslashtiriladi). Natija to'g'ridan-to'g'ri tahrirlash
// maydoniga qo'yiladi — kerak bo'lsa qo'lda ham tahrirlash mumkin.
async function importDocxFile(fileInput, editorId) {
    const file = fileInput.files[0];
    if (!file) return;

    if (typeof mammoth === "undefined") {
        alert("❌ Import kutubxonasi yuklanmadi. Internet aloqasini tekshirib, sahifani qayta yuklang.");
        fileInput.value = "";
        return;
    }

    try {
        const arrayBuffer = await file.arrayBuffer();
        const result = await mammoth.convertToHtml({ arrayBuffer });
        document.getElementById(editorId).innerHTML = result.value;
    } catch (err) {
        console.error(err);
        alert("❌ Faylni import qilishda xatolik: " + err.message);
    } finally {
        fileInput.value = "";
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
    document.getElementById("manageCoursePanel").style.display = course.canManage ? "block" : "none";
    document.getElementById("sectionsSortBar").style.display = course.canManage ? "flex" : "none";

    if (course.canManage) {
        document.getElementById("togglePublishBtn").textContent =
            course.published ? "📕 Qoralamaga o'tkazish" : "📗 Chop etish";
        document.getElementById("editCourseTitle").value = course.title;
        document.getElementById("editCourseDescription").value = course.description || "";
    }

    renderSections(course.sections);
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

function renderSections(sections) {
    const list = document.getElementById("sectionsList");

    if (!sections.length) {
        list.innerHTML = `<div class="courses-empty">Hali bo'lim yo'q</div>`;
        return;
    }

    list.innerHTML = sections.map((s, i) => {
        const indexClass = s.completed ? "section-index completed" : "section-index";
        const indexIcon = s.completed ? "✓" : s.orderIndex;
        const typeIcon = s.type === "VIDEO" ? "🎬" : s.type === "MIXED" ? "📄🎬" : "📄";

        // Butun karta bosiladigan qilindi (kurslar katalogidagi kartalar
        // bilan bir xil uslub) — shuning uchun sarlavha endi alohida <a>
        // emas, oddiy matn; hover effekti ham shu tashqi kartada.
        const titleEl = `<span class="section-title-text" title="${escapeHtml(s.title)}">${escapeHtml(s.title)}</span>`;
        const cardClick = s.locked ? "" : ` onclick="location.href='/courses/${COURSE_ID}/sections/${s.id}'"`;

        // Ichidagi tugmalar (boshqarish, test) bosilganda kartaning o'zi
        // ham navigatsiya qilib yubormasligi uchun — shu wrapper'larga
        // event.stopPropagation() qo'yiladi.
        const manageActions = cachedCourse && cachedCourse.canManage
            ? `<div class="section-manage-actions" onclick="event.stopPropagation()">
                   <button onclick="moveSectionUp(${s.id})" title="Yuqoriga" ${i === 0 ? "disabled" : ""}>⬆️</button>
                   <button onclick="moveSectionDown(${s.id})" title="Pastga" ${i === sections.length - 1 ? "disabled" : ""}>⬇️</button>
                   <button onclick="openEditSectionForm(${s.id})" title="Tahrirlash">✏️</button>
                   <button onclick="deleteSection(${s.id})" title="O'chirish">🗑️</button>
               </div>`
            : "";

        // Shu mavzu haqiqiy test tizimidagi bir mavzuga bog'langan bo'lsa —
        // ro'yxatdan turib ham, bo'limni ochmasdan, testlarni yechish tugmasi
        // (faqat ochilgan/qulflanmagan mavzularda — qulflangan bo'lsa
        // bo'limning o'zini ham ko'rib bo'lmaydi).
        const testLink = (!s.locked && s.linkedTopicId)
            ? `<button class="topic-test-btn-inline" onclick="event.stopPropagation(); location.href='/testConfigPage?scienceId=${s.linkedScienceId}&topicId=${s.linkedTopicId}'">🎯 Mavzuga oid testlarni yechish</button>`
            : "";

        return `
            <div class="section-item ${s.locked ? "locked" : ""}"${cardClick}>
                <div class="section-item-top">
                    <div class="section-item-left">
                        <div class="${indexClass}">${indexIcon}</div>
                        ${titleEl}
                        <span class="section-type-icon">${typeIcon}</span>
                        ${s.locked ? '<span class="section-type-icon">🔒</span>' : ""}
                    </div>
                    ${testLink}
                </div>
                ${manageActions}
            </div>
        `;
    }).join("");
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
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

async function deleteCourse() {
    if (!confirm("Kursni butunlay o'chirmoqchimisiz? Barcha bo'limlar ham o'chadi.")) return;

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
}

async function submitAddSection() {
    const title = document.getElementById("newSectionTitle").value.trim();
    const includeText = document.getElementById("includeText").checked;
    const includeVideo = document.getElementById("includeVideo").checked;

    if (!title) {
        alert("❌ Bo'lim nomini kiriting");
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
        textContentFormat: "HTML"
    };

    if (includeText) {
        const editor = document.getElementById("newSectionTextEditor");
        if (!editor.innerText.trim()) {
            alert("❌ Matn kontentini kiriting");
            return;
        }
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
            alert(data.error || "Bo'lim qo'shishda xatolik");
            return;
        }

        document.getElementById("newSectionTitle").value = "";
        document.getElementById("newSectionTextEditor").innerHTML = "";
        document.getElementById("newSectionVideoUrl").value = "";
        document.getElementById("newSectionScienceOther").value = "";
        document.getElementById("newSectionTopicName").value = "";
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

async function deleteSection(sectionId) {
    // Backend 409 (bog'liq ma'lumotlar — progress yozuvlari) qaytarishi
    // mumkin edi, lekin CourseService.deleteSection endi ularni avtomatik
    // o'chiradi (kursni o'chirishdagi FK bug bilan bir xil sabab/tuzatish).
    if (!confirm("Bo'limni o'chirmoqchimisiz?")) return;

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

/* ===== OWNER/ADMIN: bo'limni tahrirlash ===== */

let editingSectionId = null;

async function openEditSectionForm(sectionId) {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/${sectionId}`);
        if (!res.ok) {
            alert("Bo'lim ma'lumotlarini yuklab bo'lmadi");
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

        onEditContentToggle(document.getElementById("editIncludeText"));

        if (hasVideo) {
            document.getElementById("editSectionVideoSource").value = section.videoSourceType || "YOUTUBE";
            document.getElementById("editSectionVideoUrl").value = section.videoUrl || "";
            document.getElementById("editSectionVideoDuration").value = section.videoDurationSeconds || "";
            onEditVideoSourceChange();
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

function onEditVideoSourceChange() {
    const source = document.getElementById("editSectionVideoSource").value;
    document.getElementById("editSectionVideoUrl").style.display = source === "UPLOAD" ? "none" : "block";
    document.getElementById("editSectionVideoFile").style.display = source === "UPLOAD" ? "block" : "none";
    document.getElementById("editSectionVideoDuration").style.display = source === "EXTERNAL" ? "block" : "none";

    document.getElementById("editSectionVideoUrl").placeholder =
        source === "YOUTUBE" ? "YouTube video ID (masalan: dQw4w9WgXcQ)" : "Video URL";
}

async function submitEditSection() {
    if (!editingSectionId) return;

    const title = document.getElementById("editSectionTitle").value.trim();
    const includeText = document.getElementById("editIncludeText").checked;
    const includeVideo = document.getElementById("editIncludeVideo").checked;

    if (!title) {
        alert("❌ Bo'lim nomini kiriting");
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
        textContentFormat: "HTML"
    };

    if (includeText) {
        const editor = document.getElementById("editSectionTextEditor");
        if (!editor.innerText.trim()) {
            alert("❌ Matn kontentini kiriting");
            return;
        }
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
            alert(data.error || "Bo'limni saqlashda xatolik");
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
