package com.example.textile.repository;

import com.example.textile.entity.Produit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProduitRepository extends JpaRepository<Produit, Long> {

    Optional<Produit> findByReference(String reference);

    List<Produit> findByNomContainingIgnoreCase(String nom);

    Page<Produit> findByNomContainingIgnoreCase(String nom, Pageable pageable);

    boolean existsByReference(String reference);
}