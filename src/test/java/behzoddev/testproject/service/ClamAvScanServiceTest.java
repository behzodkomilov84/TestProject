package behzoddev.testproject.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ClamAV (clamd) bilan INSTREAM protokoli orqali gaplashish — "fail closed"
 * xavfsizlik siyosati: skaner o'chirilmagan bo'lib, lekin javob bermasa yoki
 * kutilmagan javob qaytarsa, fayl HAR DOIM rad etilishi kerak (aks holda
 * tekshiruv shunchaki chetlab o'tilishi mumkin bo'lardi). Haqiqiy TCP
 * socket'ga ulanib, clamd'ning javobini simulyatsiya qilamiz — real ClamAV
 * demoni shart emas.
 */
class ClamAvScanServiceTest {

    @Test
    void scan_disabled_skipsNetworkCallEntirely() {
        ClamAvScanService service = new ClamAvScanService();
        ReflectionTestUtils.setField(service, "enabled", false);

        assertThatCode(() -> service.scan("istalgan mazmun".getBytes(), "file.png"))
                .doesNotThrowAnyException();
    }

    @Test
    void scan_cleanResponse_doesNotThrow() throws Exception {
        try (FakeClamd clamd = FakeClamd.respondingWith("stream: OK")) {
            ClamAvScanService service = enabledServiceFor(clamd);

            assertThatCode(() -> service.scan("toza fayl".getBytes(), "file.png"))
                    .doesNotThrowAnyException();
        }
    }

    @Test
    void scan_virusFound_throwsWithVirusMessage() throws Exception {
        try (FakeClamd clamd = FakeClamd.respondingWith("stream: Eicar-Test-Signature FOUND")) {
            ClamAvScanService service = enabledServiceFor(clamd);

            assertThatThrownBy(() -> service.scan("zararli fayl".getBytes(), "virus.png"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("zararli dastur");
        }
    }

    @Test
    void scan_unexpectedResponse_failsClosedWithGenericError() throws Exception {
        try (FakeClamd clamd = FakeClamd.respondingWith("stream: ERROR something weird")) {
            ClamAvScanService service = enabledServiceFor(clamd);

            assertThatThrownBy(() -> service.scan("fayl".getBytes(), "file.png"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("kutilmagan javob");
        }
    }

    @Test
    void scan_serverUnreachable_failsClosedRatherThanAllowingUpload() {
        ClamAvScanService service = new ClamAvScanService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "host", "localhost");
        ReflectionTestUtils.setField(service, "port", findClosedPort());

        assertThatThrownBy(() -> service.scan("fayl".getBytes(), "file.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("mavjud emas");
    }

    private ClamAvScanService enabledServiceFor(FakeClamd clamd) {
        ClamAvScanService service = new ClamAvScanService();
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "host", "localhost");
        ReflectionTestUtils.setField(service, "port", clamd.port());
        return service;
    }

    private static int findClosedPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort(); // socket yopiladi -> port bo'sh, lekin tinglovchisiz
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** clamd'ning INSTREAM protokolini soddalashtirib simulyatsiya qiluvchi test-server. */
    private static final class FakeClamd implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final Thread thread;

        private FakeClamd(String response) throws IOException {
            serverSocket = new ServerSocket(0);
            thread = new Thread(() -> {
                // Test maqsadida client yuborgan INSTREAM ma'lumotini tahlil qilish shart
                // emas (baytlar juda kichik, TCP buferida osilib qolish xavfi yo'q) —
                // shunchaki ulanishni qabul qilib, kanned javobni qaytaramiz.
                try (Socket client = serverSocket.accept()) {
                    client.getOutputStream().write(response.getBytes(StandardCharsets.US_ASCII));
                    client.getOutputStream().flush();
                } catch (IOException ignored) {
                    // Socket yopilishi kutilgan holat (test tugagach).
                }
            });
            thread.setDaemon(true);
            thread.start();
        }

        static FakeClamd respondingWith(String response) throws IOException {
            return new FakeClamd(response);
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            thread.join(2000);
        }
    }
}
