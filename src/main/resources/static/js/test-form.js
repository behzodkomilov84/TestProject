// ================= Rasm/video yuklash (savol, javob, izoh uchun) =================
// Har bir ".image-upload" bloki: fayl input + "rasm qo'shish" tugmasi +
// preview <img> + "olib tashlash" tugmasi. Fayl tanlangan zahoti serverga
// yuklanadi va qaytgan URL fileInput.dataset.url'da saqlanadi — forma
// yuborilganda shu URL ishlatiladi. data-role'ga qarab (question-image /
// answer-image / commentary-image) tegishli endpoint tanlanadi.
const IMAGE_UPLOAD_ENDPOINTS = {
    "question-image": "/api/question/upload-image",
    "answer-image": "/api/question/upload-image",
    "commentary-image": "/api/question/upload-commentary-image"
};

function setupImageUpload(container) {
    const fileInput = container.querySelector(".image-input");
    const uploadBtn = container.querySelector(".image-btn");
    const preview = container.querySelector(".image-preview");
    const removeBtn = container.querySelector(".remove-image-btn");
    const endpoint = IMAGE_UPLOAD_ENDPOINTS[container.dataset.role] || "/api/question/upload-image";
    const originalLabel = uploadBtn.textContent;

    uploadBtn.addEventListener("click", () => fileInput.click());

    fileInput.addEventListener("change", async () => {
        const file = fileInput.files[0];
        if (!file) return;

        const formData = new FormData();
        formData.append("image", file);

        uploadBtn.disabled = true;
        uploadBtn.textContent = "⏳ Yuklanmoqda...";

        try {
            const res = await fetch(endpoint, {
                method: "POST",
                body: formData
            });

            const data = await res.json();

            if (!res.ok) {
                showAlertModal(data.error || "❌ Rasmni yuklab bo'lmadi");
                fileInput.value = "";
                return;
            }

            fileInput.dataset.url = data.url;
            preview.src = data.url;
            preview.classList.remove("hidden");
            removeBtn.classList.remove("hidden");

        } catch (err) {
            console.error(err);
            showAlertModal("❌ Rasmni yuklashda tarmoq xatoligi");
        } finally {
            uploadBtn.disabled = false;
            uploadBtn.textContent = originalLabel;
        }
    });

    removeBtn.addEventListener("click", () => {
        fileInput.value = "";
        delete fileInput.dataset.url;
        preview.src = "";
        preview.classList.add("hidden");
        removeBtn.classList.add("hidden");
    });
}

function getImageUrl(container) {
    const fileInput = container.querySelector(".image-input");
    return fileInput?.dataset.url || null;
}

function resetImageUpload(container) {
    const fileInput = container.querySelector(".image-input");
    const preview = container.querySelector(".image-preview");
    const removeBtn = container.querySelector(".remove-image-btn");

    fileInput.value = "";
    delete fileInput.dataset.url;
    preview.src = "";
    preview.classList.add("hidden");
    removeBtn.classList.add("hidden");
}

// ================= Video yuklash (faqat izoh uchun) =================
function setupVideoUpload(container) {
    const fileInput = container.querySelector(".video-input");
    const uploadBtn = container.querySelector(".video-btn");
    const preview = container.querySelector(".video-preview");
    const removeBtn = container.querySelector(".remove-video-btn");
    const originalLabel = uploadBtn.textContent;

    uploadBtn.addEventListener("click", () => fileInput.click());

    fileInput.addEventListener("change", async () => {
        const file = fileInput.files[0];
        if (!file) return;

        const formData = new FormData();
        formData.append("video", file);

        uploadBtn.disabled = true;
        uploadBtn.textContent = "⏳ Yuklanmoqda...";

        try {
            const res = await fetch("/api/question/upload-commentary-video", {
                method: "POST",
                body: formData
            });

            const data = await res.json();

            if (!res.ok) {
                showAlertModal(data.error || "❌ Videoni yuklab bo'lmadi");
                fileInput.value = "";
                return;
            }

            fileInput.dataset.url = data.url;
            preview.src = data.url;
            preview.classList.remove("hidden");
            removeBtn.classList.remove("hidden");

        } catch (err) {
            console.error(err);
            showAlertModal("❌ Videoni yuklashda tarmoq xatoligi");
        } finally {
            uploadBtn.disabled = false;
            uploadBtn.textContent = originalLabel;
        }
    });

    removeBtn.addEventListener("click", () => {
        fileInput.value = "";
        delete fileInput.dataset.url;
        preview.src = "";
        preview.classList.add("hidden");
        removeBtn.classList.add("hidden");
    });
}

function getVideoUrl(container) {
    const fileInput = container.querySelector(".video-input");
    return fileInput?.dataset.url || null;
}

function resetVideoUpload(container) {
    const fileInput = container.querySelector(".video-input");
    const preview = container.querySelector(".video-preview");
    const removeBtn = container.querySelector(".remove-video-btn");

    fileInput.value = "";
    delete fileInput.dataset.url;
    preview.src = "";
    preview.classList.add("hidden");
    removeBtn.classList.add("hidden");
}

// ========================================================================
// "Izoh" — boy matn muharriri (foydalanuvchi so'rovi, 2026-09-05: "Izohni
// kiritish modalda bo'lsin. Eni A4 razmerda bo'lsin. Kursga dars
// qo'shish/tahrirlashdagi barcha funksiyalarni qo'sh") — courseDetail.js
// (courses.css) dagi "Kursga dars qo'shish/tahrirlash" matn muharriri
// bilan BIR XIL andoza (richExec/richFontName/richFontSize/rang
// palitrasi/tekislash/ro'yxatlar/rasm qo'shish — ataylab shu faylga
// ko'chirilgan, chunki bu sahifa courses.css/courseDetail.js'ni ulamaydi).
// Video/PPT/.docx import KIRITILMAGAN — foydalanuvchi ANIQ shu darajani
// tanladi ("Matn formatlash + rasm").
//
// Ilgari har bir javob variantining o'zida (position:fixed panel) BESHTA
// mustaqil ".commentary-box" bor edi — endi BESHTASI HAM modal, lekin har
// biri O'ZINING mustaqil muharririga ega (bitta umumiy muharrir emas —
// shunda har bir javobning izohi/rasmi mustaqil saqlanadi, murakkab
// holat-almashtirish mantig'i shart emas). 5 marta deyarli bir xil
// ~60 qatorli HTML'ni qo'lda test-form.html'da takrorlash o'rniga — shu
// funksiya orqali DINAMIK yaratiladi (DOMContentLoaded'da bir marta).
// ========================================================================

const ANSWER_LETTERS = ["A", "B", "C", "D", "E"];

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text ?? "";
    return div.innerHTML;
}

function buildCommentaryModalHtml(index) {
    const editorId = `commentaryRichEditor-${index}`;
    return `
        <div id="commentaryModal-${index}" class="modal-overlay">
            <div class="commentary-form-modal commentary-box">
                <h2 class="modal-h2">✏️ ${ANSWER_LETTERS[index]} variant uchun izoh</h2>

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
                    <span class="rich-toolbar-sep"></span>
                    <button type="button" onclick="richExec('${editorId}','removeFormat')" title="Formatni tozalash">🧹</button>
                </div>
                <div id="${editorId}" class="rich-text-editor commentary" contenteditable="true"
                     data-placeholder="To'g'ri javob uchun izoh kiriting..."></div>

                <button type="button" class="btn link-btn hidden">🔗 Darsga havola qo'shish</button>

                <div class="commentary-media">
                    <div class="image-upload" data-role="commentary-image">
                        <input type="file" accept="image/png,image/jpeg,image/webp,image/gif" class="image-input" hidden>
                        <button type="button" class="btn image-btn">🖼️ Rasm</button>
                        <img class="image-preview hidden" alt="Izoh rasmi">
                        <button type="button" class="btn remove-image-btn hidden">✖</button>
                    </div>
                    <div class="video-upload" data-role="commentary-video">
                        <input type="file" accept="video/mp4,video/webm,video/ogg" class="video-input" hidden>
                        <button type="button" class="btn video-btn">🎬 Video</button>
                        <video class="video-preview hidden" controls></video>
                        <button type="button" class="btn remove-video-btn hidden">✖</button>
                    </div>
                </div>

                <div class="modal-footer">
                    <button type="button" class="btn primary" onclick="closeCommentaryModal(${index})">✅ Yopish</button>
                </div>
            </div>
        </div>
    `;
}

function openCommentaryModal(index) {
    const editor = document.getElementById(`commentaryRichEditor-${index}`);
    cleanupEmptyCaptions(`commentaryRichEditor-${index}`);
    document.getElementById(`commentaryModal-${index}`).classList.add("show");
    editor.focus();
}

function closeCommentaryModal(index) {
    cleanupEmptyCaptions(`commentaryRichEditor-${index}`);
    document.getElementById(`commentaryModal-${index}`).classList.remove("show");
}

// ================= Rich-toolbar: asosiy buyruqlar =================

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
// kichraytirish mumkin bo'lishi uchun). Kurs darsi tahrirlagichidan
// FARQLI — bu yerda /api/courses/.../upload-image o'rniga UMUMIY
// /api/question/upload-image endpoint ishlatiladi (bu sahifada COURSE_ID
// yo'q — savol/izoh rasmlari kurslarga bog'liq emas).
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

function startImageResize(e, clientX) {
    if (!e.target.classList || !e.target.classList.contains('rich-img-handle')) return;
    const wrap = e.target.closest('.rich-img-wrap');
    const media = wrap ? wrap.querySelector('img') : null;
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

// ================= "🔗 Darsga havola qo'shish" =================
// Izohga qo'lda <a href="..."> yozish o'rniga — bitta tugma bosilsa,
// joriy savol tegishli bo'lgan darsning kursdagi darsiga to'g'ri
// havola cursor turgan joyga qo'yiladi. Mavzu hech qaysi kurs bo'limiga
// bog'lanmagan bo'lsa (topicCourseLink == null), tugma yashirin qoladi.
// (fetchTopicCourseLink / buildTopicLinkHtml / insertTextAtCursor —
// topicLinkButton.js'da, bu sahifa va savollar jadvali bilan umumiy.)
let topicCourseLink = null;

async function loadTopicCourseLink() {
    const topicId = document.getElementById("topicId").value;
    topicCourseLink = await fetchTopicCourseLink(topicId);
    if (topicCourseLink) {
        document.querySelectorAll(".link-btn").forEach(btn => btn.classList.remove("hidden"));
    }
}

document.addEventListener("click", (e) => {
    if (!e.target.classList.contains("link-btn") || !topicCourseLink) return;

    // Izoh endi oddiy <textarea> emas, contenteditable boy muharrir — shu
    // sabab insertTextAtCursor (topicLinkButton.js, textarea.value-ga
    // asoslangan, question.js bilan HAM umumiy — u yerda hali oddiy
    // textarea) o'rniga execCommand('insertHTML') orqali kursor turgan
    // joyga HAQIQIY <a> tegi qo'yiladi.
    const editor = e.target.closest(".commentary-box")?.querySelector(".rich-text-editor");
    if (!editor) return;

    editor.focus();
    document.execCommand("insertHTML", false, buildTopicLinkHtml(topicCourseLink));
});

// Kurs mavzu kartochkasidagi "➕ Testga savol qo'shish" tugmasidan
// kelinganda — URL'da ?courseId= beriladi. "🔙 Kursga qaytish" tugmasi
// ATAYLAB kursning UMUMIY (mavzular ro'yxati) sahifasiga qaytaradi — aynan
// shu mavzu (dars) ICHIGA EMAS (foydalanuvchi so'rovi bo'yicha: test savoli
// qo'shib bo'lgach, mavzu matnini "o'qish" rejimiga emas, tashqarida —
// kursning o'ziga qaytish kerak).
const testFormUrlParams = new URLSearchParams(window.location.search);
const testFormReturnCourseId = testFormUrlParams.get("courseId");
// Kurs sahifasidagi ANIQ qaysi mavzu kartochkasidan ("➕ Testga savol
// qo'shish") kelingani — courseDetail.js shuni "&fromSectionId=" orqali
// jo'natadi. Qaytishda "?focus=" sifatida beriladi, shunda kurs sahifasi
// o'sha kartani avtomatik ekranga chiqarib, "tanlangan" holatda belgilaydi
// (courseDetail.js#applyFocusFromUrl).
const testFormReturnSectionId = testFormUrlParams.get("fromSectionId");

document.addEventListener("DOMContentLoaded", () => {

    // Faqat kursdan (courseId URL'da bo'lsa) kelinganda ko'rinadi — TEST
    // BOSHQARUVI'dagi "➕ TEST YARATISH" orqali kelinganda butunlay yashirin.
    if (testFormReturnCourseId) {
        const backBtn = document.getElementById("backToCourseBtn");
        backBtn.classList.remove("hidden");
        backBtn.onclick = () => {
            location.href = testFormReturnSectionId
                ? `/courses/${testFormReturnCourseId}?focus=${testFormReturnSectionId}`
                : `/courses/${testFormReturnCourseId}`;
        };
    }

    // "⬅ Orqaga" — TEST BOSHQARUVI'dan (kursdan emas, "➕ TEST YARATISH"
    // orqali) kelinganda ham, kursdan kelinganda ham — mavzuning o'zi
    // qaysi Fan/Bo'limga tegishli ekanini bilib, ANIQ shu bo'lim ko'rinib
    // turgan /topics sahifasiga qaytaradi (oldin history.back() edi —
    // ba'zan foydalanuvchi kutgan joyga emas, tasodifiy oldingi sahifaga
    // olib borardi). Fetch muvaffaqiyatsiz bo'lsa — history.back() eskicha
    // fallback sifatida qoladi (HTML'dagi onclick o'zgarishsiz).
    const backOrqagaBtn = document.getElementById("backBtn");
    if (backOrqagaBtn) {
        const currentTopicId = document.getElementById("topicId").value;
        fetch(`/api/topic/${currentTopicId}/location`)
            .then(r => r.ok ? r.json() : null)
            .then(loc => {
                if (!loc) return;
                backOrqagaBtn.onclick = () => {
                    location.href = loc.sectionId
                        ? `/topics?scienceId=${loc.scienceId}&sectionId=${loc.sectionId}`
                        : `/topics?scienceId=${loc.scienceId}`;
                };
            })
            .catch(err => console.error(err));
    }

    const form = document.getElementById("testForm");

    // 5 ta "Izoh" modalini DINAMIK yaratish — .image-upload/.video-upload
    // wiring'idan OLDIN, shunda ichidagi commentary-image/commentary-video
    // konteynerlari ham quyidagi querySelectorAll'ga tushadi.
    document.getElementById("commentaryModalsContainer").innerHTML =
        ANSWER_LETTERS.map((_, i) => buildCommentaryModalHtml(i)).join("");

    document.querySelectorAll(".image-upload").forEach(setupImageUpload);
    document.querySelectorAll(".video-upload").forEach(setupVideoUpload);
    loadTopicCourseLink();

    // автоподбор высоты textarea
    document.querySelectorAll(".auto-textarea").forEach(t => {
        t.addEventListener("input", () => {
            t.style.height = "auto";
            t.style.height = t.scrollHeight + "px";
        });
    });

    form.addEventListener("submit", async (e) => {
        e.preventDefault();

        const topicId = Number(document.getElementById("topicId").value);
        const questionText = document.getElementById("question").value.trim();

        const answersBlocks = document.querySelectorAll(".answer");
        // Ilgari BITTA radio edi — endi bir nechta checkbox belgilanishi
        // mumkin (ko'p to'g'ri javobli savollar, foydalanuvchi so'rovi,
        // 2026-09-05, 3-bosqich).
        const correctIndexes = [...document.querySelectorAll("input[name='correct']:checked")]
            .map(cb => Number(cb.value));

        if (correctIndexes.length === 0) {
            showAlertModal("❌ Kamida bitta to'g'ri javobni tanlang");
            return;
        }

        // ================= Валидация ответов =================
        const texts = [];

        answersBlocks.forEach((block, index) => {
            const ta = block.querySelector("textarea.auto-textarea");
            const value = ta.value.trim();

            if (!value) {
                showAlertModal("❌ Barcha javoblarni to‘ldiring");
                ta.focus();
                throw new Error("Validation failed");
            }

            texts.push(value.toLowerCase());
        });

        // уникальность
        if (new Set(texts).size !== texts.length) {
            showAlertModal("❌ Javob variantlari bir xil bo‘lishi mumkin emas");
            return;
        }

        // ================= Формирование answers =================

        const answers = [...answersBlocks].map((block, index) => {
            const answerText = block.querySelector("textarea.auto-textarea").value.trim();
            const imageUploadBlock = block.querySelector('.image-upload[data-role="answer-image"]');

            const isCorrect = correctIndexes.includes(index);

            // Izoh endi modalda, boy matn muharririda — .answer bloki
            // ICHIDA emas, umumiy #commentaryModalsContainer ichida, shu
            // sabab `index` bo'yicha alohida qidiriladi (block ichidan
            // emas). Bo'sh (faqat bo'sh joy/teg) izoh — null (avvalgi
            // xatti-harakat: bo'sh izoh saqlanmasin, backend o'zi standart
            // matn qo'yadi — QuestionController#saveQuestion).
            cleanupEmptyCaptions(`commentaryRichEditor-${index}`);
            const editor = document.getElementById(`commentaryRichEditor-${index}`);
            const commentaryHtml = editor.innerHTML.trim();
            const commentaryModal = document.getElementById(`commentaryModal-${index}`);
            const commentaryImageBlock = commentaryModal.querySelector('.image-upload[data-role="commentary-image"]');
            const commentaryVideoBlock = commentaryModal.querySelector('.video-upload[data-role="commentary-video"]');

            return {
                answerText,
                isTrue: isCorrect,
                commentary: isCorrect && editor.textContent.trim()
                    ? commentaryHtml
                    : null,
                imageUrl: imageUploadBlock ? getImageUrl(imageUploadBlock) : null,
                // Izohga (faqat to'g'ri javob(lar)ga) qo'shilgan rasm/video — matn bilan birga.
                commentaryImageUrl: isCorrect && commentaryImageBlock
                    ? getImageUrl(commentaryImageBlock)
                    : null,
                commentaryVideoUrl: isCorrect && commentaryVideoBlock
                    ? getVideoUrl(commentaryVideoBlock)
                    : null
            };
        });

        const questionImageBlock = document.querySelector('.image-upload[data-role="question-image"]');

        // ================= Payload =================
        const payload = {
            topicId,
            questionText,
            imageUrl: questionImageBlock ? getImageUrl(questionImageBlock) : null,
            answers
        };

        console.log("CREATE PAYLOAD:", payload);

        try {
            const res = await fetch("/api/question/save", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });

            if (!res.ok) {
                // Backend dublikat (409, "Bunday javoblarga ega savol
                // allaqachon mavjud.") va boshqa xatoliklarda ANIQ xabar
                // bilan javob beradi ({message, status} — ErrorResponse) —
                // avval bu yerda o'qib tashlanardi-yu, quyida doim bitta
                // umumiy "Saqlashda xatolik" ko'rsatilardi (haqiqiy
                // production bug: foydalanuvchi nima uchun saqlanmaganini
                // — masalan dublikat ekanini — hech qachon bilolmasdi).
                const data = await res.json().catch(() => ({}));
                throw new Error(data.message || data.error || "Saqlashda xatolik");
            }

            showAlertModal("✅ Test muvaffaqiyatli saqlandi");
            form.reset();
            document.querySelectorAll(".image-upload").forEach(resetImageUpload);
            document.querySelectorAll(".video-upload").forEach(resetVideoUpload);
            document.querySelectorAll(".comment-btn").forEach(btn => btn.classList.add("hidden"));
            ANSWER_LETTERS.forEach((_, i) => {
                document.getElementById(`commentaryModal-${i}`)?.classList.remove("show");
                const editor = document.getElementById(`commentaryRichEditor-${i}`);
                if (editor) editor.innerHTML = "";
            });

        } catch (err) {
            console.error(err);
            showAlertModal("❌ " + (err.message || "Saqlashda xatolik"));
        }
    });

});

// Ilgari radio edi — bitta belgilanganda QOLGAN barcha "✏️Izoh"
// tugmalari/qutilari yashirilardi (bir vaqtda faqat BITTASI to'g'ri
// bo'lishi mumkin edi). Endi checkbox — har biri MUSTAQIL: belgilansa
// o'zining "✏️Izoh" tugmasi chiqadi, belgi olib tashlansa faqat O'ZINING
// tugmasi yashiriladi (izoh modali endi .answer ICHIDA emas — ".comment-
// btn" bosilganda openCommentaryModal(index) HTML'dagi onclick orqali
// to'g'ridan-to'g'ri chaqiriladi, alohida document-level delegate shart
// emas), boshqalariga tegilmaydi (ko'p to'g'ri javobli savollar,
// foydalanuvchi so'rovi, 2026-09-05, 3-bosqich).
document.addEventListener("change", (e) => {
    if (e.target.type !== "checkbox" || e.target.name !== "correct") return;

    const answer = e.target.closest(".answer");
    const commentBtn = answer.querySelector(".comment-btn");

    if (e.target.checked) {
        commentBtn?.classList.remove("hidden");
    } else {
        commentBtn?.classList.add("hidden");
    }
});

function importExcel() {
    document.getElementById("excelFile").click();
}

document.getElementById("excelFile").addEventListener("change", async function () {
    const file = this.files[0];
    if (!file) return;

    const topicId = document.getElementById("topicId").value;

    const formData = new FormData();
    formData.append("file", file);
    formData.append("topicId", topicId);

    const res = await fetch("/api/import/excel", {
        method: "POST",
        body: formData
    });

    const data = await res.json();
    showResult(data);
});

function showResult(data) {
    const modal = document.getElementById("importModal");
    const title = document.getElementById("importTitle");
    const body = document.getElementById("importBody");

    if (data.success) {
        title.textContent = "Import successful";
        body.textContent = `Imported: ${data.imported} questions`;
    } else {
        // GlobalRestExceptionHandler kutilmagan xatolikda ImportResultDto
        // emas, oddiy {"error": "..."} shaklida javob qaytarishi mumkin —
        // shu holatda data.errors bo'lmaydi, .join() esa TypeError berib
        // hech narsa ko'rsatmasdi ("hech narsa o'zgarmagandek" tuyulardi).
        title.textContent = "Import errors";
        if (Array.isArray(data.errors) && data.errors.length) {
            const importedCount = typeof data.imported === "number" ? data.imported : 0;
            body.textContent = `Imported: ${importedCount}\n\n` + data.errors.join("\n");
        } else if (data.error) {
            body.textContent = data.error;
        } else {
            body.textContent = "Noma'lum xatolik yuz berdi.";
        }
    }

    modal.classList.remove("hidden");
}

function closeModal() {
    document.getElementById("importModal").classList.add("hidden");
}

function downloadTemplate() {
    window.location.href = "/api/export/template";
}

