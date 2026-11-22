package com.skillmirror.skills;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "skill_domains")
@Getter
@Setter
@NoArgsConstructor
public class SkillDomain {
    @Id
    private UUID id;
    private String name;
    private String description;
}