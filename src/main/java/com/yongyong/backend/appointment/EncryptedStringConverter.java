package com.yongyong.backend.appointment;

import com.yongyong.backend.account.DataCipher;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@Converter
@RequiredArgsConstructor
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private final DataCipher cipher;

    @Override
    public String convertToDatabaseColumn(String value) {
        return cipher.encrypt(value);
    }

    @Override
    public String convertToEntityAttribute(String value) {
        return cipher.decrypt(value);
    }
}
