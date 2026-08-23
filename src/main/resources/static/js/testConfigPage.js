// Kurs bo'limidagi "🎯 Mavzuga oid testlarni yechish" tugmasidan kelinganda —
// URL'da ?scienceId=&topicId= beriladi, shu fan/mavzu avtomatik tanlanadi
// (Practice rejimida — vaqt chegarasisiz, sodda oqim).
const urlParams = new URLSearchParams(window.location.search);
const preselectScienceId = urlParams.get("scienceId");
const preselectTopicId = urlParams.get("topicId");

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

function resetTestConfig() {
    // Topic dropdownni tozalash
    document.getElementById("topicDropdown").innerHTML = "";

    // Labelni qayta tiklash
    document.getElementById("topicLabel").innerText = "Mavzularni tanlang";

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

            const box = document.getElementById("topicDropdown");
            box.innerHTML = "";

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
            // Mavzular Bo'lim bo'yicha guruhlanadi (masalan "I. UMUMIY
            // KIMYO"), yig'iladigan (collapsible) sarlavha ostida.
            // Bo'limi yo'q mavzular ("sectionId" NULL) — sarlavhasiz,
            // hozirgidek tekis ro'yxatda, oxirida (backend'dan shu tartibda
            // keladi — TopicRepository.getTopicsWithQuestionCount). Bo'lim
            // umuman bo'lmagan fan uchun bu ko'rinish 100% avvalgidek qoladi.
            const groups = new Map(); // sectionId (yoki null) -> {name, topics: []}
            data.forEach(t => {
                const key = t.sectionId || "__none__";
                if (!groups.has(key)) {
                    groups.set(key, {name: t.sectionName, topics: []});
                }
                groups.get(key).topics.push(t);
            });

            groups.forEach((group, key) => {
                const topicsContainer = document.createElement("div");
                topicsContainer.className = "section-topics";

                group.topics.forEach(t => {
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

                    topicsContainer.appendChild(label);
                });

                if (key === "__none__") {
                    // Bo'limsiz — sarlavhasiz, to'g'ridan-to'g'ri qo'yiladi.
                    box.appendChild(topicsContainer);
                } else {
                    const groupDiv = document.createElement("div");
                    groupDiv.className = "section-group";

                    const header = document.createElement("div");
                    header.className = "section-header";
                    header.innerHTML = `<span>${group.name}</span> <span class="section-chevron">▾</span>`;
                    header.addEventListener("click", () => {
                        groupDiv.classList.toggle("collapsed");
                    });

                    groupDiv.appendChild(header);
                    groupDiv.appendChild(topicsContainer);
                    box.appendChild(groupDiv);
                }
            });

            // Kurs bo'limidan kelib, mavzu avtomatik belgilangan bo'lsa —
            // uning guruhini ochib qo'yamiz (aks holda collapsed holatda
            // "belgilangan" checkbox ko'zga ko'rinmay qolishi mumkin) va
            // "necha ta test bor"/label darhol yangilanadi (odatda faqat
            // checkbox o'zgarganda ishga tushardi).
            if (preselectTopicId) {
                updateMax();
                updateTopicLabel();
            }

        });
}

function toggleTopics() {
    const box = document.getElementById("topicDropdown");
    box.style.display = box.style.display === "block" ? "none" : "block";
}

function updateTopicLabel() {
    const checked = [...document.querySelectorAll("#topicDropdown input:checked")];

    if (checked.length === 0) {
        document.getElementById("topicLabel").innerText = "--Mavzuni tanlash uchun bosing--";
        return;
    }

    document.getElementById("topicLabel").innerText =
        checked.length + " ta mavzu tanlandi";
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
        alert("Mavzu tanlang!");
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

    // 👉 просто переход
    window.location.href = "/testSession";
}