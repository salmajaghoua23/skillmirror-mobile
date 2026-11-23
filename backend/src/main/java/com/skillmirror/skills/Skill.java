package com.skillmirror.skills;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "skills")
@Getter
@Setter
@NoArgsConstructor
public class Skill {
    @Id
    private UUID id;

    @Column(name = "domain_id")
    private UUID domainId;

    private String name;

    private String category;

    private String description;

    /** TECHNICAL | SOFT */
    @Column(name = "skill_type")
    private String skillType;
}