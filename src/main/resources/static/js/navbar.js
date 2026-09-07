document.addEventListener("DOMContentLoaded", () => {
    const roleB = document.querySelector(".nav-center b[data-role]");
    if (!roleB) return;

    // Dual-role: foydalanuvchida bir nechta rol bo'lishi mumkin
    // (masalan "OWNER,ADMIN"), shuning uchun eng "yuqori" rolga qarab rang tanlanadi.
    const roles = roleB.dataset.role.split(",").map(r => r.trim());

    if (roles.includes("OWNER")) {
        roleB.style.color = "#b71c1c"; // красный
    } else if (roles.includes("ADMIN")) {
        roleB.style.color = "#856404"; // золотой
    } else if (roles.includes("USER")) {
        roleB.style.color = "#1b5e20"; // зелёный
    }
});

function toggleMenu() {
    document.getElementById("nav-menu").classList.toggle("active");
}

document.querySelectorAll(".nav-items a").forEach(a =>
    a.addEventListener("click", () =>
        document.getElementById("nav-menu").classList.remove("active")
    )
);

/* закрытие при клике вне меню */
document.addEventListener("click", e => {
    const menu = document.getElementById("nav-menu");
    const burger = document.querySelector(".burger");

    if (!menu.contains(e.target) && !burger.contains(e.target)) {
        menu.classList.remove("active");
    }
});

document.querySelectorAll(".dropbtn").forEach(btn => {
    btn.addEventListener("click", () => {
        btn.parentElement.classList.toggle("active");
    });
});

/* ===== Til tanlash (Google Translate) =====
   Google'ning o'zi chizadigan (katta) select o'rniga, navbar'dagi kichik
   🌐 dropdown ishlatiladi, lekin OSTIDA baribir Google'ning haqiqiy
   tarjima motori (<select class="goog-te-combo">, googleTranslateElementInit
   — navbar.html) ishlaydi.

   MUHIM: faqat "googtrans" cookie o'rnatib sahifani qayta yuklash
   YETARLI EMAS EDI — Google'ning widget'i har doim ham cookie'ni o'qib
   avtomatik tarjima qilmaydi (ayniqsa birinchi marta). Eng ishonchli
   usul — widget skripti allaqachon yuklangan bo'lsa, uning o'z ichki
   <select class="goog-te-combo"> elementini TO'G'RIDAN-TO'G'RI
   boshqarib (qiymatini o'zgartirib, "change" hodisasini yuborib),
   Google'ning tarjima funksiyasini o'zini chaqirtirish — bu darhol
   ishlaydi, sahifani qayta yuklash ham shart emas. Skript hali
   ulgurmagan bo'lsa (juda tez bosilsa) — cookie + reload'ga tayaniladi
   (skript yuklangach cookie'ni o'qib, avtomatik tarjima qiladi).

   Manba tili har doim "auto" qilib beriladi ("uz" emas) — kurs matnlari
   ko'pincha boshqa tildan (masalan ruscha PDF/Word'dan) copy-paste
   qilib joylashtiriladi, shuning uchun sahifada aralash til bo'lishi
   mumkin; "auto" bilan Google har bir matn bo'lagining haqiqiy tilini
   alohida aniqlab, tanlangan tilga tarjima qiladi.

   "uz-cyrl" (Ўзбекча, кирилл) — Google Translate'ning o'zida bunday
   maqsad til yo'q (faqat lotin "uz"). Shuning uchun avval oddiy
   lotincha o'zbekchaga tarjima qilinadi, so'ng natija JS orqali
   (transliterateToCyrillic) kirillga o'giriladi — bu tarjima emas,
   oddiy harf almashtirish (lotin-kirill orasida so'zma-so'z, tartibli
   moslik bor). Tarjima async (natija birozdan keyin DOM'ga qo'shiladi),
   shuning uchun kirillashtirish biroz kutib (setTimeout) ishga
   tushiriladi — combo orqali darhol o'zgartirilganda shu yerning
   o'zida, reload orqali bo'lsa DOMContentLoaded'da (pastda). */
function setSiteLanguage(lang) {
    const host = location.hostname;
    const targetLang = lang === 'uz-cyrl' ? 'uz' : lang;

    function clearCookie(name) {
        document.cookie = name + "=; path=/; expires=Thu, 01 Jan 1970 00:00:00 UTC";
        document.cookie = name + "=; domain=." + host + "; path=/; expires=Thu, 01 Jan 1970 00:00:00 UTC";
    }

    clearCookie("googtrans");
    document.cookie = "googtrans=/auto/" + targetLang + "; path=/";
    document.cookie = "googtrans=/auto/" + targetLang + "; domain=." + host + "; path=/";

    if (lang === 'uz-cyrl') {
        sessionStorage.setItem('pendingCyrillicTransliteration', '1');
    } else {
        sessionStorage.removeItem('pendingCyrillicTransliteration');
    }

    const combo = document.querySelector('#google_translate_element select.goog-te-combo');
    if (combo) {
        combo.value = targetLang;
        combo.dispatchEvent(new Event('change'));
        if (lang === 'uz-cyrl') {
            setTimeout(() => {
                transliteratePageToCyrillic();
                sessionStorage.removeItem('pendingCyrillicTransliteration');
            }, 1200);
        }
        return;
    }

    location.reload();
}

// Sahifa qayta yuklangandan keyin (combo hali skript yuklanmagani
// sabab topilmagan holatda) — kirill navbatda qolgan bo'lsa, Google
// tarjimasi tugashiga biroz vaqt berib, keyin qo'llaniladi.
document.addEventListener('DOMContentLoaded', () => {
    if (sessionStorage.getItem('pendingCyrillicTransliteration') === '1') {
        setTimeout(() => {
            transliteratePageToCyrillic();
            sessionStorage.removeItem('pendingCyrillicTransliteration');
        }, 1500);
    }
});

/* ===== Lotin -> Kirill transliteratsiya (o'zbekcha) =====
   Bu TARJIMA emas — ikki alifbo orasida deyarli bir-biriga to'g'ridan-
   to'g'ri mos keladigan, standart almashtirish jadvali (ko'p harfli
   birikmalar — "sh","ch","yo','yu","ya","o'","g'" — eng uzunidan
   boshlab, keyin bitta harflar tekshiriladi). */
const CYRILLIC_MAP = {
    "o'": "ў", "oʻ": "ў", "o‘": "ў",
    "g'": "ғ", "gʻ": "ғ", "g‘": "ғ",
    "sh": "ш", "ch": "ч", "yo": "ё", "yu": "ю", "ya": "я", "ts": "ц",
    "a": "а", "b": "б", "d": "д", "e": "е", "f": "ф", "g": "г", "h": "ҳ",
    "i": "и", "j": "ж", "k": "к", "l": "л", "m": "м", "n": "н", "o": "о",
    "p": "п", "q": "қ", "r": "р", "s": "с", "t": "т", "u": "у", "v": "в",
    "x": "х", "y": "й", "z": "з", "'": "ъ", "’": "ъ"
};
const CYRILLIC_KEYS = Object.keys(CYRILLIC_MAP).sort((a, b) => b.length - a.length);
const CYRILLIC_REGEX = new RegExp(CYRILLIC_KEYS.map(k => k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')).join('|'), 'gi');

function transliterateToCyrillic(text) {
    return text.replace(CYRILLIC_REGEX, (match) => {
        const cyr = CYRILLIC_MAP[match.toLowerCase()];
        if (!cyr) return match;
        if (match === match.toUpperCase() && match !== match.toLowerCase()) {
            return cyr.toUpperCase();
        }
        if (match[0] === match[0].toUpperCase() && match[0] !== match[0].toLowerCase()) {
            return cyr[0].toUpperCase() + cyr.slice(1);
        }
        return cyr;
    });
}

// Sahifadagi barcha ko'rinadigan matn tugunlarini (script/style/forma
// maydonlari va Google Translate'ning o'z yashirin konteyneri bundan
// mustasno) kirillga o'giradi.
function transliteratePageToCyrillic(root) {
    root = root || document.body;
    const SKIP_TAGS = new Set(['SCRIPT', 'STYLE', 'INPUT', 'TEXTAREA', 'SELECT']);

    const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT, {
        acceptNode(node) {
            const parent = node.parentElement;
            if (!parent || SKIP_TAGS.has(parent.tagName)) return NodeFilter.FILTER_REJECT;
            if (parent.closest('#google_translate_element')) return NodeFilter.FILTER_REJECT;
            if (!node.nodeValue || !node.nodeValue.trim()) return NodeFilter.FILTER_REJECT;
            return NodeFilter.FILTER_ACCEPT;
        }
    });

    const nodes = [];
    let n;
    while ((n = walker.nextNode())) nodes.push(n);
    nodes.forEach(node => {
        node.nodeValue = transliterateToCyrillic(node.nodeValue);
    });
}

// Ilgari faqat "/link 123456" matnini ko'rsatib qo'yardi — botning
// NOMINI bilmagan foydalanuvchi nima qilishni bilmasdi (haqiqiy
// foydalanuvchi shikoyati, 2026-09-08). Endi: (1) kod bilan birga
// bot @username'i ham olinadi (/api/telegram/link javobi kengaytirildi),
// (2) Telegram deep-link (https://t.me/<bot>?start=link_<kod>) darhol
// yangi oynada ochiladi — foydalanuvchi bu sahifadan TO'G'RIDAN-TO'G'RI
// botga o'tadi, (3) botda "START" tugmasini bosishi bilan "/link <kod>"
// AVTOMATIK yuboriladi (qo'lda yozish shart emas, TelegramBot#route
// "/start link_<kod>"ni shu tarzda ishlaydi), (4) shu bilan birga to'liq
// bosqichma-bosqich yo'llanma va zaxira (qo'lda yozish) usuli ham modalda
// ko'rsatiladi — agar avtomatik oyna ochilishi brauzer tomonidan
// blocklansa yoki foydalanuvchi uni tasodifan yopib qo'ysa ham.
async function linkTelegram() {

    try {
        const res = await fetch("/api/telegram/link", {
            method: "POST"
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok || !data.code || !data.botUsername) {
            showAlertModal(data.error || "❌ Kod olishda xatolik yuz berdi. Qayta urinib ko'ring.");
            return;
        }

        const deepLink = "https://t.me/" + data.botUsername + "?start=link_" + data.code;
        showTelegramLinkModal(data.botUsername, data.code, deepLink);

        // Avtomatik ochish — foydalanuvchi qo'shimcha bosishi shart emas.
        // Ba'zi brauzerlar popup'ni blocklashi mumkin, shu sabab modaldagi
        // "🤖 Botga o'tish" tugmasi zaxira sifatida qoladi.
        window.open(deepLink, "_blank");
    } catch (err) {
        console.error(err);
        showAlertModal("❌ Tarmoq xatoligi — qayta urinib ko'ring.");
    }
}

let telegramLinkStylesInjected = false;

function injectTelegramLinkStyles() {
    if (telegramLinkStylesInjected) return;
    telegramLinkStylesInjected = true;

    const style = document.createElement("style");
    style.textContent = `
        .tg-link-overlay {
            position: fixed; inset: 0; background: rgba(15, 23, 42, .6);
            backdrop-filter: blur(2px); display: flex; align-items: center;
            justify-content: center; z-index: 20000; padding: 16px;
            font-family: 'Segoe UI', system-ui, sans-serif;
        }
        .tg-link-box {
            width: min(420px, 100%); background: #fff; padding: 26px 24px;
            border-radius: 16px; box-shadow: 0 25px 60px rgba(15, 23, 42, .35);
            box-sizing: border-box;
        }
        .tg-link-box h2 { margin: 0 0 6px; font-size: 18px; color: #0d3b34; }
        .tg-link-box .tg-link-bot-name {
            margin: 0 0 16px; font-size: 13.5px; color: #64748b;
        }
        .tg-link-go-btn {
            display: block; text-align: center; text-decoration: none;
            background: #29b6f6; color: #fff; font-weight: 700;
            padding: 12px; border-radius: 10px; margin-bottom: 18px;
            font-size: 15px; transition: background .15s ease;
        }
        .tg-link-go-btn:hover { background: #1e9ede; color: #fff; }
        .tg-link-steps { margin: 0 0 16px; padding-left: 20px; font-size: 13.5px; color: #334155; line-height: 1.6; }
        .tg-link-steps li { margin-bottom: 4px; }
        .tg-link-fallback {
            font-size: 12.5px; color: #7c8797; background: #f8fafc;
            border-radius: 8px; padding: 10px 12px; margin-bottom: 18px; line-height: 1.5;
        }
        .tg-link-fallback code {
            background: #e2e8f0; padding: 1px 5px; border-radius: 4px; font-weight: 600;
        }
        .tg-link-close-btn {
            width: 100%; padding: 10px; border-radius: 8px; border: none;
            background: #f1f5f9; color: #334155; font-weight: 600; cursor: pointer;
        }
        .tg-link-close-btn:hover { background: #e2e8f0; }
    `;
    document.head.appendChild(style);
}

function showTelegramLinkModal(botUsername, code, deepLink) {
    injectTelegramLinkStyles();

    const overlay = document.createElement("div");
    overlay.className = "tg-link-overlay";
    overlay.innerHTML = `
        <div class="tg-link-box">
            <h2>🤖 Telegramga ulanish</h2>
            <p class="tg-link-bot-name">Bot: <b>@${botUsername}</b></p>
            <a class="tg-link-go-btn" href="${deepLink}" target="_blank" rel="noopener">🤖 Botga o'tish</a>
            <ol class="tg-link-steps">
                <li>Yangi oyna/ilova ochiladi — Telegram botning suhbat oynasi ko'rinadi.</li>
                <li>Pastda chiqqan <b>START</b> tugmasini bosing.</li>
                <li>Hisobingiz <b>avtomatik</b> bog'lanadi — bot tasdiqlash xabarini yuboradi.</li>
                <li>Shu sahifaga qaytib, uni yangilang — Telegram ulanganini ko'rasiz.</li>
            </ol>
            <p class="tg-link-fallback">Agar avtomatik ishlamasa: Telegram'da <b>@${botUsername}</b> botini toping,
                suhbatni boshlang va xabar oynasiga qo'lda <code>/link ${code}</code> deb yozib yuboring
                (kod 5 daqiqa amal qiladi).</p>
            <button type="button" class="tg-link-close-btn" id="tgLinkCloseBtn">Yopish</button>
        </div>
    `;
    document.body.appendChild(overlay);

    document.getElementById("tgLinkCloseBtn").addEventListener("click", () => overlay.remove());
    overlay.addEventListener("click", (e) => {
        if (e.target === overlay) overlay.remove();
    });
}

/* ===== Bildirishnomalar (notification center) =====
   Endi alohida ochiladigan panel yo'q — "🔔 Bildirishnomalar" Profil
   menyusi ichidagi oddiy matnli havola (/notifications), yonida faqat
   o'qilmagan sonini ko'rsatuvchi belgi (badge) yangilanib turadi.
   To'liq ro'yxat, tab'lar (Yangi/O'qilgan) va statistika — notifications.js. */

function refreshUnreadCount() {
    fetch("/api/notifications/unread-count")
        .then(r => r.ok ? r.json() : { count: 0 })
        .then(data => {
            const badge = document.getElementById("notif-badge");
            if (badge) {
                if (data.count > 0) {
                    badge.style.display = "inline-flex";
                    badge.textContent = data.count > 99 ? "99+" : data.count;
                } else {
                    badge.style.display = "none";
                }
            }
        })
        .catch(err => console.error(err));
}

document.addEventListener("DOMContentLoaded", () => {
    if (document.getElementById("notif-badge")) {
        refreshUnreadCount();
        setInterval(refreshUnreadCount, 30000);
    }
});


