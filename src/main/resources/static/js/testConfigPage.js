// Kurs darsidagi "🎯 Darsga oid testlarni yechish" tugmasidan kelinganda —
// URL'da ?scienceId=&topicId= beriladi, shu bo'lim/dars avtomatik tanlanadi
// (Practice rejimida — vaqt chegarasisiz, sodda oqim). "scienceId" endi
// alohida ishlatilmaydi (to'liq ierarxiya bir yo'la yuklanadi) — faqat
// "topicId" orqali aniq shu darsni topib, selectOnlyTopic() belgilaydi.
const urlParams = new URLSearchParams(window.location.search);
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

document.body.dataset.testMode = testMode;

document.addEventListener("DOMContentLoaded", () => {

    const timeSection = document.getElementById("timeSection");

    if (testMode === "practice") {
        timeSection.style.display = "none";
    }

    // "hard" rejimida — foydalanuvchi hali yechmagan (yoki xato yechgan)
    // barcha savollar, mavzu/dars darajasida ANIQ tanlov shart emas
    // (avval umuman ko'rsatilmasdi ham). Endi ham xuddi shunday: daraxt
    // ko'rinadi (Yo'nalish/Bo'lim darajasida — QAYSI fanlardan hard
    // savollar olinishini tanlash uchun), lekin Mavzu/Dars darajalari
    // yashiriladi. Checkbox'lar ENDI default-checked EMAS (foydalanuvchi
    // so'rovi, 2026-09-07) — foydalanuvchi kerakli Bo'lim(lar)ni belgilasa,
    // cascadeDown() shu Bo'limning BARCHA (yashirin) Mavzu/Dars
    // checkbox'larini avtomatik belgilaydi, shuning uchun natija baribir
    // "shu fan(lar)dagi BARCHA darslar" bilan bir xil bo'lib chiqadi
    // (renderHierarchy'dagi CSS klassi orqali, .hierarchy-tree[data-mode="hard"]).

    // Faqat kurs darsidan ("🎯 Darsga oid testlarni yechish") kelinganda
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

    loadHierarchy();
});

// ==========================================================================
// TO'RT DARAJALI (Yo'nalish -> Bo'lim -> Mavzu -> Dars) checkbox daraxti
// (foydalanuvchi so'rovi, 2026-09-07). Bitta so'rovda (/api/tests/hierarchy)
// TEKIS qatorlar sifatida keladi, shu yerda ichma-ich (nested) struktura
// qurib chiqiladi. Haqiqiy tanlov — faqat eng pastki DARS (Topic)
// darajasidagi checkbox'lar (ular "startTest"da backend'ga yuboriladigan
// topicIds ro'yxatini belgilaydi); yuqori darajalar shunchaki KO'PLAB
// darslarni bir zumda belgilash/bekor qilish uchun QULAYLIK — checkbox
// holati pastdan yuqoriga (indeterminate bilan) va yuqoridan pastga
// (cascade) sinxronlashtiriladi.
let hierarchyData = [];

function loadHierarchy() {
    fetch("/api/tests/hierarchy")
        .then(r => r.json())
        .then(rows => {
            hierarchyData = rows;
            renderHierarchy(rows);
        })
        .catch(err => {
            console.error(err);
            document.getElementById("hierarchyTree").innerHTML =
                `<div class="hierarchy-loading">❌ Yuklashda xatolik yuz berdi.</div>`;
        });
}

// Tekis qatorlarni Yo'nalish -> Bo'lim -> Mavzu -> [Dars] ichma-ich
// Map'larga guruhlaydi. "none" kaliti — shu daraja biriktirilmagan
// (fieldId/sectionId NULL) guruh uchun, doim RO'YXAT OXIRIDA ko'rsatiladi
// (topics.html'dagi "— Mavzusiz darslar —" bilan bir xil g'oya).
function groupHierarchy(rows) {
    const fields = new Map();

    for (const row of rows) {
        const fieldKey = row.fieldId ?? "none";
        if (!fields.has(fieldKey)) {
            fields.set(fieldKey, {
                id: row.fieldId, name: row.fieldName || "Yo'nalishsiz", sciences: new Map()
            });
        }
        const field = fields.get(fieldKey);

        const scienceKey = row.scienceId;
        if (!field.sciences.has(scienceKey)) {
            field.sciences.set(scienceKey, {
                id: row.scienceId, name: row.scienceName, sections: new Map()
            });
        }
        const science = field.sciences.get(scienceKey);

        const sectionKey = row.sectionId ?? "none";
        if (!science.sections.has(sectionKey)) {
            science.sections.set(sectionKey, {
                id: row.sectionId, name: row.sectionName || "Mavzusiz darslar", topics: []
            });
        }
        science.sections.get(sectionKey).topics.push({
            id: row.topicId, name: row.topicName, questionCount: row.questionCount
        });
    }

    // "none" guruhlarni oxiriga suramiz.
    const sortedByNoneLast = (map) => [...map.entries()]
        .sort(([a], [b]) => (a === "none") - (b === "none"));

    return sortedByNoneLast(fields).map(([, field]) => ({
        ...field,
        sciences: sortedByNoneLast(field.sciences).map(([, science]) => ({
            ...science,
            sections: sortedByNoneLast(science.sections).map(([, section]) => section)
        }))
    }));
}

function renderHierarchy(rows) {
    const tree = document.getElementById("hierarchyTree");

    if (!rows.length) {
        tree.innerHTML = `<div class="hierarchy-loading">Hozircha testlar mavjud emas.</div>`;
        return;
    }

    const fields = groupHierarchy(rows);
    tree.dataset.mode = testMode === "hard" ? "hard" : "normal";

    tree.innerHTML = fields.map(renderFieldNode).join("");

    // Har bir daraja o'zgarganda pastga/yuqoriga sinxronlashtirish.
    tree.querySelectorAll(".topic-checkbox").forEach(cb => {
        cb.addEventListener("change", () => {
            syncAncestors(cb);
            afterSelectionChange();
        });
    });
    tree.querySelectorAll(".section-checkbox").forEach(cb => {
        cb.addEventListener("change", () => {
            cascadeDown(cb, ".hierarchy-section", ".topic-checkbox");
            syncAncestors(cb);
            afterSelectionChange();
        });
    });
    tree.querySelectorAll(".science-checkbox").forEach(cb => {
        cb.addEventListener("change", () => {
            cascadeDown(cb, ".hierarchy-science", ".section-checkbox, .topic-checkbox");
            syncAncestors(cb);
            afterSelectionChange();
        });
    });
    tree.querySelectorAll(".field-checkbox").forEach(cb => {
        cb.addEventListener("change", () => {
            cascadeDown(cb, ".hierarchy-field", ".science-checkbox, .section-checkbox, .topic-checkbox");
            afterSelectionChange();
        });
    });

    // Kurs darsidan ("🎯 Darsga oid testlarni yechish") aniq bitta dars
    // bilan kelingan bo'lsa — hammasini bekor qilib, faqat o'shani
    // belgilaymiz va shu shoxni ochib, ko'rinadigan joyga aylantiramiz.
    if (preselectTopicId) {
        selectOnlyTopic(Number(preselectTopicId));
    } else {
        afterSelectionChange();
    }
}

function fieldKeyOf(field) {
    return field.id ?? "none";
}

// MUHIM: checkbox'lar ENDI default-checked EMAS (foydalanuvchi so'rovi,
// 2026-09-07: "Птичкасини по умолчанию олиб ташла" — avval hammasi
// belgilangan holda boshlanardi, endi foydalanuvchi o'zi kerakli
// dars(lar)ni tanlaydi; "☑️ Barchasini belgilash" tugmasi bir zumda
// hammasini belgilash imkonini beradi).
function renderFieldNode(field) {
    const sciencesHtml = field.sciences.map(renderScienceNode).join("");
    return `
        <details class="hierarchy-field" data-field-id="${field.id ?? ""}">
            <summary>
                <label onclick="event.stopPropagation()">
                    <input type="checkbox" class="field-checkbox">
                    <span class="hierarchy-name">${escapeHierarchyHtml(field.name)}</span>
                </label>
            </summary>
            <div class="hierarchy-children hierarchy-sciences">${sciencesHtml}</div>
        </details>
    `;
}

function renderScienceNode(science) {
    const sectionsHtml = science.sections.map(renderSectionNode).join("");
    return `
        <details class="hierarchy-science" data-science-id="${science.id}">
            <summary>
                <label onclick="event.stopPropagation()">
                    <input type="checkbox" class="science-checkbox">
                    <span class="hierarchy-name">${escapeHierarchyHtml(science.name)}</span>
                </label>
            </summary>
            <div class="hierarchy-children hierarchy-sections">${sectionsHtml}</div>
        </details>
    `;
}

function renderSectionNode(section) {
    const topicsHtml = section.topics.map(renderTopicRow).join("");
    return `
        <details class="hierarchy-section" data-section-id="${section.id ?? ""}">
            <summary>
                <label onclick="event.stopPropagation()">
                    <input type="checkbox" class="section-checkbox">
                    <span class="hierarchy-name">${escapeHierarchyHtml(section.name)}</span>
                </label>
            </summary>
            <div class="hierarchy-children hierarchy-topics">${topicsHtml}</div>
        </details>
    `;
}

function renderTopicRow(topic) {
    return `
        <label class="topic-row" data-topic-id="${topic.id}">
            <input type="checkbox" class="topic-checkbox" value="${topic.id}">
            <span class="hierarchy-name">${escapeHierarchyHtml(topic.name)}</span>
            <span class="topic-count">${topic.questionCount} ta test</span>
        </label>
    `;
}

function escapeHierarchyHtml(text) {
    const div = document.createElement("div");
    div.textContent = text ?? "";
    return div.innerHTML;
}

// Yuqoridan pastga — bitta checkbox bosilganda, ICHIDAGI barcha
// checkbox'larni (belgilangan selektorlar bo'yicha) xuddi shu holatga
// keltiradi (hammasi belgilanadi yoki hammasi bekor qilinadi).
function cascadeDown(checkbox, containerSelector, targetSelector) {
    const container = checkbox.closest(containerSelector);
    container.querySelectorAll(targetSelector).forEach(cb => {
        cb.checked = checkbox.checked;
        cb.indeterminate = false;
    });
}

// Pastdan yuqoriga — bitta Dars/Mavzu/Bo'lim checkbox'i o'zgarganda,
// ota-bobolarining holatini yangilaydi: barchasi belgilangan bo'lsa —
// ota ham belgilanadi; barchasi bo'sh bo'lsa — ota ham bo'sh; aralash
// bo'lsa — ota "indeterminate" (chiziqcha) holatga o'tadi. Bu — "chiroyli"
// UX uchun muhim: aks holda foydalanuvchi bitta darsni bekor qilganda
// yuqoridagi Bo'lim checkbox'i hali ham "to'liq belgilangan" bo'lib
// ko'rinib, chalg'itardi.
function syncAncestors(checkbox) {
    let node = checkbox.closest(".hierarchy-section, .hierarchy-science, .hierarchy-field");
    while (node) {
        const ownCheckbox = node.querySelector(":scope > summary input[type=checkbox]");
        const childContainer = node.querySelector(":scope > .hierarchy-children");

        let children;
        if (node.classList.contains("hierarchy-section")) {
            children = [...childContainer.querySelectorAll(".topic-checkbox")];
        } else if (node.classList.contains("hierarchy-science")) {
            children = [...childContainer.querySelectorAll(":scope > .hierarchy-section > summary .section-checkbox")];
        } else {
            children = [...childContainer.querySelectorAll(":scope > .hierarchy-science > summary .science-checkbox")];
        }

        const allChecked = children.every(cb => cb.checked && !cb.indeterminate);
        const noneChecked = children.every(cb => !cb.checked && !cb.indeterminate);

        ownCheckbox.checked = allChecked;
        ownCheckbox.indeterminate = !allChecked && !noneChecked;

        node = node.parentElement.closest(".hierarchy-section, .hierarchy-science, .hierarchy-field");
    }
}

// Kurs darsidan ("🎯 Darsga oid testlarni yechish") aniq bitta dars bilan
// kelinganda — bosh menyudagi kabi HAMMASI emas, FAQAT o'sha dars
// belgilangan holda boshlanadi (avvalgi xulq-atvor bilan bir xil).
function selectOnlyTopic(topicId) {
    const tree = document.getElementById("hierarchyTree");
    tree.querySelectorAll(".topic-checkbox").forEach(cb => {
        cb.checked = Number(cb.value) === topicId;
    });
    tree.querySelectorAll(".section-checkbox, .science-checkbox, .field-checkbox")
        .forEach(cb => cb.checked = false);

    const targetRow = tree.querySelector(`.topic-row[data-topic-id="${topicId}"]`);
    if (targetRow) {
        const topicCheckbox = targetRow.querySelector(".topic-checkbox");
        syncAncestors(topicCheckbox);
        // Shu darsgacha bo'lgan barcha <details>'larni ochamiz (aks holda
        // yopiq bo'lib, foydalanuvchiga ko'rinmay qolishi mumkin edi).
        let el = targetRow.closest("details");
        while (el) {
            el.open = true;
            el = el.parentElement.closest("details");
        }
        targetRow.scrollIntoView({block: "center", behavior: "smooth"});
    }

    afterSelectionChange();
}

// ==== "☑️ Barchasini belgilash / bekor qilish" — BITTA tugma ====
// (foydalanuvchi so'rovi, 2026-09-07: "Бир кнопка билан барчасини
// marked ни олиб ташлаш ва қўйиш имкони бўлсин"). Joriy holatga qarab
// (agar hech bo'lmasa bitta dars bekor qilingan bo'lsa) — bosilganda
// HAMMASINI belgilaydi; aks holda (hammasi allaqachon belgilangan)
// HAMMASINI bekor qiladi.
function toggleAll() {
    const tree = document.getElementById("hierarchyTree");
    const topicCheckboxes = [...tree.querySelectorAll(".topic-checkbox")];
    if (topicCheckboxes.length === 0) return;

    const shouldCheckAll = topicCheckboxes.some(cb => !cb.checked);

    tree.querySelectorAll("input[type=checkbox]").forEach(cb => {
        cb.checked = shouldCheckAll;
        cb.indeterminate = false;
    });

    afterSelectionChange();
}

function updateToggleAllLabel() {
    const tree = document.getElementById("hierarchyTree");
    const topicCheckboxes = [...tree.querySelectorAll(".topic-checkbox")];
    const btn = document.getElementById("toggleAllBtn");
    if (!btn || topicCheckboxes.length === 0) return;

    const allChecked = topicCheckboxes.every(cb => cb.checked);
    btn.textContent = allChecked ? "☐ Barchasini bekor qilish" : "☑️ Barchasini belgilash";
}

function getCheckedTopicIds() {
    return [...document.querySelectorAll("#hierarchyTree .topic-checkbox:checked")]
        .map(cb => Number(cb.value));
}

function afterSelectionChange() {
    updateToggleAllLabel();
    updateMax();
}

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

function updateMax() {
    const topicIds = getCheckedTopicIds();

    if (topicIds.length === 0) {
        document.getElementById("max").innerText = "0";
        return;
    }

    fetch("/api/tests/max", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({topicIds, testMode})
    })
        .then(r => r.json())
        .then(max => {
            document.getElementById("max").innerText = max;

            // Avval "limit" avtomatik "max" bilan to'ldirilardi —
            // foydalanuvchi buni o'zgartirib bo'lmaydigan/tayyor son deb
            // o'ylab qolgan edi (haqiqiy topilgan muammo, 2026-09-07).
            // Endi bo'sh qoladi (pastdagi izoh matni tushuntiradi), FAQAT
            // "max" cheklovi (HTML5 validatsiya uchun) o'rnatiladi. Agar
            // foydalanuvchi allaqachon biror son kiritgan bo'lsa (masalan
            // tanlovni o'zgartirib, "max" kamaygan bo'lsa) — kiritilgan
            // qiymat yangi maksimumdan oshib ketmasligi uchun qisqartiriladi.
            const limitInput = document.getElementById("limit");
            limitInput.max = max;
            if (limitInput.value && Number(limitInput.value) > max) {
                limitInput.value = max;
            }

            validateLimit();
        });
}

/*==================================================================*/
function startTest() {

    const mode = sessionStorage.getItem("testMode");
    const topicIds = getCheckedTopicIds();
    const limit = Number(document.getElementById("limit").value);
    const timeValue = Number(document.getElementById("time").value);

    if (topicIds.length === 0) {
        showAlertModal("Kamida bitta dars tanlang!");
        return;
    }

    if (limit <= 0) {
        showAlertModal("Test sonini kiriting");
        return;
    }

    // "time" input endi avtomatik "10" bilan to'ldirilmaydi (foydalanuvchi
    // so'rovi, 2026-09-07) — shuning uchun bo'sh qolishi mumkin, bu yerda
    // aniq tekshiriladi. "practice" rejimida vaqt cheklovi umuman
    // ko'rsatilmaydi (timeSection yashirin), shuning uchun bu yerda
    // tekshirilmaydi.
    if (mode !== "practice" && timeValue <= 0) {
        showAlertModal("Vaqtni kiriting");
        return;
    }

    // Сохраняем данные в sessionStorage
    sessionStorage.setItem("topicIds", JSON.stringify(topicIds));
    sessionStorage.setItem("limit", limit);
    sessionStorage.setItem("time", timeValue);
    sessionStorage.setItem("testMode", mode);

    // "🔙 Darsga qaytish" tugmasi test sessiyasining OXIRIGACHA (masalan
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
