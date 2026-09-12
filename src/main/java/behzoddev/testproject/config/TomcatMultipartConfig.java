package behzoddev.testproject.config;

import org.apache.catalina.connector.Connector;
import org.springframework.boot.tomcat.servlet.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// HAQIQIY TOPILGAN BUG (2026-09-12): "📥 Darslar + testlarni import
// qilish" (CourseSectionController#bulkImport) bir martada 10 ta .docx
// + 10 ta .xlsx (jami 20 ta fayl qismi) yuborilganda hamon xom "Failed
// to parse multipart servlet request" xatosini berardi — application.yaml
// dagi UCH XIL hajm chegarasi (max-file-size, max-request-size,
// max-http-form-post-size) 2026-09-10'da 200-300MB'ga oshirilgan bo'lsa
// ham (fayllar hajmi umuman muammo emas edi), sabab BUTUNLAY TO'RTINCHI,
// hajmga UMUMAN aloqasi yo'q chegara ekan: Tomcat 10.1+/11'ning
// Connector#maxPartCount'i — bitta multipart so'rovda RUXSAT ETILGAN
// FAYL QISMLARI SONI (standart qiymati — bor-yo'g'i 10 ta!), xizmat
// (DoS)dan himoya sifatida qo'shilgan. Bu YAML orqali emas, faqat
// Tomcat Connector'ining o'zida dasturiy ravishda o'rnatiladi (Spring
// Boot bu uchun alohida "server.tomcat.*" xususiyat taqdim etmaydi).
// Servisning o'zi (CourseService#bulkImportLessonsWithTests) 200 tagacha
// elementni loyihalashtirilgan deb hisoblaydi (har biriga .docx + .xlsx
// juft bo'lishi mumkin — ya'ni 400 tagacha fayl qismi + "items" JSON
// qismi), shu sabab margin bilan 500'ga qo'yildi.
@Configuration
public class TomcatMultipartConfig {

    @Bean
    public WebServerFactoryCustomizer<TomcatServletWebServerFactory> maxPartCountCustomizer() {
        return factory -> factory.addConnectorCustomizers((Connector connector) ->
                connector.setMaxPartCount(500));
    }
}
