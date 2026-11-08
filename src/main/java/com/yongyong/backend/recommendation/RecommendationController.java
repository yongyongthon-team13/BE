package com.yongyong.backend.recommendation;

import com.yongyong.backend.recommendation.RecommendationService.Clinic;
import com.yongyong.backend.recommendation.RecommendationService.Recommendation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class RecommendationController {

    public record RecommendationRequest(@Size(max = 500) String text, @Size(max = 20) String bodyPart) {}

    private final RecommendationService recommendationService;

    @PostMapping("/department-recommendations")
    public Recommendation recommend(@Valid @RequestBody RecommendationRequest request) {
        return recommendationService.recommend(request.text(), request.bodyPart());
    }

    @GetMapping("/clinics")
    public List<Clinic> clinics(@RequestParam @NotBlank String department,
                                @RequestParam(required = false) String district) {
        return recommendationService.clinics(department, district);
    }
}
