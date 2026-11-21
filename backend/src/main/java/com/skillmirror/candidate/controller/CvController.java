package com.skillmirror.candidate.controller;

import com.skillmirror.account.CurrentUser;
import com.skillmirror.candidate.dto.CvResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import com.skillmirror.candidate.service.CvService;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/candidate/cvs")
@RequiredArgsConstructor
public class CvController {
    private final CvService service;

    @GetMapping
    public List<CvResponse> list() {
        return service.list(CurrentUser.uid());
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CvResponse upload(@RequestParam("file") MultipartFile file) {
        return service.upload(CurrentUser.uid(), file);
    }

    @PutMapping("/{id}/active")
    public void setActive(@PathVariable UUID id) {
        service.setActive(CurrentUser.uid(), id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        service.delete(CurrentUser.uid(), id);
    }
}