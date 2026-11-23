package com.skillmirror.assessment;

import com.skillmirror.assessment.AssessmentDtos.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/assessments")
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService service;

    /** Compétences pour lesquelles un test existe. */
    @GetMapping("/available")
    public List<AvailableSkill> available(Authentication auth) {
        return service.available(auth.getName());
    }

    /** Démarre un test. */
    @PostMapping("/start")
    @ResponseStatus(HttpStatus.CREATED)
    public StartResponse start(Authentication auth, @Valid @RequestBody StartRequest req) {
        return service.start(auth.getName(), req.skillId(), req.level(), req.count());
    }

    /** Génère des questions avec l'IA (RAG) et les ajoute à la banque. */
    @PostMapping("/generate")
    public GenerateResponse generate(Authentication auth, @Valid @RequestBody StartRequest req) {
        return service.generate(auth.getName(), req.skillId(), req.level(), req.count());
    }
}