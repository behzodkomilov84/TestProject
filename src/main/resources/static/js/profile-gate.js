// Kursga kirishda profilni to'liq to'ldirishni talab qiladi
// (foydalanuvchi so'rovi, 2026-09-07: "Ish yoki o'qish joyi va
// lavozimini registration formdan olib tashla ... Kursga kirmoqchi
// bo'lganlar uchun sloy qil, yani profildagi hali to'ldirilmagan
// polyalarni to'ldirish uchun modal ochilsin va majburiy polyalarni
// to'ldirsin"). Keyinroq (shu kuni) workplace/jobTitle/telefon qaytadan
// registratsiya formasiga MAJBURIY sifatida qo'shildi va ism/familiya
// ham shu ro'yxatga qo'shildi ("бошқа мажбурий поляларни ҳам текширсин,
// мисол, исм, фамилия") — lekin bu tekshiruv HAMON kerak: Telegram/
// Google/Facebook orqali ro'yxatdan o'tganlar (UserServiceImpl#register
// dan umuman o'tmaydi) va eski (o'zgarishdan oldingi) hisoblar uchun bu
// maydonlar bo'sh bo'lishi mumkin — shu sabab bu yer BARCHA usul bilan
// ro'yxatdan o'tgan foydalanuvchiga bir xil talabni ta'minlaydigan
// yagona joy.
//
// ENDI navbar.html fragment orqali BARCHA sahifada (navbar bor joyda)
// ulanadi — faqat kurs sahifasida emas (foydalanuvchi so'rovi, 2026-09-08:
// "Har qanday kirishda ham... profildagi majburiy polyalarni to'ldirsin.
// Modal ko'rinishda"). Sahifa yuklanishi bilan avtomatik ishga tushadi,
// har bir alohida sahifani o'zgartirish shart emas.
//
// country-picker.js'ga bog'liq (telefon maydoni uchun, /profile'dagi
// bilan bir xil widget) — navbar.html'da shu skriptdan KEYIN ulanishi
// kerak (defer tartibi saqlanadi).

let profileGateMissing = null; // {firstName, lastName, workplace, jobTitle, phone} — shu safar aniqlangan bo'sh maydonlar
let profileGateCountries = null;
let profileGateCountryPicker = null;
let profileGateFullProfile = null; // to'liq /api/profile javobi — full-name PATCH uchun mavjud qiymatni topishda kerak

document.addEventListener("DOMContentLoaded", () => {
    fetch("/api/profile")
        .then(r => r.ok ? r.json() : Promise.reject(new Error("profil olinmadi")))
        .then(profile => {
            profileGateFullProfile = profile;
            const isBlank = v => !v || !String(v).trim();

            profileGateMissing = {
                firstName: isBlank(profile.firstName),
                lastName: isBlank(profile.lastName),
                workplace: isBlank(profile.workplace),
                jobTitle: isBlank(profile.jobTitle),
                phone: isBlank(profile.phoneNumber)
            };

            const hasMissing = Object.values(profileGateMissing).some(Boolean);
            if (hasMissing) {
                showProfileGateModal();
            }
        })
        .catch(err => console.error("Profil to'liqligini tekshirishda xatolik:", err));
});

let profileGateStylesInjected = false;

function injectProfileGateStyles() {
    if (profileGateStylesInjected) return;
    profileGateStylesInjected = true;

    const style = document.createElement("style");
    style.textContent = `
        .profile-gate-overlay {
            position: fixed; inset: 0; background: rgba(15, 23, 42, .6);
            backdrop-filter: blur(2px); display: flex; align-items: center;
            justify-content: center; z-index: 20000; padding: 16px;
            font-family: 'Poppins', system-ui, sans-serif;
        }
        .profile-gate-box {
            width: min(440px, 100%); background: #fff; padding: 28px 26px;
            border-radius: 16px; box-shadow: 0 25px 60px rgba(15, 23, 42, .35);
            box-sizing: border-box;
        }
        .profile-gate-box h2 { margin: 0 0 8px; font-size: 19px; color: #0d3b34; }
        .profile-gate-box p.profile-gate-desc {
            margin: 0 0 18px; color: #555; font-size: 14px; line-height: 1.4;
        }
        .profile-gate-box label {
            display: block; margin-bottom: 14px; font-size: 13px;
            font-weight: 600; color: #334155;
        }
        .profile-gate-box input {
            width: 100%; box-sizing: border-box; margin-top: 5px; padding: 10px 12px;
            font-size: 14px; border: 1.5px solid #cbd5e1; border-radius: 8px; outline: none;
            font-family: inherit; font-weight: normal; transition: border-color .15s ease, box-shadow .15s ease;
        }
        .profile-gate-box input:focus {
            border-color: #009579; box-shadow: 0 0 0 3px rgba(0, 149, 121, .15);
        }
        .profile-gate-error {
            color: #dc2626; font-size: 13px; font-weight: 600; margin: 0 0 12px;
        }
        .profile-gate-actions { display: flex; justify-content: flex-end; gap: 10px; margin-top: 6px; }
        .profile-gate-actions button {
            padding: 9px 20px; border-radius: 8px; border: none; font-size: 14px;
            cursor: pointer; font-weight: 600; font-family: inherit; transition: background .15s ease;
        }
        .profile-gate-cancel { background: #f1f5f9; color: #334155; }
        .profile-gate-cancel:hover { background: #e2e8f0; }
        .profile-gate-save { background: #009579; color: #fff; }
        .profile-gate-save:hover { background: #007a63; }
        .profile-gate-save:disabled { opacity: .6; cursor: not-allowed; }

        /* Telefon maydoni — davlat tanlash widget'i (country-picker.js)
           + raqam, bir qatorda ("Ish/o'qish joyingiz" bilan bir xil
           label ostidagi bo'shliqni saqlash uchun label emas, oddiy div). */
        .profile-gate-phone-row { display: flex; gap: 8px; margin-top: 5px; }
        .profile-gate-phone-row .country-picker { flex-shrink: 0; }
        .profile-gate-phone-row input { margin-top: 0; }
    `;
    document.head.appendChild(style);
}

// Faqat HALI TO'LDIRILMAGAN maydonlar so'raladi — masalan, eski
// foydalanuvchida ism/familiya/workplace/jobTitle bor-u, faqat telefon
// yo'q bo'lsa, modalda YAGONA telefon maydoni chiqadi (foydalanuvchi
// so'rovi: "агар мажбурий полиаларда маълумотлар бўлса, ... модал
// очилмасин" — bu qoida HAR BIR maydon darajasida qo'llaniladi).
function showProfileGateModal() {
    injectProfileGateStyles();

    const m = profileGateMissing;

    const nameFieldsHtml = (m.firstName || m.lastName) ? `
        <div class="form-row-gate" style="display:flex; gap:10px;">
            ${m.firstName ? `<label style="flex:1;">Ismingiz
                <input type="text" id="profileGateFirstName" placeholder="Ismingiz">
            </label>` : ""}
            ${m.lastName ? `<label style="flex:1;">Familiyangiz
                <input type="text" id="profileGateLastName" placeholder="Familiyangiz">
            </label>` : ""}
        </div>` : "";

    const workplaceHtml = m.workplace ? `
        <label>Ish yoki o'qish joyingiz
            <input type="text" id="profileGateWorkplace" placeholder="Masalan: 1-son maktab">
        </label>` : "";

    const jobTitleHtml = m.jobTitle ? `
        <label>Lavozimingiz
            <input type="text" id="profileGateJobTitle" placeholder="Masalan: shifokor, talaba">
        </label>` : "";

    const phoneHtml = m.phone ? `
        <label>Telefon raqamingiz
            <div class="profile-gate-phone-row">
                <div id="profileGateCountryPicker"></div>
                <input type="tel" id="profileGatePhone" placeholder="901234567">
            </div>
        </label>` : "";

    const overlay = document.createElement("div");
    overlay.className = "profile-gate-overlay";
    overlay.innerHTML = `
        <div class="profile-gate-box">
            <h2>📋 Profilingizni to'ldiring</h2>
            <p class="profile-gate-desc">Davom etishdan oldin quyidagi ma'lumotlarni to'ldiring — bu qaysi soha/kasb vakillari saytdan foydalanayotganini bilishga yordam beradi.</p>
            <p class="profile-gate-error" id="profileGateError" hidden></p>
            ${nameFieldsHtml}
            ${workplaceHtml}
            ${jobTitleHtml}
            ${phoneHtml}
            <div class="profile-gate-actions">
                <button type="button" class="profile-gate-cancel" id="profileGateCancel">Bekor qilish</button>
                <button type="button" class="profile-gate-save" id="profileGateSave">Davom etish</button>
            </div>
        </div>
    `;
    document.body.appendChild(overlay);

    if (m.phone) {
        fetch("/api/profile/phone/countries")
            .then(r => r.json())
            .then(countries => {
                profileGateCountries = countries;
                profileGateCountryPicker = initCountryPicker(
                    document.getElementById("profileGateCountryPicker"),
                    countries, "UZ", () => {}
                );
            })
            .catch(err => console.error("Davlatlar ro'yxati olinmadi:", err));
    }

    // "Bekor qilish" — endi BUTUN saytda ko'rsatilgani uchun (faqat kurs
    // sahifasida emas, foydalanuvchi so'rovi, 2026-09-08) qattiq bloklash
    // o'rniga shu joyida yopiladi — foydalanuvchi joriy sahifada davom
    // etaveradi, keyingi sahifa yuklanganda (hali to'ldirilmagan bo'lsa)
    // modal yana chiqadi.
    document.getElementById("profileGateCancel").addEventListener("click", () => {
        overlay.remove();
    });

    document.getElementById("profileGateSave").addEventListener("click", async () => {
        const errorEl = document.getElementById("profileGateError");
        errorEl.hidden = true;

        const firstName = m.firstName ? document.getElementById("profileGateFirstName").value.trim() : null;
        const lastName = m.lastName ? document.getElementById("profileGateLastName").value.trim() : null;
        const workplace = m.workplace ? document.getElementById("profileGateWorkplace").value.trim() : null;
        const jobTitle = m.jobTitle ? document.getElementById("profileGateJobTitle").value.trim() : null;
        const phoneRaw = m.phone ? document.getElementById("profileGatePhone").value.trim() : null;

        if ((m.firstName && !firstName) || (m.lastName && !lastName) || (m.workplace && !workplace) ||
            (m.jobTitle && !jobTitle) || (m.phone && !phoneRaw)) {
            errorEl.textContent = "❌ Barcha maydonlarni to'ldiring.";
            errorEl.hidden = false;
            return;
        }

        const saveBtn = document.getElementById("profileGateSave");
        saveBtn.disabled = true;

        // Ism/familiya bitta endpoint (full-name) orqali BIRGALIKDA
        // yuboriladi — ChangeFullNameDto ikkalasini ham talab qiladi,
        // shu sabab faqat bittasi yetishmagan bo'lsa ham, mavjudini
        // profildan olib to'ldiramiz (bo'sh qator bilan ustidan
        // yozib qo'ymaslik uchun).
        const requests = [];
        if (firstName !== null || lastName !== null) {
            requests.push(fetch("/api/profile/full-name", {
                method: "PATCH",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({
                    firstName: firstName ?? profileGateCurrentValue("firstName"),
                    lastName: lastName ?? profileGateCurrentValue("lastName")
                })
            }));
        }
        if (workplace !== null) {
            requests.push(fetch("/api/profile/workplace", {
                method: "PATCH",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({workplace})
            }));
        }
        if (jobTitle !== null) {
            requests.push(fetch("/api/profile/job-title", {
                method: "PATCH",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({jobTitle})
            }));
        }
        if (phoneRaw !== null) {
            const isoCode = profileGateCountryPicker ? profileGateCountryPicker.getIso() : "UZ";
            requests.push(fetch("/api/profile/phone", {
                method: "PATCH",
                headers: {"Content-Type": "application/json"},
                body: JSON.stringify({isoCode, rawNumber: phoneRaw})
            }));
        }

        try {
            const results = await Promise.all(requests);

            if (results.some(r => !r.ok)) {
                const failed = results.find(r => !r.ok);
                const data = await failed.json().catch(() => ({}));
                errorEl.textContent = data.error || "❌ Saqlashda xatolik yuz berdi, qayta urinib ko'ring.";
                errorEl.hidden = false;
                saveBtn.disabled = false;
                return;
            }

            overlay.remove();
        } catch (err) {
            console.error(err);
            errorEl.textContent = "❌ Tarmoq xatosi, qayta urinib ko'ring.";
            errorEl.hidden = false;
            saveBtn.disabled = false;
        }
    });
}

// full-name endpoint ikkala maydonni ham birga talab qiladi — agar
// faqat bittasi (masalan familiya) yetishmayotgan bo'lsa, ikkinchisining
// (ism) profilda ALLAQACHON bor qiymatini shu yordamida topamiz.
function profileGateCurrentValue(field) {
    return profileGateFullProfile ? (profileGateFullProfile[field] || "") : "";
}
