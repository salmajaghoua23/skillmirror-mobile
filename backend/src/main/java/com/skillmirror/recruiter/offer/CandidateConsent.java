package com.skillmirror.recruiter.offer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * Vue minimale de candidate_profiles : uniquement le consentement.
 * (On ne touche pas à l'entité CandidateProfile de la Personne 1.)
 */
@Entity
@Table(name = "candidate_profiles")
@Getter
@Setter
@NoArgsConstructor
public class CandidateConsent {

    @Id
    private UUID id;

    @Column(name = "profile_id", insertable = false, updatable = false)
    private UUID profileId;

    @Column(name = "share_with_recruiters")
    private Boolean shareWithRecruiters = false;
}
