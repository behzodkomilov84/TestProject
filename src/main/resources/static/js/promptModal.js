// Brauzerning o'ziga xos prompt()/confirm()/alert() oynalari joylashuvini
// CSS bilan boshqarib bo'lmaydi (ayniqsa mobil brauzerlarda ekran
// markazida emas, tepada yoki boshqa joyda chiqishi mumkin) — shuning
// uchun butun sayt bo'ylab BITTA markazlashtirilgan, ilovaning o'z
// uslubidagi (courses.css'dagi .modal-overlay bilan bir xil g'oyadagi)
// modal oynalari bilan almashtirildi (foydalanuvchi so'rovi bo'yicha).
// Chaqiruv joyi deyarli o'zgarmaydi — faqat sinxron prompt()/confirm()
// o'rniga "await showPromptModal(...)"/"await showConfirmModal(...)"
// ishlatiladi; alert() esa "showAlertModal(...)" bilan (await SHART
// EMAS — chaqiruvchi funksiya async bo'lmasa ham ishlaydi, chunki
// alert xabari keyingi kodning davom etishiga xalaqit bermaydi, xuddi
// asl alert() dan farqli — bu ataylab shunday, minglab chaqiruv joyini
// "async" qilib chiqishning oldini olish uchun).
// Hech qanday tashqi CSS/HTML'ga bog'liq emas (o'z uslubini o'zi bir
// martalik <style> sifatida qo'shadi) — shuning uchun shunchaki
// <script src="/js/promptModal.js"> qo'shish yetarli, boshqa sozlash shart emas.

let promptModalStylesInjected = false;

function injectPromptModalStyles() {
    if (promptModalStylesInjected) return;
    promptModalStylesInjected = true;

    const style = document.createElement("style");
    style.textContent = `
        .prompt-modal-overlay {
            position: fixed; inset: 0; background: rgba(15, 23, 42, .55);
            backdrop-filter: blur(2px); display: flex; align-items: center;
            justify-content: center; z-index: 10000; padding: 16px;
            animation: promptModalFadeIn .15s ease;
        }
        @keyframes promptModalFadeIn { from { opacity: 0; } to { opacity: 1; } }
        .prompt-modal-box {
            width: min(420px, 100%); background: #fff; padding: 24px 26px;
            border-radius: 16px; box-shadow: 0 25px 60px rgba(15, 23, 42, .35);
            display: flex; flex-direction: column; gap: 14px;
            font-family: inherit; box-sizing: border-box;
            animation: promptModalPopIn .18s cubic-bezier(.2, .9, .3, 1.2);
        }
        @keyframes promptModalPopIn {
            from { opacity: 0; transform: translateY(10px) scale(.97); }
            to { opacity: 1; transform: translateY(0) scale(1); }
        }
        .prompt-modal-message {
            margin: 0; font-size: 15px; color: #1e293b; white-space: pre-wrap;
        }
        .prompt-modal-input {
            width: 100%; box-sizing: border-box; padding: 10px 12px; font-size: 15px;
            border: 1px solid #cbd5e1; border-radius: 8px; outline: none;
            font-family: inherit;
        }
        .prompt-modal-input:focus {
            border-color: #2563eb; box-shadow: 0 0 0 3px rgba(37, 99, 235, .15);
        }
        .prompt-modal-actions { display: flex; justify-content: flex-end; gap: 10px; }
        .prompt-modal-actions button {
            padding: 8px 18px; border-radius: 8px; border: none; font-size: 14px;
            cursor: pointer; font-weight: 600; font-family: inherit;
        }
        .prompt-modal-cancel { background: #f1f5f9; color: #334155; }
        .prompt-modal-cancel:hover { background: #e2e8f0; }
        .prompt-modal-ok { background: #2563eb; color: #fff; }
        .prompt-modal-ok:hover { background: #1d4ed8; }
        .prompt-modal-ok.danger { background: #dc2626; }
        .prompt-modal-ok.danger:hover { background: #b91c1c; }
    `;
    document.head.appendChild(style);
}

// Umumiy skelet — showPromptModal/showConfirmModal/showAlertModal
// uchastasi bir xil (overlay + box + xabar + tugmalar), farqi faqat
// input maydoni bor-yo'qligida va tugmalar sonida. "input: true" bo'lsa
// matn maydoni qo'shiladi (showPromptModal), aks holda faqat xabar.
function buildModal(message, { input, cancelText, okText, danger } = {}) {
    injectPromptModalStyles();

    const overlay = document.createElement("div");
    overlay.className = "prompt-modal-overlay";

    const box = document.createElement("div");
    box.className = "prompt-modal-box";

    const messageEl = document.createElement("p");
    messageEl.className = "prompt-modal-message";
    messageEl.textContent = message;
    box.appendChild(messageEl);

    let inputEl = null;
    if (input) {
        inputEl = document.createElement("input");
        inputEl.type = "text";
        inputEl.className = "prompt-modal-input";
        inputEl.value = input.defaultValue || "";
        box.appendChild(inputEl);
    }

    const actions = document.createElement("div");
    actions.className = "prompt-modal-actions";

    let cancelBtn = null;
    if (cancelText != null) {
        cancelBtn = document.createElement("button");
        cancelBtn.type = "button";
        cancelBtn.className = "prompt-modal-cancel";
        cancelBtn.textContent = cancelText;
        actions.appendChild(cancelBtn);
    }

    const okBtn = document.createElement("button");
    okBtn.type = "button";
    okBtn.className = "prompt-modal-ok" + (danger ? " danger" : "");
    okBtn.textContent = okText;
    actions.appendChild(okBtn);

    box.appendChild(actions);
    overlay.appendChild(box);
    document.body.appendChild(overlay);

    return { overlay, box, inputEl, cancelBtn, okBtn };
}

// window.prompt() bilan BIR XIL imzo: (matn, standart qiymat) -> natija.
// Farqi — sinxron string emas, Promise<string|null> qaytaradi (shuning
// uchun chaqiruvchi funksiya "async" bo'lishi va "await" bilan chaqirishi
// kerak). Natija: kiritilgan matn (OK/Enter bosilsa) yoki null (Bekor
// qilish/Escape/fon bosilsa) — xuddi prompt() singari.
function showPromptModal(message, defaultValue = "") {
    return new Promise((resolve) => {
        const { overlay, inputEl, cancelBtn, okBtn } = buildModal(message, {
            input: { defaultValue },
            cancelText: "Bekor qilish",
            okText: "OK"
        });

        let settled = false;
        function close(result) {
            if (settled) return;
            settled = true;
            document.removeEventListener("keydown", onKeyDown);
            overlay.remove();
            resolve(result);
        }

        function onKeyDown(e) {
            if (e.key === "Escape") close(null);
            if (e.key === "Enter") close(inputEl.value);
        }

        cancelBtn.onclick = () => close(null);
        okBtn.onclick = () => close(inputEl.value);
        // Faqat aynan fonning o'ziga (overlay) bosilganda yopiladi — box
        // ichidagi bosishlar bubble bo'lib overlay'gacha yetib kelmaydi,
        // chunki box overlay'ning o'z ichida (target === overlay tekshiruvi
        // shuni ta'minlaydi).
        overlay.onclick = (e) => { if (e.target === overlay) close(null); };
        document.addEventListener("keydown", onKeyDown);

        inputEl.focus();
        inputEl.select();
    });
}

// window.confirm() bilan BIR XIL maqsad: (matn) -> Promise<boolean> (true —
// OK/Enter, false — Bekor qilish/Escape/fon bosilsa). "options.danger"
// true bo'lsa OK tugmasi qizil (o'chirish kabi qaytarib bo'lmas amallar
// uchun). Chaqiruvchi funksiya "async" bo'lishi va "await" bilan
// chaqirishi kerak (showPromptModal bilan bir xil sabab).
function showConfirmModal(message, options = {}) {
    return new Promise((resolve) => {
        const { overlay, cancelBtn, okBtn } = buildModal(message, {
            cancelText: options.cancelText || "Bekor qilish",
            okText: options.okText || "Ha",
            danger: !!options.danger
        });

        let settled = false;
        function close(result) {
            if (settled) return;
            settled = true;
            document.removeEventListener("keydown", onKeyDown);
            overlay.remove();
            resolve(result);
        }

        function onKeyDown(e) {
            if (e.key === "Escape") close(false);
            if (e.key === "Enter") close(true);
        }

        cancelBtn.onclick = () => close(false);
        okBtn.onclick = () => close(true);
        overlay.onclick = (e) => { if (e.target === overlay) close(false); };
        document.addEventListener("keydown", onKeyDown);

        okBtn.focus();
    });
}

// window.alert() bilan BIR XIL maqsad: (matn) -> xabar oynasini ko'rsatadi
// va "OK" bosilguncha (yoki Enter/Escape) ochiq turadi. DIQQAT: asl
// alert()dan farqli, KEYINGI KODNI TO'XTATMAYDI (await SHART EMAS) —
// ataylab shunday, chaqiruvchi funksiyani "async" qilib chiqishning
// oldini olish uchun (bu funksiya odatda xabar berish uchun, keyingi
// mantiq unga bog'liq bo'lmaydi). Agar chindan ham "OK bosilguncha
// kutish" kerak bo'lsa — "await showAlertModal(...)" ham ishlaydi,
// chunki Promise baribir qaytariladi.
function showAlertModal(message) {
    return new Promise((resolve) => {
        const { overlay, okBtn } = buildModal(message, { okText: "OK" });

        let settled = false;
        function close() {
            if (settled) return;
            settled = true;
            document.removeEventListener("keydown", onKeyDown);
            overlay.remove();
            resolve();
        }

        function onKeyDown(e) {
            if (e.key === "Escape" || e.key === "Enter") close();
        }

        okBtn.onclick = close;
        overlay.onclick = (e) => { if (e.target === overlay) close(); };
        document.addEventListener("keydown", onKeyDown);

        okBtn.focus();
    });
}
