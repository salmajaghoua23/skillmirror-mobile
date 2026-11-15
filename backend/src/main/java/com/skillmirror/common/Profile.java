package com.skillmirror.common;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "profiles")
@Getter
@Setter
@NoArgsConstructor
public class Profile {
    @Id
    private UUID id;

    @Column(name = "firebase_uid")
    private String firebaseUid;

    @Column(name = "full_name")
    private String fullName;

    private String email;

    /** 'candidate' ou 'recruiter' */
    private String role;

    @Column(name = "onboarding_done")
    private Boolean onboardingDone;
}
