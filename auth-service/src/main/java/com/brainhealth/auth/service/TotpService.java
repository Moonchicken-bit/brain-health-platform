package com.brainhealth.auth.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class TotpService {
    private static final String ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec encryptionKey;

    public TotpService(@Value("${brain-health.auth.otp-encryption-key}") String key) {
        if (key == null || key.length() < 24) throw new IllegalStateException("OTP_ENCRYPTION_KEY must be at least 24 characters");
        try {
            encryptionKey = new SecretKeySpec(MessageDigest.getInstance("SHA-256")
                .digest(key.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String generateSecret() {
        byte[] bytes = new byte[20];
        random.nextBytes(bytes);
        return base32Encode(bytes);
    }

    public boolean verify(String secret, String code) {
        if (code == null || !code.matches("\\d{6}")) return false;
        long step = Instant.now().getEpochSecond() / 30;
        for (long offset = -1; offset <= 1; offset++) {
            if (generateCode(secret, step + offset).equals(code)) return true;
        }
        return false;
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer output = ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(output.array());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to encrypt OTP secret", e);
        }
    }

    public String decrypt(String ciphertext) {
        try {
            byte[] input = Base64.getUrlDecoder().decode(ciphertext);
            byte[] iv = Arrays.copyOfRange(input, 0, 12);
            byte[] encrypted = Arrays.copyOfRange(input, 12, input.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to decrypt OTP secret", e);
        }
    }

    public List<String> recoveryCodes() {
        List<String> codes = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            byte[] bytes = new byte[6];
            random.nextBytes(bytes);
            codes.add(HexFormat.of().formatHex(bytes).toUpperCase(Locale.ROOT));
        }
        return codes;
    }

    private String generateCode(String secret, long step) {
        try {
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(base32Decode(secret), "HmacSHA1"));
            byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(step).array());
            int offset = hash[hash.length - 1] & 0x0f;
            int value = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
            return String.format("%06d", value % 1_000_000);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base32Encode(byte[] input) {
        StringBuilder out = new StringBuilder();
        int buffer = 0, bits = 0;
        for (byte b : input) {
            buffer = (buffer << 8) | (b & 0xff);
            bits += 8;
            while (bits >= 5) {
                out.append(ALPHABET.charAt((buffer >> (bits - 5)) & 31));
                bits -= 5;
            }
        }
        if (bits > 0) out.append(ALPHABET.charAt((buffer << (5 - bits)) & 31));
        return out.toString();
    }

    private static byte[] base32Decode(String input) {
        ByteBuffer out = ByteBuffer.allocate(input.length() * 5 / 8 + 1);
        int buffer = 0, bits = 0;
        for (char c : input.replace("=", "").toUpperCase(Locale.ROOT).toCharArray()) {
            int value = ALPHABET.indexOf(c);
            if (value < 0) throw new IllegalArgumentException("Invalid Base32");
            buffer = (buffer << 5) | value;
            bits += 5;
            if (bits >= 8) {
                out.put((byte) ((buffer >> (bits - 8)) & 0xff));
                bits -= 8;
            }
        }
        return Arrays.copyOf(out.array(), out.position());
    }
}
