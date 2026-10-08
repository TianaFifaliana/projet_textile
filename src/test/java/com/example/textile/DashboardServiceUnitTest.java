package com.example.textile;

import com.example.textile.dto.response.AlerteControleQualiteResponse;
import com.example.textile.dto.response.AlerteLivraisonResponse;
import com.example.textile.dto.response.CommandeResponse;
import com.example.textile.dto.response.DashboardResponse;
import com.example.textile.entity.Commande;
import com.example.textile.entity.ControleQualite;
import com.example.textile.entity.EtapeProduction;
import com.example.textile.entity.enums.PrioriteCommande;
import com.example.textile.entity.enums.ResultatControle;
import com.example.textile.entity.enums.StatutCommande;
import com.example.textile.entity.enums.StatutEtape;
import com.example.textile.entity.enums.TypeEtape;
import com.example.textile.mapper.CommandeMapper;
import com.example.textile.repository.CommandeRepository;
import com.example.textile.repository.EtapeProductionRepository;
import com.example.textile.service.DashboardService;
import com.example.textile.service.impl.DashboardServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DashboardServiceUnitTest {

    @Mock
    private CommandeRepository commandeRepository;

    @Mock
    private EtapeProductionRepository etapeProductionRepository;

    @Mock
    private CommandeMapper commandeMapper;

    private DashboardService dashboardService;

    @BeforeEach
    void setUp() {
        dashboardService = new DashboardServiceImpl(
                commandeRepository, etapeProductionRepository, commandeMapper);

        lenient().when(commandeRepository.countByStatutIn(anyCollection())).thenReturn(0L);
        lenient().when(commandeRepository.countByDatePrevueLivraisonBeforeAndStatutNot(
                any(LocalDate.class), any(StatutCommande.class))).thenReturn(0L);
        lenient().when(commandeRepository.countByStatut(any(StatutCommande.class))).thenReturn(0L);
        lenient().when(commandeRepository.countLivreesDuMois(
                any(LocalDate.class), any(LocalDate.class))).thenReturn(0L);
        lenient().when(commandeRepository.findByPrioriteInAndStatutNotIn(anyCollection(), anyCollection()))
                .thenReturn(List.of());
        lenient().when(commandeRepository.findCommandesAvecControleNonConforme()).thenReturn(List.of());
        lenient().when(commandeRepository.findByDatePrevueLivraisonBetweenAndStatutNotIn(
                any(LocalDate.class), any(LocalDate.class), anyCollection())).thenReturn(List.of());
        lenient().when(etapeProductionRepository.countEtapesEnCoursParType(anyCollection()))
                .thenReturn(List.of());
        lenient().when(commandeMapper.toResponse(any(Commande.class)))
                .thenAnswer(invocation -> toResponse(invocation.getArgument(0)));
    }

    // ---- Helpers ----

    private Commande commande(Long id, String numero, PrioriteCommande priorite, LocalDate dateLivraison) {
        return Commande.builder()
                .id(id)
                .numero(numero)
                .priorite(priorite)
                .statut(StatutCommande.EN_PRODUCTION)
                .datePrevueLivraison(dateLivraison)
                .build();
    }

    private CommandeResponse toResponse(Commande commande) {
        return CommandeResponse.builder()
                .id(commande.getId())
                .numero(commande.getNumero())
                .datePrevueLivraison(commande.getDatePrevueLivraison())
                .priorite(commande.getPriorite())
                .statut(commande.getStatut())
                .build();
    }

    // ---- Tests ----

    @Test
    void getDashboardRetourneEtatVideParDefaut() {
        DashboardResponse dashboard = dashboardService.getDashboard();

        assertThat(dashboard.getNombreEnCours()).isZero();
        assertThat(dashboard.getNombreEnRetard()).isZero();
        assertThat(dashboard.getNombreTerminees()).isZero();
        assertThat(dashboard.getNombreLivreesMois()).isZero();
        assertThat(dashboard.getCommandesUrgentes()).isEmpty();
        assertThat(dashboard.getControlesNonConformes()).isEmpty();
        assertThat(dashboard.getLivraisonsProches()).isEmpty();
        assertThat(dashboard.getCommandesParEtape()).hasSize(4);
        assertThat(dashboard.getCommandesParEtape().values()).containsOnly(0L);
    }

    @Test
    void getDashboardCompteCartesSynthese() {
        when(commandeRepository.countByStatutIn(anyCollection())).thenReturn(7L);
        when(commandeRepository.countByDatePrevueLivraisonBeforeAndStatutNot(
                any(LocalDate.class), eq(StatutCommande.LIVREE))).thenReturn(3L);
        when(commandeRepository.countByStatut(StatutCommande.TERMINEE)).thenReturn(5L);
        when(commandeRepository.countLivreesDuMois(
                any(LocalDate.class), any(LocalDate.class))).thenReturn(12L);

        DashboardResponse dashboard = dashboardService.getDashboard();

        assertThat(dashboard.getNombreEnCours()).isEqualTo(7);
        assertThat(dashboard.getNombreEnRetard()).isEqualTo(3);
        assertThat(dashboard.getNombreTerminees()).isEqualTo(5);
        assertThat(dashboard.getNombreLivreesMois()).isEqualTo(12);
    }

    @Test
    void getDashboardTrieCommandesUrgentesParPrioritePuisDate() {
        Commande urgenteTardive = commande(1L, "CMD-001", PrioriteCommande.URGENTE, LocalDate.now().plusDays(10));
        Commande tresUrgente = commande(2L, "CMD-002", PrioriteCommande.TRES_URGENTE, LocalDate.now().plusDays(5));
        Commande urgenteProche = commande(3L, "CMD-003", PrioriteCommande.URGENTE, LocalDate.now().plusDays(2));
        Commande tresUrgenteSansDate = commande(4L, "CMD-004", PrioriteCommande.TRES_URGENTE, null);

        when(commandeRepository.findByPrioriteInAndStatutNotIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(urgenteTardive, tresUrgente, urgenteProche, tresUrgenteSansDate));

        DashboardResponse dashboard = dashboardService.getDashboard();

        assertThat(dashboard.getCommandesUrgentes())
                .extracting(CommandeResponse::getId)
                .containsExactly(2L, 4L, 3L, 1L);
    }

    @Test
    void getDashboardLimiteCommandesUrgentesA10() {
        List<Commande> urgentes = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            urgentes.add(commande((long) i, "CMD-" + i, PrioriteCommande.URGENTE, LocalDate.now().plusDays(i)));
        }
        when(commandeRepository.findByPrioriteInAndStatutNotIn(anyCollection(), anyCollection()))
                .thenReturn(urgentes);

        DashboardResponse dashboard = dashboardService.getDashboard();

        assertThat(dashboard.getCommandesUrgentes()).hasSize(10);
        assertThat(dashboard.getCommandesUrgentes())
                .extracting(CommandeResponse::getId)
                .first()
                .isEqualTo(1L);
    }

    @Test
    void getDashboardConstruitCommandesParEtapeAvecLibellesFrancais() {
        when(etapeProductionRepository.countEtapesEnCoursParType(anyCollection()))
                .thenReturn(List.of(
                        new Object[]{TypeEtape.COUPE, 3L},
                        new Object[]{TypeEtape.CONTROLE_QUALITE, 2L}));

        DashboardResponse dashboard = dashboardService.getDashboard();

        assertThat(dashboard.getCommandesParEtape())
                .containsExactly(
                        Map.entry("Coupe", 3L),
                        Map.entry("Couture", 0L),
                        Map.entry("Finition", 0L),
                        Map.entry("Contrôle qualité", 2L));
    }

    @Test
    void getDashboardCalculeDelaisLivraisonsProches() {
        LocalDate today = LocalDate.now();
        Commande aujourdhui = commande(1L, "CMD-001", PrioriteCommande.NORMALE, today);
        Commande demain = commande(2L, "CMD-002", PrioriteCommande.NORMALE, today.plusDays(1));
        Commande dansTroisJours = commande(3L, "CMD-003", PrioriteCommande.NORMALE, today.plusDays(3));

        when(commandeRepository.findByDatePrevueLivraisonBetweenAndStatutNotIn(
                any(LocalDate.class), any(LocalDate.class), anyCollection()))
                .thenReturn(List.of(dansTroisJours, aujourdhui, demain));

        DashboardResponse dashboard = dashboardService.getDashboard();

        assertThat(dashboard.getLivraisonsProches())
                .extracting(AlerteLivraisonResponse::getLibelleDelai)
                .containsExactly("Aujourd'hui", "Demain", "J-3");
        assertThat(dashboard.getLivraisonsProches())
                .extracting(AlerteLivraisonResponse::getJoursRestants)
                .containsExactly(0L, 1L, 3L);
    }

    @Test
    void getDashboardRecupereCommentaireControleNonConforme() {
        EtapeProduction controle = EtapeProduction.builder()
                .type(TypeEtape.CONTROLE_QUALITE)
                .statut(StatutEtape.TERMINEE)
                .controleQualite(ControleQualite.builder()
                        .resultat(ResultatControle.NON_CONFORME)
                        .commentaire("Racords visibles sur la piece 3")
                        .build())
                .build();
        Commande commande = Commande.builder()
                .id(1L)
                .numero("CMD-001")
                .priorite(PrioriteCommande.URGENTE)
                .statut(StatutCommande.EN_PRODUCTION)
                .datePrevueLivraison(LocalDate.now())
                .etapesProduction(new ArrayList<>(List.of(controle)))
                .build();

        when(commandeRepository.findCommandesAvecControleNonConforme()).thenReturn(List.of(commande));

        DashboardResponse dashboard = dashboardService.getDashboard();

        assertThat(dashboard.getControlesNonConformes()).hasSize(1);
        AlerteControleQualiteResponse alerte = dashboard.getControlesNonConformes().get(0);
        assertThat(alerte.getCommande().getId()).isEqualTo(1L);
        assertThat(alerte.getCommentaire()).isEqualTo("Racords visibles sur la piece 3");
    }
}
