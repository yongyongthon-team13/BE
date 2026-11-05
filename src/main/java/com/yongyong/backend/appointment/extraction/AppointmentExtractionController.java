package com.yongyong.backend.appointment.extraction;

import com.yongyong.backend.appointment.extraction.dto.AppointmentExtractionRequest;
import com.yongyong.backend.appointment.extraction.dto.AppointmentExtractionResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/appointment-extractions")
@RequiredArgsConstructor
public class AppointmentExtractionController {

    private final AppointmentExtractionService appointmentExtractionService;

    private static final Set<String> IMAGE_TYPES =
            Set.of("image/png", "image/jpeg", "image/webp", "image/heic", "image/heif");

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public AppointmentExtractionResponse extract(@Valid @RequestBody AppointmentExtractionRequest request) {
        return appointmentExtractionService.extract(request);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AppointmentExtractionResponse extractImage(
            @RequestPart MultipartFile image,
            @RequestParam(required = false) OffsetDateTime receivedAt) throws IOException {
        if (image.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이미지가 비어 있습니다.");
        }
        if (!IMAGE_TYPES.contains(String.valueOf(image.getContentType()))) {
            throw new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "지원하지 않는 이미지 형식입니다.");
        }
        return appointmentExtractionService.extractImage(image.getBytes(), image.getContentType(), receivedAt);
    }
}
