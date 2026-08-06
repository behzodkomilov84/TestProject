let youTubePlayer = null;
let sectionData = null;
let externalTimerId = null;

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

    if (data.type === "TEXT") {
        content.textContent = data.textContent || "";
        // Matn bo'lim — ochilgan zahoti "tugatilgan" deb belgilanadi.
        markCompleted();
    } else {
        content.innerHTML = buildVideoEmbed(data);
        setupVideoCompletionTracking(data);
    }

    updateNextButton(data);
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
    if (data.completed) return; // Allaqachon tugatilgan — qayta kuzatish shart emas.

    if (data.videoSourceType === "UPLOAD") {
        const video = document.getElementById("uploadedVideo");
        video.addEventListener("ended", () => markCompleted());
        return;
    }

    if (data.videoSourceType === "YOUTUBE") {
        initYouTubePlayer(data.videoUrl);
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
    if (sectionData && sectionData.type === "VIDEO" && sectionData.videoSourceType === "YOUTUBE") {
        initYouTubePlayer(sectionData.videoUrl);
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
