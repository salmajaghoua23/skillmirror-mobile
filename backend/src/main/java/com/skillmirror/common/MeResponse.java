package com.skillmirror.common;

import java.util.UUID;

public record MeResponse(UUID id, String fullName, String email, String role, boolean onboardingDone) {}
