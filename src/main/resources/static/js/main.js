document.addEventListener("DOMContentLoaded", () => {
    const roleB = document.querySelector(".nav-center b[data-role]");
    if (!roleB) return;

    const role = roleB.dataset.role.trim();

    switch (role) {
        case "OWNER":
            roleB.style.color = "#b71c1c"; // красный
            break;
        case "ADMIN":
            roleB.style.color = "#856404"; // золотой
            break;
        case "USER":
            roleB.style.color = "#1b5e20"; // зелёный
            break;
    }
});

/*
/!*Если хотите оставить автоматический logout,
сделайте условие только при закрытии вкладки, но не при переходе на внутренние страницы.*!/
window.addEventListener("beforeunload", function (event) {
    if (document.activeElement.tagName === "A") return; // не логаут при переходе
    navigator.sendBeacon("/logout");
});  //Бу кўп муаммо чиқаряпти. Бошқа page га ўтганда logout бўлиб кетяпти.
*/

function startPractice() {
    // режим практики
    window.location.href = "/testConfigPage";
}

function startExam() {
    // режим экзамена
    window.location.href = "/testConfigPage";
}

function startHardMode() {
    // сложный режим
    window.location.href = "/testConfigPage";
}
