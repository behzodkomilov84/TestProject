package behzoddev.testproject.dao;

import behzoddev.testproject.entity.AssignmentChat;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssignmentChatRepository extends JpaRepository<AssignmentChat, Long> {

    List<AssignmentChat> findByAssignmentIdOrderByCreatedAtAsc(Long id);

    // HAQIQIY TOPILGAN BUG (foydalanuvchi so'rovi, 2026-09-12: "admin-
    // assignment va student sahifasidagi chat ishlamayapti") — chat
    // funksiyasi BIRINCHI marta qo'shilgandanoq (commit "chat is added")
    // ishlamas ekan: "sender.role" (birlik) — User entity'da BUNDAY MAYDON
    // YO'Q, haqiqiy maydon nomi "roles" (ko'plik, Set<Role> — dual-rol
    // qo'llab-quvvatlash uchun). Hibernate har safar "Unable to locate
    // Attribute with the given name [role] on this ManagedType [User]"
    // xatosi bilan 400 qaytarardi (GlobalRestExceptionHandler), frontend
    // esa buni massiv o'rniga xato-obyekt sifatida olib, "data.forEach is
    // not a function" bilan chat oynasini butunlay ocholmas edi.
    @EntityGraph(attributePaths = {"sender", "sender.roles"})
    List<AssignmentChat> findByAssignmentIdAndDeletedFalseOrderByCreatedAtAsc(Long assignmentId); //⚡ @EntityGraph — предотвращает N+1 при выводе sender.


}
