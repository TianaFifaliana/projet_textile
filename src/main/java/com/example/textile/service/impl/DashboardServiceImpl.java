package com.example.textile.service.impl;

import com.example.textile.dto.response.AlerteControleQualiteResponse;
import com.example.textile.dto.response.AlerteLivraisonResponse;
import com.example.textile.dto.response.CommandeResponse;
import com.example.textile.dto.response.DashboardResponse;
import com.example.textile.entity.Commande;
import com.example.textile.entity.enums.PrioriteCommande;
import com.example.textile.entity.enums.ResultatControle;
import com.example.textile.entity.enums.StatutCommande;
import com.example.textile.entity.enums.TypeEtape;
import com.example.textile.mapper.CommandeMapper;
import com.example.textile.repository.CommandeRepository;
import com.example.textile.repository.EtapeProductionRepository;
import com.example.textile.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {

    private static final List<StatutCommande> STATUTS_EN_COURS = List.of(
            StatutCommande.EN_ATTENTE, StatutCommande.VALIDEE, StatutCommande.EN_PRODUCTION);

    private static final List<PrioriteCommande> PRIORITES_URGENTES = List.of(
            PrioriteCommande.TRES_URGENTE, PrioriteCommande.URGENTE);

    private static final List<StatutCommande> STATUTS_EXCLUS = List.of(
            StatutCommande.LIVREE, StatutCommande.ANNULEE);

    private static final List<TypeEtape> ETAPES_SUIVIES = List.of(
            TypeEtape.COUPE, TypeEtape.COUTURE, TypeEtape.FINITION, TypeEtape.CONTROLE_QUALITE);

    private static final Map<TypeEtape, String> LIBELLES_ETAPES = Map.of(
            TypeEtape.COUPE, "Coupe",
            TypeEtape.COUTURE, "Couture",
            TypeEtape.FINITION, "Finition",
            TypeEtape.CONTROLE_QUALITE, "Contrôle qualité");

    private static final int SEUIL_LIVRAISON_PROCHE_JOURS = 3;
    private static final int NOMBRE_URGENTES_AFFICHEES = 10;

    private final CommandeRepository commandeRepository;
    private final EtapeProductionRepository etapeProductionRepository;
    private final CommandeMapper commandeMapper;

    @Override
    @Transactional(readOnly = true)
    public DashboardResponse getDashboard() {
        LocalDate today = LocalDate.now();
        LocalDate debutMois = today.withDayOfMonth(1);
        LocalDate finMois = debutMois.plusMonths(1);

        return DashboardResponse.builder()
                .nombreEnCours(commandeRepository.countByStatutIn(STATUTS_EN_COURS))
                .nombreEnRetard(commandeRepository
                        .countByDatePrevueLivraisonBeforeAndStatutNot(today, StatutCommande.LIVREE))
                .nombreTerminees(commandeRepository.countByStatut(StatutCommande.TERMINEE))
                .nombreLivreesMois(commandeRepository.countLivreesDuMois(debutMois, finMois))
                .commandesUrgentes(getCommandesUrgentes())
                .commandesParEtape(getCommandesParEtape())
                .controlesNonConformes(getControlesNonConformes())
                .livraisonsProches(getLivraisonsProches(today))
                .build();
    }

    // TRÈS_URGENTE avant URGENTE, puis par date de livraison prévue (nulls en dernier).
    private List<CommandeResponse> getCommandesUrgentes() {
        Comparator<Commande> triUrgentes = Comparator
                .comparingInt((Commande commande) -> commande.getPriorite().ordinal())
                .reversed()
                .thenComparing(Commande::getDatePrevueLivraison,
                        Comparator.nullsLast(Comparator.naturalOrder()));

        return commandeRepository.findByPrioriteInAndStatutNotIn(PRIORITES_URGENTES, STATUTS_EXCLUS)
                .stream()
                .sorted(triUrgentes)
                .limit(NOMBRE_URGENTES_AFFICHEES)
                .map(commandeMapper::toResponse)
                .toList();
    }

    private Map<String, Long> getCommandesParEtape() {
        Map<TypeEtape, Long> compteurs = new EnumMap<>(TypeEtape.class);
        for (Object[] ligne : etapeProductionRepository.countEtapesEnCoursParType(ETAPES_SUIVIES)) {
            compteurs.put((TypeEtape) ligne[0], ((Number) ligne[1]).longValue());
        }

        Map<String, Long> parEtape = new LinkedHashMap<>();
        for (TypeEtape type : ETAPES_SUIVIES) {
            parEtape.put(LIBELLES_ETAPES.get(type), compteurs.getOrDefault(type, 0L));
        }
        return parEtape;
    }

    private List<AlerteControleQualiteResponse> getControlesNonConformes() {
        return commandeRepository.findCommandesAvecControleNonConforme()
                .stream()
                .map(commande -> AlerteControleQualiteResponse.builder()
                        .commande(commandeMapper.toResponse(commande))
                        .commentaire(commentaireNonConforme(commande))
                        .build())
                .toList();
    }

    private List<AlerteLivraisonResponse> getLivraisonsProches(LocalDate today) {
        return commandeRepository
                .findByDatePrevueLivraisonBetweenAndStatutNotIn(
                        today, today.plusDays(SEUIL_LIVRAISON_PROCHE_JOURS), STATUTS_EXCLUS)
                .stream()
                .map(commande -> {
                    long joursRestants = ChronoUnit.DAYS.between(today, commande.getDatePrevueLivraison());
                    return AlerteLivraisonResponse.builder()
                            .commande(commandeMapper.toResponse(commande))
                            .joursRestants(joursRestants)
                            .libelleDelai(libelleDelai(joursRestants))
                            .build();
                })
                .sorted(Comparator.comparingLong(AlerteLivraisonResponse::getJoursRestants))
                .toList();
    }

    private String commentaireNonConforme(Commande commande) {
        return commande.getEtapesProduction().stream()
                .filter(etape -> etape.getControleQualite() != null
                        && etape.getControleQualite().getResultat() == ResultatControle.NON_CONFORME)
                .map(etape -> etape.getControleQualite().getCommentaire())
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private String libelleDelai(long joursRestants) {
        if (joursRestants <= 0) {
            return "Aujourd'hui";
        }
        if (joursRestants == 1) {
            return "Demain";
        }
        return "J-" + joursRestants;
    }
}
