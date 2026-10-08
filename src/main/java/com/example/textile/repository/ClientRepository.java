package com.example.textile.repository;

import com.example.textile.entity.Client;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClientRepository extends JpaRepository<Client, Long> {

    Optional<Client> findByEmail(String email);

    List<Client> findByNomContainingIgnoreCase(String nom);

    Page<Client> findByNomContainingIgnoreCase(String nom, Pageable pageable);

    boolean existsByEmail(String email);
}