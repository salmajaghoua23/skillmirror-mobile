package com.skillmirror.recruiter;

import com.skillmirror.common.Profile;
import com.skillmirror.common.ProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecruiterProfileServiceTest {

    @Mock ProfileRepository profiles;
    @Mock RecruiterProfileRepository recruiters;
    @Mock SectorRepository sectors;
    @Mock RecruitmentDomainRepository domains;
    @InjectMocks RecruiterProfileService service;

    private Profile account(String role) {
        Profile p = new Profile();
        p.setId(UUID.randomUUID());
        p.setFirebaseUid("uid-1");
        p.setEmail("a@b.com");
        p.setFullName("Test");
        p.setRole(role);
        return p;
    }

    private RecruiterProfileRequest request(UUID sectorId, Set<UUID> domainIds) {
        return new RecruiterProfileRequest("Salma J", "RH", "Acme", sectorId, "11-50",
                "", "Maroc", "Rabat", "Description", "", domainIds);
    }

    @Test
    void candidateCannotUseRecruiterEndpoint() {
        when(profiles.findByFirebaseUid("uid-1")).thenReturn(Optional.of(account("candidate")));

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.get("uid-1"));
        assertEquals(HttpStatus.FORBIDDEN, e.getStatusCode());
    }

    @Test
    void unknownSectorIsRejected() {
        when(profiles.findByFirebaseUid("uid-1")).thenReturn(Optional.of(account("recruiter")));
        when(recruiters.findByProfileId(any())).thenReturn(Optional.empty());
        UUID sectorId = UUID.randomUUID();
        when(sectors.findById(sectorId)).thenReturn(Optional.empty());

        ResponseStatusException e = assertThrows(ResponseStatusException.class,
                () -> service.save("uid-1", request(sectorId, Set.of(UUID.randomUUID()))));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatusCode());
    }

    @Test
    void saveMarksOnboardingDoneAndReturnsProfile() {
        Profile p = account("recruiter");
        when(profiles.findByFirebaseUid("uid-1")).thenReturn(Optional.of(p));
        when(recruiters.findByProfileId(p.getId())).thenReturn(Optional.empty());

        Sector sector = new Sector();
        sector.setId(UUID.randomUUID());
        sector.setName("Informatique");
        when(sectors.findById(sector.getId())).thenReturn(Optional.of(sector));

        RecruitmentDomain d = new RecruitmentDomain();
        d.setId(UUID.randomUUID());
        d.setName("Data & IA");
        when(domains.findAllById(any())).thenReturn(List.of(d));

        RecruiterProfileResponse res = service.save("uid-1", request(sector.getId(), Set.of(d.getId())));

        assertEquals("Acme", res.companyName());
        assertNull(res.companyWebsite());          // chaîne vide -> null
        assertEquals("Informatique", res.sector().name());
        assertEquals(1, res.domains().size());
        assertTrue(p.getOnboardingDone());
    }
}
