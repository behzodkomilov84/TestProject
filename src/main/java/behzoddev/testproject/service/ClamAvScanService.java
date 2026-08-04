package behzoddev.testproject.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Yuklangan fayllarni ClamAV daemon (clamd)ga INSTREAM protokoli orqali
 * yuborib, virus/zararli kod bor-yo'qligini tekshiradi.
 * <p>
 * {@code app.upload.clamav.enabled=false} bo'lsa (standart — masalan
 * IntelliJ'dan Docker'siz ishga tushirilganda, clamd konteyneri yo'q),
 * tekshiruv shunchaki o'tkazib yuboriladi — bu holatda faqat
 * {@link FileStorageService}dagi magic-byte tekshiruvi ishlaydi.
 * Docker orqali ishga tushirilganda (dev ham, prod ham) bu flag "true"ga
 * o'rnatiladi va clamd konteyneri sog'lom bo'lguncha ilova kutadi
 * (docker-compose'dagi depends_on/healthcheck orqali).
 */
@Slf4j
@Service
public class ClamAvScanService {

    private static final int CHUNK_SIZE = 2048;
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;

    @Value("${app.upload.clamav.enabled:false}")
    private boolean enabled;

    @Value("${app.upload.clamav.host:clamav}")
    private String host;

    @Value("${app.upload.clamav.port:3310}")
    private int port;

    /**
     * @throws IllegalArgumentException virus topilsa yoki skaner (yoqilgan
     *                                  bo'lsa) mavjud/ishonchli bo'lmasa —
     *                                  xavfsizlik uchun "fail closed":
     *                                  tekshirib bo'lmagan fayl saqlanmaydi.
     */
    public void scan(byte[] content, String originalFilename) {
        if (!enabled) {
            log.debug("ClamAV tekshiruvi o'chirilgan (app.upload.clamav.enabled=false) — '{}' skanerlanmadi.",
                    originalFilename);
            return;
        }

        String response;
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            socket.setSoTimeout(READ_TIMEOUT_MS);

            OutputStream out = socket.getOutputStream();
            out.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            out.flush();

            try (InputStream data = new ByteArrayInputStream(content)) {
                byte[] buffer = new byte[CHUNK_SIZE];
                int read;
                while ((read = data.read(buffer)) > 0) {
                    out.write(intToBigEndianBytes(read));
                    out.write(buffer, 0, read);
                }
            }
            // Nol uzunlikdagi chunk — stream tugaganini bildiradi.
            out.write(intToBigEndianBytes(0));
            out.flush();

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.US_ASCII));
            response = reader.readLine();

        } catch (IOException e) {
            log.error("ClamAV (clamd) bilan bog'lanib bo'lmadi: {}", e.getMessage());
            throw new IllegalArgumentException(
                    "❌ Virus skaneri hozircha mavjud emas — xavfsizlik uchun fayl yuklanmadi. " +
                            "Birozdan keyin qayta urinib ko'ring.");
        }

        if (response == null) {
            throw new IllegalArgumentException(
                    "❌ Virus skaneridan javob kelmadi — xavfsizlik uchun fayl yuklanmadi.");
        }

        log.info("ClamAV natijasi ('{}'): {}", originalFilename, response);

        if (response.contains("FOUND")) {
            throw new IllegalArgumentException(
                    "❌ Fayl zararli dastur (virus) sifatida aniqlandi va rad etildi: " + originalFilename);
        }

        if (!response.contains("OK")) {
            // Kutilmagan/ERROR javob — xavfsizlik uchun rad etamiz.
            throw new IllegalArgumentException("❌ Virus skaneri kutilmagan javob qaytardi — fayl yuklanmadi.");
        }
    }

    private static byte[] intToBigEndianBytes(int value) {
        return new byte[]{
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }
}
