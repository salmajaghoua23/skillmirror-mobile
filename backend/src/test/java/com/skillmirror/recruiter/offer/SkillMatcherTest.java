package com.skillmirror.recruiter.offer;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillMatcherTest {

    @Test
    void scoreUsesWeights() {
        Map<String, Boolean> offer = new LinkedHashMap<>();
        offer.put("Java", true);          // poids 2
        offer.put("Spring Boot", true);   // poids 2
        offer.put("Docker", false);       // poids 1
        // le candidat a Java + Docker => (2 + 1) / 5 = 60 %
        var r = SkillMatcher.match(offer, Set.of("java", "DOCKER"));
        assertEquals(60, r.score());
        assertEquals(2, r.matched().size());
        assertTrue(r.missing().contains("Spring Boot"));
    }

    @Test
    void ignoresCaseAndAccents() {
        var r = SkillMatcher.match(Map.of("Gestion de projet", true), Set.of("  gestion DE projet "));
        assertEquals(100, r.score());
        assertEquals(100, SkillMatcher.match(Map.of("Résolution", true), Set.of("resolution")).score());
    }

    @Test
    void noSkillsMeansZero() {
        assertEquals(0, SkillMatcher.match(Map.of(), Set.of("java")).score());
        assertEquals(0, SkillMatcher.match(Map.of("Java", true), Set.of()).score());
    }
}
