package behzoddev.testproject.dao;

import behzoddev.testproject.entity.CourseSubscription;
import behzoddev.testproject.entity.enums.CourseSubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface CourseSubscriptionRepository extends JpaRepository<CourseSubscription, Long> {

    boolean existsByUser_IdAndCourse_IdAndStatus(Long userId, Long courseId, CourseSubscriptionStatus status);

    // "3 kunlik bepul sinov" allaqachon ishlatilganmi (holatidan qat'i
    // nazar — CONFIRMED/EXPIRED/CANCELLED, faqat BIR MARTA berilishi
    // kerak) — CourseSubscriptionService#startFreeTrial shu bilan
    // tekshiradi (foydalanuvchi so'rovi, 2026-09-09).
    boolean existsByUser_IdAndCourse_IdAndTrialTrue(Long userId, Long courseId);

    // Haqiqiy (real-time) kirish tekshiruvi uchun — status=CONFIRMED bo'lsa-da,
    // kunlik expireSubscriptions() job'i hali ishlamagan bo'lishi mumkin,
    // shuning uchun endDate to'g'ridan-to'g'ri tekshiriladi.
    boolean existsByUser_IdAndCourse_IdAndStatusAndEndDateAfter(
            Long userId, Long courseId, CourseSubscriptionStatus status, LocalDateTime time);

    // PaymentOrderService#createCourseOrder uchun — "allaqachon obuna
    // bo'lgansiz" tekshiruvi FAQAT haqiqiy (pullik) faol obunani hisobga
    // olishi kerak, "🎁 bepul sinov" (trial=true) ni EMAS — aks holda
    // sinov faol paytida "💳 Hoziroq to'lash" tugmasi HECH QACHON
    // ishlamas edi (haqiqiy topilgan bug, foydalanuvchi so'rovi,
    // 2026-09-09: "3 кун триал берилди. Лекин 'Хозироқ тўлашни боссам
    // шу чиқаяпти'" — "Siz allaqachon shu kursga obuna bo'lgansiz").
    boolean existsByUser_IdAndCourse_IdAndStatusAndEndDateAfterAndTrialFalse(
            Long userId, Long courseId, CourseSubscriptionStatus status, LocalDateTime time);

    Optional<CourseSubscription> findByUser_IdAndCourse_IdAndStatus(Long userId, Long courseId, CourseSubscriptionStatus status);

    List<CourseSubscription> findByCourse_IdOrderByCreatedAtDesc(Long courseId);

    List<CourseSubscription> findAllByOrderByCreatedAtDesc();

    // Muddati o'tgan, lekin hali EXPIRED deb belgilanmagan kurs obunalari
    // (kunlik scheduled job shularni topib yopadi).
    List<CourseSubscription> findByStatusAndEndDateBefore(CourseSubscriptionStatus status, LocalDateTime time);

    // Kursni o'chirishdan oldin — foreign key RESTRICT bo'lgani uchun,
    // avval shu kursga tegishli barcha obunalarni o'chirish kerak
    // (CourseService.deleteCourse).
    void deleteByCourse_Id(Long courseId);

    // Foydalanuvchini o'chirishdan oldin — "course_subscriptions.user_id"
    // NOT NULL FK RESTRICT (haqiqiy topilgan bug, 2026-09-07). O'zining
    // kurs obuna yozuvlari — o'chirilib ketaveradi.
    void deleteByUser_Id(Long userId);

    // "confirmed_by" NULL-ga ruxsat beriladi — bu foydalanuvchi OWNER
    // sifatida BOSHQA birovning kurs obunasini tasdiqlagan bo'lishi
    // mumkin, o'sha to'lov tarixi yo'qolib qolmasin deb qator
    // o'chirilmaydi, faqat "kim tasdiqlagani" NULL qilinadi.
    @Modifying
    @Query("UPDATE CourseSubscription cs SET cs.confirmedBy = NULL WHERE cs.confirmedBy.id = :userId")
    void clearConfirmedBy(@Param("userId") Long userId);
}
