package behzoddev.testproject.entity.enums;

// Bo'lim matni (textContent) qanday saqlanganini bildiradi:
// PLAIN — qo'lda yozilgan xom matn (frontend uni escape qilib, http(s)
//         havolalarni bosiladigan qiladi — CourseSectionView.js linkify()).
// HTML  — .docx fayldan import qilingan, formatlash (abzatslar,
//         qalin/kursiv, sarlavhalar, ro'yxatlar) saqlangan HTML (frontend
//         to'g'ridan-to'g'ri, o'zgartirmasdan ko'rsatadi).
public enum CourseSectionContentFormat {
    PLAIN,
    HTML
}
