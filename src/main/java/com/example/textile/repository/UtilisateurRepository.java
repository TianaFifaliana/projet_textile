package com.example.textile.repository;

import com.example.textile.entity.Utilisateur;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UtilisateurRepository extends JpaRepository<Utilisateur, Long> {

    Optional<Utilisateur> findByUsername(String username);

    Optional<Utilisateur> findByUsernameAndActifTrue(String username);

    boolean existsByUsername(String username);
}