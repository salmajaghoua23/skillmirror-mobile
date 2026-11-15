package com.skillmirror.recruiter;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/reference")
@RequiredArgsConstructor
public class ReferenceController {

    private final SectorRepository sectors;
    private final RecruitmentDomainRepository domains;

    @GetMapping("/sectors")
    public List<RefItem> sectors() {
        return sectors.findAll(Sort.by("name")).stream()
                .map(s -> new RefItem(s.getId(), s.getName())).toList();
    }

    @GetMapping("/recruitment-domains")
    public List<RefItem> domains() {
        return domains.findAll(Sort.by("name")).stream()
                .map(d -> new RefItem(d.getId(), d.getName())).toList();
    }
}
