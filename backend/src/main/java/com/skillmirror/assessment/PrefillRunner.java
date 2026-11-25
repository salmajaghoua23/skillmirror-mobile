package com.skillmirror.assessment;

import com.skillmirror.skills.Skill;
import com.skillmirror.skills.SkillRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Pré-remplissage des listes de questions (5 listes de 10 questions par niveau) avec le RAG.
 * Désactivé par défaut. À activer avec la variable ASSESSMENTS_PREFILL=true : le backend remplit
 * alors, en arrière-plan et l'une après l'autre, toutes les compétences et tous les niveaux
 * (plusieurs heures sur un ordinateur sans carte graphique : à lancer pour la nuit).
 */
@Component
@RequiredArgsConstructor
public class PrefillRunner {

    private static final Logger log = LoggerFactory.getLogger(PrefillRunner.class);
    private static final List<String> LEVELS = List.of("BEGINNER", "INTERMEDIATE", "ADVANCED");

    private final QuestionRepository questions;
    private final SkillRepository skills;
    private final QuestionGenerator generator;

    @Value("${assessments.prefill:false}")
    private boolean prefill;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (prefill) CompletableFuture.runAsync(this::run);
    }

    private void run() {
        try {
            Set<UUID> ids = new LinkedHashSet<>();
            for (Object[] row : questions.countBySkillAndLevel()) ids.add((UUID) row[0]);
            log.info("RAG : pré-remplissage lancé pour {} compétence(s).", ids.size());
            for (Skill s : skills.findAllById(ids)) {
                for (String level : LEVELS) {
                    generator.fillNow(s, level);
                }
            }
            log.info("RAG : pré-remplissage terminé.");
        } catch (Throwable t) {
            log.error("RAG : erreur pendant le pré-remplissage", t);
        }
    }
}