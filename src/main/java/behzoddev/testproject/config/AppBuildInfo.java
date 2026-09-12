package behzoddev.testproject.config;

// Sahifadagi statik JS/CSS fayllarni CACHE-BUSTING qilish uchun — HAQIQIY
// TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-12: "'O'chirilgan darslar'
// modaliga guruhlash + checkbox qo'shildi debsan, lekin frontda
// ko'rinmayapti") — bu funksiya haqiqatan ham deploy qilingan edi, lekin
// script/style manzillarida ("/js/courseDetail.js", "/css/courses.css")
// hech qanday versiya belgisi YO'Q edi, shu sabab brauzer bir necha
// ketma-ket deploy davomida ESKI (keshlangan) faylni ishlatishda davom
// etardi — server yangi kodni to'g'ri xizmat qilayotgan bo'lsa ham.
//
// STARTED_AT — ilova ishga tushgan (JVM yuklangan) paytdagi bir martalik
// vaqt belgisi: bir xil ishlab turgan instansiya davomida BARQAROR
// (normal brauzer keshlashi buzilmaydi — bir xil "?v=" qiymati bilan
// so'ralgan fayl baribir keshdan olinadi), lekin HAR BIR YANGI deploy'da
// (JVM qayta ishga tushganda) albatta YANGI qiymatga ega bo'ladi — shu
// sabab har bir deploy'dan keyin brauzer avtomatik yangi faylni yuklaydi.
public final class AppBuildInfo {

    public static final long STARTED_AT = System.currentTimeMillis();

    private AppBuildInfo() {
    }
}
