document.addEventListener("DOMContentLoaded", () => {
    const container = document.getElementById("test-details");
    const summary = document.getElementById("test-summary");

    const testId = container.dataset.testId;

    if (!testId) {
        container.innerText = "Ошибка: ID теста не найден";
        return;
    }

    fetch(`/api/profile/history/${testId}`)
        .then(r => {
            if (!r.ok) throw new Error("Ошибка загрузки теста");
            return r.json();
        })
        .then(data => {
            container.innerHTML = `
                <p><b>Начало:</b> ${new Date(data.startedAt).toLocaleString()}</p>
                <p><b>Окончание:</b> ${data.finishedAt ? new Date(data.finishedAt).toLocaleString() : "—"}</p>
            `;

            summary.innerHTML = `
                <li>Всего вопросов: ${data.totalQuestions}</li>
                <li>Правильных: ${data.correctAnswers}</li>
                <li>Ошибок: ${data.wrongAnswers}</li>
                <li>Процент: ${data.percent}%</li>
                <li>Длительность: ${data.durationSec} сек</li>
            `;
        })
        .catch(err => {
            container.innerText = "Ошибка загрузки данных";
            console.error(err);
        });
});
