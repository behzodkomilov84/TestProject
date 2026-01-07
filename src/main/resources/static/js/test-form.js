document.addEventListener("DOMContentLoaded", () => {

    const form = document.getElementById("testForm");

    // автоподбор высоты textarea
    document.querySelectorAll(".auto-textarea").forEach(t => {
        t.addEventListener("input", () => {
            t.style.height = "auto";
            t.style.height = t.scrollHeight + "px";
        });
    });

    /*form.addEventListener("submit", async (e) => {
        e.preventDefault();

        const topicId = document.getElementById("topicId").value;
        const questionText = document.getElementById("question").value.trim();

        const answerTextAreas = document.querySelectorAll(".answer textarea");
        const correctIndex = document.querySelector("input[name='correct']:checked")?.value;

        if (correctIndex === undefined) {
            alert("❌ To‘g‘ri javobni tanlang");
            return;
        }

        //=================Javob textlarini yig'ish====================
        // собираем тексты ответов
        const texts = [];
        for (const ta of answerTextAreas) {
            const text = ta.value.trim();
            if (!text) {
                alert("❌ Barcha javoblarni to‘ldiring");
                ta.focus();
                return;
            }
            texts.push(text.toLowerCase());
        }

                    // проверка на уникальность
        const uniqueTexts = new Set(texts);
        if (uniqueTexts.size !== texts.length) {
            alert("❌ Javob variantlari bir xil bo‘lishi mumkin emas");
            return;
        }

                    // формируем ответы
        const answers = texts.map((text, index) => ({
            answerText: answerTextAreas[index].value.trim(),
            isTrue: Number(correctIndex) === index
        }));

        //=============================================================

        const payload = {
            topicId: Number(topicId),
            question: questionText,
            answers: answers
        };

        try {
            const res = await fetch("/api/question/save", {
                method: "POST", headers: {
                    "Content-Type": "application/json"
                }, body: JSON.stringify(payload)
            });

            if (!res.ok) {
                const err = await res.text();
                throw new Error(err);
            }

            alert("✅ Test muvaffaqiyatli saqlandi");
            form.reset();

        } catch (e) {
            console.error(e);
            alert("❌ Saqlashda xatolik");
        }
    });*/

    form.addEventListener("submit", async (e) => {
        e.preventDefault();

        const topicId = Number(document.getElementById("topicId").value);
        const questionText = document.getElementById("question").value.trim();

        const answersBlocks = document.querySelectorAll(".answer");
        const correctRadio = document.querySelector("input[name='correct']:checked");

        if (!correctRadio) {
            alert("❌ To‘g‘ri javobni tanlang");
            return;
        }

        const correctIndex = Number(correctRadio.value);

        // ================= Валидация ответов =================
        const texts = [];

        answersBlocks.forEach((block, index) => {
            const ta = block.querySelector("textarea.auto-textarea");
            const value = ta.value.trim();

            if (!value) {
                alert("❌ Barcha javoblarni to‘ldiring");
                ta.focus();
                throw new Error("Validation failed");
            }

            texts.push(value.toLowerCase());
        });

        // уникальность
        if (new Set(texts).size !== texts.length) {
            alert("❌ Javob variantlari bir xil bo‘lishi mumkin emas");
            return;
        }

        // ================= Формирование answers =================
        /*const answers = answersBlocks.map((block, index) => {
            const answerText = block.querySelector("textarea.auto-textarea").value.trim();
            const commentaryTextarea = block.querySelector(".commentary");

            return {
                answerText,
                isTrue: index === correctIndex,
                commentary: index === correctIndex
                    ? commentaryTextarea?.value.trim() || null
                    : null
            };
        });*/

        const answers = [...answersBlocks].map((block, index) => {
            const answerText = block.querySelector("textarea.auto-textarea").value.trim();
            const commentaryTextarea = block.querySelector(".commentary");

            return {
                answerText,
                isTrue: index === correctIndex,
                commentary: index === correctIndex
                    ? commentaryTextarea?.value.trim() || null
                    : null
            };
        });


        // ================= Payload =================
        const payload = {
            topicId,
            questionText,
            answers
        };

        console.log("CREATE PAYLOAD:", payload);

        try {
            const res = await fetch("/api/question/save", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });

            if (!res.ok) {
                throw new Error(await res.text());
            }

            alert("✅ Test muvaffaqiyatli saqlandi");
            form.reset();

        } catch (err) {
            console.error(err);
            alert("❌ Saqlashda xatolik");
        }
    });

});

document.addEventListener("change", (e) => {
    if (e.target.type !== "radio" || e.target.name !== "correct") return;

    const allAnswers = document.querySelectorAll(".answer");

    // скрываем всё
    allAnswers.forEach(answer => {
        answer.querySelector(".comment-btn")?.classList.add("hidden");
        answer.querySelector(".commentary")?.classList.add("hidden");
    });

    // показываем кнопку только у выбранного
    const selectedAnswer = e.target.closest(".answer");
    selectedAnswer.querySelector(".comment-btn")?.classList.remove("hidden");
});

document.addEventListener("click", (e) => {
    if (!e.target.classList.contains("comment-btn")) return;

    const answer = e.target.closest(".answer");
    const textarea = answer.querySelector(".commentary");

    textarea.classList.remove("hidden");
    textarea.focus();
});
