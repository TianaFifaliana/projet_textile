package com.example.textile;

import com.example.textile.dto.request.CompleteEtapeRequest;
import com.example.textile.dto.request.ControleQualiteRequest;
import com.example.textile.dto.request.CreateCommandeRequest;
import com.example.textile.dto.request.CreateLigneCommandeRequest;
import com.example.textile.entity.Client;
import com.example.textile.entity.Produit;
import com.example.textile.entity.enums.PrioriteCommande;
import com.example.textile.entity.enums.ResultatControle;
import com.example.textile.entity.enums.StatutEtape;
import com.example.textile.entity.enums.TypeEtape;
import com.example.textile.repository.ClientRepository;
import com.example.textile.repository.ProduitRepository;
import com.example.textile.service.CommandeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommandeDetailWorkflowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProduitRepository produitRepository;

    @Autowired
    private CommandeService commandeService;

    private Long commandeId;

    @BeforeEach
    void setUp() {
        Client client = clientRepository.save(Client.builder()
                .nom("Client Workflow")
                .email("client-wf-" + UUID.randomUUID() + "@example.com")
                .build());
        Produit produit = produitRepository.save(Produit.builder()
                .nom("Chemise")
                .reference("REF-WF-" + UUID.randomUUID())
                .prixBase(new BigDecimal("20.00"))
                .build());

        commandeId = commandeService.create(CreateCommandeRequest.builder()
                .numero("CMD-WF-" + UUID.randomUUID())
                .clientId(client.getId())
                .dateCommande(LocalDate.now())
                .datePrevueLivraison(LocalDate.now().plusDays(5))
                .priorite(PrioriteCommande.NORMALE)
                .lignes(List.of(CreateLigneCommandeRequest.builder()
                        .produitId(produit.getId())
                        .quantite(10)
                        .taille("M")
                        .couleur("Bleu")
                        .prixUnitaire(new BigDecimal("12.50"))
                        .build()))
                .build()).getId();
        commandeService.startProduction(commandeId);
    }

    private CompleteEtapeRequest requete(int quantite) {
        return CompleteEtapeRequest.builder()
                .dateFinReelle(LocalDate.now())
                .quantiteProduite(quantite)
                .build();
    }

    private void terminerJusqua(TypeEtape type, int quantite) {
        for (TypeEtape courant : List.of(TypeEtape.COUPE, TypeEtape.COUTURE, TypeEtape.FINITION,
                TypeEtape.CONTROLE_QUALITE, TypeEtape.LIVRAISON)) {
            commandeService.completeEtape(commandeId, courant, requete(quantite));
            if (courant == type) {
                return;
            }
        }
    }

    // ---- 1. Page de détail : workflow visuel ----

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void detailAfficheLesSixEtapesEtLePanneauDeLEtapeCourante() throws Exception {
        mockMvc.perform(get("/commandes/" + commandeId))
                .andExpect(status().isOk())
                .andExpect(view()
                        .name("commande-detail"))
                .andExpect(content()
                        .string(containsString("Commande créée")))
                .andExpect(content()
                        .string(containsString("Workflow de production")))
                .andExpect(content()
                        .string(containsString("stepper-card")))
                .andExpect(content()
                        .string(containsString("etape-EN_COURS")))
                .andExpect(content()
                        .string(containsString("href=\"#etape-detail\"")))
                .andExpect(content()
                        .string(containsString("Détail de l'étape")))
                .andExpect(content()
                        .string(containsString("Enregistrer et passer à l'étape suivante")))
                .andExpect(content()
                        .string(containsString("Valider et passer à l'étape suivante")))
                .andExpect(content()
                        .string(containsString("Responsable")));

        assertThat(commandeService.getEtapes(commandeId)).hasSize(5);
    }

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void detailMasqueLetapeCliquablePourUnRoleSansDroit() throws Exception {
        mockMvc.perform(get("/commandes/" + commandeId))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(not(containsString("href=\"#etape-detail\""))))
                .andExpect(content()
                        .string(not(containsString("Valider et passer à l'étape suivante"))));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void detailMasqueLetapeCliquablePourLectureSeule() throws Exception {
        mockMvc.perform(get("/commandes/" + commandeId))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(not(containsString("href=\"#etape-detail\""))));
    }

    // ---- 2. Validation d'une étape via le service métier ----

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void validationEtapeCourantePasseALaSuivante() throws Exception {
        mockMvc.perform(post("/commandes/" + commandeId + "/etapes/COUPE")
                        .with(csrf())
                        .param("dateFinReelle", LocalDate.now().toString())
                        .param("quantiteProduite", "10")
                        .param("commentaire", "Coupe ok"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/commandes/" + commandeId))
                .andExpect(flash().attribute("successMessage", containsString("COUPE")));

        assertThat(commandeService.getEtapes(commandeId).get(0).getStatut())
                .isEqualTo(StatutEtape.TERMINEE);
        assertThat(commandeService.getEtapes(commandeId).get(1).getStatut())
                .isEqualTo(StatutEtape.EN_COURS);
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void validationEtapeEnregistreResponsableEtDateDebut() throws Exception {
        mockMvc.perform(post("/commandes/" + commandeId + "/etapes/COUPE")
                        .with(csrf())
                        .param("responsable", "manager")
                        .param("dateDebut", LocalDate.now().minusDays(2).toString())
                        .param("dateFinReelle", LocalDate.now().toString())
                        .param("quantiteProduite", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("COUPE")));

        var etape = commandeService.getEtapes(commandeId).get(0);
        assertThat(etape.getResponsable()).isEqualTo("manager");
        assertThat(etape.getDateDebut()).isEqualTo(LocalDate.now().minusDays(2));
        assertThat(etape.getDateFinReelle()).isEqualTo(LocalDate.now());
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void validationEtapeAvecDateDebutApresDateFinReelleRemonteUneErreur() throws Exception {
        mockMvc.perform(post("/commandes/" + commandeId + "/etapes/COUPE")
                        .with(csrf())
                        .param("dateDebut", LocalDate.now().plusDays(3).toString())
                        .param("dateFinReelle", LocalDate.now().toString())
                        .param("quantiteProduite", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("postérieure")));

        assertThat(commandeService.getEtapes(commandeId).get(0).getStatut())
                .isEqualTo(StatutEtape.EN_COURS);
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void validationEtapePrecedenteNonTermineeRemonteLeMessageDuService() throws Exception {
        mockMvc.perform(post("/commandes/" + commandeId + "/etapes/COUTURE")
                        .with(csrf())
                        .param("dateFinReelle", LocalDate.now().toString())
                        .param("quantiteProduite", "5"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/commandes/" + commandeId))
                .andExpect(flash().attribute("errorMessage", containsString("n'est pas terminée")));
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void validationQuantiteSuperieureALaQuantiteCommandeeRemonteLeMessage() throws Exception {
        mockMvc.perform(post("/commandes/" + commandeId + "/etapes/COUPE")
                        .with(csrf())
                        .param("dateFinReelle", LocalDate.now().toString())
                        .param("quantiteProduite", "999"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("dépasse la quantité commandée")));
    }

    @Test
    @WithMockUser(username = "user", roles = "USER")
    void validationInterditePourUnRoleSansDroit() throws Exception {
        mockMvc.perform(post("/commandes/" + commandeId + "/etapes/COUPE")
                        .with(csrf())
                        .param("dateFinReelle", LocalDate.now().toString()))
                .andExpect(status().is3xxRedirection());

        assertThat(commandeService.getEtapes(commandeId).get(0).getStatut())
                .isEqualTo(StatutEtape.EN_COURS);
    }

    // ---- 3. Contrôle qualité ----

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void etapeControleQualiteProposeConformeOuNonConforme() throws Exception {
        terminerJusqua(TypeEtape.FINITION, 10);

        mockMvc.perform(get("/commandes/" + commandeId))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(containsString("Conforme")))
                .andExpect(content()
                        .string(containsString("Non conforme")))
                .andExpect(content()
                        .string(containsString("Enregistrer le contrôle")))
                .andExpect(content()
                        .string(containsString("/etapes/CONTROLE_QUALITE")));
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void controleNonConformeBloqueLaLivraison() throws Exception {
        terminerJusqua(TypeEtape.FINITION, 10);
        mockMvc.perform(post("/commandes/" + commandeId + "/etapes/CONTROLE_QUALITE")
                        .with(csrf())
                        .param("resultat", "NON_CONFORME")
                        .param("dateFinReelle", LocalDate.now().toString())
                        .param("quantiteProduite", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("CONTROLE_QUALITE")));

        mockMvc.perform(get("/commandes/" + commandeId))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(containsString("Livraison bloquée : contrôle qualité non conforme.")))
                .andExpect(content()
                        .string(not(containsString("Marquer comme livrée"))));

        mockMvc.perform(post("/commandes/" + commandeId + "/etapes/LIVRAISON")
                        .with(csrf())
                        .param("dateFinReelle", LocalDate.now().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("NON_CONFORME")));
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void livraisonPossibleQuandControleConforme() throws Exception {
        terminerJusqua(TypeEtape.FINITION, 10);
        commandeService.enregistrerControleQualite(commandeId, ControleQualiteRequest.builder()
                .resultat(ResultatControle.CONFORME)
                .build());
        mockMvc.perform(post("/commandes/" + commandeId + "/etapes/CONTROLE_QUALITE")
                        .with(csrf())
                        .param("resultat", "CONFORME")
                        .param("dateFinReelle", LocalDate.now().toString())
                        .param("quantiteProduite", "10"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("CONTROLE_QUALITE")));

        mockMvc.perform(get("/commandes/" + commandeId))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(containsString("Marquer comme livrée")))
                .andExpect(content()
                        .string(not(containsString("Livraison bloquée"))));

        mockMvc.perform(post("/commandes/" + commandeId + "/etapes/LIVRAISON")
                        .with(csrf())
                        .param("dateFinReelle", LocalDate.now().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMessage", containsString("LIVRAISON")));

        assertThat(commandeService.getById(commandeId).getStatut().name()).isEqualTo("LIVREE");
    }

    @Test
    @WithMockUser(username = "operator", roles = "OPERATOR")
    void controleQualiteSansResultatRemonteUneErreur() throws Exception {
        terminerJusqua(TypeEtape.FINITION, 10);

        mockMvc.perform(post("/commandes/" + commandeId + "/etapes/CONTROLE_QUALITE")
                        .with(csrf())
                        .param("dateFinReelle", LocalDate.now().toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("errorMessage", containsString("resultat du controle qualite")));
    }

    // ---- 4. Production non démarrée ----

    @Test
    @WithMockUser(username = "manager", roles = "MANAGER")
    void detailSansProductionAfficheLesEtapesPlaceholders() throws Exception {
        Long sansProduction = commandeService.create(CreateCommandeRequest.builder()
                .numero("CMD-NOPROD-" + UUID.randomUUID())
                .clientId(clientRepository.findAll().get(0).getId())
                .dateCommande(LocalDate.now())
                .datePrevueLivraison(LocalDate.now().plusDays(3))
                .priorite(PrioriteCommande.URGENTE)
                .lignes(List.of(CreateLigneCommandeRequest.builder()
                        .produitId(produitRepository.findAll().get(0).getId())
                        .quantite(5)
                        .prixUnitaire(new BigDecimal("10.00"))
                        .build()))
                .build()).getId();

        mockMvc.perform(get("/commandes/" + sansProduction))
                .andExpect(status().isOk())
                .andExpect(content()
                        .string(containsString("Commande créée")))
                .andExpect(content()
                        .string(containsString("Démarrer la production")));
    }
}
