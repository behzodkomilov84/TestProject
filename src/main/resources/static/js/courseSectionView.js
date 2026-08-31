let youTubePlayer = null;
let sectionData = null;
let externalTimerId = null;

// YouTube pleyeri (YT.Player) "videoId" parametri sifatida FAQAT xom
// ID'ni (masalan "dQw4w9WgXcQ") qabul qiladi — to'liq URL bersa, hech
// narsa yuklamay qora ekran qoladi. Bazada ba'zi eski yozuvlarda
// (o'qituvchi to'liq havolani joylashtirgan bo'lsa) to'liq URL saqlanib
// qolgan bo'lishi mumkin — shu sabab ko'rsatishdan oldin har doim shu
// funksiya orqali xom ID'ga o'giramiz (agar allaqachon xom ID bo'lsa,
// o'zgarishsiz qaytadi).
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

// "?focus=" — kurs sahifasiga qaytganda ANIQ shu mavzu kartochkasini
// avtomatik ekranga chiqarib, "tanlangan" holatda belgilash uchun
// (courseDetail.js#applyFocusFromUrl) — test-form.js'dagi "🔙 Kursga
// qaytish" bilan bir xil andoza.
document.getElementById("backToCourseBtn").onclick = () => {
    location.href = `/courses/${COURSE_ID}?focus=${SECTION_ID}`;
};

// searchNavContext — bir marta, sahifa yuklanganda o'qiladi (loadSearchNavContext),
// keyin HAM "Oldingi/Keyingi natija" paneli (setupSearchNav), HAM mavzu
// matni ichidagi qidiruv so'zini fonini o'zgartirish (highlightSearchQuery,
// renderSection() oxirida) shundan foydalanadi — ikkalasi ham AYNAN bir
// xil "joriy sahifa qidiruv natijasiga mosmi" tekshiruviga tayanadi.
let searchNavContext = null;

document.addEventListener("DOMContentLoaded", () => {
    searchNavContext = loadSearchNavContext();
    loadSection();
    setupSearchNav();
});

// ========================================================================
//     Qidiruv natijalari orasida navigatsiya
// ========================================================================
// topic.js / courseDetail.js'dagi "kurs ichidan mavzu yoritmasi bo'yicha
// qidiruv" natijasiga bosilganda, BUTUN natijalar ro'yxati + bosilgan
// natijaning indeksi + qidirilgan so'z + qidirilgan asl sahifa manzili
// sessionStorage'ga saqlanadi. Shu yerda o'sha ma'lumot o'qib, agar u
// AYNAN joriy (COURSE_ID, SECTION_ID) bilan mos kelsa — "Oldingi/Keyingi
// natija" paneli ko'rsatiladi VA mavzu matni ichida qidirilgan so'z
// topilib, foni o'zgartiriladi. Mos kelmasa (masalan foydalanuvchi oddiy
// "Keyingi mavzu →" tugmasi orqali boshqa bo'limga o'tgan bo'lsa) —
// ikkalasi ham o'chiq qoladi, alohida "tozalash" kodi shart emas.
const EXPLANATION_SEARCH_NAV_KEY = "explanationSearchNav";

function loadSearchNavContext() {
    let nav;
    try {
        nav = JSON.parse(sessionStorage.getItem(EXPLANATION_SEARCH_NAV_KEY) || "null");
    } catch (e) {
        return null;
    }
    if (!nav || !Array.isArray(nav.results) || !nav.results.length) return null;

    const current = nav.results[nav.index];
    if (!current || Number(current.courseId) !== Number(COURSE_ID) || Number(current.sectionId) !== Number(SECTION_ID)) {
        return null;
    }
    return nav;
}

function setupSearchNav() {
    const bar = document.getElementById("searchNavBar");
    if (!bar) return;

    const nav = searchNavContext;
    if (!nav) {
        bar.classList.add("hidden");
        return;
    }

    bar.classList.remove("hidden");
    document.getElementById("searchNavPosition").textContent = `${nav.index + 1} / ${nav.results.length}`;

    document.getElementById("searchNavBackBtn").onclick = () => {
        location.href = nav.returnUrl || ("/courses/" + COURSE_ID);
    };

    const prevBtn = document.getElementById("searchNavPrevBtn");
    prevBtn.disabled = nav.index <= 0;
    prevBtn.onclick = () => goToSearchResult(nav, nav.index - 1);

    const nextBtn = document.getElementById("searchNavNextBtn");
    nextBtn.disabled = nav.index >= nav.results.length - 1;
    nextBtn.onclick = () => goToSearchResult(nav, nav.index + 1);
}

function goToSearchResult(nav, newIndex) {
    const target = nav.results[newIndex];
    if (!target) return;
    nav.index = newIndex;
    sessionStorage.setItem(EXPLANATION_SEARCH_NAV_KEY, JSON.stringify(nav));
    location.href = `/courses/${target.courseId}/sections/${target.sectionId}`;
}

function loadSection() {
    fetch(`/api/courses/${COURSE_ID}/sections/${SECTION_ID}`)
        .then(r => {
            if (!r.ok) {
                return r.json().then(data => { throw new Error(data.error || "Mavzu topilmadi yoki hali ochilmagan"); });
            }
            return r.json();
        })
        .then(renderSection)
        .catch(err => {
            document.getElementById("sectionTitle").textContent = "Xatolik";
            document.getElementById("sectionContent").textContent = err.message;
        });
}

// Matn ichiga Instagram posti/reels qo'yilgan bo'lishi mumkin (courseDetail.js,
// insertVideoEmbedHtml) — bu oddiy <iframe> emas, Instagram'ning rasmiy
// blockquote+embed.js usuli, shuning uchun o'qish sahifasida ham xuddi
// shu skript yuklanib, process() chaqirilishi kerak, aks holda bo'sh
// blockquote ko'rinib qoladi.
let instagramEmbedScriptState = "idle"; // idle | loading | loaded

function ensureInstagramEmbedProcessed() {
    if (!document.querySelector(".instagram-media")) return; // Kontentda Instagram embed yo'q — shart emas.
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

function renderSection(data) {
    sectionData = data;

    document.getElementById("sectionTitle").textContent = data.title;

    const content = document.getElementById("sectionContent");
    // HTML — .docx'dan mammoth.js orqali import qilingan, formatlash
    // (abzatslar, qalin/kursiv, sarlavhalar, ro'yxatlar) saqlangan kontent —
    // o'zgartirmasdan ko'rsatiladi. PLAIN (eski/qo'lda yozilgan) — xavfsiz
    // escape qilinib, http(s) havolalar bosiladigan qilinadi (linkify).
    content.classList.toggle("html-content", data.textContentFormat === "HTML");

    if (data.type === "TEXT") {
        content.innerHTML = renderTextContent(data);
        // Matn bo'lim — ochilgan zahoti "tugatilgan" deb belgilanadi.
        markCompleted();
    } else if (data.type === "VIDEO") {
        content.innerHTML = buildVideoEmbed(data);
        setupVideoCompletionTracking(data);
    } else {
        // MIXED — matn va video birga ko'rsatiladi, lekin "tugatish" faqat
        // video oxirigacha ko'rilganda (matn kabi darhol emas — video
        // ko'rilishini majburlash uchun).
        content.innerHTML =
            `<div class="section-text-block">${renderTextContent(data)}</div>` +
            buildVideoEmbed(data);
        setupVideoCompletionTracking(data);
    }

    ensureInstagramEmbedProcessed();
    renderTopicTestLink(data);
    updatePrevButton(data);
    updateNextButton(data);

    // Shu sahifaga "kurs ichidan mavzu yoritmasi bo'yicha qidiruv"
    // natijasidan kelingan bo'lsa (searchNavContext) — qidirilgan so'zni
    // mavzu matni ICHIDA topib, fonini o'zgartiramiz (topish oson bo'lishi
    // uchun). content.innerHTML ALLAQACHON to'ldirilgandan KEYIN
    // chaqirilishi shart — aks holda hali bo'sh div ichida qidirardi.
    if (searchNavContext && searchNavContext.query) {
        highlightSearchQuery(content, searchNavContext.query);
    }
}

// Berilgan konteyner ICHIDAGI matn (text node)larda "query" so'zini
// (katta-kichik harfga sezgirmas, HAR BIR uchragan joyda) topib,
// <mark class="search-highlight-mark"> bilan o'raydi — HTML teglariga
// (masalan <img alt="...">) TEGMAYDI, chunki faqat TEXT node'lar bo'ylab
// yuriladi (TreeWalker). Birinchi topilgan joyga avtomatik skroll qiladi.
function highlightSearchQuery(container, query) {
    const trimmed = (query || "").trim();
    if (!trimmed) return;

    const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, {
        acceptNode: (node) => {
            // <script>/<style> ichidagi matnga tegilmaydi (bu kontentda
            // odatda bo'lmaydi, lekin xavfsizlik uchun).
            const tag = node.parentElement ? node.parentElement.tagName : "";
            return (tag === "SCRIPT" || tag === "STYLE")
                ? NodeFilter.FILTER_REJECT
                : NodeFilter.FILTER_ACCEPT;
        }
    });

    const textNodes = [];
    let node;
    while ((node = walker.nextNode())) {
        textNodes.push(node);
    }

    const lowerQuery = trimmed.toLowerCase();
    let firstMark = null;

    textNodes.forEach((textNode) => {
        const text = textNode.nodeValue;
        const lowerText = text.toLowerCase();
        let idx = lowerText.indexOf(lowerQuery);
        if (idx === -1) return;

        const frag = document.createDocumentFragment();
        let lastEnd = 0;
        while (idx !== -1) {
            if (idx > lastEnd) {
                frag.appendChild(document.createTextNode(text.slice(lastEnd, idx)));
            }
            const mark = document.createElement("mark");
            mark.className = "search-highlight-mark";
            mark.textContent = text.slice(idx, idx + trimmed.length);
            frag.appendChild(mark);
            if (!firstMark) firstMark = mark;
            lastEnd = idx + trimmed.length;
            idx = lowerText.indexOf(lowerQuery, lastEnd);
        }
        if (lastEnd < text.length) {
            frag.appendChild(document.createTextNode(text.slice(lastEnd)));
        }
        textNode.parentNode.replaceChild(frag, textNode);
    });

    if (firstMark) {
        firstMark.scrollIntoView({ behavior: "smooth", block: "center" });
    }
}

// Oldingi mavzuga qaytish — ketma-ket ochilish tartibida oldingi bo'lim
// har doim ko'rish uchun ochiq bo'ladi (foydalanuvchi shu bo'limga
// yetib kelgan bo'lsa, undan oldingisini allaqachon ko'rgan/tugatgan),
// shuning uchun qulflash tekshiruvi shart emas — nextSectionBtn'dan farqli.
function updatePrevButton(data) {
    const btn = document.getElementById("prevSectionBtn");

    if (!data.prevSectionId) {
        btn.disabled = true;
        return;
    }

    btn.disabled = false;
    btn.onclick = () => {
        location.href = `/courses/${COURSE_ID}/sections/${data.prevSectionId}`;
    };
}

// Faqat shu mavzuga bog'langan bo'limlarda — saytning haqiqiy test
// tizimiga (/testConfigPage) shu fan/mavzu avtomatik tanlangan holda
// o'tkazuvchi tugma. DOM API orqali yaratiladi (innerHTML emas) —
// xavfsizroq va bu yerda dinamik qism faqat butun son (topicId).
function renderTopicTestLink(data) {
    const container = document.getElementById("topicTestLink");
    container.innerHTML = "";

    if (!data.linkedTopicId) return;

    const btn = document.createElement("button");
    btn.textContent = "🎯 Mavzuga oid testlarni yechish";
    btn.className = "topic-test-btn";
    btn.onclick = () => {
        // "courseId" — testConfigPage'da "🔙 Mavzularga qaytish" tugmasini
        // ko'rsatish uchun (foydalanuvchi testni boshlamasdan, brauzer
        // "orqaga"siga tayanmasdan, kursga qaytishi uchun).
        const params = new URLSearchParams({
            scienceId: data.linkedScienceId,
            topicId: data.linkedTopicId,
            courseId: COURSE_ID,
            // Test sessiyasi TUGAGUNCHA ham "🔙 Mavzuga qaytish" tugmasi
            // ko'rinib turishi uchun (testConfigPage.js -> testSession.js,
            // sessionStorage orqali) — aynan SHU darsga (bo'lim emas)
            // qaytarish uchun.
            sectionId: SECTION_ID
        });
        location.href = "/testConfigPage?" + params.toString();
    };

    container.appendChild(btn);
}

// HTML formatdagi kontent (.docx'dan import qilingan yoki qo'lda HTML
// sifatida saqlangan) — mavjud teglar o'zgartirilmaydi, lekin oddiy
// matn ko'rinishidagi (hali <a>ga o'ralmagan) http(s) havolalar ham
// linkifyHtmlContent orqali bosiladigan qilinadi (masalan "PDF: https://...
// .pdf" kabi qo'lda yozilgan yuklab olish havolalari — foydalanuvchi
// so'rovi bo'yicha, bunday havolalar HAR DOIM giperssilka bo'lishi kerak).
// PLAIN (qo'lda yozilgan yoki eski bo'limlar) esa xavfsiz escape+linkify
// qilingan holda qaytariladi.
function renderTextContent(data) {
    if (data.textContentFormat === "HTML") {
        return linkifyHtmlContent(data.textContent || "");
    }
    return linkify(data.textContent || "");
}

// HTML kontent ichidagi, hali <a> tegiga o'ralmagan oddiy matndagi
// http(s) havolalarni bosiladigan <a> tegiga aylantiradi. Mavjud
// teglarga (jumladan allaqachon <a> ichida turgan havolalarga) TEGMAYDI
// — faqat MATN TUGUNLARI (text node) bo'ylab yuradi (TreeWalker), <a>/
// <script>/<style> ichidagilarni o'tkazib yuboradi, shu sabab xavfsiz:
// hech qanday mavjud teg qayta ishlanmaydi yoki qayta escape qilinmaydi
// (oddiy string.replace() qilsa, mavjud HTML teglari buzilib qolardi).
function linkifyHtmlContent(html) {
    const container = document.createElement("div");
    container.innerHTML = html;

    const urlPattern = /(https?:\/\/[^\s<]+)/g;

    const walker = document.createTreeWalker(container, NodeFilter.SHOW_TEXT, {
        acceptNode(node) {
            let parent = node.parentElement;
            while (parent && parent !== container) {
                if (parent.tagName === "A" || parent.tagName === "SCRIPT" || parent.tagName === "STYLE") {
                    return NodeFilter.FILTER_SKIP;
                }
                parent = parent.parentElement;
            }
            urlPattern.lastIndex = 0;
            return urlPattern.test(node.textContent) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_SKIP;
        }
    });

    const textNodes = [];
    let n;
    while ((n = walker.nextNode())) textNodes.push(n);

    textNodes.forEach((node) => {
        urlPattern.lastIndex = 0;
        const text = node.textContent;
        const frag = document.createDocumentFragment();
        let lastIndex = 0;
        let match;
        while ((match = urlPattern.exec(text))) {
            if (match.index > lastIndex) {
                frag.appendChild(document.createTextNode(text.slice(lastIndex, match.index)));
            }
            const a = document.createElement("a");
            a.href = match[0];
            a.target = "_blank";
            a.rel = "noopener noreferrer";
            a.textContent = match[0];
            frag.appendChild(a);
            lastIndex = match.index + match[0].length;
        }
        if (lastIndex < text.length) {
            frag.appendChild(document.createTextNode(text.slice(lastIndex)));
        }
        node.parentNode.replaceChild(frag, node);
    });

    return container.innerHTML;
}

// Avval xavfsiz escape qilinadi (XSS'dan himoya — matn hech qachon
// ishonchli manba emas deb qaraladi), keyin http(s) havolalar bosiladigan
// <a> teglariga aylantiriladi. Qatorlar orasidagi bo'shliq uchun <br>
// shart emas — .section-content'da white-space:pre-wrap bor, xom "\n"
// belgisi o'zi qator ko'chirish sifatida chiziladi.
function linkify(text) {
    const div = document.createElement("div");
    div.textContent = text;
    const escaped = div.innerHTML;
    return escaped.replace(
        /(https?:\/\/[^\s<]+)/g,
        (url) => `<a href="${url}" target="_blank" rel="noopener noreferrer">${url}</a>`
    );
}

// ◀/▶ tugmalari — courseDetail.js#pptSlideNav bilan AYNAN BIR XIL (PPT
// slayd-shou HTML'i matn ichida saqlanadi, shu sabab ikkala sahifada —
// tahrirlashda VA shu yerda, o'qishda — ham ishlashi kerak). Shu wrap
// ichidagi <img>ning src'ini data-slides ro'yxatidagi keyingi/oldingi
// slaydga almashtiradi (aylanma).
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

function buildVideoEmbed(data) {
    if (data.videoSourceType === "YOUTUBE") {
        return `<div class="video-embed-wrapper"><div id="ytPlayer"></div></div>`;
    }

    if (data.videoSourceType === "UPLOAD") {
        return `<div class="video-embed-wrapper">
            <video id="uploadedVideo" src="${data.videoUrl}" controls></video>
        </div>`;
    }

    // EXTERNAL — umumiy iframe embed.
    return `<div class="video-embed-wrapper">
        <iframe src="${data.videoUrl}" allowfullscreen></iframe>
    </div>`;
}

function setupVideoCompletionTracking(data) {
    // YouTube pleyeri — <video>/<iframe>'dan farqli, buildVideoEmbed()
    // uni to'ldirmaydi, faqat bo'sh <div id="ytPlayer"> qoldiradi. Pleyer
    // FAQAT shu funksiya orqali ishga tushadi, shuning uchun "allaqachon
    // tugatilgan" tekshiruvidan OLDIN, har doim chaqirilishi shart —
    // aks holda talaba avval ko'rib bo'lgan videoni qayta ochganda doim
    // bo'sh (qora) joy ko'radi. "Tugatilgan" holat pastdagi qoidaga
    // (avtomatik belgilash/eslatma) tegishli, pleyerning o'ziga emas.
    if (data.videoSourceType === "YOUTUBE") {
        initYouTubePlayer(extractYouTubeId(data.videoUrl));
        return;
    }

    if (data.completed) return; // Allaqachon tugatilgan — qayta kuzatish shart emas.

    if (data.videoSourceType === "UPLOAD") {
        const video = document.getElementById("uploadedVideo");
        video.addEventListener("ended", () => markCompleted());
        return;
    }

    // EXTERNAL — aniq "tugadi" hodisasi yo'q. Agar davomiylik berilgan bo'lsa,
    // shu vaqt o'tgach avtomatik belgilaymiz; aks holda qo'lda tasdiqlash tugmasi.
    if (data.videoDurationSeconds && data.videoDurationSeconds > 0) {
        document.getElementById("nextHint").textContent =
            `Video tugagach (${data.videoDurationSeconds} soniya) keyingi mavzu ochiladi...`;
        externalTimerId = setTimeout(() => markCompleted(), data.videoDurationSeconds * 1000);
    } else {
        const hint = document.getElementById("nextHint");
        hint.innerHTML = `<button onclick="markCompleted()">✅ Videoni ko'rib chiqdim</button>`;
    }
}

// YouTube IFrame API global callback — API skripti yuklangach avtomatik chaqiriladi.
function onYouTubeIframeAPIReady() {
    if (sectionData && sectionData.type !== "TEXT" && sectionData.videoSourceType === "YOUTUBE") {
        initYouTubePlayer(extractYouTubeId(sectionData.videoUrl));
    }
}

function initYouTubePlayer(videoId) {
    if (typeof YT === "undefined" || !YT.Player) return; // API hali yuklanmagan — onYouTubeIframeAPIReady keyin chaqiradi.
    if (youTubePlayer) return;

    youTubePlayer = new YT.Player("ytPlayer", {
        videoId: videoId,
        events: {
            onStateChange: (event) => {
                if (event.data === YT.PlayerState.ENDED) {
                    markCompleted();
                }
            }
        }
    });
}

async function markCompleted() {
    try {
        const res = await fetch(`/api/courses/${COURSE_ID}/sections/${SECTION_ID}/complete`, { method: "POST" });
        if (!res.ok) return;

        if (sectionData) {
            sectionData.completed = true;
            sectionData.nextUnlocked = !!sectionData.nextSectionId;
        }
        updateNextButton(sectionData);
        document.getElementById("nextHint").textContent = "";
    } catch (err) {
        console.error(err);
    }
}

function updateNextButton(data) {
    const btn = document.getElementById("nextSectionBtn");

    if (!data.nextSectionId) {
        btn.textContent = "🎉 Kurs tugadi";
        btn.disabled = true;
        return;
    }

    btn.textContent = "Keyingi mavzu →";
    btn.disabled = !data.nextUnlocked;
    btn.onclick = () => {
        location.href = `/courses/${COURSE_ID}/sections/${data.nextSectionId}`;
    };
}
