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
