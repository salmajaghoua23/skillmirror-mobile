package com.skillmirror.candidate.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import com.skillmirror.candidate.exception.CvProcessingException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class CvStorageService {

    @Value("${supabase.url}") private String supabaseUrl;
    @Value("${supabase.service-key}") private String serviceKey;
    @Value("${supabase.cv-bucket:cvs}") private String bucket;

    private final HttpClient client = HttpClient.newHttpClient();

    public byte[] download(String filePath) {
        try {
            HttpRequest.Builder b = HttpRequest.newBuilder(
                            URI.create(supabaseUrl + "/storage/v1/object/" + bucket + "/" + filePath))
                    .header("apikey", serviceKey);
            if (!serviceKey.startsWith("sb_")) {          // ancienne clé JWT (service_role)
                b.header("Authorization", "Bearer " + serviceKey);
            }
            HttpRequest req = b.GET().build();
            HttpResponse<byte[]> res = client.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (res.statusCode() != 200) {
                throw new CvProcessingException(
                        "Impossible de récupérer le CV depuis le stockage (HTTP " + res.statusCode() + ").");
            }
            return res.body();
        } catch (IOException e) {
            throw new CvProcessingException("Erreur réseau lors de la récupération du CV.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CvProcessingException("Récupération du CV interrompue.", e);
        }
    }
}