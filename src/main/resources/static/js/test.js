/*
// ========================================================================
// ГЛОБАЛЬНЫЕ ПЕРЕМЕННЫЕ
// ========================================================================
let subjects = []; // сюда будут загружены данные из БД
let focusIndex = null;
let savedSubjectIds = new Set(); // ИЗМЕНЕНИЕ: отслеживаем сохраненные ID

// ========================================================================
// ЗАГРУЗКА ДАННЫХ С СЕРВЕРА (исправлено)
// ========================================================================
document.addEventListener("DOMContentLoaded", () => {
    fetch("/api/science")
        .then(response => {
            if (!response.ok) {
                throw new Error(`Server error: ${response.status}`);
            }
            return response.json();
        })
        .then(data => {
            subjects = data.map(s => ({
                id: s.id,
                name: s.name,
                original: s.name,
                mode: "VIEW"
            }));

            // ИЗМЕНЕНИЕ: Добавляем ID в множество сохраненных
            data.forEach(s => savedSubjectIds.add(s.id));

            render();
            showToast('success', `Fanlar yuklandi: ${data.length} ta`, 2000);
        })
        .catch(err => {
            console.error('Yuklash xatosi:', err);
            showToast('error', 'Fanlarni yuklashda xatolik', 5000);
        });
});

// ========================================================================
// СОХРАНЕНИЕ В БАЗУ ДАННЫХ (полностью переписано)
// ========================================================================
async function saveToDb() {
    // 1. Проверка наличия незавершенных новых записей
    if (subjects.some(s => s.mode === "NEW")) {
        showToast('error', 'Barcha yangi fanlarni avval saqlang!');
        focusIndex = subjects.findIndex(s => s.mode === "NEW");
        render();
        return;
    }

    // 2. Проверка дубликатов перед отправкой
    const duplicateCheck = checkDuplicatesBeforeSave();
    if (duplicateCheck.hasDuplicates) {
        let errorMessage = "Quyidagi fan nomlari takrorlangan:\n\n";
        duplicateCheck.duplicates.forEach(dup => {
            errorMessage += `• "${dup.name}" (${dup.count} marta)\n`;
        });
        errorMessage += "\nIltimos, takrorlangan nomlarni o'zgartiring.";
        alert(`❌ Xatolik:\n${errorMessage}`);
        return;
    }

    // ИЗМЕНЕНИЕ: 3. Разделяем предметы правильно
    const newSubjects = [];
    const updatedSubjects = [];

    subjects.forEach(subject => {
        if (subject.id === null) {
            // Предмет без ID - новый
            newSubjects.push(subject.name);
        } else if (subject.id && subject.name !== subject.original) {
            // Предмет с ID и измененным именем - обновленный
            // ИЗМЕНЕНИЕ: Проверяем, был ли уже сохранен
            if (savedSubjectIds.has(subject.id)) {
                updatedSubjects.push({id: subject.id, name: subject.name});
            } else {
                // Если ID есть, но не в сохраненных, значит это новый предмет
                newSubjects.push(subject.name);
            }
        }
    });

    // 4. Проверка наличия данных для сохранения
    if (newSubjects.length === 0 && updatedSubjects.length === 0) {
        showToast('info', 'Saqlash uchun yangi yoki o\'zgartirilgan ma\'lumotlar yo\'q');
        return;
    }

    // 5. Подтверждение перед отправкой
    const confirmed = confirm(
        `Siz ${newSubjects.length} ta yangi fan va ${updatedSubjects.length} ta o'zgartirilgan fan saqlamoqchisiz.\n\nDavom etishni xohlaysizmi?`
    );

    if (!confirmed) return;

    // 6. Отправка на сервер
    try {
        showToast('info', 'Ma\'lumotlar bazaga saqlanmoqda...', 10000);

        const payload = {
            new: newSubjects,
            updated: updatedSubjects
        };

        console.log("Отправляем на сервер:", payload); // Для отладки

        const response = await fetch("/api/science/save", {
            method: "POST",
            headers: {"Content-Type": "application/json"},
            body: JSON.stringify(payload)
        });

        const data = await response.json();

        if (!response.ok) {
            if (response.status === 409) {
                // Конфликт - дубликаты в БД
                let errorMsg = "Bazada quyidagi fanlar allaqachon mavjud:\n\n";
                if (data.duplicates) {
                    data.duplicates.forEach(dup => errorMsg += `• ${dup}\n`);
                }
                errorMsg += "\nIltimos, fan nomlarini o'zgartiring.";
                throw new Error(errorMsg);
            } else {
                throw new Error(data.message || `Server xatosi (${response.status})`);
            }
        }

        // ИЗМЕНЕНИЕ: 7. Обработка успешного сохранения
        if (data.newIds && data.newIds.length > 0) {
            // Находим все предметы без ID
            const subjectsWithoutId = subjects.filter(s => s.id === null);

            // Присваиваем ID новым предметам
            subjectsWithoutId.forEach((subject, index) => {
                if (index < data.newIds.length) {
                    subject.id = data.newIds[index];
                    subject.original = subject.name;
                    // Добавляем ID в множество сохраненных
                    savedSubjectIds.add(data.newIds[index]);
                }
            });
        }

        // 8. Обновляем original для всех измененных предметов
        subjects.forEach(s => {
            if (s.id && s.name !== s.original) {
                s.original = s.name;
            }
        });

        // 9. Показываем результат
        let successMessage = `✅ Muvaffaqiyatli saqlandi!\n\n`;
        if (newSubjects.length > 0) {
            successMessage += `Yangi fanlar: ${newSubjects.length} ta\n`;
        }
        if (updatedSubjects.length > 0) {
            successMessage += `Yangilangan fanlar: ${updatedSubjects.length} ta\n`;
        }

        showToast('success', successMessage, 5000);
        console.log("Saqlash natijasi:", data);

        // 10. Перерисовываем список
        render();

    } catch (error) {
        console.error('Saqlash xatosi:', error);

        let errorMessage = "❌ Saqlashda xatolik yuz berdi\n\n";
        if (error.message.includes("allaqachon mavjud")) {
            errorMessage += error.message;
        } else {
            errorMessage += "Sabab: " + error.message + "\n\n";
            errorMessage += "Iltimos, qayta urinib ko'ring yoki tizim administratoriga murojaat qiling.";
        }

        showToast('error', errorMessage, 8000);
        alert(errorMessage);
    }
}

// ========================================================================
// ФУНКЦИЯ ADD (улучшена)
// ========================================================================
function add() {
    if (subjects.some(s => s.mode === "NEW")) {
        showToast('warning', 'Avval saqlash tugmasini bosing!');
        focusIndex = subjects.findIndex(s => s.mode === "NEW");
        render();
        return;
    }

    // ИЗМЕНЕНИЕ: Увеличиваем временный ID
    const tempId = Date.now() * -1; // Отрицательный ID для временных записей

    subjects.push({
        id: tempId, // Временный ID
        name: "",
        original: "",
        mode: "NEW"
    });

    focusIndex = subjects.length - 1;
    render();
}

// ========================================================================
// ФУНКЦИЯ SAVE (улучшена)
// ========================================================================
function save(i) {
    const s = subjects[i];
    const name = s.name.trim();

    if (!name) {
        showToast('error', 'Fan nomi bo\'sh bo\'lishi mumkin emas!');
        focusIndex = i;
        return;
    }

    // Проверка дубликатов
    if (hasDuplicate(i, name)) {
        showToast('error', 'Bu fan nomi allaqachon mavjud!');
        focusIndex = i;
        return;
    }

    s.original = name;
    s.mode = "VIEW";

    // ИЗМЕНЕНИЕ: Если это временный предмет (отрицательный ID),
    // оставляем его как есть, он будет обработан при сохранении в БД
    if (s.id < 0) {
        // Это временный предмет, ждем сохранения в БД
        showToast('success', 'Yangi fan saqlandi (hozircha lokal saqlandi)', 3000);
    } else {
        showToast('success', 'Fan o\'zgartirildi (hozircha lokal saqlandi)', 3000);
    }

    render();
}

// ========================================================================
// ФУНКЦИЯ CHECKDUPLICATESBEFORESAVE (улучшена)
// ========================================================================
function checkDuplicatesBeforeSave() {
    const nameCount = {};
    const duplicates = [];

    subjects.forEach(subject => {
        const name = subject.name.trim().toLowerCase();
        if (name) {
            nameCount[name] = (nameCount[name] || 0) + 1;
        }
    });

    for (const [name, count] of Object.entries(nameCount)) {
        if (count > 1) {
            const originalName = subjects.find(s =>
                s.name.toLowerCase().trim() === name
            )?.name || name;

            duplicates.push({
                name: originalName,
                count: count
            });
        }
    }

    return {
        hasDuplicates: duplicates.length > 0,
        duplicates: duplicates
    };
}*/
