/* Bayroqli, qidiriladigan davlat (telefon kodi) tanlash widget'i.
   Yopiq holatda faqat bayroq + kod ko'rsatadi ("🇺🇿 +998"), ochilganda
   qidirish maydoni va bayroq+nom+kod bilan to'liq ro'yxat chiqadi.
   /registration va /profile — ikkalasida ham shu bitta fayl ishlatiladi. */

// ISO 3166-1 alpha-2 kodni bayroq emojisiga aylantiradi (masalan "UZ" -> 🇺🇿).
// Unicode "Regional Indicator Symbol" harflar orqali — har qanday davlat
// kodi uchun ishlaydi, qo'lda bayroq rasm/emoji ro'yxati kerak emas.
function isoToFlagEmoji(iso) {
    if (!iso || iso.length !== 2) return "🏳️";
    return iso.toUpperCase().replace(/./g, ch => String.fromCodePoint(127397 + ch.charCodeAt(0)));
}

function cpEscapeHtml(text) {
    const div = document.createElement("div");
    div.textContent = text == null ? "" : text;
    return div.innerHTML;
}

/**
 * @param container - widget joylashadigan bo'sh <div>
 * @param countries - [{isoCode, name, dialCode}, ...]
 * @param initialIso - boshlang'ich tanlangan davlat ("UZ")
 * @param onChange - (isoCode) => {} — tanlov o'zgarganda chaqiriladi
 * @returns {getIso, setIso}
 */
function initCountryPicker(container, countries, initialIso, onChange) {
    let selected = countries.find(c => c.isoCode === initialIso) || countries[0];

    container.classList.add("country-picker");
    container.innerHTML = `
        <button type="button" class="cp-toggle">
            <span class="cp-flag"></span>
            <span class="cp-dial"></span>
            <span class="cp-arrow">▾</span>
        </button>
        <div class="cp-panel" style="display:none;">
            <input type="text" class="cp-search" placeholder="🔍 Davlat qidirish..." autocomplete="off">
            <div class="cp-list"></div>
        </div>
    `;

    const toggle = container.querySelector(".cp-toggle");
    const panel = container.querySelector(".cp-panel");
    const search = container.querySelector(".cp-search");
    const list = container.querySelector(".cp-list");

    function renderToggle() {
        toggle.querySelector(".cp-flag").textContent = isoToFlagEmoji(selected.isoCode);
        toggle.querySelector(".cp-dial").textContent = "+" + selected.dialCode;
        toggle.title = selected.name + " (+" + selected.dialCode + ")";
    }

    function renderList(filter) {
        const q = (filter || "").trim().toLowerCase();
        const filtered = !q ? countries : countries.filter(c =>
            c.name.toLowerCase().includes(q) || c.dialCode.includes(q) || c.isoCode.toLowerCase().includes(q)
        );

        if (!filtered.length) {
            list.innerHTML = `<div class="cp-empty">Topilmadi</div>`;
            return;
        }

        list.innerHTML = filtered.map(c => `
            <div class="cp-row ${c.isoCode === selected.isoCode ? "active" : ""}" data-iso="${c.isoCode}">
                <span class="cp-flag">${isoToFlagEmoji(c.isoCode)}</span>
                <span class="cp-name">${cpEscapeHtml(c.name)}</span>
                <span class="cp-dial">+${c.dialCode}</span>
            </div>
        `).join("");

        list.querySelectorAll(".cp-row").forEach(row => {
            row.addEventListener("click", () => {
                const found = countries.find(c => c.isoCode === row.dataset.iso);
                if (!found) return;
                selected = found;
                renderToggle();
                closePanel();
                if (onChange) onChange(selected.isoCode);
            });
        });
    }

    function openPanel() {
        panel.style.display = "block";
        search.value = "";
        renderList("");
        setTimeout(() => search.focus(), 0);
    }

    function closePanel() {
        panel.style.display = "none";
    }

    toggle.addEventListener("click", (e) => {
        e.stopPropagation();
        panel.style.display === "none" ? openPanel() : closePanel();
    });

    search.addEventListener("input", () => renderList(search.value));
    search.addEventListener("click", e => e.stopPropagation());

    document.addEventListener("click", (e) => {
        if (!container.contains(e.target)) closePanel();
    });

    renderToggle();

    return {
        getIso: () => selected.isoCode,
        setIso: (iso) => {
            const found = countries.find(c => c.isoCode === iso);
            if (found) {
                selected = found;
                renderToggle();
            }
        }
    };
}
