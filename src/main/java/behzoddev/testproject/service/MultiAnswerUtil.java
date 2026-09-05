package behzoddev.testproject.service;

import behzoddev.testproject.entity.Answer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// Ko'p to'g'ri javobli savollar (foydalanuvchi so'rovi, 2026-09-05) uchun
// TestSessionService VA AssignmentAttemptService'da AYNAN BIR XIL
// formuladan foydalanilishini kafolatlash uchun — ikkalasi ham shu
// yerdan chaqiradi, "to'g'ri hisoblash qoidasi" ikki joyda mustaqil
// yozilib, vaqt o'tishi bilan bir-biridan chetlashib qolmasin deb
// (Java'da JS'dagi "har bir sahifa mustaqil fayl" konvensiyasidan farqli
// — bu yerda MANTIQ bir xil bo'lishi kritik).
public final class MultiAnswerUtil {

    private MultiAnswerUtil() {
    }

    // "selected_answer_ids" ustuniga yozish uchun — id'lar barqaror
    // tartibda (kichikdan kattaga) saqlanadi, shu bilan bir xil to'plam
    // har doim bir xil satrga aylanadi (taqqoslash/diagnostika osonroq).
    public static String join(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return null;
        return ids.stream()
                .distinct()
                .sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    // "selected_answer_ids" ustunidan o'qish uchun.
    public static List<Long> parse(String csv) {
        if (csv == null || csv.isBlank()) return List.of();
        return Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::parseLong)
                .toList();
    }

    // Klient DTO'sidan (eski bitta "answerId"/"selectedAnswerId" YOKI
    // yangi "answerIds"/"selectedAnswerIds" ro'yxati) haqiqiy tanlangan
    // id'larni chiqarib beradi. Yangi maydon (ro'yxat) bo'lsa — SHU
    // ustun keladi; bo'lmasa (yoki bo'sh bo'lsa) — eski bitta maydonga
    // qaytiladi. Ikkalasi ham bo'lmasa — bo'sh ro'yxat (savol o'tkazib
    // yuborilgan).
    public static List<Long> resolveSubmittedIds(Long singleId, List<Long> multiIds) {
        if (multiIds != null && !multiIds.isEmpty()) {
            return multiIds.stream().distinct().toList();
        }
        return singleId != null ? List.of(singleId) : List.of();
    }

    // Savolning TO'G'RI (isTrue=true) javoblari ID to'plami — taqqoslash
    // uchun Set (tartib va takrorlanish ahamiyatsiz).
    public static Set<Long> correctAnswerIds(List<Answer> answers) {
        return answers.stream()
                .filter(a -> Boolean.TRUE.equals(a.getIsTrue()))
                .map(Answer::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    // Baholash qoidasi — "hammasi yoki hech narsa": talaba ANIQ barcha
    // to'g'ri variantlarni belgilagan bo'lishi kerak, ortiqcha ham, kam
    // ham emas. Bitta to'g'ri javobli savolda bu eskicha "bitta to'g'ri
    // tanlangan" tekshiruvi bilan AYNAN bir xil natija beradi — alohida
    // shoxobcha (branch) shart emas.
    public static boolean isCorrect(List<Long> submittedIds, Set<Long> correctIds) {
        return new LinkedHashSet<>(submittedIds).equals(correctIds);
    }

    // submittedIds ichidan HAQIQATDA mavjud (savolga tegishli) Answer
    // obyektlarini topib beradi — "selectedAnswer" (BIRINCHISI, orqaga
    // moslik) va "selectedAnswerIds" ustunlarini to'ldirish uchun.
    public static List<Answer> resolveAnswers(List<Long> submittedIds, List<Answer> questionAnswers) {
        List<Answer> result = new ArrayList<>();
        for (Long id : submittedIds) {
            questionAnswers.stream()
                    .filter(a -> a.getId().equals(id))
                    .findFirst()
                    .ifPresent(result::add);
        }
        return result;
    }
}
