// Bo'limlar ko'rinish maydoniga kirganda yumshoq paydo bo'lishi
// (foydalanuvchi so'rovi, 2026-09-08: yangi bosh sahifa uchun) —
// IntersectionObserver, Bootstrap'ning o'zida bunday "scroll reveal"
// tayyor emas, shu sabab yengil JS bilan.
document.addEventListener("DOMContentLoaded", () => {
    const sections = document.querySelectorAll(".fade-in-section");
    if (!sections.length) return;

    if (!("IntersectionObserver" in window)) {
        sections.forEach(s => s.classList.add("in-view"));
        return;
    }

    const observer = new IntersectionObserver((entries) => {
        entries.forEach(entry => {
            if (entry.isIntersecting) {
                entry.target.classList.add("in-view");
                observer.unobserve(entry.target);
            }
        });
    }, {threshold: 0.12});

    sections.forEach(s => observer.observe(s));
});

function startPractice() {
    // режим практики
    sessionStorage.setItem("testMode", "practice");
    window.location.href = "/testConfigPage";
}

function startExam() {
    // режим экзамена
    sessionStorage.setItem("testMode", "exam");
    window.location.href = "/testConfigPage";
}

function startHardMode() {
    // сложный режим
    sessionStorage.setItem("testMode", "hard");
    window.location.href = "/testConfigPage";
}

function showError(msg) {

    document.getElementById("errorText").innerText = msg;

    new bootstrap.Modal(
        document.getElementById("errorModal")
    ).show();
}

// ⭐ Глобальный ловец JS ошибок
window.onerror = function (message, source, line, col, error) {

    const text = `
JS Error:
${message}

File: ${source}
Line: ${line}
`;

    showError(text);

    console.error("Captured JS error:", error);

    return true; // предотвращает стандартный alert браузера
};

// ⭐ Ловец Promise / fetch ошибок
window.onunhandledrejection = function (event) {

    const err = event.reason;

    const text = typeof err === "object"
        ? err.message
        : err;

    showError("Async error:\n" + text);

    console.error("Captured async error:", err);
};
