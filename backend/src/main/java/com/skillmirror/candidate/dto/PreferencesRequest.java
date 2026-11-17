package com.skillmirror.candidate.dto;

import java.util.List;

public record PreferencesRequest(
        String sector,
        String availability,
        Boolean mobility,
        String interviewLanguage,
        List<String> interests,
        String targetOfferDescription,
        String trainingObjective) {}