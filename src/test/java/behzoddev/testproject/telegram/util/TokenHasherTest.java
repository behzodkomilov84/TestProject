package behzoddev.testproject.telegram.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TokenHasherTest {

    @Test
    void sha256Hex_sameInput_producesSameHash() {
        assertThat(TokenHasher.sha256Hex("abc")).isEqualTo(TokenHasher.sha256Hex("abc"));
    }

    @Test
    void sha256Hex_differentInput_producesDifferentHash() {
        assertThat(TokenHasher.sha256Hex("abc")).isNotEqualTo(TokenHasher.sha256Hex("abd"));
    }

    @Test
    void sha256Hex_returnsLowercase64CharHex() {
        String hash = TokenHasher.sha256Hex("some-random-token-value");

        assertThat(hash).hasSize(64);
        assertThat(hash).matches("[0-9a-f]{64}");
    }

    @Test
    void sha256Hex_knownVector_matchesStandardSha256() {
        // Ma'lum SHA-256("abc") qiymati — algoritm to'g'ri ishlatilganini tasdiqlaydi.
        assertThat(TokenHasher.sha256Hex("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
    }
}
