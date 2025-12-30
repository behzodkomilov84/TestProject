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

        const topicId = document.getElementById("topicId").value;
        const questionText = document.getElementById("question").value.trim();

        const answerTextAreas = document.querySelectorAll(".answer textarea");
        const correctIndex = document.querySelector("input[name='correct']:checked")?.value;

        if (correctIndex === undefined) {
            alert("❌ To‘g‘ri javobni tanlang");
            return;
        }

        const answers = [];

        answerTextAreas.forEach((ta, index) => {
            answers.push({
                answerText: ta.value.trim(),
                isTrue: Number(correctIndex) === index
            });
        });

        const payload = {
            topicId: Number(topicId),
            question: questionText,
            answers: answers
        };

        try {
            const res = await fetch("/api/question/save", {
                method: "POST",
                headers: {
                    "Content-Type": "application/json"
                },
                body: JSON.stringify(payload)
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
    });

});
