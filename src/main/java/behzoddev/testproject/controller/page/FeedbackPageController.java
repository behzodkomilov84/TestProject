package behzoddev.testproject.controller.page;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

// "Fikr va takliflar" — sayt bo'yicha (global) ochiq fikr-taklif sahifasi
// (foydalanuvchi so'rovi, 2026-09-06). Kirish huquqi (kim yozadi/o'chiradi/
// statusini o'zgartiradi) BUTUNLAY API darajasida (FeedbackService)
// tekshiriladi — bu yerda faqat sahifa qobig'i ochiladi, "/feedback"ning
// o'zi SecurityConfig'dagi "anyRequest().authenticated()" orqali istalgan
// login qilgan foydalanuvchiga ochiq.
@Controller
public class FeedbackPageController {

    @GetMapping("/feedback")
    public String openFeedback() {
        return "feedback";
    }
}
