const ROLE = document.body.dataset.role;
// OWNER barcha kurslarni, ADMIN esa faqat o'zi yaratgan kurslarni
// boshqara oladi — ikkalasi ham "+ Yangi kurs yaratish" tugmasini
// ko'rishi kerak (aks holda ADMIN kurs yarata olmaydi).
const CAN_CREATE_COURSE = ROLE === "ROLE_OWNER" || ROLE === "ROLE_ADMIN";

document.addEventListener("DOMContentLoaded", () => {
    if (CAN_CREATE_COURSE) {
        document.querySelectorAll(".owner-only-el").forEach(el => el.style.display = "");
    }
    loadCourses();
});

function loadCourses() {
    fetch("/api/courses")
        .then(r => r.ok ? r.json() : [])
        .then(renderCourses)
        .catch(err => {
            console.error(err);
            document.getElementById("coursesGrid").innerHTML =
                `<div class="courses-empty">Kurslarni yuklashda xatolik</div>`;
        });
}

function renderCourses(courses) {
    const grid = document.getElementById("coursesGrid");

    if (!courses.length) {
        grid.innerHTML = `<div class="courses-empty">Hali kurslar yo'q</div>`;
        return;
    }

    grid.innerHTML = courses.map(c => {
        let badge;
        if (!c.published) {
            badge = `<span class="course-badge draft">Qoralama</span>`;
        } else if (c.free) {
            badge = `<span class="course-badge free">🆓 Bepul</span>`;
        } else if (c.subscribed) {
            badge = `<span class="course-badge subscribed">✅ Obuna bor</span>`;
        } else {
            // Narxi belgilangan bo'lsa — foydalanuvchi obuna so'rovini
            // yuborishdan oldin qancha to'lashini ko'rib turishi uchun.
            const priceText = c.price ? ` — ${formatPrice(c.price)} so'm` : "";
            badge = `<span class="course-badge locked">🔒 Obuna kerak${priceText}</span>`;
        }

        const cover = c.coverImageUrl
            ? `<img class="course-card-cover" src="${c.coverImageUrl}" alt="">`
            : `<div class="course-card-cover"></div>`;

        return `
            <div class="course-card" onclick="location.href='/courses/${c.id}'">
                ${cover}
                <div class="course-card-body">
                    <h3 class="course-card-title">${escapeHtml(c.title)}</h3>
                    <p class="course-card-desc">${escapeHtml(c.description || "")}</p>
                    <div class="course-card-footer">
                        <span>${c.sectionCount} bo'lim</span>
                        ${badge}
                    </div>
                </div>
            </div>
        `;
    }).join("");
}

function escapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text;
    return div.innerHTML;
}

// "150000" -> "150 000" — minglik ajratkichi doim bo'shliq bo'lishi uchun
// (toLocaleString brauzer/OS lokaliga qarab boshqa belgi ishlatishi mumkin).
function formatPrice(price) {
    return String(Math.round(Number(price))).replace(/\B(?=(\d{3})+(?!\d))/g, " ");
}

/* ===== OWNER: kurs yaratish ===== */

function openCreateCourseForm() {
    document.getElementById("createCourseForm").style.display = "flex";
    onNewCourseFreeToggle();
}

function closeCreateCourseForm() {
    document.getElementById("createCourseForm").style.display = "none";
}

// "🆓 Bepul kurs" belgilansa — narx maydoni keraksiz, yashiriladi.
function onNewCourseFreeToggle() {
    const free = document.getElementById("newCourseFree").checked;
    document.getElementById("newCoursePriceField").style.display = free ? "none" : "block";
}

async function submitCreateCourse() {
    const title = document.getElementById("newCourseTitle").value.trim();
    const description = document.getElementById("newCourseDescription").value.trim();
    const fileInput = document.getElementById("newCourseCoverFile");

    if (!title) {
        alert("❌ Kurs nomini kiriting");
        return;
    }

    let coverImageUrl = null;

    try {
        if (fileInput.files[0]) {
            document.getElementById("newCourseCoverStatus").textContent = "Yuklanmoqda...";
            const formData = new FormData();
            formData.append("image", fileInput.files[0]);
            const uploadRes = await fetch("/api/courses/upload-cover", { method: "POST", body: formData });
            const uploadData = await uploadRes.json().catch(() => ({}));
            if (!uploadRes.ok) {
                alert(uploadData.error || "Rasm yuklashda xatolik");
                return;
            }
            coverImageUrl = uploadData.url;
        }

        const free = document.getElementById("newCourseFree").checked;
        const priceValue = document.getElementById("newCoursePrice").value;
        const price = !free && priceValue ? Number(priceValue) : null;

        const res = await fetch("/api/courses", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ title, description, coverImageUrl, published: false, free, price })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            alert(data.error || "Kurs yaratishda xatolik");
            return;
        }

        location.href = "/courses/" + data.id;
    } catch (err) {
        console.error(err);
        alert("Tarmoq xatoligi");
    }
}
