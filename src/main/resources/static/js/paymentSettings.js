// "⚙️ To'lov sozlamalari" — /admin-subscriptions'dan ajratildi
// (foydalanuvchi so'rovi, 2026-09-10: "bu faqat admin uchun bo'lmasa,
// barcha to'lovlar uchun bo'lsa, bu yerdan olib, alohida ⚙️ Sozlamalar
// tugmasi bilan OWNER PANEL ga joyla" — Click'ning minimal tranzaksiya
// summasi ADMIN-rol VA kurs to'lovlarining IKKALASIGA ham tegishli).
const ROLE = document.body.dataset.role;

if (ROLE !== "ROLE_OWNER") {
    showAlertModal("⛔ Доступ запрещён");
    location.href = "/login";
}

document.addEventListener("DOMContentLoaded", () => {
    loadMinAmount();
});

function loadMinAmount() {
    const input = document.getElementById("minAmountInput");
    if (!input) return;

    fetch("/api/payments/min-amount")
        .then(r => r.ok ? r.json() : null)
        .then(data => {
            if (data) input.value = data.minAmountSom;
        })
        .catch(err => console.error(err));
}

async function saveMinAmount() {
    const value = Number(document.getElementById("minAmountInput").value);

    if (!value || value <= 0) {
        showAlertModal("❌ To'g'ri summa kiriting");
        return;
    }

    try {
        const res = await fetch("/api/payments/min-amount", {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ minAmountSom: value })
        });

        const data = await res.json().catch(() => ({}));

        if (!res.ok) {
            showAlertModal(data.error || "Xatolik yuz berdi");
            return;
        }

        showAlertModal("✅ Minimal summa saqlandi: " + data.minAmountSom + " so'm");
    } catch (err) {
        console.error(err);
        showAlertModal("Network error");
    }
}
