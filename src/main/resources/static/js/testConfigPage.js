// Kurs darsidagi "🎯 Darsga oid testlarni yechish" tugmasidan kelinganda —
// URL'da ?scienceId=&topicId= beriladi, shu bo'lim/dars avtomatik tanlanadi
// (Practice rejimida — vaqt chegarasisiz, sodda oqim).
const urlParams = new URLSearchParams(window.location.search);
const preselectScienceId = urlParams.get("scienceId");
const preselectTopicId = urlParams.get("topicId");
// Shu kurs (mazkur "🎯 Darsga oid testlarni yechish" tugmasi qaysi
// kursdan bosilgan bo'lsa) — "🔙 Darsga qaytish" tugmasini
// ko'rsatish/qayerga qaytarishni bilish uchun (pastda, DOMContentLoaded).
// Test boshlangandan keyin ham (testSession.js) ko'rinib turishi uchun
// startTest()'da sessionStorage'ga ham yozib qo'yiladi.
const returnCourseId = urlParams.get("courseId");
const returnSectionId = urlParams.get("sectionId");
// Kurs sahifasidagi mavzu KARTOCHKALARI ro'yxatidan ("🎯 Mavzuga oid
// testlarni yechish", courseDetail.js) kelinganda — "sectionId" (dars/
// bo'lim sahifasi) o'rniga shu kartochkaning o'zi beriladi, chunki bu
// yerda "dars sahifasi" umuman yo'q — foydalanuvchi to'g'ridan-to'g'ri
// ro'yxatdan kelgan. Qaytishda /courses/{courseId}?focus= orqali ANIQ
// shu kartochkaga qaytariladi (courseDetail.js#applyFocusFromUrl bilan
// bir xil g'oya, test-form.js#backToCourseBtn bilan bir xil yechim).
const returnFocusSectionId = urlParams.get("fromSectionId");

if (preselectTopicId) {
    sessionStorage.setItem("testMode", "practice");
}

const testMode = sessionStorage.getItem("testMode");
let hardTopicIds = [];

document.body.dataset.testMode = testMode;

document.addEventListener("DOMContentLoaded", () => {

    const timeSection = document.getElementById("timeSection");
    const topicSection = document.getElementById("topicSection");

    if (testMode === "practice") {
        timeSection.style.display = "none";
    }

    if (testMode === "hard") {
        topicSection.style.display = "none";
    }

    // Faqat kurs mavzusidan ("🎯 Mavzuga oid testlarni yechish") kelinganda
    // ko'rinadi — boshqa hollarda (bosh menyudan to'g'ridan-to'g'ri
    // kirilganda, courseId URL'da bo'lmaydi) butunlay yashirin qoladi.
    if (returnCourseId) {
        const backBtn = document.getElementById("backToCourseBtn");
        backBtn.classList.remove("hidden");
        // Haqiqiy production bug: ilgari FAQAT kursning o'ziga
        // (/courses/{courseId} — umumiy ro'yxat) qaytarardi, aynan
        // qaysi darsdan kelingani "yo'qolib" ketardi — foydalanuvchi
        // "tashqarida" qolib, qaytadan o'sha darsni qidirishga majbur
        // bo'lardi. Endi returnSectionId bo'lsa, ANIQ o'sha darsning
        // o'ziga qaytaradi.
        backBtn.onclick = () => {
            location.href = returnSectionId
                ? `/courses/${returnCourseId}/sections/${returnSectionId}`
                : returnFocusSectionId
                    ? `/courses/${returnCourseId}?focus=${returnFocusSectionId}`
                    : `/courses/${returnCourseId}`;
        };
    }

});

fetch("/api/tests/sciences")
    .then(r => r.json())
    .then(data => {
        const select = document.getElementById("scienceSelect");

        data.forEach(s => {
            const opt = document.createElement("option");
            opt.value = s.id;
            opt.textContent = s.name;
            select.appendChild(opt);
        });

        if (preselectScienceId) {
            select.value = preselectScienceId;
            loadTopicsFromSelect();
        }
    });

function loadTopicsFromSelect() {
    const scienceId = document.getElementById("scienceSelect").value;

    // Har doim reset qilamiz
    resetTestConfig();

    if (!scienceId) {
        return;
    }
    loadTopics(scienceId);
}

// Fan tanlangach yuklangan to'liq mavzular ro'yxati (sectionId/sectionName
// bilan) — Bo'lim tanlanganda qayta so'rov yubormasdan shu yerdan
// filtrlanadi (onSectionSelectChange).
let currentTopicsData = [];

function resetTestConfig() {
    // Topic dropdownni tozalash
    document.getElementById("topicDropdown").innerHTML = "";

    // Labelni qayta tiklash
    document.getElementById("topicLabel").innerText = "Darslarni tanlang";

    // Mavzu tanlash qadamini yashirish/tozalash
    document.getElementById("bolimSection").classList.add("hidden");
    document.getElementById("sectionSelect").innerHTML = '<option value="">-- Mavzuni tanlang --</option>';

    // Max testlarni 0 qilish
    document.getElementById("max").innerText = "0";

    // Inputni bo‘sh qilish
    document.getElementById("limit").value = "";

    // Oldingi testlarni o‘chirish
    document.getElementById("test").innerHTML = "";

    // Timer to‘xtatish
    document.getElementById("timer").innerText = "";
}

function loadTopics(id) {

    const mode = sessionStorage.getItem("testMode");

    fetch(`/api/tests/science/${id}/topics`)
        .then(r => r.json())
        .then(data => {

            currentTopicsData = data;
            document.getElementById("topicDropdown").innerHTML = "";

            // ==============================
            // HARD MODE
            // ==============================
            if (mode === "hard") {

                hardTopicIds = data.map(t => t.id);

                updateMax(hardTopicIds);   // ← теперь реально вызовется
                updateTopicLabel();

                return;
            }

            // ==============================
            // NORMAL MODES
            // ==============================
            // Avval Fan tanlanadi (yuqorida), SHU FANDA Bo'lim(lar) bo'lsa —
            // keyin Bo'lim, KEYINGINA o'sha bo'limdagi mavzular ko'rsatiladi
            // (bosqichma-bosqich: Fan -> Bo'lim -> Mavzu). Bo'limi yo'q
            // mavzular ("sectionId" NULL) — agar fanda umuman Bo'lim
            // bo'lmasa, hozirgidek to'g'ridan-to'g'ri tekis ro'yxat sifatida
            // ko'rsatiladi (ko'rinish 100% avvalgidek qoladi); agar fanda
            // BOSHQA mavzular bo'lim(lar)ga ega bo'lsa-yu, ba'zilari hali
            // biriktirilmagan bo'lsa — "— Bo'limsiz mavzular —" degan
            // alohida variant sifatida Bo'lim tanlovida chiqadi.
            const sectionsById = new Map();
            let hasUnassigned = false;
            data.forEach(t => {
                if (t.sectionId) {
                    if (!sectionsById.has(t.sectionId)) {
                        sectionsById.set(t.sectionId, {
                            id: t.sectionId,
                            name: t.sectionName,
                            orderIndex: t.sectionOrderIndex
                        });
                    }
                } else {
                    hasUnassigned = true;
                }
            });

            const bolimSection = document.getElementById("bolimSection");
            const sectionSelect = document.getElementById("sectionSelect");

            if (sectionsById.size === 0) {
                // Bu fanda Bo'lim umuman yo'q — eskicha, to'g'ridan-to'g'ri
                // tekis ro'yxat (Bo'lim qadami butunlay o'tkazib yuboriladi).
                bolimSection.classList.add("hidden");
                renderTopicCheckboxes(data);
                return;
            }

            bolimSection.classList.remove("hidden");
            sectionSelect.innerHTML = '<option value="">-- Bo\'limni tanlang --</option>';

            Array.from(sectionsById.values())
                .sort((a, b) => a.orderIndex - b.orderIndex)
                .forEach(sec => {
                    const opt = document.createElement("option");
                    opt.value = sec.id;
                    opt.textContent = sec.name;
                    sectionSelect.appendChild(opt);
                });

            if (hasUnassigned) {
                const opt = document.createElement("option");
                opt.value = "__none__";
                opt.textContent = "— Mavzusiz darslar —";
                sectionSelect.appendChild(opt);
            }

            // Kurs darsidan kelib, dars avtomatik belgilangan bo'lsa —
            // uning Mavzusini avtomatik tanlab, to'g'ridan-to'g'ri o'sha
            // mavzuning darslar ro'yxatini ochamiz (foydalanuvchi qo'lda
            // Mavzu tanlashini kutib o'tirmasdan).
            if (preselectTopicId) {
                const preselected = data.find(t => Number(t.id) === Number(preselectTopicId));
                if (preselected) {
                    sectionSelect.value = preselected.sectionId || "__none__";
                    onSectionSelectChange();
                }
            }

        });
}

// Mavzu tanlangach — faqat o'sha mavzuga tegishli darslar ko'rsatiladi
// (qayta server so'rovi shart emas, currentTopicsData'dan filtrlanadi).
function onSectionSelectChange() {
    const sectionValue = document.getElementById("sectionSelect").value;

    if (!sectionValue) {
        document.getElementById("topicDropdown").innerHTML = "";
        document.getElementById("topicLabel").innerText = "Darslarni tanlang";
        updateMax();
        return;
    }

    const filtered = sectionValue === "__none__"
        ? currentTopicsData.filter(t => !t.sectionId)
        : currentTopicsData.filter(t => Number(t.sectionId) === Number(sectionValue));

    renderTopicCheckboxes(filtered);
}

function renderTopicCheckboxes(topics) {
    const box = document.getElementById("topicDropdown");
    box.innerHTML = "";

    topics.forEach(t => {
        const label = document.createElement("label");

        label.innerHTML = `
            <input type="checkbox" value="${t.id}">
            ${t.name} (${t.questionCount} ta test)
        `;

        const checkbox = label.querySelector("input");

        if (preselectTopicId && Number(t.id) === Number(preselectTopicId)) {
            checkbox.checked = true;
        }

        checkbox.addEventListener("change", () => {
            updateMax();
            updateTopicLabel();
        });

        box.appendChild(label);
    });

    // Kurs bo'limidan kelib, mavzu avtomatik belgilangan bo'lsa — "necha ta
    // test bor"/label darhol yangilanishi kerak (odatda faqat checkbox
    // o'zgarganda ishga tushardi).
    if (preselectTopicId) {
        updateMax();
        updateTopicLabel();
    }
}

function toggleTopics() {
    const box = document.getElementById("topicDropdown");
    box.style.display = box.style.display === "block" ? "none" : "block";
}

function updateTopicLabel() {
    const checked = [...document.querySelectorAll("#topicDropdown input:checked")];

    if (checked.length === 0) {
        document.getElementById("topicLabel").innerText = "--Darslarni tanlash uchun bosing--";
        return;
    }

    document.getElementById("topicLabel").innerText =
        checked.length + " ta dars tanlandi";
}

function updateMax(forcedIds = null) {

    let topicIds;

    // hard mode
    if (forcedIds) {
        topicIds = forcedIds;
    } else {
        // normal mode
        topicIds = [...document.querySelectorAll("#topicDropdown input:checked")]
            .map(i => Number(i.value));
    }


    if (topicIds.length === 0) {
        document.getElementById("max").innerText = "0";
        return;
    }

    fetch("/api/tests/max", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
            topicIds,
            testMode})
    })
        .then(r => r.json())
        .then(max => {
            document.getElementById("max").innerText = max;

            document.getElementById("limit").max = max;

            document.getElementById("limit").value =
                Math.min(document.getElementById("limit").value || max, max);
        });
}

/*==================================================================*/
/*Mavzuni tanlayotganda, tanlov maydonidan tashqariga bosilsa, polyani yopish*/
/*==================================================================*/
document.addEventListener("click", function (event) {
    const multiselect = document.getElementById("topicMultiselect");
    const dropdown = document.getElementById("topicDropdown");

    // Agar click multiselect ichida bo‘lmasa → yopamiz
    if (!multiselect.contains(event.target)) {
        dropdown.style.display = "none";
    }
});

/*==================================================================*/
/*Testlar sonini tanlashda validatsiya qo'yish*/
document.getElementById("limit").addEventListener("input", validateLimit);

function validateLimit() {
    const limitInput = document.getElementById("limit");
    const max = Number(document.getElementById("max").innerText);
    const error = document.getElementById("limitError");

    if (!max) {
        limitInput.classList.remove("error");
        error.innerText = "";
        return;
    }

    if (limitInput.value > max) {
        limitInput.classList.add("error");
        error.innerText = `Maksimum ${max} tagacha test yecha olasiz`;
    } else {
        limitInput.classList.remove("error");
        error.innerText = "";
    }
}

/*==================================================================*/
function startTest() {

    const mode = sessionStorage.getItem("testMode");

    let topicIds;
    // получаем выбранные темы и лимит
    if (mode === "hard") {
        topicIds = hardTopicIds;
    }else {
        topicIds = [...document.querySelectorAll("#topicDropdown input:checked")]
            .map(i => Number(i.value));
    }
    const limit = Number(document.getElementById("limit").value);
    const timeValue = Number(document.getElementById("time").value);

    if (topicIds.length === 0) {
        alert("Dars tanlang!");
        return;
    }

    if (limit <= 0) {
        alert("Test sonini kiriting");
        return;
    }

    // Сохраняем данные в sessionStorage
    sessionStorage.setItem("topicIds", JSON.stringify(topicIds));
    sessionStorage.setItem("limit", limit);
    sessionStorage.setItem("time", timeValue);
    sessionStorage.setItem("testMode", mode);

    // "🔙 Mavzuga qaytish" tugmasi test sessiyasining OXIRIGACHA (masalan
    // 5-savolni yechayotganda ham) ko'rinib turishi uchun — testSession.js
    // shu ikkalasini o'qib, doimiy tugma chiqaradi. Bo'sh bo'lsa (odatiy
    // holat — testConfigPage'ga bosh menyudan to'g'ridan-to'g'ri
    // kirilganda) — testSession'da tugma ko'rsatilmaydi.
    if (returnCourseId) {
        sessionStorage.setItem("returnCourseId", returnCourseId);
        sessionStorage.setItem("returnSectionId", returnSectionId || "");
        sessionStorage.setItem("returnFocusSectionId", returnFocusSectionId || "");
    } else {
        sessionStorage.removeItem("returnCourseId");
        sessionStorage.removeItem("returnSectionId");
        sessionStorage.removeItem("returnFocusSectionId");
    }

    // 👉 просто переход
    window.location.href = "/testSession";
}