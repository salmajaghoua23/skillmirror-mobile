package com.skillmirror.recruiter.offer;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "job_offer_skills")
@Getter
@Setter
@NoArgsConstructor
public class JobOfferSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "offer_id")
    private JobOffer offer;

    @Column(name = "skill_name", nullable = false)
    private String skillName;

    private Boolean required = true;
}
