package com.yongyong.backend.account;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class DataCipher {
    private final byte[] key;
    private final SecureRandom random = new SecureRandom();

    public DataCipher(@Value("${app.data-key:}") String encodedKey) {
        if (encodedKey.isBlank()) {
            key = null;
        } else {
            key = Base64.getDecoder().decode(encodedKey);
            if (key.length != 32) throw new IllegalArgumentException("APP_DATA_KEY must contain 32 bytes in Base64");
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        if (key == null) throw new IllegalStateException("APP_DATA_KEY is required before storing personal data");
        try {
            byte[] nonce = new byte[12];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] output = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, output, 0, nonce.length);
            System.arraycopy(encrypted, 0, output, nonce.length, encrypted.length);
            return Base64.getEncoder().encodeToString(output);
        } catch (Exception e) {
            throw new IllegalStateException("Personal data encryption failed", e);
        }
    }

    public String decrypt(String encoded) {
        if (encoded == null) return null;
        if (key == null) throw new IllegalStateException("APP_DATA_KEY is required before reading personal data");
        try {
            byte[] input = Base64.getDecoder().decode(encoded);
            byte[] nonce = new byte[12];
            System.arraycopy(input, 0, nonce, 0, nonce.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(input, nonce.length, input.length - nonce.length), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("Personal data decryption failed", e);
        }
    }
}
