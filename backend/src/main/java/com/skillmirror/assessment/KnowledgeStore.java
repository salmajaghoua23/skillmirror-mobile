package com.skillmirror.assessment;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Base de connaissances du RAG (table knowledge_chunks, avec pgvector).
 * Requêtes natives : Hibernate ne connaît pas le type "vector".
 */
@Component
public class KnowledgeStore {

    @PersistenceContext
    private EntityManager em;

    public record Pending(UUID id, String content) {}

    /** Les k passages les plus proches de la requête (distance cosinus), pour une compétence. */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<String> search(UUID skillId, float[] queryVector, int k) {
        return (List<String>) em.createNativeQuery(
                        "select content from knowledge_chunks "
                                + "where skill_id = :sid and embedding is not null "
                                + "order by embedding <=> cast(:q as vector) limit :k")
                .setParameter("sid", skillId)
                .setParameter("q", OllamaClient.toVectorLiteral(queryVector))
                .setParameter("k", k)
                .getResultList();
    }

    /** Secours sans embeddings : les premiers passages de la compétence. */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<String> firstChunks(UUID skillId, int k) {
        return (List<String>) em.createNativeQuery(
                        "select content from knowledge_chunks where skill_id = :sid "
                                + "order by created_at, title limit :k")
                .setParameter("sid", skillId)
                .setParameter("k", k)
                .getResultList();
    }

    /** Passages pas encore transformés en vecteurs. */
    @SuppressWarnings("unchecked")
    @Transactional(readOnly = true)
    public List<Pending> pending() {
        List<Object[]> rows = em.createNativeQuery(
                        "select cast(id as text), content from knowledge_chunks where embedding is null")
                .getResultList();
        List<Pending> out = new ArrayList<>();
        for (Object[] r : rows) out.add(new Pending(UUID.fromString((String) r[0]), (String) r[1]));
        return out;
    }

    @Transactional
    public void saveEmbedding(UUID chunkId, float[] vector) {
        em.createNativeQuery(
                        "update knowledge_chunks set embedding = cast(:q as vector) "
                                + "where id = cast(:id as uuid)")
                .setParameter("q", OllamaClient.toVectorLiteral(vector))
                .setParameter("id", chunkId.toString())
                .executeUpdate();
    }
}