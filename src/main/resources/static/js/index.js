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

