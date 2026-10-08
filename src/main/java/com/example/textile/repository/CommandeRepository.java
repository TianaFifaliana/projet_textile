package com.example.textile.repository;

import com.example.textile.entity.Commande;
import com.example.textile.entity.enums.PrioriteCommande;
import com.example.textile.entity.enums.StatutCommande;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface CommandeRepository extends JpaRepository<Commande, Long>, JpaSpecificationExecutor<Commande> {

    boolean existsByNumero(String numero);

    long countByStatut(StatutCommande statut);

    long countByStatutIn(Collection<StatutCommande> statuts);

    long countByPriorite(PrioriteCommande priorite);

    long countByDatePrevueLivraisonBeforeAndStatutNot(LocalDate date, StatutCommande statutExclu);

    List<Commande> findByPrioriteInAndStatutNotIn(Collection<PrioriteCommande> priorites,
                                                  Collection<StatutCommande> statutsExclus);

    List<Commande> findByDatePrevueLivraisonBetweenAndStatutNotIn(LocalDate debut, LocalDate fin,
                                                                  Collection<StatutCommande> statutsExclus);

    // Commandes effectivement livrées pendant le mois [debut, fin[ (étape LIVRAISON terminée).
    @Query("SELECT COUNT(DISTINCT c) FROM Commande c JOIN c.etapesProduction e " +
            "WHERE c.statut = com.example.textile.entity.enums.StatutCommande.LIVREE " +
            "AND e.type = com.example.textile.entity.enums.TypeEtape.LIVRAISON " +
            "AND e.dateFinReelle >= :debut AND e.dateFinReelle < :fin")
    long countLivreesDuMois(@Param("debut") LocalDate debut, @Param("fin") LocalDate fin);

    // Commandes dont le dernier contrôle qualité est non conforme et qui ne sont ni livrées ni annulées.
    @Query("SELECT DISTINCT c FROM Commande c JOIN c.etapesProduction e JOIN e.controleQualite cq " +
            "WHERE cq.resultat = com.example.textile.entity.enums.ResultatControle.NON_CONFORME " +
            "AND c.statut <> com.example.textile.entity.enums.StatutCommande.LIVREE " +
            "AND c.statut <> com.example.textile.entity.enums.StatutCommande.ANNULEE " +
            "ORDER BY c.datePrevueLivraison ASC")
    List<Commande> findCommandesAvecControleNonConforme();

    List<Commande> findByStatut(StatutCommande statut);

    List<Commande> findByDatePrevueLivraisonBeforeAndStatutNot(LocalDate date, StatutCommande statutExclu);

    Page<Commande> findByDatePrevueLivraisonBeforeAndStatutNot(LocalDate date, StatutCommande statutExclu, Pageable pageable);

    Page<Commande> findByStatut(StatutCommande statut, Pageable pageable);

    Page<Commande> findByPriorite(PrioriteCommande priorite, Pageable pageable);

    Page<Commande> findByClientId(Long clientId, Pageable pageable);

    Page<Commande> findByStatutAndPriorite(StatutCommande statut, PrioriteCommande priorite, Pageable pageable);

    Page<Commande> findByStatutAndClientId(StatutCommande statut, Long clientId, Pageable pageable);

    Page<Commande> findByPrioriteAndClientId(PrioriteCommande priorite, Long clientId, Pageable pageable);

    Page<Commande> findByStatutAndPrioriteAndClientId(StatutCommande statut, PrioriteCommande priorite, Long clientId, Pageable pageable);
}