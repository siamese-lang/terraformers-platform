package com.terraformers.modernization.identity;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

    Optional<UserEntity> findByExternalIdentityProviderAndExternalIdentitySubject(
            String externalIdentityProvider,
            String externalIdentitySubject
    );

    Optional<UserEntity> findByEmail(String email);
}
