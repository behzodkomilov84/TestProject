package behzoddev.testproject;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.TimeZone;

@EnableScheduling
@SpringBootApplication
public class TestApplication {

    // MUHIM: JVM standart timezone'i (ZoneId.systemDefault()) production
    // konteynerida odatda UTC bo'ladi (bazaviy Docker image O'zbekiston
    // vaqtini bilmaydi), foydalanuvchilar esa Toshkent vaqtida (UTC+5)
    // ishlaydi. Bu — TestSessionService.finishTest() kabi joylarda
    // ko'rsatilayotgan sana-vaqt "boshqa timezone'da chiqyapti" degan
    // xabarning sababi edi. Bundan tashqari BU BIR XIL muammo
    // @Scheduled(cron=...) vazifalariga ham (masalan, kunlik 00:30
    // obunalarni tekshirish, 09:00 eslatma yuborish) tegishli edi —
    // ular ham JVM standart zonasiga qarab ishga tushadi, demak
    // aslida noto'g'ri (UTC) vaqtda ishga tushayotgan edi.
    // Statik blokda (main()dan OLDIN, klass yuklanishi bilanoq) o'rnatish
    // — .env/Docker-compose'dagi TZ o'zgaruvchisiga bog'liq bo'lmasdan,
    // barcha muhitlarda (production, lokal, @SpringBootTest) bir xil
    // ishlashini kafolatlaydi.
    static {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Tashkent"));
    }

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

}
