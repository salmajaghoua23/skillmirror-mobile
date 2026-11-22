package com.skillmirror.skills;

import com.skillmirror.skills.SkillDtos.*;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Référentiel des compétences, accessible à tout utilisateur connecté. */
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
public class SkillReferenceController {

    private final SkillReferenceService service;

    @GetMapping("/domains")
    public List<DomainOut> domains() {
        return service.domains();
    }

    @GetMapping
    public List<SkillOut> search(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) UUID domainId) {
        return service.search(q, domainId);
    }

    @GetMapping("/roles")
    public List<RoleOut> roles() {
        return service.roles();
    }

    @GetMapping("/roles/{id}")
    public RoleDetail role(@PathVariable UUID id) {
        return service.role(id);
    }
}