package behzoddev.testproject.entity.enums;

public enum VideoSourceType {
    // Fayl sifatida serverga yuklangan (FileStorageService orqali).
    UPLOAD,
    // YouTube — IFrame Player API orqali "video oxirigacha ko'rildi" hodisasi aniq ushlanadi.
    YOUTUBE,
    // Boshqa (noma'lum) tashqi manba — aniq "tugadi" hodisasi yo'q, shu sabab
    // videoDurationSeconds asosida taxminiy vaqt o'tgach ochiladi.
    EXTERNAL
}
