package com.example.textile;

import com.example.textile.dto.request.CompleteEtapeRequest;
import com.example.textile.dto.request.ControleQualiteRequest;
import com.example.textile.dto.request.CreateCommandeRequest;
import com.example.textile.dto.request.CreateLigneCommandeRequest;
import com.example.textile.dto.request.UpdateCommandeRequest;
import com.example.textile.dto.response.CommandeResponse;
import com.example.textile.dto.response.EtapeProductionResponse;
import com.example.textile.entity.Client;
import com.example.textile.entity.Commande;
import com.example.textile.entity.Produit;
import com.example.textile.entity.enums.PrioriteCommande;
import com.example.textile.entity.enums.ResultatControle;
import com.example.textile.entity.enums.StatutCommande;
import com.example.textile.entity.enums.StatutEtape;
import com.example.textile.entity.enums.TypeEtape;
import com.example.textile.exception.BusinessException;
import com.example.textile.exception.ConflictException;
import com.example.textile.exception.InvalidOperationException;
import com.example.textile.exception.ResourceNotFoundException;
import com.example.textile.repository.ClientRepository;
import com.example.textile.repository.CommandeRepository;
import com.example.textile.repository.ProduitRepository;
import com.example.textile.service.CommandeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class CommandeServiceTest {

    @Autowired
    private CommandeService commandeService;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProduitRepository produitRepository;

    @Autowired
    private CommandeRepository commandeRepository;

    private Client client;
    private Produit produit;

    @BeforeEach
    void setUp() {
        client = clientRepository.save(Client.builder()
                .nom("Client Test")
                .email("client.test@example.com")
                .build());
        produit = produitRepository.save(Produit.builder()
                .nom("T-shirt")
                .reference("REF-" + UUID.randomUUID())
                .prixBase(new BigDecimal("15.00"))
                .build());
    }

    // ---- Helpers ----

    private CreateLigneCommandeRequest ligneRequest(int quantite) {
        return CreateLigneCommandeRequest.builder()
                .produitId(produit.getId())
                .quantite(quantite)
                .taille("M")
                .couleur("Rouge")
                .prixUnitaire(new BigDecimal("12.00"))
                .build();
    }

    private CreateCommandeRequest commandeRequest(String numero, int quantite) {
        return CreateCommandeRequest.builder()
                .numero(numero)
                .clientId(client.getId())
                .dateCommande(LocalDate.now())
                .datePrevueLivraison(LocalDate.now().plusDays(5))
                .priorite(PrioriteCommande.NORMALE)
                .lignes(List.of(ligneRequest(quantite)))
                .build();
    }

    private Long createEtDemarrer(String numero, int quantite) {
        Long id = commandeService.create(commandeRequest(numero, quantite)).getId();
        commandeService.startProduction(id);
        return id;
    }

    private EtapeProductionResponse complete(Long commandeId, TypeEtape type, int quantite) {
        return commandeService.completeEtape(commandeId, type, CompleteEtapeRequest.builder()
                .dateFinReelle(LocalDate.now())
                .quantiteProduite(quantite)
                .build());
    }

    private void livrerCommande(Long commandeId) {
        complete(commandeId, TypeEtape.COUPE, 10);
        complete(commandeId, TypeEtape.COUTURE, 10);
        complete(commandeId, TypeEtape.FINITION, 10);
        commandeService.enregistrerControleQualite(commandeId,
                ControleQualiteRequest.builder().resultat(ResultatControle.CONFORME).build());
        complete(commandeId, TypeEtape.CONTROLE_QUALITE, 10);
        complete(commandeId, TypeEtape.LIVRAISON, 10);
    }

    // ---- Tests création ----

    @Test
    void createPersistsCommandeAvecLignesEtStatutInitial() {
        CommandeResponse response = commandeService.create(commandeRequest("CMD-CREATE-1", 10));

        assertThat(response.getId()).isNotNull();
        assertThat(response.getStatut()).isEqualTo(StatutCommande.EN_ATTENTE);
        assertThat(response.getClient().getId()).isEqualTo(client.getId());
        assertThat(response.getLignes()).hasSize(1);
        assertThat(response.getLignes().get(0).getProduit().getReference()).isEqualTo(produit.getReference());

        Commande saved = commandeRepository.findById(response.getId()).orElseThrow();
        assertThat(saved.getLignes().get(0).getCommande().getId()).isEqualTo(saved.getId());
    }

    @Test
    void createWithDuplicateNumeroThrowsConflict() {
        commandeService.create(commandeRequest("CMD-DUP", 5));

        assertThatThrownBy(() -> commandeService.create(commandeRequest("CMD-DUP", 5)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("déjà utilisé");
    }

    @Test
    void createWithUnknownClientThrowsResourceNotFound() {
        CreateCommandeRequest request = commandeRequest("CMD-NOCLIENT", 5);
        request.setClientId(999999L);

        assertThatThrownBy(() -> commandeService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Client introuvable");
    }

    @Test
    void createWithUnknownProduitThrowsResourceNotFound() {
        CreateCommandeRequest request = commandeRequest("CMD-NOPRODUIT", 5);
        request.getLignes().get(0).setProduitId(999999L);

        assertThatThrownBy(() -> commandeService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Produit introuvable");
    }

    // ---- Tests démarrage du workflow ----

    @Test
    void startProductionInitializeLeWorkflowDansLOrdre() {
        Long commandeId = commandeService.create(commandeRequest("CMD-START", 10)).getId();

        CommandeResponse demarree = commandeService.startProduction(commandeId);

        assertThat(demarree.getStatut()).isEqualTo(StatutCommande.EN_PRODUCTION);
        List<EtapeProductionResponse> etapes = commandeService.getEtapes(commandeId);
        assertThat(etapes).hasSize(5);
        assertThat(etapes).extracting(EtapeProductionResponse::getType)
                .containsExactly(TypeEtape.COUPE, TypeEtape.COUTURE, TypeEtape.FINITION,
                        TypeEtape.CONTROLE_QUALITE, TypeEtape.LIVRAISON);
        assertThat(etapes).extracting(EtapeProductionResponse::getStatut)
                .containsExactly(StatutEtape.EN_COURS, StatutEtape.EN_ATTENTE, StatutEtape.EN_ATTENTE,
                        StatutEtape.EN_ATTENTE, StatutEtape.EN_ATTENTE);
    }

    @Test
    void startProductionTwiceThrowsInvalidOperation() {
        Long commandeId = createEtDemarrer("CMD-START2", 10);

        assertThatThrownBy(() -> commandeService.startProduction(commandeId))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("déjà été démarrée");
    }

    // ---- Tests validation d'étape ----

    @Test
    void completeEtapeHorsOrdreThrowsInvalidOperation() {
        Long commandeId = createEtDemarrer("CMD-ORDER", 10);

        assertThatThrownBy(() -> complete(commandeId, TypeEtape.COUTURE, 10))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("n'est pas terminée");
    }

    @Test
    void completeEtapeQuantiteDepassantCommandeeThrowsBusiness() {
        Long commandeId = createEtDemarrer("CMD-QTY1", 10);

        assertThatThrownBy(() -> complete(commandeId, TypeEtape.COUPE, 12))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("dépasse la quantité commandée");
    }

    @Test
    void completeEtapeQuantiteDepassantEtapePrecedenteThrowsBusiness() {
        Long commandeId = createEtDemarrer("CMD-QTY2", 20);
        complete(commandeId, TypeEtape.COUPE, 12);

        assertThatThrownBy(() -> complete(commandeId, TypeEtape.COUTURE, 15))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("quantité produite à l'étape précédente");
    }

    @Test
    void completeEtapeQuantiteMonotoneAcceptee() {
        Long commandeId = createEtDemarrer("CMD-QTY3", 10);
        complete(commandeId, TypeEtape.COUPE, 10);
        EtapeProductionResponse couture = complete(commandeId, TypeEtape.COUTURE, 9);
        EtapeProductionResponse finition = complete(commandeId, TypeEtape.FINITION, 9);

        assertThat(couture.getQuantiteProduite()).isEqualTo(9);
        assertThat(finition.getStatut()).isEqualTo(StatutEtape.TERMINEE);
    }

    // ---- Tests contrôle qualité / livraison ----

    @Test
    void workflowCompletAvecControleConformeAboutitALaLivraison() {
        Long commandeId = createEtDemarrer("CMD-LIV", 10);
        livrerCommande(commandeId);

        CommandeResponse commande = commandeService.getById(commandeId);
        assertThat(commande.getStatut()).isEqualTo(StatutCommande.LIVREE);
        assertThat(commandeService.getEtapes(commandeId).get(3).getResultatControle())
                .isEqualTo(ResultatControle.CONFORME);
    }

    @Test
    void completeEtapeControleSansResultatThrowsInvalidOperation() {
        Long commandeId = createEtDemarrer("CMD-CQ0", 10);
        complete(commandeId, TypeEtape.COUPE, 10);
        complete(commandeId, TypeEtape.COUTURE, 10);
        complete(commandeId, TypeEtape.FINITION, 10);

        assertThatThrownBy(() -> complete(commandeId, TypeEtape.CONTROLE_QUALITE, 10))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("résultat du contrôle qualité");
    }

    @Test
    void livraisonBloqueeSiControleNonConforme() {
        Long commandeId = createEtDemarrer("CMD-NC", 10);
        complete(commandeId, TypeEtape.COUPE, 10);
        complete(commandeId, TypeEtape.COUTURE, 10);
        complete(commandeId, TypeEtape.FINITION, 10);
        commandeService.enregistrerControleQualite(commandeId,
                ControleQualiteRequest.builder().resultat(ResultatControle.NON_CONFORME)
                        .commentaire("Défaut couture").build());
        complete(commandeId, TypeEtape.CONTROLE_QUALITE, 10);

        assertThatThrownBy(() -> complete(commandeId, TypeEtape.LIVRAISON, 10))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("NON_CONFORME");
    }

    @Test
    void livraisonImpossibleSiCommandeNonTerminee() {
        Long commandeId = createEtDemarrer("CMD-NOTERM", 10);
        complete(commandeId, TypeEtape.COUPE, 10);
        complete(commandeId, TypeEtape.COUTURE, 10);

        assertThatThrownBy(() -> complete(commandeId, TypeEtape.LIVRAISON, 10))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("n'est pas terminée");
    }

    // ---- Tests contraintes globales ----

    @Test
    void updateCommandeLivreeThrowsInvalidOperation() {
        Long commandeId = createEtDemarrer("CMD-UPDLIV", 10);
        livrerCommande(commandeId);

        UpdateCommandeRequest request = UpdateCommandeRequest.builder()
                .numero("CMD-UPDLIV")
                .clientId(client.getId())
                .dateCommande(LocalDate.now())
                .datePrevueLivraison(LocalDate.now().plusDays(5))
                .priorite(PrioriteCommande.URGENTE)
                .statut(StatutCommande.LIVREE)
                .lignes(List.of(ligneRequest(10)))
                .build();

        assertThatThrownBy(() -> commandeService.update(commandeId, request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("déjà livrée");
    }

    @Test
    void updateCommandeNonLivreeModifieLesChamps() {
        Long commandeId = commandeService.create(commandeRequest("CMD-UPD", 10)).getId();

        UpdateCommandeRequest request = UpdateCommandeRequest.builder()
                .numero("CMD-UPD2")
                .clientId(client.getId())
                .dateCommande(LocalDate.now())
                .datePrevueLivraison(LocalDate.now().plusDays(10))
                .priorite(PrioriteCommande.URGENTE)
                .statut(StatutCommande.EN_ATTENTE)
                .lignes(List.of(ligneRequest(20)))
                .build();

        CommandeResponse response = commandeService.update(commandeId, request);

        assertThat(response.getNumero()).isEqualTo("CMD-UPD2");
        assertThat(response.getPriorite()).isEqualTo(PrioriteCommande.URGENTE);
        assertThat(response.getLignes().get(0).getQuantite()).isEqualTo(20);
    }

    // ---- Tests retards / délais ----

    @Test
    void getCommandesEnRetardDetecteUniquementLesNonLivrees() {
        CreateCommandeRequest enRetard = commandeRequest("CMD-R1", 10);
        enRetard.setDatePrevueLivraison(LocalDate.now().minusDays(3));
        commandeService.create(enRetard);

        CreateCommandeRequest livree = commandeRequest("CMD-R2", 10);
        livree.setDatePrevueLivraison(LocalDate.now().minusDays(3));
        Long commandeLivreeId = commandeService.create(livree).getId();
        commandeService.startProduction(commandeLivreeId);
        livrerCommande(commandeLivreeId);

        List<CommandeResponse> enRetardList = commandeService.getCommandesEnRetard();

        assertThat(enRetardList).extracting(CommandeResponse::getNumero).contains("CMD-R1");
        assertThat(enRetardList).extracting(CommandeResponse::getNumero).doesNotContain("CMD-R2");
    }

    @Test
    void calculDesDelaisRetourneDureesEtRetard() {
        CreateCommandeRequest request = commandeRequest("CMD-DELAI", 10);
        request.setDateCommande(LocalDate.now().minusDays(10));
        request.setDatePrevueLivraison(LocalDate.now().minusDays(2));
        Long commandeId = commandeService.create(request).getId();

        assertThat(commandeService.getDureePrevue(commandeId)).isEqualTo(8);
        assertThat(commandeService.getRetardEnJours(commandeId)).isEqualTo(2);
        assertThat(commandeService.getDureeReelle(commandeId)).isEqualTo(10);
    }

    @Test
    void retardNulSiPasEncoreDepassee() {
        Long commandeId = commandeService.create(commandeRequest("CMD-PAS", 10)).getId();

        assertThat(commandeService.getRetardEnJours(commandeId)).isEqualTo(0);
    }
}