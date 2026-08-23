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

document.getElementById("backToCourseBtn").onclick = () => {
    location.href = "/courses/" + COURSE_ID;
};

document.addEventListener("DOMContentLoaded", () => {
    loadSection();
});

function loadSection() {
    fetch(`/api/courses/${COURSE_ID}/sections/${SECTION_ID}`)
        .then(r => {
            if (!r.ok) {
                return r.json().then(data => { throw new Error(data.error || "Bo'lim topilmadi yoki hali ochilmagan"); });
            }
            return r.json();
        })
        .then(renderSection)
        .catch(err => {
            document.getElementById("sectionTitle").textContent = "Xatolik";
            document.getElementById("sectionContent").textContent = err.message;
        });
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

    renderTopicTestLink(data);
    updatePrevButton(data);
    updateNextButton(data);
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
        const params = new URLSearchParams({
            scienceId: data.linkedScienceId,
            topicId: data.linkedTopicId
        });
        location.href = "/testConfigPage?" + params.toString();
    };

    container.appendChild(btn);
}

// HTML formatdagi kontent (.docx'dan import qilingan) o'zgartirmasdan,
// PLAIN (qo'lda yozilgan yoki eski bo'limlar) esa xavfsiz escape+linkify
// qilingan holda qaytariladi.
function renderTextContent(data) {
    if (data.textContentFormat === "HTML") {
        return data.textContent || "";
    }
    return linkify(data.textContent || "");
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
