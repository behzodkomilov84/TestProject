package behzoddev.testproject.dto.course;

import java.util.List;

// "Forum" savollari ro'yxati + joriy foydalanuvchi shu kursni boshqara
// oladimi (OWNER yoki kurs muallifi) — frontend shu bo'yicha BOSHQA
// foydalanuvchilarning yozuvlarini ham o'chirish tugmasini ko'rsatish/
// yashirishni hal qiladi (courseForum.js).
public record ForumThreadsResponseDto(List<ForumThreadDto> threads, boolean canManage) {
}
