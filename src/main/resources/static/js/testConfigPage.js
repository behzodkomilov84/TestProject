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
            data.forEach(t => {

                const label = document.createElement("label");

                label.innerHTML = `
                    <input type="checkbox" value="${t.id}">
                    ${t.name} (${t.questionCount} ta test)
                `;

                const checkbox = label.querySelector("input");

                checkbox.addEventListener("change", () => {
                    updateMax();
                    updateTopicLabel();
                });

                box.appendChild(label);
            });

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