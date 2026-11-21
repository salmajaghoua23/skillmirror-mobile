package com.skillmirror.candidate.dto;

import com.skillmirror.candidate.entity.CandidateProfile;
import com.skillmirror.common.Profile;

import java.util.List;

public record CandidateProfileResponse(
        String fullName, String email,
        Integer age, String gender, String country, String region,
        String studyField, String educationLevel, String diploma,
        String sector, String availability, Boolean mobility,
        String interviewLanguage, List<String> interests,
        String targetOfferDescription, String trainingObjective,
        String cvStoragePath) {

    public static CandidateProfileResponse from(Profile p, CandidateProfile c) {
        return new CandidateProfileResponse(
                p.getFullName(), p.getEmail(),
                c.getAge(), c.getGender(), c.getCountry(), c.getRegion(),
                c.getStudyField(), c.getEducationLevel(), c.getDiploma(),
                c.getSector(), c.getAvailability(), c.getMobility(),
                c.getInterviewLanguage(), c.getInterests(),
                c.getTargetOfferDescription(), c.getTrainingObjective(),
                c.getCvStoragePath());
    }
}