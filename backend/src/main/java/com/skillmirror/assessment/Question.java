package com.skillmirror.assessment;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Une question à choix multiples (une seule bonne réponse). */
@Entity
@Table(name = "questions")
@Getter
@Setter
@NoArgsConstructor
public class Question {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    /** BEGINNER | INTERMEDIATE | ADVANCED */
    private String level;

    @Column(name = "question_text", columnDefinition = "text")
    private String questionText;

    @Column(name = "option_a", columnDefinition = "text")
    private String optionA;

    @Column(name = "option_b", columnDefinition = "text")
    private String optionB;

    @Column(name = "option_c", columnDefinition = "text")
    private String optionC;

    @Column(name = "option_d", columnDefinition = "text")
    private String optionD;

    /** 0 = A, 1 = B, 2 = C, 3 = D */
    @Column(name = "correct_index")
    private Integer correctIndex;

    @Column(columnDefinition = "text")
    private String explanation;

    /** BANK (banque de départ) | AI (générée avec RAG) */
    private String source = "BANK";

    /** Numéro de la liste (1 à 5) à laquelle appartient la question. */
    @Column(name = "list_no")
    private Integer listNo = 1;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    public List<String> options() {
        return List.of(optionA, optionB, optionC, optionD);
    }

    public void applyOptions(List<String> o) {
        optionA = o.get(0);
        optionB = o.get(1);
        optionC = o.get(2);
        optionD = o.get(3);
    }
}