package com.skillmirror.assessment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Évaluation d'un candidat sur une compétence.
 * Table existante "assessments" (colonnes de départ + colonnes ajoutées en 3B).
 * Les colonnes jsonb strengths / weaknesses / results seront utilisées en 3C.
 */
@Entity
@Table(name = "assessments")
@Getter
@Setter
@NoArgsConstructor
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "profile_id", nullable = false)
    private UUID profileId;

    @Column(name = "skill_id")
    private UUID skillId;

    private String level;
    /** Liste de questions utilisée (1 à 5), vide si échantillon mélangé. */
    @Column(name = "list_no")
    private Integer listNo;

    /** IN_PROGRESS | COMPLETED */
    private String status = "IN_PROGRESS";

    /** Identifiants des questions, dans l'ordre, séparés par des virgules. */
    @Column(name = "question_ids", columnDefinition = "text")
    private String questionIds;

    @Column(name = "total_questions")
    private Integer totalQuestions;

    private Double score;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
    /** JSON {questionId: index} */
    @Column(name = "draft_answers", columnDefinition = "text")
    private String draftAnswers;      // JSON {questionId: index}

    @Column(name = "current_index")
    private Integer currentIndex = 0;
    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}