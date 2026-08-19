package com.brainhealth.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class TotpServiceTest {
    private final TotpService service = new TotpService("test-encryption-key-that-is-long-enough");

    @Test
    void generatedCodeVerifiesAndEncryptedSecretRoundTrips() {
        String secret = service.generateSecret();
        String encrypted = service.encrypt(secret);
        assertNotEquals(secret, encrypted);
        assertEquals(secret, service.decrypt(encrypted));
        long step = Instant.now().getEpochSecond() / 30;
        String code = ReflectionTestUtils.invokeMethod(service, "generateCode", secret, step);
        assertTrue(service.verify(secret, code));
        assertFalse(service.verify(secret, "000000".equals(code) ? "111111" : "000000"));
    }

    @Test
    void recoveryCodesAreUnique() {
        var codes = service.recoveryCodes();
        assertEquals(8, codes.size());
        assertEquals(8, codes.stream().distinct().count());
    }
}
