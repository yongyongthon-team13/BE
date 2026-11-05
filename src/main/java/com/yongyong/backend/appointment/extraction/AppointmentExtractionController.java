package com.yongyong.backend.appointment.extraction;

import com.yongyong.backend.appointment.extraction.dto.AppointmentExtractionRequest;
import com.yongyong.backend.appointment.extraction.dto.AppointmentExtractionResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/appointment-extractions")
@RequiredArgsConstructor
public class AppointmentExtractionController {

    private final AppointmentExtractionService appointmentExtractionService;

    @PostMapping
    public AppointmentExtractionResponse extract(@Valid @RequestBody AppointmentExtractionRequest request) {
        return appointmentExtractionService.extract(request);
    }
}
