// Javob variantlari uchun A/B/C/D/E belgilari — question.js'dagi admin
// jadvali bilan bir xil ko'rinish (savol yaratishda ham shu tartibda).
const ANSWER_LETTERS = ["A", "B", "C", "D", "E"];

// Savol/javob rasmiga "Savol formasi"da tanlangan eni/bo'yi (agar
// bo'lsa) — talaba ham ANIQ shu o'lchamda ko'radi (foydalanuvchi so'rovi,
// 2026-09-05). Ikkalasi ham bo'lmasa — bo'sh string (rasm o'zining
// tabiiy o'lchamida, CSS bo'yicha ko'rsatiladi).
function imageSizeStyleAttr(width, height) {
    if (!width && !height) return "";
    const w = width ? `width:${width}px;` : "";
    const h = height ? `height:${height}px;` : "";
    return ` style="${w}${h}max-width:100%;"`;
}

// Ko'p to'g'ri javobli savollar (foydalanuvchi so'rovi, 2026-09-06 —
// "talaba UI" bosqichi, avvalgi bosqichlarda backend/admin/Excel allaqachon
// tayyor edi, faqat talaba tomoni radio bo'lib qolgan edi). Har bir savol
// UCHUN alohida hisoblanadi (bazadagi haqiqiy isTrue soniga qarab) —
// odatdagi (1 ta to'g'ri) savollar hamon radio (o'zgarishsiz), faqat 2+
// to'g'ri javobli savollargina checkbox bo'lib chiqadi.
function isMultiCorrectQuestion(q) {
    return q.answers.filter(a => a.isTrue).length > 1;
}

// Ikkita to'plam (Set) bir xil elementlardan iboratmi — "hammasi yoki
// hech narsa" baholash qoidasi (backend#MultiAnswerUtil.isCorrect bilan
// AYNAN bir xil semantika: talaba ANIQ barcha to'g'ri variantlarni
// belgilagan bo'lishi kerak, ortiqcha ham, kam ham emas).
function setsEqual(a, b) {
    if (a.size !== b.size) return false;
    for (const x of a) if (!b.has(x)) return false;
    return true;
}

//==============================================================
//            Состояние теста
//==============================================================
const testState = {
    mode: sessionStorage.getItem("testMode"),

    topicIds: JSON.parse(sessionStorage.getItem("topicIds") || "[]"),
    limit: Number(sessionStorage.getItem("limit") || 10),
    time: Number(sessionStorage.getItem("time") || 10),

    testSessionId: null,
    allQuestions: [],
    questions: [],
    currentIndex: 0,
    answers: new Map(),
    startedAt: null,
    finishedAt: null,
    finished: false,
    // Barcha savollarga javob berilgani haqida eslatma faqat BIR MARTA
    // chiqishi uchun (har testda qayta tiklanadi — startTest/repeatWrongOnly).
    allAnsweredNotified: false
};

//==============================================================
//                DOMContentLoaded
//==============================================================
document.addEventListener("DOMContentLoaded", () => {

    if (testState.mode !== "practice") {
        startTimer(testState.time);
    } else {
        document.getElementById("timer").style.display = "none";
    }

    if (testState.topicIds.length === 0) {
        showAlertModal("Нет выбранных тем. Вернитесь на предыдущую страницу.");
        window.location.href = "/testConfigPage";
        return;
    }

    setupModeLabel();
    setupReturnToTopicButton();

    // Запрашиваем тест сразу после загрузки страницы
    fetch("/api/test-session/start", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify({
            topicIds: testState.topicIds,
            limit: testState.limit,
            mode: testState.mode
        })
    })
        .then(r => r.json())
        .then(data => {
            if (!data.questions || data.questions.length === 0) {
                document.getElementById("questions").innerHTML = "<p class='empty'>❌ Вопросы не пришли с сервера</p>";
                return;
            }
            testState.testSessionId = data.testSessionId;
            testState.allQuestions = data.questions;
            testState.questions = data.questions;
            testState.startedAt = Date.now();

            console.log("TestSession ID:", testState.testSessionId);

            startTest();
        })
        .catch(err => {
            console.error(err);
            showAlertModal(err);
            document.getElementById("questions").innerHTML = "<p class='empty'>❌ Ошибка загрузки теста</p>";
        });

});

// "🔙 Darsga qaytish" — kurs darsidan ("🎯 Mavzuga oid testlarni
// yechish") kelingan bo'lsa (testConfigPage.js#startTest sessionStorage'ga
// yozib qo'ygan), test sessiyasining OXIRIGACHA (5-savolni yechayotganda
// ham) ko'rinib turadi — foydalanuvchi so'rovi bo'yicha. Boshqa hollarda
// (bosh menyudan to'g'ridan-to'g'ri kirilganda) butunlay yashirin qoladi.
function setupReturnToTopicButton() {
    const courseId = sessionStorage.getItem("returnCourseId");
    if (!courseId) return;

    const sectionId = sessionStorage.getItem("returnSectionId");
    // Kurs sahifasidagi dars KARTOCHKALARI ro'yxatidan ("🎯 Mavzuga oid
    // testlarni yechish", courseDetail.js) kelingan bo'lsa — "sectionId"
    // (dars sahifasi) o'rniga shu kartochkaning o'zi (testConfigPage.js
    // #returnFocusSectionId orqali sessionStorage'ga yozilgan). Qaytishda
    // /courses/{courseId}?focus= orqali ANIQ shu kartochkaga qaytariladi.
    const focusSectionId = sessionStorage.getItem("returnFocusSectionId");
    const btn = document.getElementById("returnToTopicBtn");
    btn.classList.remove("hidden");
    btn.onclick = () => {
        location.href = sectionId
            ? `/courses/${courseId}/sections/${sectionId}`
            : focusSectionId
                ? `/courses/${courseId}?focus=${focusSectionId}`
                : `/courses/${courseId}`;
    };
}

function setupModeLabel() {

    const label = document.getElementById("modeLabel");

    const modeNames = {
        practice: "📝 PRACTICE MODE",
        exam: "⏱ EXAM MODE",
        hard: "🔥 HARD MODE"
    };

    const mode = testState.mode;

    label.innerText = modeNames[mode] || mode.toUpperCase();

    // ключевая строка — режим в body для CSS
    document.body.dataset.mode = mode;
}

//==============================================================
//                   Таймер
//==============================================================
let time;
let timerInterval = null;

function startTimer(min) {
    time = min * 60;

    // 🔴 защита от повторного запуска
    if (timerInterval !== null) {
        clearInterval(timerInterval);
    }

    timerInterval = setInterval(() => {
        const m = Math.floor(time / 60);
        const s = time % 60;

        document.getElementById("timer").innerText =
            `${m}:${s < 10 ? '0' : ''}${s}`;

        time--;

        if (time < 0) {
            stopTimer();  // ✅ правильно
            finishTest();
        }
    }, 1000);
}

function stopTimer() {
    if (timerInterval !== null) {
        clearInterval(timerInterval);
        timerInterval = null;
    }
}
//Отрисовка вопросов
function renderQuestions(questions) {
    const container = document.getElementById("questions");
    container.innerHTML = "";

    questions.forEach((q, index) => {

        const correctAnswer = q.answers.find(a => a.isTrue);
        const isMulti = isMultiCorrectQuestion(q);

        const block = document.createElement("div");
        block.className = "question-block";
        if (index === 0) block.classList.add("active");
        block.dataset.questionId = q.id;

        block.innerHTML = `
            <div class="question-content-row">
                ${q.imageUrl ? `<img class="question-image" src="${q.imageUrl}"${imageSizeStyleAttr(q.imageWidth, q.imageHeight)} alt="Savol rasmi">` : ""}
                <h3>${index + 1}. ${q.questionText}</h3>
            </div>
            ${isMulti ? `<p class="multi-correct-hint">☑️ Bir nechta to'g'ri javob bo'lishi mumkin</p>` : ""}
            <ul>
                ${q.answers.map((a, i) => `
                    <li>
                        <label>
                            <input type="${isMulti ? "checkbox" : "radio"}" name="q-${q.id}" data-answer-id="${a.id}">
                            <div class="answer-content-row">
                                ${a.imageUrl ? `<img class="answer-image" src="${a.imageUrl}"${imageSizeStyleAttr(a.imageWidth, a.imageHeight)} alt="Javob rasmi">` : ""}
                                <span><b>${ANSWER_LETTERS[i] || ""}) </b>${a.answerText}</span>
                            </div>
                        </label>
                    </li>
                `).join("")}
            </ul>
            <div class="actions-bottom">
                ${testState.mode === "practice" ? `
                <button class="action-btn comment"
                    data-comment="${encodeURIComponent(correctAnswer?.commentary || '')}"
                    data-comment-image="${encodeURIComponent(correctAnswer?.commentaryImageUrl || '')}"
                    data-comment-video="${encodeURIComponent(correctAnswer?.commentaryVideoUrl || '')}"
                    onclick="openCommentModal(this)">
                💬
                </button>` : ""}

                <button onclick="goToPreviousQuestion()">AVVALGI</button>
                <button onclick="goToNextQuestion()">KEYINGI</button>
                <button onclick="finishTest()">Test Natijasi</button>
            </div>
        `;
        container.appendChild(block);
    });
    showQuestion(0);
}

function getQuestions() {
    return document.querySelectorAll('.question-block');
}

function getActiveQuestion() {
    return document.querySelector('.question-block.active');
}

function getActiveIndex() {
    const questions = getQuestions();
    return [...questions].findIndex(q => q.classList.contains("active"));
}

function showQuestion(index) {
    const questions = getQuestions();
    if (!questions.length) return;

    document.querySelectorAll('.question-block').forEach(q => {
        q.classList.remove('active');
        q.style.display = 'none';
    });

    if (index < 0) index = questions.length - 1;
    if (index >= questions.length) index = 0;


    const active = document.querySelectorAll('.question-block')[index];
    active.classList.add('active');
    active.style.display = 'block';

    // Har bir savol ko'rsatilganda sahifa ENG TEPAGA (rejim yorlig'i +
    // "🔙 Darsga qaytish" tugmasi ham ko'rinadigan holatda) qaytadi —
    // avval bu yerda "active.scrollIntoView({block:'start'})" ishlatilgan
    // edi, lekin u savol blokini navbar OSTIGA "yashiruvchi" (haqiqiy
    // production bug — foydalanuvchi HAR safar yangi savolga o'tganda
    // qo'lda tepaga tortishga majbur bo'lardi) noto'g'ri joylashuv
    // berardi, chunki navbar/mode-label/qaytish tugmasi savol blokidan
    // OLDIN turadi — scrollIntoView ularni ham ekrandan chiqarib
    // yuborardi.
    window.scrollTo({top: 0, behavior: 'smooth'});

    focusFirstAnswer();
}

function goToNextQuestion() {
    showQuestion(getActiveIndex() + 1);
}

function goToPreviousQuestion() {
    showQuestion(getActiveIndex() - 1);
}
//Выбор ответа
document.addEventListener("change", (e) => {
    if (e.target.type !== "radio" && e.target.type !== "checkbox") return;

    const block = e.target.closest('.question-block');
    if (!block) return;
    const questionId = Number(block.dataset.questionId);
    const answerId = Number(e.target.dataset.answerId);

    if (e.target.type === "radio") {
        testState.answers.set(questionId, new Set([answerId]));
    } else {
        // Checkbox (ko'p to'g'ri javobli savol) — belgi/bekor qilish
        // MAVJUD to'plamga qo'shiladi/olib tashlanadi, boshqa javoblarga
        // tegmaydi (radio'dan farqli — bir nechtasi birga tanlanishi mumkin).
        const current = testState.answers.get(questionId) || new Set();
        if (e.target.checked) current.add(answerId);
        else current.delete(answerId);

        if (current.size === 0) testState.answers.delete(questionId);
        else testState.answers.set(questionId, current);
    }

    // Checkbox'da savol "javob berilgan" <-> "berilmagan" ikkala tomonga
    // ham o'tishi mumkin (oxirgi belgi bekor qilinsa) — shu sabab radio'dagi
    // kabi faqat "wasAnsweredBefore=false" holatida emas, HAR DOIM
    // yangilanadi (progress-bar arzon hisoblash, muammo yo'q).
    updateProgress();
});

function startTest() {
    testState.startedAt = Date.now();
    testState.currentIndex = 0;
    testState.answers.clear();
    testState.allAnsweredNotified = false;

    // ✅ ПОКАЗЫВАЕМ progress + timer
    document.getElementById("progress").style.height = "0%";
    document.getElementById("progressFraction").innerText = `0/${testState.questions.length}`;
    document.getElementById("progressPercent").innerText = "0%";
    document.getElementById("progressWrapper").classList.remove("hidden");
    document.body.classList.remove("no-progress");

    const timerEl = document.getElementById("timer");

    if (testState.mode === "practice") {
        timerEl.style.display = "none";
    } else {
        timerEl.style.display = "flex";
        startTimer(testState.time);
    }


    renderQuestions(testState.questions);

    document.body.classList.add("test-started");
}

function finishTest() {

    testState.finished = true;

    stopTimer(); // 🔴 ВАЖНО

    // 👻 Скрыть таймер
    const timerEl = document.getElementById("timer");
    if (timerEl) {
        timerEl.style.display = "none";
    }

    const unanswered = testState.questions.filter(q => !testState.answers.has(q.id));
    if (unanswered.length > 0) {
        showAlertModal(`❗ Barcha savollarga javob bering, (${unanswered.length} ta qoldi)`);
        return;
    }

    // ✅ СКРЫВАЕМ progress + timer
    document.getElementById("progressWrapper")
        .classList.add("hidden");

    // ✅ корректируем отступы
    document.body.classList.add("no-progress");

    testState.finishedAt = Date.now();
    calculateResult();

    saveTestResult();
}

function saveTestResult() {

    const payload = {
        testSessionId: testState.testSessionId,
        startedAt: testState.startedAt,
        finishedAt: testState.finishedAt,
        // Ajratilgan (mavjud) savollar soni — javob berilganlar emas.
        // Vaqt tugab avtomatik yakunlanganda ba'zi savollarga ulgurilmagan
        // bo'lishi mumkin, natija ("X/Y") shu haqiqiy sonlarga nisbatan
        // hisoblanishi uchun.
        totalQuestions: testState.allQuestions.length,
        // "answerIds" — ko'p to'g'ri javobli savollar uchun (backend
        // AnswerResultDto/MultiAnswerUtil.resolveSubmittedIds — ro'yxat
        // bo'lsa SHU ustun keladi, bitta javobli savolda ham bitta
        // elementli ro'yxat sifatida to'g'ri ishlaydi).
        answers: Array.from(testState.answers.entries()).map(
            ([questionId, answerIdSet]) => ({
                questionId,
                answerIds: Array.from(answerIdSet)
            })
        )
    };

    fetch("/api/test-session/finish", {
        method: "POST",
        headers: {"Content-Type": "application/json"},
        body: JSON.stringify(payload)
    })
        .then(r => {
            if (!r.ok) throw new Error("Ошибка сохранения теста");
        })
        .then(() => {
            console.log("✅ Test sessiyasi saqlandi");
        });
}

function calculateResult() {
    let correct = 0;
    testState.questions.forEach(q => {
        // "hammasi yoki hech narsa" — backend#MultiAnswerUtil.isCorrect
        // bilan AYNAN bir xil qoida (talaba ANIQ barcha to'g'ri
        // variantlarni belgilagan bo'lishi kerak). Bitta to'g'ri javobli
        // savolda bu eskicha "bitta to'g'ri tanlangan" tekshiruvi bilan
        // bir xil natija beradi.
        const selectedSet = testState.answers.get(q.id) || new Set();
        const correctIds = new Set(q.answers.filter(a => a.isTrue).map(a => a.id));
        if (correctIds.size > 0 && setsEqual(selectedSet, correctIds)) correct++;
    });

    const result = {
        total: testState.questions.length,
        correct,
        percent: Math.round((correct / testState.questions.length) * 100),
        durationSec: Math.floor((testState.finishedAt - testState.startedAt) / 1000)
    };

    showResult(result);
}

function showResult(result) {
    document.getElementById("questions").innerHTML = `
        <div class="result-card">
            <h2>📊 Natija</h2>

            <p>Jami savollar: <b>${result.total}</b></p>
            <p>To‘g‘ri javoblar: <b>${result.correct}</b></p>
            <p>Xato javoblar: <b>${result.total - result.correct}</b></p>

            <p>Foiz: <b>${result.percent}%</b></p>
            <p>Vaqt: <b>${result.durationSec} soniya</b></p>

            <div class="result-actions">
                <button onclick="restartTest()">🔄 Qayta boshlash</button>
                <button onclick="goBack()">⬅ Qayta sozlash</button>
                
                ${testState.mode === "practice" 
                ? `<button onclick="showWrongAnswers()">❌ Xatolarni ko‘rish</button>`
                : ""}

            </div>
        </div>
    `;
}

function restartTest() {
    testState.questions = testState.allQuestions;
    startTest();
}

function goBack() {
    history.back();
}

function showWrongAnswers() {

    document.getElementById("progressWrapper")
        .classList.add("hidden");

    document.body.classList.add("no-progress");


    // ⛔ Остановить таймер
    stopTimer();

    // 👻 Скрыть таймер
    const timerEl = document.getElementById("timer");
    if (timerEl) {
        timerEl.style.display = "none";
    }

    const container = document.getElementById("questions");
    container.innerHTML = "";

    let hasErrors = false;

    testState.questions.forEach((q, index) => {

        const selectedSet = testState.answers.get(q.id) || new Set();
        const correctAnswers = q.answers.filter(a => a.isTrue);
        const correctIds = new Set(correctAnswers.map(a => a.id));

        // если ответ верный (barcha to'g'ri variantlar, ORTIQCHASIZ va
        // KAMSIZ, tanlangan bo'lsa) — пропускаем
        if (correctAnswers.length === 0 || setsEqual(selectedSet, correctIds)) {
            return;
        }

        hasErrors = true;

        // "Izoh" — bitta UMUMIY maydon (admin formasidagi kabi), shu
        // sabab BIRINCHI to'g'ri javobdan olinadi, ko'p to'g'ri javobli
        // savolda ham (correctAnswers[0]).
        const primaryCorrect = correctAnswers[0];
        const selectedAnswers = q.answers.filter(a => selectedSet.has(a.id));
        // Ko'p to'g'ri javobli savolda BARCHA tanlangan/BARCHA to'g'ri
        // javoblar vergul bilan ko'rsatiladi (foydalanuvchi so'rovi,
        // 2026-09-06 — talaba UI bosqichi).
        const selectedText = selectedAnswers.length > 0
            ? selectedAnswers.map(a => a.answerText).join(", ")
            : "Javob tanlanmagan";
        const correctText = correctAnswers.map(a => a.answerText).join(", ");

        const block = document.createElement("div");
        block.className = "wrong-question-card";

        block.innerHTML = `
            <div class="question-content-row">
                ${q.imageUrl ? `<img class="question-image" src="${q.imageUrl}"${imageSizeStyleAttr(q.imageWidth, q.imageHeight)} alt="Savol rasmi">` : ""}
                <h3>❓ ${index + 1}. ${q.questionText}</h3>
            </div>

            <ul class="answers-review">
                <li class="wrong-answer">
                    ❌ Siz tanlagan javob${selectedAnswers.length > 1 ? "lar" : ""}:
                    <div>${selectedText}</div>
                </li>

                <li class="correct-answer">
                    ✅ To‘g‘ri javob${correctAnswers.length > 1 ? "lar" : ""}:
                    <div>${correctText}</div>
                </li>
            </ul>

            ${primaryCorrect.commentary ? `<div class="commentary-box">💬 Izoh: ${primaryCorrect.commentary}</div>` : ""}
            ${primaryCorrect.commentaryImageUrl ? `<img class="comment-image" src="${primaryCorrect.commentaryImageUrl}" alt="Izoh rasmi">` : ""}
            ${primaryCorrect.commentaryVideoUrl ? `<video class="comment-video" src="${primaryCorrect.commentaryVideoUrl}" controls></video>` : ""}
        `;

        container.appendChild(block);
    });

    if (!hasErrors) {
        container.innerHTML = `
            <div class="result-card">
                <h2>🎉 Tabriklaymiz!</h2>
                <p>Sizda xato javoblar yo‘q.</p>
                <div class="result-actions">
                <button onclick="restartTest()">🔄 Testni qayta boshlash</button>
                <button onclick="goBack()">⬅ Qayta sozlash</button>
                </div>
                
            </div>
        `;
        return;
    }
    /* === КНОПКИ ПОСЛЕ СПИСКА ОШИБОК === */
    const actions = document.createElement("div");
    actions.className = "result-actions";

    actions.innerHTML = `
    <button onclick="restartTest()">🔄 Testni qayta boshlash</button>
    <button onclick="repeatWrongOnly()">🧪 Faqat xatolar bilan test</button>
    <button onclick="goBack()">⬅ Qayta sozlash</button>
`;

    container.appendChild(actions);
}

function repeatWrongOnly() {
    const wrongQuestions = getWrongQuestions();
    if (wrongQuestions.length === 0) {
        showAlertModal("🎉 Xato savollar yo‘q");
        return;
    }

    testState.questions = wrongQuestions;
    testState.answers.clear();
    testState.currentIndex = 0;
    testState.startedAt = Date.now();
    testState.finishedAt = null;
    testState.allAnsweredNotified = false;

    const container = document.getElementById("questions");
    container.innerHTML = "";
    renderQuestions(wrongQuestions);
    showQuestion(0);
    focusFirstAnswer();
}

function focusFirstAnswer() {

    // берём активный вопрос
    const activeQuestion = getActiveQuestion();

    if (!activeQuestion) return;

    // ищем первый radio (yoki checkbox — ko'p to'g'ri javobli savolda)
    const firstRadio = activeQuestion.querySelector(
        'input[type="radio"], input[type="checkbox"]'
    );

    if (!firstRadio) return;

    // небольшой defer — чтобы гарантировать готовность DOM
    requestAnimationFrame(() => {
        firstRadio.focus();
    });
}

function getWrongQuestions() {
    return testState.questions.filter(q => {
        const selectedSet = testState.answers.get(q.id) || new Set();
        const correctIds = new Set(q.answers.filter(a => a.isTrue).map(a => a.id));
        return correctIds.size === 0 || !setsEqual(selectedSet, correctIds);
    });
}

document.addEventListener("keydown", (e) => {

    const activeQuestion = getActiveQuestion();

    if (!activeQuestion) return;

    const tag = e.target.tagName;

    // ⛔ ПОЛНОСТЬЮ БЛОКИРУЕМ стандартную навигацию radio
    if (tag === "INPUT") {
        e.preventDefault();
    }

    switch (e.key) {
        case "ArrowRight":
            e.preventDefault();
            goToNextQuestion();
            break;

        case "ArrowLeft":
            e.preventDefault();
            goToPreviousQuestion();
            break;

        case "ArrowUp":
        case "ArrowDown":
            e.preventDefault();
            moveAnswerCursor(e.key === "ArrowDown" ? 1 : -1);
            break;

        case "Enter":
            e.preventDefault();
            selectAnswerAndNext();
            break;

        case " ":
        case "Spacebar": //для старых браузеров
            e.preventDefault();
            selectAnswerOnly();
            break;
    }
});

function moveAnswerCursor(direction) {
    const question = getActiveQuestion();
    const radios = [...question.querySelectorAll('input[type="radio"], input[type="checkbox"]')];

    if (!radios.length) return;

    const index = radios.findIndex(r => r === document.activeElement);
    let nextIndex = index + direction;

    if (nextIndex < 0) nextIndex = radios.length - 1;
    if (nextIndex >= radios.length) nextIndex = 0;

    radios[nextIndex].focus();
}

function selectAnswerAndNext() {
    const focused = document.activeElement;
    if (!focused) return;

    if (focused.type === "radio") {
        focused.checked = true;
        focused.dispatchEvent(new Event("change", {bubbles: true}));

        // перейти к следующему вопросу
        setTimeout(() => {
            goToNextQuestion();
        }, 1000);
    } else if (focused.type === "checkbox") {
        // Ko'p to'g'ri javobli savolda Enter belgilashga TEGMAYDI (bitta
        // javobni "belgilab-o'tish" tushunchasi ma'nosiz — bir nechtasi
        // birga kerak bo'lishi mumkin), faqat KEYINGI savolga o'tkazadi.
        // Belgilash SPACE orqali (selectAnswerOnly — native checkbox
        // xatti-harakatiga mos, toggle).
        goToNextQuestion();
    }
}

function selectAnswerOnly() {
    const focused = document.activeElement;

    if (!focused) return;

    if (focused.type === "radio") {
        focused.checked = true;
    } else if (focused.type === "checkbox") {
        // SPACE — native checkbox xatti-harakati kabi TOGGLE (radio'dagi
        // "har doim true" emas, chunki checkbox'ni bekor qilish ham
        // kerak bo'lishi mumkin).
        focused.checked = !focused.checked;
    } else {
        return;
    }

    // 🔑 ЯВНО вызываем change для прохождения теста
    focused.dispatchEvent(new Event("change", {bubbles: true}));
}

function updateProgress() {
    const answered = testState.answers.size;
    const total = testState.questions.length;

    const percent = Math.round((answered / total) * 100);

    const bar = document.getElementById("progress");
    if (bar) {
        bar.style.height = percent + "%";
    }
    // Foydalanuvchi so'rovi: progress-bar USTIDA "2/5" kabi aniq son,
    // OSTIDA esa foiz — ikkalasi ham ko'rinib turadi.
    const fractionLabel = document.getElementById("progressFraction");
    if (fractionLabel) {
        fractionLabel.innerText = `${answered}/${total}`;
    }
    const percentLabel = document.getElementById("progressPercent");
    if (percentLabel) {
        percentLabel.innerText = percent + "%";
    }

    // Barcha savollarga javob berilgach — foydalanuvchi buni sezmasligi
    // mumkin edi (masalan Exam/Hard rejimida hali vaqt tugamagan bo'lsa,
    // yoki oxirgi savoldan "Keyingi" bosilsa ro'yxat boshiga qaytib
    // ketaveradi) va aslida test tugaganini bilmasdan qaytadan yecha
    // boshlashi mumkin edi. Shu sabab BIR MARTA (har testda) aniq eslatma.
    if (answered === total && !testState.allAnsweredNotified) {
        testState.allAnsweredNotified = true;
        const timeNote = testState.mode === "practice" ? "" : " Vaqtingiz hali bor —";
        showAlertModal(`✅ Siz barcha savollarga javob berdingiz!\n\n${timeNote} xohlasangiz javoblaringizni qayta ko'rib chiqishingiz mumkin ("AVVALGI"/"KEYINGI" tugmalari bilan). Tayyor bo'lsangiz, "Test Natijasi" tugmasini bosing.`);
    }
}

function openCommentModal(button) {
    const comment = decodeURIComponent(button.dataset.comment || "");
    const imageUrl = decodeURIComponent(button.dataset.commentImage || "");
    const videoUrl = decodeURIComponent(button.dataset.commentVideo || "");

    if (!comment.trim() && !imageUrl && !videoUrl) {
        showAlertModal("Izoh mavjud emas");
        return;
    }

    // Izohda matn, rasm va video birga bo'lishi mumkin.
    const body = document.getElementById("commentModalBody");
    body.innerHTML = `
        ${comment ? `<p>${comment}</p>` : ""}
        ${imageUrl ? `<img class="comment-image" src="${imageUrl}" alt="Izoh rasmi">` : ""}
        ${videoUrl ? `<video class="comment-video" src="${videoUrl}" controls></video>` : ""}
    `;

    document.getElementById("commentModal").classList.remove("hidden");
}

function closeCommentModal() {
    document.getElementById("commentModal").classList.add("hidden");
}

window.addEventListener("beforeunload", () => {

    if (!testState.finished && testState.testSessionId) {

        fetch("/api/test-session/cancel", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
                testSessionId: testState.testSessionId
            }),
            keepalive: true
        });
    }
});





