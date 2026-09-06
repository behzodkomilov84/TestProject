// Kursga kirishda profilni to'liq to'ldirishni talab qiladi
// (foydalanuvchi so'rovi, 2026-09-07: "Ish yoki o'qish joyi va
// lavozimini registration formdan olib tashla ... Kursga kirmoqchi
// bo'lganlar uchun sloy qil, yani profildagi hali to'ldirilmagan
// polyalarni to'ldirish uchun modal ochilsin va majburiy polyalarni
// to'ldirsin" — bu ikki maydon endi ro'yxatdan o'tishda emas, birinchi
// marta biror kursga kirishga urinilganda so'raladi).
//
// Bitta joyda, sahifa yuklanishi bilan avtomatik ishga tushadi
// (courseDetail.html) — "/courses/{id}"ga qanday kirilishidan qat'i
// nazar (katalogdan bosib, to'g'ridan-to'g'ri havola orqali yoki
// bookmark orqali) bir xil ishlaydi, courses.js'dagi har bir alohida
// bosish joyini o'zgartirish shart emas.
//
// Hech qanday tashqi CSS/HTML'ga bog'liq emas (promptModal.js bilan
// bir xil g'oya) — shunchaki <script src="/js/profile-gate.js" defer>
// qo'shish yetarli.

document.addEventListener("DOMContentLoaded", () => {
    fetch("/api/profile")
        .then(r => r.ok ? r.json() : Promise.reject(new Error("profil olinmadi")))
        .then(profile => {
            const missingWorkplace = !profile.workplace || !profile.workplace.trim();
            const missingJobTitle = !profile.jobTitle || !profile.jobTitle.trim();
            if (missingWorkplace || missingJobTitle) {
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
    `;
    document.head.appendChild(style);
}

function showProfileGateModal() {
    injectProfileGateStyles();

    const overlay = document.createElement("div");
    overlay.className = "profile-gate-overlay";
    overlay.innerHTML = `
        <div class="profile-gate-box">
            <h2>📋 Profilingizni to'ldiring</h2>
            <p class="profile-gate-desc">Kursga kirishdan oldin quyidagi ma'lumotlarni to'ldiring — bu qaysi soha/kasb vakillari saytdan foydalanayotganini bilishga yordam beradi.</p>
            <p class="profile-gate-error" id="profileGateError" hidden></p>
            <label>Ish yoki o'qish joyingiz
                <input type="text" id="profileGateWorkplace" placeholder="Masalan: 1-son maktab">
            </label>
            <label>Lavozimingiz
                <input type="text" id="profileGateJobTitle" placeholder="Masalan: shifokor, talaba">
            </label>
            <div class="profile-gate-actions">
                <button type="button" class="profile-gate-cancel" id="profileGateCancel">Bekor qilish</button>
                <button type="button" class="profile-gate-save" id="profileGateSave">Davom etish</button>
            </div>
        </div>
    `;
    document.body.appendChild(overlay);

    // "Bekor qilish" — kursga kira olmaydi, katalogga qaytariladi
    // (foydalanuvchi maydonlarni to'ldirmasdan kursni ko'ra olmaydi).
    document.getElementById("profileGateCancel").addEventListener("click", () => {
        location.href = "/courses";
    });

    document.getElementById("profileGateSave").addEventListener("click", async () => {
        const workplace = document.getElementById("profileGateWorkplace").value.trim();
        const jobTitle = document.getElementById("profileGateJobTitle").value.trim();
        const errorEl = document.getElementById("profileGateError");
        errorEl.hidden = true;

        if (!workplace || !jobTitle) {
            errorEl.textContent = "❌ Ikkala maydonni ham to'ldiring.";
            errorEl.hidden = false;
            return;
        }

        const saveBtn = document.getElementById("profileGateSave");
        saveBtn.disabled = true;

        try {
            const [wpRes, jtRes] = await Promise.all([
                fetch("/api/profile/workplace", {
                    method: "PATCH",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({workplace})
                }),
                fetch("/api/profile/job-title", {
                    method: "PATCH",
                    headers: {"Content-Type": "application/json"},
                    body: JSON.stringify({jobTitle})
                })
            ]);

            if (!wpRes.ok || !jtRes.ok) {
                errorEl.textContent = "❌ Saqlashda xatolik yuz berdi, qayta urinib ko'ring.";
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
