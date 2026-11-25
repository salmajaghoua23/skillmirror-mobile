package com.skillmirror.assessment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "level_progress")
@Getter @Setter
public class LevelProgress {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(name = "profile_id") private UUID profileId;
    @Column(name = "skill_id")   private UUID skillId;
    private String level;
    private int attempt = 1;
    @Column(name = "next_list")       private int nextList = 1;
    @Column(name = "correct_total")   private int correctTotal = 0;
    @Column(name = "question_total")  private int questionTotal = 0;
    private String status = "IN_PROGRESS";
    @Column(name = "updated_at") private Instant updatedAt = Instant.now();

    public double score() {
        return questionTotal == 0 ? 0 : Math.round(1000.0 * correctTotal / questionTotal) / 10.0;
    }
}