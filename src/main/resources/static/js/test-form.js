document.addEventListener("DOMContentLoaded", () => {

    const form = document.getElementById("testForm");

    // автоподбор высоты textarea
    document.querySelectorAll(".auto-textarea").forEach(t => {
        t.addEventListener("input", () => {
            t.style.height = "auto";
            t.style.height = t.scrollHeight + "px";
        });
    });

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

function importExcel() {
    document.getElementById("excelFile").click();
}

document.getElementById("excelFile").addEventListener("change", async function () {
    const file = this.files[0];
    if (!file) return;

    const topicId = document.getElementById("topicId").value;

    const formData = new FormData();
    formData.append("file", file);
    formData.append("topicId", topicId);

    const res = await fetch("/api/import/excel", {
        method: "POST",
        body: formData
    });

    const data = await res.json();
    showResult(data);
});

function showResult(data) {
    const modal = document.getElementById("importModal");
    const title = document.getElementById("importTitle");
    const body = document.getElementById("importBody");

    if (data.success) {
        title.textContent = "Import successful";
        body.textContent = `Imported: ${data.imported} questions`;
    } else {
        title.textContent = "Import errors";
        body.textContent = data.errors.join("\n");
    }

    modal.classList.remove("hidden");
}

function closeModal() {
    document.getElementById("importModal").classList.add("hidden");
}

function downloadTemplate() {
    window.location.href = "/api/export/template";
}

