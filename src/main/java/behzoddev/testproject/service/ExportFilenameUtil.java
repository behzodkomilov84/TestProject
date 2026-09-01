package behzoddev.testproject.service;

// ExcelService/WordService/ExamVariantService uchun umumiy — mavzu/bo'lim/
// fan NOMIDAN xavfsiz fayl nomi qismi yasaydi (diskka yozib bo'lmaydigan
// belgilar — \/:*?"<>| — bo'sh joyga, keyin bo'shliqlar pastki chiziqqa
// almashtiriladi). Oddiy statik utility — DI shart emas.
final class ExportFilenameUtil {

    private ExportFilenameUtil() {
    }

    static String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "savollar";
        }
        String cleaned = name.replaceAll("[\\\\/:*?\"<>|]", " ").trim().replaceAll("\\s+", "_");
        return cleaned.isEmpty() ? "savollar" : cleaned;
    }
}
