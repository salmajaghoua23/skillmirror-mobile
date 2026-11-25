package com.skillmirror.common.progress;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AnalysisProgressService {

    public record Progress(String status, int percent, String message) {}

    private final Map<String, Progress> map = new ConcurrentHashMap<>();
    private final ThreadLocal<String> current = new ThreadLocal<>();

    public static String cvKey(UUID cvId)        { return "cv:" + cvId; }
    public static String goalKey(UUID profileId) { return "goal:" + profileId; }

    public void bind(String key) { current.set(key); }
    public void unbind()         { current.remove(); }

    public void queued(String key) {
        map.put(key, new Progress("QUEUED", 0, "En attente"));
    }

    /** Met à jour la tâche liée au thread courant (sans effet hors d'un job). */
    public void update(int percent, String message) {
        String key = current.get();
        if (key != null) {
            map.put(key, new Progress("PROCESSING", percent, message));
        }
    }

    public void done(String key) {
        map.put(key, new Progress("DONE", 100, "Terminé"));
    }

    public void fail(String key, String message) {
        map.put(key, new Progress("FAILED", 0, message == null ? "Erreur" : message));
    }

    public Progress get(String key) {
        return map.getOrDefault(key, new Progress("UNKNOWN", 0, ""));
    }

    public boolean isRunning(String key) {
        String s = get(key).status();
        return s.equals("QUEUED") || s.equals("PROCESSING");
    }
}