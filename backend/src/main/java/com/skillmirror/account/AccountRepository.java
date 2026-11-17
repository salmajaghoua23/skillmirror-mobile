package com.skillmirror.account;

import com.skillmirror.common.Profile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountRepository extends JpaRepository<Profile, UUID> {
    Optional<Profile> findByFirebaseUid(String firebaseUid);
}