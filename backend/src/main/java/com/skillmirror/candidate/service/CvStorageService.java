package com.skillmirror.candidate.service;

import com.skillmirror.candidate.exception.CvProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
public class CvStorageService {

    private static final Logger log = LoggerFactory.getLogger(CvStorageService.class);

    @Value("${supabase.url}") private String supabaseUrl;
    @Value("${supabase.service-key}") private String serviceKey;
    @Value("${supabase.cv-bucket:candidate_cvs}") private String bucket;

    private final HttpClient client = HttpClient.newHttpClient();

    private URI url(String path) {
        String encoded = Arrays.stream(path.split("/"))
                .map(s -> URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20"))
                .collect(Collectors.joining("/"));
        return URI.create(supabaseUrl + "/storage/v1/object/" + bucket + "/" + encoded);
    }

    private HttpRequest.Builder base(String path) {
        HttpRequest.Builder b = HttpRequest.newBuilder(url(path)).header("apikey", serviceKey);
        if (!serviceKey.startsWith("sb_")) {
            b.header("Authorization", "Bearer " + serviceKey);
        }
        return b;
    }

    public void upload(String path, byte[] content, String contentType) {
        HttpRequest req = base(path)
                .header("Content-Type", contentType)
                .header("x-upsert", "true")
                .POST(HttpRequest.BodyPublishers.ofByteArray(content))
                .build();
        HttpResponse<String> res = send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() != 200 && res.statusCode() != 201) {
            log.error("Upload Storage HTTP {} bucket={} path={} réponse={}",
                    res.statusCode(), bucket, path, res.body());
            throw new CvProcessingException("Envoi du CV vers le stockage impossible (HTTP " + res.statusCode() + ").");
        }
    }

    public byte[] download(String path) {
        HttpResponse<byte[]> res = send(base(path).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
        if (res.statusCode() != 200) {
            String body = new String(res.body(), StandardCharsets.UTF_8);
            log.error("Storage HTTP {} bucket={} path={} réponse={}", res.statusCode(), bucket, path, body);
            if (body.contains("not_found") || body.contains("Object not found")) {
                throw new CvProcessingException(
                        "Ce CV n'existe pas dans le stockage. Supprime-le puis importe-le à nouveau.");
            }
            throw new CvProcessingException(
                    "Impossible de récupérer le CV depuis le stockage (HTTP " + res.statusCode() + ").");
        }
        return res.body();
    }

    /** Suppression silencieuse : un échec ne doit pas bloquer l'utilisateur. */
    public void delete(String path) {
        try {
            send(base(path).DELETE().build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            log.warn("Suppression Storage impossible pour {} : {}", path, e.getMessage());
        }
    }

    private <T> HttpResponse<T> send(HttpRequest req, HttpResponse.BodyHandler<T> handler) {
        try {
            return client.send(req, handler);
        } catch (IOException e) {
            throw new CvProcessingException("Erreur réseau vers le stockage.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CvProcessingException("Opération de stockage interrompue.", e);
        }
    }
}