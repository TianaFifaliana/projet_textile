package com.example.textile;

import com.example.textile.dto.request.CompleteEtapeRequest;
import com.example.textile.entity.Commande;
import com.example.textile.entity.ControleQualite;
import com.example.textile.entity.EtapeProduction;
import com.example.textile.entity.LigneCommande;
import com.example.textile.entity.enums.PrioriteCommande;
import com.example.textile.entity.enums.ResultatControle;
import com.example.textile.entity.enums.StatutCommande;
import com.example.textile.entity.enums.StatutEtape;
import com.example.textile.entity.enums.TypeEtape;
import com.example.textile.exception.BusinessException;
import com.example.textile.exception.InvalidOperationException;
import com.example.textile.mapper.CommandeMapper;
import com.example.textile.mapper.EtapeProductionMapper;
import com.example.textile.repository.ClientRepository;
import com.example.textile.repository.CommandeRepository;
import com.example.textile.repository.EtapeProductionRepository;
import com.example.textile.repository.ProduitRepository;
import com.example.textile.service.CommandeService;
import com.example.textile.service.impl.CommandeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommandeServiceUnitTest {

    @Mock
    private CommandeRepository commandeRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private ProduitRepository produitRepository;

    @Mock
    private EtapeProductionRepository etapeProductionRepository;

    @Mock
    private CommandeMapper commandeMapper;

    @Mock
    private EtapeProductionMapper etapeProductionMapper;

    private CommandeService commandeService;

    @BeforeEach
    void setUp() {
        commandeService = new CommandeServiceImpl(
                commandeRepository, clientRepository, produitRepository,
                etapeProductionRepository, commandeMapper, etapeProductionMapper);
    }

    // ---- Helpers ----

    private EtapeProduction etape(TypeEtape type, StatutEtape statut) {
        return EtapeProduction.builder().type(type).statut(statut).build();
    }

    private List<EtapeProduction> workflowComplet() {
        return new ArrayList<>(List.of(
                etape(TypeEtape.COUPE, StatutEtape.EN_COURS),
                etape(TypeEtape.COUTURE, StatutEtape.EN_ATTENTE),
                etape(TypeEtape.FINITION, StatutEtape.EN_ATTENTE),
                etape(TypeEtape.CONTROLE_QUALITE, StatutEtape.EN_ATTENTE),
                etape(TypeEtape.LIVRAISON, StatutEtape.EN_ATTENTE)));
    }

    private Commande commandeEnProduction(String numero) {
        return Commande.builder()
                .numero(numero)
                .statut(StatutCommande.EN_PRODUCTION)
                .priorite(PrioriteCommande.NORMALE)
                .lignes(new ArrayList<>())
                .build();
    }

    private CompleteEtapeRequest requeteComplete(int quantite) {
        return CompleteEtapeRequest.builder()
                .dateFinReelle(LocalDate.now())
                .quantiteProduite(quantite)
                .build();
    }

    // ---- Blocage : passage à une étape si la précédente n'est pas terminée ----

    @Test
    void completeEtapeBloqueeSiEtapePrecedenteNonTerminee() {
        Commande commande = commandeEnProduction("CMD-ORDER");
        when(commandeRepository.findById(1L)).thenReturn(Optional.of(commande));
        when(etapeProductionRepository.findByCommandeIdOrderedByWorkflow(1L))
                .thenReturn(workflowComplet());

        assertThatThrownBy(() -> commandeService.completeEtape(1L, TypeEtape.COUTURE, requeteComplete(5)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("n'est pas terminée")
                .hasMessageContaining("COUPE");
    }

    // ---- Blocage : quantité déclarée > quantité de l'étape précédente ----

    @Test
    void completeEtapeQuantiteDepassantEtapePrecedente() {
        LigneCommande ligne = LigneCommande.builder().quantite(20).build();
        Commande commande = commandeEnProduction("CMD-QTY");
        commande.getLignes().add(ligne);

        List<EtapeProduction> etapes = workflowComplet();
        etapes.get(0).setStatut(StatutEtape.TERMINEE);
        etapes.get(0).setQuantiteProduite(10);
        etapes.get(1).setStatut(StatutEtape.EN_COURS);

        when(commandeRepository.findById(1L)).thenReturn(Optional.of(commande));
        when(etapeProductionRepository.findByCommandeIdOrderedByWorkflow(1L)).thenReturn(etapes);

        assertThatThrownBy(() -> commandeService.completeEtape(1L, TypeEtape.COUTURE, requeteComplete(15)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quantité produite à l'étape précédente");
    }

    // ---- Blocage : livraison si contrôle qualité NON_CONFORME ----

    @Test
    void livraisonBloqueeSiControleQualiteNonConforme() {
        Commande commande = commandeEnProduction("CMD-NC");

        List<EtapeProduction> etapes = workflowComplet();
        etapes.get(0).setStatut(StatutEtape.TERMINEE);
        etapes.get(1).setStatut(StatutEtape.TERMINEE);
        etapes.get(2).setStatut(StatutEtape.TERMINEE);
        etapes.get(3).setStatut(StatutEtape.TERMINEE);
        EtapeProduction controleQualite = etapes.get(3);
        controleQualite.setControleQualite(ControleQualite.builder()
                .resultat(ResultatControle.NON_CONFORME)
                .commentaire("Défaut couture")
                .build());
        etapes.get(4).setStatut(StatutEtape.EN_COURS);

        when(commandeRepository.findById(1L)).thenReturn(Optional.of(commande));
        when(etapeProductionRepository.findByCommandeIdOrderedByWorkflow(1L)).thenReturn(etapes);

        assertThatThrownBy(() -> commandeService.completeEtape(1L, TypeEtape.LIVRAISON,
                CompleteEtapeRequest.builder().dateFinReelle(LocalDate.now()).build()))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("NON_CONFORME");
    }

    // ---- Calcul du retard ----

    @Test
    void getRetardEnJoursCommandeNonLivreeEnRetard() {
        Commande commande = Commande.builder()
                .numero("CMD-RET-1")
                .statut(StatutCommande.EN_PRODUCTION)
                .datePrevueLivraison(LocalDate.now().minusDays(3))
                .build();
        when(commandeRepository.findById(1L)).thenReturn(Optional.of(commande));

        assertThat(commandeService.getRetardEnJours(1L)).isEqualTo(3);
    }

    @Test
    void getRetardEnJoursLivraisonTardiveMesureDepuisDateReelle() {
        Commande commande = Commande.builder()
                .numero("CMD-RET-2")
                .statut(StatutCommande.LIVREE)
                .datePrevueLivraison(LocalDate.now().minusDays(5))
                .build();
        EtapeProduction livraison = etape(TypeEtape.LIVRAISON, StatutEtape.TERMINEE);
        livraison.setDateFinReelle(LocalDate.now().minusDays(2));

        when(commandeRepository.findById(1L)).thenReturn(Optional.of(commande));
        when(etapeProductionRepository.findByCommandeIdOrderedByWorkflow(1L))
                .thenReturn(List.of(livraison));

        assertThat(commandeService.getRetardEnJours(1L)).isEqualTo(3);
    }

    @Test
    void getRetardEnJoursCommandeNonLivreeNonDepassee() {
        Commande commande = Commande.builder()
                .numero("CMD-RET-3")
                .statut(StatutCommande.EN_PRODUCTION)
                .datePrevueLivraison(LocalDate.now().plusDays(4))
                .build();
        when(commandeRepository.findById(1L)).thenReturn(Optional.of(commande));

        assertThat(commandeService.getRetardEnJours(1L)).isEqualTo(0);
    }
}