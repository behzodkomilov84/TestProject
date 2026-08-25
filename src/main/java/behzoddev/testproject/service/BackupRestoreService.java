package behzoddev.testproject.service;

import behzoddev.testproject.dao.CourseChapterRepository;
import behzoddev.testproject.dao.CourseRepository;
import behzoddev.testproject.dao.CourseSectionProgressRepository;
import behzoddev.testproject.dao.CourseSectionRepository;
import behzoddev.testproject.dao.CourseSubscriptionRepository;
import behzoddev.testproject.dto.backup.BackupCourseCandidateDto;
import behzoddev.testproject.dto.backup.BackupFileDto;
import behzoddev.testproject.dto.backup.BackupRestoreResultDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

// "📦 Backup orqali tiklash" — "O'chirilganlar savati" (Course.deletedAt)
// yetarli bo'lmagan holatlar uchun: kurs BUTUNLAY o'chirilgan (permanentlyDeleteCourse)
// yoki bu funksiya yaratilishidan OLDIN yo'qolgan bo'lsa, kunlik mysqldump
// zaxirasidan TANLAB (vaqt oralig'i + aniq kurslar) tiklash imkonini beradi.
//
// XAVFSIZLIK — ASOSIY g'oya: backup fayli HECH QACHON to'g'ridan-to'g'ri
// jonli "test_project" sxemasiga qo'llanilmaydi (aks holda ichidagi
// "DROP TABLE IF EXISTS ...; CREATE TABLE ...;" qatorlari JONLI
// jadvallarni BUTUNLAY tozalab qayta yaratib yuborardi!). Buning o'rniga:
//   1. Backup MySQL serverning O'ZIDA (xuddi shu konteyner — Docker
//      socket/qo'shimcha konteyner SHART EMAS), vaqtinchalik, tasodifiy
//      nomli, izolyatsiyalangan SXEMAga yuklanadi.
//   2. Kerakli qatorlar oddiy SELECT bilan shu vaqtinchalik sxemadan
//      o'qiladi (mysqldump matnini qo'lda "parse" qilish YO'Q — buning
//      o'rniga MySQL'ning o'zi standart SQL sifatida bajaradi).
//   3. Faqat ANIQ tanlangan, ID to'qnashuvi bo'lmagan qatorlar jonli
//      bazaga (oddiy JDBC INSERT orqali) ko'chiriladi.
//   4. Vaqtinchalik sxema — muvaffaqiyatli bo'lsin, xato bersin — HAR
//      DOIM (finally) o'chiriladi.
@Service
@RequiredArgsConstructor
@Slf4j
public class BackupRestoreService {

    // backup.sh'da yaratiladigan fayl nomi andozasi: test_project-20260825_030001.sql.gz
    private static final Pattern FILE_NAME_PATTERN =
            Pattern.compile("^test_project-(\\d{8}_\\d{6})\\.sql\\.gz$");
    private static final DateTimeFormatter FILE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    // mysqldump "--databases test_project" dump matnida bazani TANLAYDIGAN
    // ikkita direktiv qator bo'ladi: "CREATE DATABASE ... `test_project` ..."
    // va "USE `test_project`;". Bular OLIB TASHLANADI va o'rniga bizning
    // vaqtinchalik sxemamiz qo'yiladi — aks holda dump JONLI bazaga
    // qo'llanib ketardi (yuqoridagi klass izohiga qarang).
    private static final Pattern DB_DIRECTIVE_LINE =
            Pattern.compile("(?im)^(CREATE\\s+DATABASE\\b.*|USE\\s+`?[\\w-]+`?\\s*;)\\s*$");

    private final CourseRepository courseRepository;
    private final CourseChapterRepository courseChapterRepository;
    private final CourseSectionRepository courseSectionRepository;
    private final CourseSubscriptionRepository courseSubscriptionRepository;
    private final CourseSectionProgressRepository courseSectionProgressRepository;
    private final DataSource dataSource;

    @Value("${app.backups-dir:}")
    private String backupsDir;

    @Value("${spring.datasource.url}")
    private String liveJdbcUrl;

    @Value("${spring.datasource.username}")
    private String dbUsername;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    // ===================== 1) Ro'yxat =====================

    public List<BackupFileDto> listBackups() {
        if (backupsDir == null || backupsDir.isBlank()) {
            return List.of();
        }
        Path dir = Paths.get(backupsDir);
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files
                    .map(this::toBackupFileDto)
                    .filter(Objects::nonNull)
                    .sorted(Comparator.comparing(BackupFileDto::capturedAt).reversed())
                    .toList();
        } catch (IOException e) {
            log.error("Backup papkasini o'qib bo'lmadi: {}", backupsDir, e);
            return List.of();
        }
    }

    private BackupFileDto toBackupFileDto(Path path) {
        Matcher m = FILE_NAME_PATTERN.matcher(path.getFileName().toString());
        if (!m.matches()) {
            return null;
        }
        try {
            LocalDateTime capturedAt = LocalDateTime.parse(m.group(1), FILE_TS_FORMAT);
            return new BackupFileDto(path.getFileName().toString(), capturedAt, Files.size(path));
        } catch (Exception e) {
            return null;
        }
    }

    // ===================== 2) Ko'rish (preview) =====================
    // Jonli bazaga HECH NARSA yozilmaydi — faqat vaqtinchalik sxemadan
    // o'qiladi va darhol o'chiriladi.

    public List<BackupCourseCandidateDto> previewCourses(String fileName, LocalDateTime from, LocalDateTime to) {
        validateRange(from, to);
        Path file = resolveBackupFile(fileName);
        String tempSchema = newTempSchemaName();

        try (Connection rawConn = openRawConnection()) {
            loadDumpIntoTempSchema(rawConn, file, tempSchema);

            List<BackupCourseCandidateDto> result = new ArrayList<>();
            String sql = "SELECT id, title, created_at FROM `" + tempSchema + "`.`courses` " +
                    "WHERE created_at BETWEEN ? AND ? ORDER BY created_at DESC";
            try (PreparedStatement ps = rawConn.prepareStatement(sql)) {
                ps.setObject(1, from);
                ps.setObject(2, to);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Long id = rs.getLong("id");
                        result.add(new BackupCourseCandidateDto(
                                id,
                                rs.getString("title"),
                                rs.getObject("created_at", LocalDateTime.class),
                                courseRepository.existsById(id)));
                    }
                }
            }
            return result;
        } catch (SQLException e) {
            log.error("Backup'ni ko'rishda xatolik: {}", fileName, e);
            throw new IllegalStateException("❌ Backup faylini o'qishda xatolik: " + e.getMessage(), e);
        } finally {
            dropTempSchemaQuietly(tempSchema);
        }
    }

    // ===================== 3) Tiklash (apply) =====================
    // Faqat: (a) tanlangan vaqt oralig'ida haqiqatan mavjud, (b) jonli
    // bazada HALI YO'Q (faol yoki savatda) kurslar tiklanadi — qolganlari
    // "skippedCourseIds"ga tushadi.

    @Transactional
    public BackupRestoreResultDto applyRestore(String fileName, LocalDateTime from, LocalDateTime to, List<Long> courseIds) {
        validateRange(from, to);
        if (courseIds == null || courseIds.isEmpty()) {
            throw new IllegalArgumentException("❌ Tiklash uchun kamida bitta kurs tanlang.");
        }

        Path file = resolveBackupFile(fileName);
        String tempSchema = newTempSchemaName();
        JdbcTemplate live = new JdbcTemplate(dataSource);

        int restoredCourses = 0;
        int restoredChapters = 0;
        int restoredSections = 0;
        int restoredSubscriptions = 0;
        int restoredProgress = 0;
        List<Long> skipped = new ArrayList<>();

        try (Connection rawConn = openRawConnection()) {
            loadDumpIntoTempSchema(rawConn, file, tempSchema);

            for (Long courseId : courseIds) {
                Map<String, Object> courseRow = selectOneInRange(rawConn, tempSchema, "courses", courseId, from, to, "created_at");
                if (courseRow == null || courseRepository.existsById(courseId)) {
                    skipped.add(courseId);
                    continue;
                }

                insertRow(live, "courses", courseRow, Set.of());
                restoredCourses++;

                restoredChapters += copyChildRows(rawConn, live, tempSchema, "course_chapters", "course_id", courseId, courseChapterRepository);

                List<Long> sectionIds = copySectionsReturningIds(rawConn, live, tempSchema, courseId);
                restoredSections += sectionIds.size();

                restoredSubscriptions += copyChildRows(rawConn, live, tempSchema, "course_subscriptions", "course_id", courseId, courseSubscriptionRepository);

                for (Long sectionId : sectionIds) {
                    restoredProgress += copyProgressRows(rawConn, live, tempSchema, sectionId);
                }
            }
        } catch (SQLException e) {
            log.error("Backupdan tiklashda xatolik: {}", fileName, e);
            throw new IllegalStateException("❌ Backupdan tiklashda xatolik: " + e.getMessage(), e);
        } finally {
            dropTempSchemaQuietly(tempSchema);
        }

        return new BackupRestoreResultDto(restoredCourses, restoredChapters, restoredSections,
                restoredSubscriptions, restoredProgress, skipped);
    }

    // ===================== Yordamchi metodlar =====================

    private void validateRange(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || from.isAfter(to)) {
            throw new IllegalArgumentException("❌ Vaqt oralig'i noto'g'ri (boshlanish tugashdan keyin bo'lishi mumkin emas).");
        }
    }

    private Path resolveBackupFile(String fileName) {
        if (fileName == null || !FILE_NAME_PATTERN.matcher(fileName).matches()) {
            throw new IllegalArgumentException("❌ Noto'g'ri backup fayl nomi.");
        }
        if (backupsDir == null || backupsDir.isBlank()) {
            throw new IllegalStateException("❌ Backup papkasi sozlanmagan (BACKUPS_DIR).");
        }
        Path base = Paths.get(backupsDir).normalize();
        Path file = base.resolve(fileName).normalize();
        // Yo'l "traversal" himoyasi — fileName allaqachon regex bilan qattiq
        // cheklangan bo'lsa-da (ichida "/" yoki ".." bo'lishi mumkin emas),
        // qo'shimcha xavfsizlik qatlami sifatida saqlanadi.
        if (!file.startsWith(base) || !Files.isRegularFile(file)) {
            throw new IllegalArgumentException("❌ Backup fayl topilmadi: " + fileName);
        }
        return file;
    }

    private String newTempSchemaName() {
        return "restore_tmp_" + System.currentTimeMillis() + "_" + Math.abs(new Random().nextInt(1_000_000));
    }

    private Connection openRawConnection() throws SQLException {
        String url = liveJdbcUrl + (liveJdbcUrl.contains("?") ? "&" : "?") + "allowMultiQueries=true";
        return DriverManager.getConnection(url, dbUsername, dbPassword);
    }

    private void dropTempSchemaQuietly(String tempSchema) {
        try (Connection conn = openRawConnection(); Statement st = conn.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS `" + tempSchema + "`");
        } catch (SQLException e) {
            log.warn("Vaqtinchalik sxemani ({}) o'chirib bo'lmadi — qo'lda tozalash talab qilinishi mumkin.", tempSchema, e);
        }
    }

    private String decompressGzip(Path file) {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(file));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            in.transferTo(out);
            return out.toString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("❌ Backup faylini ochib bo'lmadi: " + file.getFileName(), e);
        }
    }

    // Klass izohidagi xavfsizlik qoidasi shu yerda amalga oshiriladi: dump
    // matnidagi baza tanlash direktivlari olib tashlanadi, o'rniga BIZNING
    // vaqtinchalik sxemamiz qo'yiladi, so'ng BUTUN skript (jadval
    // yaratish + INSERT'lar) bitta "allowMultiQueries" ulanish orqali
    // bajariladi.
    private void loadDumpIntoTempSchema(Connection rawConn, Path backupFile, String tempSchema) throws SQLException {
        String dumpSql = decompressGzip(backupFile);
        String cleaned = DB_DIRECTIVE_LINE.matcher(dumpSql).replaceAll("");

        try (Statement st = rawConn.createStatement()) {
            st.execute("CREATE DATABASE `" + tempSchema + "`");
            st.execute("USE `" + tempSchema + "`");
            executeMultiStatementScript(st, cleaned);
        }
    }

    // "allowMultiQueries=true" bilan bitta katta ";"-bilan-ajratilgan SQL
    // matnini to'liq bajarish uchun — barcha natijalarni "getMoreResults()"
    // orqali oxirigacha "drain" qilish shart, aks holda faqat BIRINCHI
    // ifoda bajarilib qolishi mumkin.
    private void executeMultiStatementScript(Statement st, String sql) throws SQLException {
        boolean isResultSet = st.execute(sql);
        while (true) {
            if (!isResultSet && st.getUpdateCount() == -1) {
                break;
            }
            isResultSet = st.getMoreResults();
        }
    }

    private Map<String, Object> readRow(ResultSet rs) throws SQLException {
        ResultSetMetaData md = rs.getMetaData();
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= md.getColumnCount(); i++) {
            row.put(md.getColumnLabel(i), rs.getObject(i));
        }
        return row;
    }

    private Map<String, Object> selectOneInRange(Connection rawConn, String tempSchema, String table, Long id,
                                                  LocalDateTime from, LocalDateTime to, String dateColumn) throws SQLException {
        String sql = "SELECT * FROM `" + tempSchema + "`.`" + table + "` WHERE id = ? AND `" + dateColumn + "` BETWEEN ? AND ?";
        try (PreparedStatement ps = rawConn.prepareStatement(sql)) {
            ps.setLong(1, id);
            ps.setObject(2, from);
            ps.setObject(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? readRow(rs) : null;
            }
        }
    }

    // Vaqtinchalik sxemadagi "table"'dan (id, name, va h.k. hech qanday
    // farqi yo'q — barcha ustunlar generik o'qiladi) jonli bazaga
    // ko'chiradi, FAQAT id hali jonli bazada mavjud bo'lmagan qatorlarni
    // (repo.existsById tekshiruvi — collision himoyasi).
    private int copyChildRows(Connection rawConn, JdbcTemplate live, String tempSchema, String table,
                               String fkColumn, Long parentId, JpaRepository<?, Long> repo) throws SQLException {
        int count = 0;
        String sql = "SELECT * FROM `" + tempSchema + "`.`" + table + "` WHERE `" + fkColumn + "` = ?";
        try (PreparedStatement ps = rawConn.prepareStatement(sql)) {
            ps.setLong(1, parentId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = readRow(rs);
                    Long id = ((Number) row.get("id")).longValue();
                    if (repo.existsById(id)) {
                        continue;
                    }
                    insertRow(live, table, row, Set.of());
                    count++;
                }
            }
        }
        return count;
    }

    // course_sections uchun alohida — pastdagi course_section_progress
    // qatorlarini topish uchun bo'lim ID'lari kerak bo'ladi (mavjud bo'lsin,
    // yangi qo'shilgan bo'lsin — bari birdek).
    private List<Long> copySectionsReturningIds(Connection rawConn, JdbcTemplate live, String tempSchema, Long courseId) throws SQLException {
        List<Long> ids = new ArrayList<>();
        String sql = "SELECT * FROM `" + tempSchema + "`.`course_sections` WHERE `course_id` = ?";
        try (PreparedStatement ps = rawConn.prepareStatement(sql)) {
            ps.setLong(1, courseId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = readRow(rs);
                    Long id = ((Number) row.get("id")).longValue();
                    ids.add(id);
                    if (!courseSectionRepository.existsById(id)) {
                        insertRow(live, "course_sections", row, Set.of());
                    }
                }
            }
        }
        return ids;
    }

    // course_section_progress — bu sessiyadagi haqiqiy hodisada ID
    // to'qnashuvi ANIQ shu jadvalda chiqqan edi, shu sabab ID HAR DOIM
    // qayta yaratiladi (AUTO_INCREMENT'ga qoldiriladi — "id" ustuni
    // insertga QO'SHILMAYDI). Mantiqiy dublikatning oldini olish uchun
    // (user_id, section_id) juftligi jonli bazada allaqachon bormi
    // tekshiriladi.
    private int copyProgressRows(Connection rawConn, JdbcTemplate live, String tempSchema, Long sectionId) throws SQLException {
        int count = 0;
        String sql = "SELECT * FROM `" + tempSchema + "`.`course_section_progress` WHERE `section_id` = ?";
        try (PreparedStatement ps = rawConn.prepareStatement(sql)) {
            ps.setLong(1, sectionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Map<String, Object> row = readRow(rs);
                    Long userId = ((Number) row.get("user_id")).longValue();
                    if (courseSectionProgressRepository.existsByUser_IdAndSection_Id(userId, sectionId)) {
                        continue;
                    }
                    insertRow(live, "course_section_progress", row, Set.of("id"));
                    count++;
                }
            }
        }
        return count;
    }

    // Generik INSERT — ustun ro'yxati/nomlari kod ichida QATTIQ yozilmagan,
    // to'g'ridan-to'g'ri vaqtinchalik sxemadan o'qilgan ResultSetMetaData'dan
    // olinadi. "table" va ustun nomlari (map key'lari) HECH QACHON
    // to'g'ridan-to'g'ri foydalanuvchi so'rovidan kelmaydi (har doim shu
    // klassning o'zi tuzgan SELECT natijasi) — shu sabab SQL-injection
    // xavfi yo'q, garchi identifikatorlar string-concatenation orqali
    // qo'yilsa ham.
    private void insertRow(JdbcTemplate live, String table, Map<String, Object> row, Set<String> excludeColumns) {
        List<String> columns = row.keySet().stream()
                .filter(c -> !excludeColumns.contains(c))
                .toList();
        String colList = columns.stream().map(c -> "`" + c + "`").collect(Collectors.joining(","));
        String placeholders = columns.stream().map(c -> "?").collect(Collectors.joining(","));
        String sql = "INSERT INTO `" + table + "` (" + colList + ") VALUES (" + placeholders + ")";
        Object[] values = columns.stream().map(row::get).toArray();
        live.update(sql, values);
    }
}
