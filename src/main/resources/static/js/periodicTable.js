// 🧬 "Mendeleyev jadvali" — davriy jadval oynasi. Kimyo kursi (7-sinf
// kimyo, COURSE_ID=11) darslaridan turib ochiladigan yordamchi vosita —
// har bir elementga sichqonni olib borilganda uning asosiy
// xarakteristikasi (tartib raqami, nomi, massasi, turi, agregat holati)
// ko'rsatiladi. Ma'lumotlar to'liq shu faylda statik saqlanadi (backend
// so'rov shart emas) — 118 ta element, IUPAC standart qiymatlari asosida.
//
// Har bir element: [tartib raqami, belgisi, nomi, nisbiy atom massasi,
// turi, agregat holati, jadvaldagi qator, jadvaldagi ustun]
const PERIODIC_ELEMENTS = [
    [1,"H","Vodorod",1.008,"nonmetal","gaz",1,1],
    [2,"He","Geliy",4.003,"noble-gas","gaz",1,18],
    [3,"Li","Litiy",6.94,"alkali-metal","qattiq",2,1],
    [4,"Be","Berilliy",9.012,"alkaline-earth","qattiq",2,2],
    [5,"B","Bor",10.81,"metalloid","qattiq",2,13],
    [6,"C","Uglerod",12.01,"nonmetal","qattiq",2,14],
    [7,"N","Azot",14.01,"nonmetal","gaz",2,15],
    [8,"O","Kislorod",16.00,"nonmetal","gaz",2,16],
    [9,"F","Ftor",19.00,"halogen","gaz",2,17],
    [10,"Ne","Neon",20.18,"noble-gas","gaz",2,18],
    [11,"Na","Natriy",22.99,"alkali-metal","qattiq",3,1],
    [12,"Mg","Magniy",24.31,"alkaline-earth","qattiq",3,2],
    [13,"Al","Aluminiy",26.98,"post-transition","qattiq",3,13],
    [14,"Si","Kremniy",28.09,"metalloid","qattiq",3,14],
    [15,"P","Fosfor",30.97,"nonmetal","qattiq",3,15],
    [16,"S","Oltingugurt",32.07,"nonmetal","qattiq",3,16],
    [17,"Cl","Xlor",35.45,"halogen","gaz",3,17],
    [18,"Ar","Argon",39.95,"noble-gas","gaz",3,18],
    [19,"K","Kaliy",39.10,"alkali-metal","qattiq",4,1],
    [20,"Ca","Kalsiy",40.08,"alkaline-earth","qattiq",4,2],
    [21,"Sc","Skandiy",44.96,"transition-metal","qattiq",4,3],
    [22,"Ti","Titan",47.87,"transition-metal","qattiq",4,4],
    [23,"V","Vanadiy",50.94,"transition-metal","qattiq",4,5],
    [24,"Cr","Xrom",52.00,"transition-metal","qattiq",4,6],
    [25,"Mn","Marganets",54.94,"transition-metal","qattiq",4,7],
    [26,"Fe","Temir",55.85,"transition-metal","qattiq",4,8],
    [27,"Co","Kobalt",58.93,"transition-metal","qattiq",4,9],
    [28,"Ni","Nikel",58.69,"transition-metal","qattiq",4,10],
    [29,"Cu","Mis",63.55,"transition-metal","qattiq",4,11],
    [30,"Zn","Rux",65.38,"transition-metal","qattiq",4,12],
    [31,"Ga","Galliy",69.72,"post-transition","qattiq",4,13],
    [32,"Ge","Germaniy",72.63,"metalloid","qattiq",4,14],
    [33,"As","Arsen",74.92,"metalloid","qattiq",4,15],
    [34,"Se","Selen",78.97,"nonmetal","qattiq",4,16],
    [35,"Br","Brom",79.90,"halogen","suyuq",4,17],
    [36,"Kr","Kripton",83.80,"noble-gas","gaz",4,18],
    [37,"Rb","Rubidiy",85.47,"alkali-metal","qattiq",5,1],
    [38,"Sr","Stronsiy",87.62,"alkaline-earth","qattiq",5,2],
    [39,"Y","Ittriy",88.91,"transition-metal","qattiq",5,3],
    [40,"Zr","Sirkoniy",91.22,"transition-metal","qattiq",5,4],
    [41,"Nb","Niobiy",92.91,"transition-metal","qattiq",5,5],
    [42,"Mo","Molibden",95.95,"transition-metal","qattiq",5,6],
    [43,"Tc","Texnetsiy",98,"transition-metal","qattiq",5,7],
    [44,"Ru","Ruteniy",101.07,"transition-metal","qattiq",5,8],
    [45,"Rh","Rodiy",102.91,"transition-metal","qattiq",5,9],
    [46,"Pd","Palladiy",106.42,"transition-metal","qattiq",5,10],
    [47,"Ag","Kumush",107.87,"transition-metal","qattiq",5,11],
    [48,"Cd","Kadmiy",112.41,"transition-metal","qattiq",5,12],
    [49,"In","Indiy",114.82,"post-transition","qattiq",5,13],
    [50,"Sn","Qalay",118.71,"post-transition","qattiq",5,14],
    [51,"Sb","Surma",121.76,"metalloid","qattiq",5,15],
    [52,"Te","Tellur",127.60,"metalloid","qattiq",5,16],
    [53,"I","Yod",126.90,"halogen","qattiq",5,17],
    [54,"Xe","Ksenon",131.29,"noble-gas","gaz",5,18],
    [55,"Cs","Seziy",132.91,"alkali-metal","qattiq",6,1],
    [56,"Ba","Bariy",137.33,"alkaline-earth","qattiq",6,2],
    [57,"La","Lantan",138.91,"lanthanide","qattiq",9,4],
    [58,"Ce","Seriy",140.12,"lanthanide","qattiq",9,5],
    [59,"Pr","Prazeodim",140.91,"lanthanide","qattiq",9,6],
    [60,"Nd","Neodim",144.24,"lanthanide","qattiq",9,7],
    [61,"Pm","Prometiy",145,"lanthanide","qattiq",9,8],
    [62,"Sm","Samariy",150.36,"lanthanide","qattiq",9,9],
    [63,"Eu","Yevropiy",151.96,"lanthanide","qattiq",9,10],
    [64,"Gd","Gadoliniy",157.25,"lanthanide","qattiq",9,11],
    [65,"Tb","Terbiy",158.93,"lanthanide","qattiq",9,12],
    [66,"Dy","Disproziy",162.50,"lanthanide","qattiq",9,13],
    [67,"Ho","Golmiy",164.93,"lanthanide","qattiq",9,14],
    [68,"Er","Erbiy",167.26,"lanthanide","qattiq",9,15],
    [69,"Tm","Tuliy",168.93,"lanthanide","qattiq",9,16],
    [70,"Yb","Itterbiy",173.05,"lanthanide","qattiq",9,17],
    [71,"Lu","Lyutetsiy",174.97,"lanthanide","qattiq",9,18],
    [72,"Hf","Gafniy",178.49,"transition-metal","qattiq",6,4],
    [73,"Ta","Tantal",180.95,"transition-metal","qattiq",6,5],
    [74,"W","Volfram",183.84,"transition-metal","qattiq",6,6],
    [75,"Re","Reniy",186.21,"transition-metal","qattiq",6,7],
    [76,"Os","Osmiy",190.23,"transition-metal","qattiq",6,8],
    [77,"Ir","Iridiy",192.22,"transition-metal","qattiq",6,9],
    [78,"Pt","Platina",195.08,"transition-metal","qattiq",6,10],
    [79,"Au","Oltin",196.97,"transition-metal","qattiq",6,11],
    [80,"Hg","Simob",200.59,"transition-metal","suyuq",6,12],
    [81,"Tl","Talliy",204.38,"post-transition","qattiq",6,13],
    [82,"Pb","Qo'rg'oshin",207.2,"post-transition","qattiq",6,14],
    [83,"Bi","Bizmut",208.98,"post-transition","qattiq",6,15],
    [84,"Po","Poloniy",209,"post-transition","qattiq",6,16],
    [85,"At","Astat",210,"halogen","qattiq",6,17],
    [86,"Rn","Radon",222,"noble-gas","gaz",6,18],
    [87,"Fr","Fransiy",223,"alkali-metal","qattiq",7,1],
    [88,"Ra","Radiy",226,"alkaline-earth","qattiq",7,2],
    [89,"Ac","Aktiniy",227,"actinide","qattiq",10,4],
    [90,"Th","Toriy",232.04,"actinide","qattiq",10,5],
    [91,"Pa","Protaktiniy",231.04,"actinide","qattiq",10,6],
    [92,"U","Uran",238.03,"actinide","qattiq",10,7],
    [93,"Np","Neptuniy",237,"actinide","qattiq",10,8],
    [94,"Pu","Plutoniy",244,"actinide","qattiq",10,9],
    [95,"Am","Amerikiy",243,"actinide","qattiq",10,10],
    [96,"Cm","Kyuriy",247,"actinide","qattiq",10,11],
    [97,"Bk","Berkliy",247,"actinide","qattiq",10,12],
    [98,"Cf","Kaliforniy",251,"actinide","qattiq",10,13],
    [99,"Es","Eynshteyniy",252,"actinide","qattiq",10,14],
    [100,"Fm","Fermiy",257,"actinide","qattiq",10,15],
    [101,"Md","Mendeleviy",258,"actinide","qattiq",10,16],
    [102,"No","Nobeliy",259,"actinide","qattiq",10,17],
    [103,"Lr","Lourensiy",266,"actinide","qattiq",10,18],
    [104,"Rf","Rezerfordiy",267,"transition-metal","noma'lum",7,4],
    [105,"Db","Dubniy",268,"transition-metal","noma'lum",7,5],
    [106,"Sg","Seaborgiy",269,"transition-metal","noma'lum",7,6],
    [107,"Bh","Boriy",270,"transition-metal","noma'lum",7,7],
    [108,"Hs","Xassiy",269,"transition-metal","noma'lum",7,8],
    [109,"Mt","Meytneriy",278,"transition-metal","noma'lum",7,9],
    [110,"Ds","Darmshtatiy",281,"transition-metal","noma'lum",7,10],
    [111,"Rg","Rentgeniy",282,"transition-metal","noma'lum",7,11],
    [112,"Cn","Kopernitsiy",285,"transition-metal","noma'lum",7,12],
    [113,"Nh","Nixoniy",286,"post-transition","noma'lum",7,13],
    [114,"Fl","Fleroviy",289,"post-transition","noma'lum",7,14],
    [115,"Mc","Moskoviy",290,"post-transition","noma'lum",7,15],
    [116,"Lv","Livermoriy",293,"post-transition","noma'lum",7,16],
    [117,"Ts","Tennessin",294,"halogen","noma'lum",7,17],
    [118,"Og","Oganesson",294,"noble-gas","noma'lum",7,18]
];

const PERIODIC_CATEGORY_LABELS = {
    "alkali-metal": "Ishqoriy metall",
    "alkaline-earth": "Ishqoriy-yer metall",
    "transition-metal": "O'tish metali",
    "lanthanide": "Lantanoid",
    "actinide": "Aktinoid",
    "post-transition": "O'tishdan keyingi metall",
    "metalloid": "Metalloid (yarim metall)",
    "nonmetal": "Metallmas",
    "halogen": "Galogen",
    "noble-gas": "Inert (nodir) gaz"
};

let periodicTableBuilt = false;
let periodicTablePinnedNumber = null;
let periodicTablePinnedCellNode = null;
let periodicTablePinnedElement = null;

function openPeriodicTable() {
    ensurePeriodicTableBuilt();
    document.getElementById("periodicTableModal").classList.add("show");
}

function closePeriodicTable() {
    document.getElementById("periodicTableModal").classList.remove("show");
    hidePeriodicTooltip();
}

function ensurePeriodicTableBuilt() {
    if (periodicTableBuilt) return;
    periodicTableBuilt = true;

    const grid = document.getElementById("periodicTableGrid");
    const cellsByPos = {};

    PERIODIC_ELEMENTS.forEach((el) => {
        const [number, symbol, name, mass, category, phase, row, col] = el;
        const cell = document.createElement("div");
        cell.className = "pt-cell pt-cat-" + category;
        cell.style.gridRow = row;
        cell.style.gridColumn = col;
        cell.dataset.number = number;
        cell.innerHTML =
            `<span class="pt-number">${number}</span>` +
            `<span class="pt-symbol">${symbol}</span>` +
            `<span class="pt-mass">${mass}</span>`;

        cell.addEventListener("mouseenter", (evt) => showPeriodicTooltip(el, evt.currentTarget));
        // Sichqon boshqa hujayradan chetlashganda — agar biror element
        // "mahkamlangan" (pinned) bo'lsa, o'sha elementning ma'lumotiga
        // qaytariladi (shunchaki yashirilmaydi), aks holda butunlay yashiriladi.
        cell.addEventListener("mouseleave", () => {
            if (periodicTablePinnedNumber === null) {
                hidePeriodicTooltip();
            } else if (periodicTablePinnedNumber !== number) {
                showPeriodicTooltip(periodicTablePinnedElement, periodicTablePinnedCellNode);
            }
        });
        // Sichqonsiz (mobil/tegish) qurilmalarda ham xarakteristikani
        // ko'rish imkoni bo'lishi uchun — bosilganda ma'lumot "mahkamlanadi"
        // (qayta bosilmaguncha yoki boshqa element bosilmaguncha ochiq turadi).
        cell.addEventListener("click", (evt) => {
            evt.stopPropagation();
            if (periodicTablePinnedNumber === number) {
                periodicTablePinnedNumber = null;
                periodicTablePinnedCellNode = null;
                periodicTablePinnedElement = null;
                hidePeriodicTooltip();
            } else {
                periodicTablePinnedNumber = number;
                periodicTablePinnedCellNode = evt.currentTarget;
                periodicTablePinnedElement = el;
                showPeriodicTooltip(el, evt.currentTarget);
            }
        });

        grid.appendChild(cell);
        cellsByPos[row + "-" + col] = true;
    });

    // Lantanoid/aktinoid qatorlariga ishora qiluvchi "57-71" / "89-103"
    // yorliqli hujayralar — asosiy jadvalning 6- va 7-davr, 3-ustunida.
    addPeriodicPlaceholder(grid, 6, 3, "57-71", "lanthanide");
    addPeriodicPlaceholder(grid, 7, 3, "89-103", "actinide");
}

function addPeriodicPlaceholder(grid, row, col, label, category) {
    const cell = document.createElement("div");
    cell.className = "pt-cell pt-placeholder pt-cat-" + category;
    cell.style.gridRow = row;
    cell.style.gridColumn = col;
    cell.innerHTML = `<span class="pt-symbol">${label}</span>`;
    grid.appendChild(cell);
}

function showPeriodicTooltip(el, cellNode) {
    const [number, symbol, name, mass, category, phase] = el;
    const tooltip = document.getElementById("periodicTooltip");

    tooltip.innerHTML =
        `<div class="pt-tooltip-title">${symbol} — ${name}</div>` +
        `<div class="pt-tooltip-row"><span>Tartib raqami:</span><b>${number}</b></div>` +
        `<div class="pt-tooltip-row"><span>Nisbiy atom massasi:</span><b>${mass}</b></div>` +
        `<div class="pt-tooltip-row"><span>Turi:</span><b>${PERIODIC_CATEGORY_LABELS[category] || category}</b></div>` +
        `<div class="pt-tooltip-row"><span>Agregat holati (xona harorati):</span><b>${phase}</b></div>`;

    tooltip.classList.add("show");

    // Hujayra ustida joylashtirish — CHEGARA TEKSHIRUVI OYNA (window) ga
    // emas, balki #periodicTableCard'ning O'ZIGA nisbatan qilinadi: karta
    // kengroq ekranlarda markazlashtirilib ochiladi (min(1300px, 96vw)),
    // shu sabab oyna cheti kartaning chap/o'ng chetidan ancha uzoqda
    // bo'lishi mumkin — faqat oyna chegarasini tekshirish chap/o'ng
    // ustunlardagi elementlarda tooltip kartadan tashqariga (fon ustiga)
    // chiqib ketishiga olib kelardi (foydalanuvchi so'rovi, 2026-09-20:
    // avval chap, keyin o'ng chetda ham "yarmi ko'rinmay qolyapti").
    const cellRect = cellNode.getBoundingClientRect();
    const cardRect = document.getElementById("periodicTableCard").getBoundingClientRect();
    let left = cellRect.left - cardRect.left + cellRect.width / 2;
    let top = cellRect.top - cardRect.top + cellRect.height + 6;

    tooltip.style.left = left + "px";
    tooltip.style.top = top + "px";

    requestAnimationFrame(() => {
        const tooltipRect = tooltip.getBoundingClientRect();
        const PAD = 8;
        // Gorizontal chegara — kartaning O'ZI (chap/o'ng chetlaridagi
        // ustunlar uchun) va OYNA (viewport) chegarasining QATTIQROQ
        // (tor)idan foydalaniladi — karta oynadan kengroq bo'lgan holatda
        // ham (masalan gorizontal skroll bo'lganda) tooltip ekrandan
        // chiqib ketmasligi uchun.
        const boundLeft = Math.max(cardRect.left, 0) + PAD;
        const boundRight = Math.min(cardRect.right, window.innerWidth) - PAD;
        if (tooltipRect.right > boundRight) {
            tooltip.style.left = (left - (tooltipRect.right - boundRight)) + "px";
        } else if (tooltipRect.left < boundLeft) {
            tooltip.style.left = (left + (boundLeft - tooltipRect.left)) + "px";
        }

        // Vertikal chegara — jadvalning PASTKI qatorlaridagi elementlarda
        // (masalan aktinoidlar: Fm, Md, No, Lr) hujayra OSTIGA joylashtirish
        // tooltip'ni karta/oyna tagidan chiqarib, kesib qo'yardi
        // (foydalanuvchi so'rovi, 2026-09-20: "tagida muammo bor" — Lr
        // elementi misolida). Shunday holatda tooltip HUJAYRA USTIGA
        // ko'chiriladi.
        const boundBottom = Math.min(cardRect.bottom, window.innerHeight) - PAD;
        if (tooltipRect.bottom > boundBottom) {
            const cellTopInCard = cellRect.top - cardRect.top;
            top = cellTopInCard - tooltipRect.height - 6;
            tooltip.style.top = top + "px";
        }
    });
}

function hidePeriodicTooltip() {
    document.getElementById("periodicTooltip").classList.remove("show");
}

document.addEventListener("DOMContentLoaded", () => {
    const modal = document.getElementById("periodicTableModal");
    if (!modal) return;

    modal.addEventListener("click", (evt) => {
        if (evt.target === modal) closePeriodicTable();
    });

    const grid = document.getElementById("periodicTableGrid");
    grid.addEventListener("click", (evt) => {
        if (evt.target === grid) {
            periodicTablePinnedNumber = null;
            periodicTablePinnedCellNode = null;
            periodicTablePinnedElement = null;
            hidePeriodicTooltip();
        }
    });
});
