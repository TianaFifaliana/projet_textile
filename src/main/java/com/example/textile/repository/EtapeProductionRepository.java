package com.example.textile.repository;

import com.example.textile.entity.EtapeProduction;
import com.example.textile.entity.enums.TypeEtape;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EtapeProductionRepository extends JpaRepository<EtapeProduction, Long> {

    @Query("SELECT e FROM EtapeProduction e WHERE e.commande.id = :commandeId " +
            "ORDER BY CASE e.type " +
            "WHEN com.example.textile.entity.enums.TypeEtape.COUPE THEN 1 " +
            "WHEN com.example.textile.entity.enums.TypeEtape.COUTURE THEN 2 " +
            "WHEN com.example.textile.entity.enums.TypeEtape.FINITION THEN 3 " +
            "WHEN com.example.textile.entity.enums.TypeEtape.CONTROLE_QUALITE THEN 4 " +
            "WHEN com.example.textile.entity.enums.TypeEtape.LIVRAISON THEN 5 ELSE 6 END, e.id")
    List<EtapeProduction> findByCommandeIdOrderedByWorkflow(@Param("commandeId") Long commandeId);

    @Query("SELECT e.type, SUM(e.quantiteProduite) FROM EtapeProduction e " +
            "WHERE e.quantiteProduite IS NOT NULL GROUP BY e.type")
    List<Object[]> sumQuantitesProduiteParType();

    List<EtapeProduction> findByCommandeId(Long commandeId);

    // Nombre d'étapes en cours par type (étape en cours = commande actuellement sur cette étape),
    // en excluant les commandes annulées.
    @Query("SELECT e.type, COUNT(e) FROM EtapeProduction e " +
            "WHERE e.statut = com.example.textile.entity.enums.StatutEtape.EN_COURS " +
            "AND e.type IN (:types) " +
            "AND e.commande.statut <> com.example.textile.entity.enums.StatutCommande.ANNULEE " +
            "GROUP BY e.type")
    List<Object[]> countEtapesEnCoursParType(@Param("types") Collection<TypeEtape> types);
}