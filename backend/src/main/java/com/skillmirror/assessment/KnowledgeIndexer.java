package com.skillmirror.assessment;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Indexation du RAG : au démarrage, calcule le vecteur des passages qui n'en ont pas.
 * Tourne en arrière-plan et s'arrête sans erreur si Ollama n'est pas lancé.
 */
@Component
@RequiredArgsConstructor
public class KnowledgeIndexer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexer.class);

    private final KnowledgeStore store;
    private final OllamaClient ollama;

    @EventListener(ApplicationReadyEvent.class)
    public void indexOnStartup() {
        CompletableFuture.runAsync(this::indexMissing);
    }

    public void indexMissing() {
        try {
            List<KnowledgeStore.Pending> pending = store.pending();
            if (pending.isEmpty()) return;
            int ok = 0;
            for (KnowledgeStore.Pending p : pending) {
                try {
                    store.saveEmbedding(p.id(), ollama.embed(p.content()));
                    ok++;
                } catch (Exception e) {
                    log.warn("RAG : indexation interrompue (Ollama / nomic-embed-text indisponible ?) : {}",
                            e.getMessage());
                    break;
                }
            }
            log.info("RAG : {} / {} passage(s) indexé(s)", ok, pending.size());
        } catch (Exception e) {
            log.warn("RAG : base de connaissances inaccessible : {}", e.getMessage());
        }
    }
}