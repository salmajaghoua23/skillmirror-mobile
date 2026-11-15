package com.skillmirror.recruiter;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

public record RecruiterProfileRequest(
        @NotBlank(message = "Nom obligatoire") @Size(max = 150) String fullName,
        @NotBlank(message = "Poste obligatoire") @Size(max = 150) String position,
        @NotBlank(message = "Nom de l'entreprise obligatoire") @Size(max = 150) String companyName,
        @NotNull(message = "Secteur obligatoire") UUID sectorId,
        @NotBlank(message = "Taille obligatoire") @Size(max = 30) String companySize,
        @Size(max = 255)
        @Pattern(regexp = "^(https?://\\S+)?$", message = "Site web invalide (http:// ou https://)")
        String companyWebsite,
        @NotBlank(message = "Pays obligatoire") @Size(max = 100) String country,
        @NotBlank(message = "Ville obligatoire") @Size(max = 100) String city,
        @NotBlank(message = "Description obligatoire") @Size(max = 2000) String companyDescription,
        @Size(max = 2000) String hiringNeeds,
        @NotEmpty(message = "Au moins un domaine de recrutement") Set<UUID> domainIds) {}