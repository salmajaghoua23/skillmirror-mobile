package com.skillmirror.recruiter;

import java.util.List;

public record RecruiterProfileResponse(
        String fullName, String email, String position, String companyName,
        RefItem sector, String companySize, String companyWebsite,
        String country, String city, String companyDescription,
        String hiringNeeds, List<RefItem> domains) {}
