let currentProfile = null;

document.addEventListener("DOMContentLoaded", () => {
    fetch("/api/profile")
        .then(r => r.json())
        .then(data => {
            currentProfile = data;

            document.getElementById("username").innerText = data.username;
            document.getElementById("email").innerText = data.email || "— (kiritilmagan)";
            document.getElementById("phone").innerText = data.phoneNumberFormatted || "— (kiritilmagan)";
            // Dual-role: foydalanuvchida bir nechta rol bo'lishi mumkin
            // (masalan, ham o'qituvchi, ham o'quvchi) — barchasi ko'rsatiladi.
            document.getElementById("role").innerText =
                (data.roles || []).map(r => r.replace("ROLE_", "")).join(", ");

            loadPaymentConfig(data.roles || []);
        });

    loadPhoneCountries();

    document.getElementById("edit").addEventListener("click", enableEditUsername);
    document.getElementById("save-username").addEventListener("click", saveUsername);
    document.getElementById("cancel-username").addEventListener("click", cancelUsernameEdit);

    document.getElementById("edit-email").addEventListener("click", enableEditEmail);
    document.getElementById("save-email").addEventListener("click", saveEmail);
    document.getElementById("cancel-email").addEventListener("click", cancelEmailEdit);

    document.getElementById("edit-phone").addEventListener("click", enableEditPhone);
    document.getElementById("save-phone").addEventListener("click", savePhone);
    document.getElementById("cancel-phone").addEventListener("click", cancelPhoneEdit);
});

/* Test tarixi (paginatsiya bilan) endi /student sahifasidagi "Statistika"
   tugmasida ko'rsatiladi (student/student.js -> loadStatistics()), bir xil
   /api/profile/history manbasidan. */


function enableEditUsername() {
    // Boshqa maydonlarda saqlanmagan tahrirlash bo'lsa, avval uni bekor qilamiz —
    // aks holda bir vaqtda bir nechta maydon tahrirlash rejimida qolib ketardi.
    cancelEmailEdit();
    cancelPhoneEdit();

    const current = document.getElementById("username").innerText;

    document.getElementById("username-input").value = current;

    document.getElementById("username-view").style.display = "none";
    document.getElementById("username-edit").style.display = "inline";
    document.getElementById("edit").style.display = "none";
}

function cancelUsernameEdit() {
    document.getElementById("username-edit").style.display = "none";
    document.getElementById("username-view").style.display = "inline";
    document.getElementById("edit").style.display = "inline";
}



function enableEditEmail() {
    cancelUsernameEdit();
    cancelPhoneEdit();

    const current = document.getElementById("email").innerText;
    const input = document.getElementById("email-input");

    input.value = current.startsWith("—") ? "" : current;

    document.getElementById("email-view").style.display = "none";
    document.getElementById("email-edit").style.display = "inline";
    document.getElementById("edit-email").style.display = "none";
}

function cancelEmailEdit() {
    document.getElementById("email-edit").style.display = "none";
    document.getElementById("email-view").style.display = "inline";
    document.getElementById("edit-email").style.display = "inline";
}

function saveEmail() {
    const newEmail = document.getElementById("email-input").value.trim();

    if (!newEmail || !newEmail.includes("@")) {
        showAlertModal("To'g'ri email kiriting");
        return;
    }

    fetch("/api/profile/email", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ newEmail })
    })
        .then(async r => {
            if (!r.ok) {
                const data = await r.json().catch(() => ({}));
                throw new Error(data.error || "Xatolik yuz berdi");
            }
        })
        .then(() => {
            document.getElementById("email").innerText = newEmail;

            document.getElementById("email-edit").style.display = "none";
            document.getElementById("email-view").style.display = "inline";
            document.getElementById("edit-email").style.display = "inline";

            showAlertModal("✅ Email saqlandi. Endi parolni tiklashda zaxira kanal sifatida ishlatiladi.");
        })
        .catch(err => {
            showAlertModal(err.message || "Bu email band yoki xatolik");
        });
}

/* ===== Telefon raqam (xalqaro, istalgan davlat, bayroqli tanlov) ===== */

let phoneCountriesCache = null;
let phoneCountryPicker = null;

function loadPhoneCountries() {
    fetch("/api/profile/phone/countries")
        .then(r => r.json())
        .then(countries => {
            phoneCountriesCache = countries;
            phoneCountryPicker = initCountryPicker(
                document.getElementById("phone-country-picker"),
                countries,
                "UZ",
                () => {}
            );
        })
        .catch(err => console.error(err));
}

function enableEditPhone() {
    cancelUsernameEdit();
    cancelEmailEdit();

    const input = document.getElementById("phone-input");

    if (currentProfile && phoneCountryPicker) {
        phoneCountryPicker.setIso(currentProfile.phoneCountryIso || "UZ");
        input.value = currentProfile.phoneNationalNumber || "";
    }

    document.getElementById("phone-view").style.display = "none";
    document.getElementById("phone-edit").style.display = "inline";
    document.getElementById("edit-phone").style.display = "none";
}

function cancelPhoneEdit() {
    document.getElementById("phone-edit").style.display = "none";
    document.getElementById("phone-view").style.display = "inline";
    document.getElementById("edit-phone").style.display = "inline";
}

function savePhone() {
    const isoCode = phoneCountryPicker ? phoneCountryPicker.getIso() : "UZ";
    const rawNumber = document.getElementById("phone-input").value.trim();

    if (!rawNumber) {
        showAlertModal("Telefon raqamni kiriting");
        return;
    }

    fetch("/api/profile/phone", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ isoCode, rawNumber })
    })
        .then(async r => {
            if (!r.ok) {
                const data = await r.json().catch(() => ({}));
                throw new Error(data.error || "Xatolik yuz berdi");
            }
            return fetch("/api/profile").then(r2 => r2.json());
        })
        .then(data => {
            currentProfile = data;
            document.getElementById("phone").innerText = data.phoneNumberFormatted || "— (kiritilmagan)";

            document.getElementById("phone-edit").style.display = "none";
            document.getElementById("phone-view").style.display = "inline";
            document.getElementById("edit-phone").style.display = "inline";

            showAlertModal("✅ Telefon raqam saqlandi");
        })
        .catch(err => {
            showAlertModal(err.message || "Telefon raqamda xatolik");
        });
}

function saveUsername() {
    const newUsername = document.getElementById("username-input").value.trim();

    if (newUsername.length < 3) {
        showAlertModal("Username juda qisqa");
        return;
    }

    fetch("/api/profile/username", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ newUsername })
    })
        .then(r => {
            if (!r.ok) throw new Error();
            return r;
        })
        .then(() => {
            document.getElementById("username").innerText = newUsername;

            document.getElementById("username-edit").style.display = "none";
            document.getElementById("username-view").style.display = "inline";
            document.getElementById("edit").style.display = "inline";

            showAlertModal("Username o'zgartirildi");
        })
        .catch(() => {
            showAlertModal("Bu username band yoki xatolik");
        });
}

/*ОТКРЫТЬ / ЗАКРЫТЬ МОДАЛКУ*/
document.getElementById("open-password-modal")
    .addEventListener("click", () => {
        document.getElementById("password-modal").classList.remove("hidden");
    });

document.getElementById("close-password-modal")
    .addEventListener("click", closePasswordModal);

function closePasswordModal() {
    document.getElementById("password-modal").classList.add("hidden");
    document.getElementById("currentPassword").value = "";
    document.getElementById("newPassword").value = "";
}

/*СОХРАНЕНИЕ ПАРОЛЯ (API)*/
document.getElementById("save-password")
    .addEventListener("click", changePassword);

function changePassword() {
    const currentPassword = document.getElementById("currentPassword").value;
    const newPassword = document.getElementById("newPassword").value;

    if (!currentPassword || !newPassword) {
        showAlertModal("Barcha maydonlarni to‘ldiring");
        return;
    }

    if (newPassword.length < 6) {
        showAlertModal("Parol kamida 6 belgidan iborat bo‘lishi kerak");
        return;
    }

    fetch("/api/profile/password", {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
            currentPassword,
            newPassword
        })
    })
        .then(r => {
            if (!r.ok) throw new Error();
            showAlertModal("Parol o‘zgartirildi. Qayta kiring.");
            location.href = "/logout";
        })
        .catch(() => {
            showAlertModal("Hozirgi parol noto‘g‘ri");
        });
}
//=========================================================

/* ===== Onlayn to'lov (Click) — ROLE_ADMIN obunasini o'zi sotib olish ===== */

function loadPaymentConfig(roles) {
    if (roles.includes("ROLE_OWNER")) return; // OWNER'ga kerak emas

    fetch("/api/payments/config")
        .then(r => r.ok ? r.json() : null)
        .then(config => {
            if (!config || !config.clickEnabled) return;

            document.getElementById("onlinePaymentSection").style.display = "block";
            document.getElementById("pricePerMonthText").textContent =
                `1 oy = ${Number(config.pricePerMonthSom).toLocaleString("uz-UZ")} so'm`;

            document.getElementById("payWithClickBtn").style.display = "inline-block";
        })
        .catch(err => console.error(err));
}

async function startPayment(provider) {
    const durationMonths = Number(document.getElementById("paymentMonths").value) || 1;

    try {
        const res = await fetch("/api/payments/orders", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ durationMonths, provider })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }

        location.href = data.checkoutUrl;
    } catch (err) {
        console.error(err);
        showAlertModal("Tarmoq xatoligi");
    }
}
