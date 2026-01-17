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

function toggleMenu() {
    document.getElementById("nav-menu").classList.toggle("active");
}

function closeMenu() {
    document.getElementById("nav-menu").classList.remove("active");
}

/* закрытие при клике вне меню */
document.addEventListener("click", e => {
    const menu = document.getElementById("nav-menu");
    const burger = document.querySelector(".burger");

    if (!menu.contains(e.target) && !burger.contains(e.target)) {
        menu.classList.remove("active");
    }
});
