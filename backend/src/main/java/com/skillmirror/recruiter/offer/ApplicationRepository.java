package com.skillmirror.recruiter.offer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ApplicationRepository extends JpaRepository<Application, UUID> {

    List<Application> findByOfferIdOrderByCreatedAtDesc(UUID offerId);

    long countByOfferId(UUID offerId);

    boolean existsByOfferIdAndCandidateProfileId(UUID offerId, UUID candidateProfileId);

    /** Toutes les candidatures reçues sur les offres d'un recruteur. */
    @Query("select a from Application a where a.offerId in "
            + "(select o.id from JobOffer o where o.recruiterId = :recruiterId) "
            + "order by a.createdAt desc")
    List<Application> findAllForRecruiter(@Param("recruiterId") UUID recruiterId);

    /** Le candidat a-t-il postulé à au moins une offre de ce recruteur ? */
    @Query("select count(a) from Application a where a.candidateProfileId = :candidateId and a.offerId in "
            + "(select o.id from JobOffer o where o.recruiterId = :recruiterId)")
    long countByCandidateAndRecruiter(@Param("candidateId") UUID candidateId,
                                      @Param("recruiterId") UUID recruiterId);
}
