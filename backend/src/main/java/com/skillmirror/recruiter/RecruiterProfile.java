package com.skillmirror.recruiter;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "recruiter_profiles")
@Getter
@Setter
@NoArgsConstructor
public class RecruiterProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "profile_id", nullable = false, unique = true)
    private UUID profileId;

    @Column(name = "company_name")
    private String companyName;

    private String position;

    @Column(name = "company_website")
    private String companyWebsite;

    @Column(name = "company_description")
    private String companyDescription;

    @Column(name = "company_size")
    private String companySize;

    private String country;
    private String city;

    @Column(name = "hiring_needs")
    private String hiringNeeds;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "sector_id")
    private Sector sector;

    @ManyToMany
    @JoinTable(name = "recruiter_domains",
            joinColumns = @JoinColumn(name = "recruiter_id"),
            inverseJoinColumns = @JoinColumn(name = "domain_id"))
    private Set<RecruitmentDomain> domains = new HashSet<>();
}
