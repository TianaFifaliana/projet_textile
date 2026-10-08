package com.example.textile;

import com.example.textile.dto.request.CreateCommandeRequest;
import com.example.textile.dto.request.CreateLigneCommandeRequest;
import com.example.textile.dto.response.EtapeProductionResponse;
import com.example.textile.entity.Client;
import com.example.textile.entity.Produit;
import com.example.textile.entity.enums.PrioriteCommande;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommandeWebControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProduitRepository produitRepository;

    @Autowired
    private CommandeService commandeService;

    private Client client;
    private Produit produit;

    @BeforeEach
    void setUp() {
        client = clientRepository.save(Client.builder()
                .nom("Client Web")
                .email("client-web-" + UUID.randomUUID() + "@example.com")
                .build());
        produit = produitRepository.save(Produit.builder()
                .nom("Chemise")
                .reference("REF-WEB-" + UUID.randomUUID())
                .prixBase(new BigDecimal("20.00"))
                .build());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void formulaireNouvelleCommandeAccessibleAuManager() throws Exception {
        mockMvc.perform(get("/commandes/nouvelle"))
                .andExpect(status().isOk())
                .andExpect(view().name("commande-form"))
                .andExpect(model().attributeExists("commandeForm", "clients", "produits", "priorities"));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void creationCommandeParManagerRedirigeVersLeDetail() throws Exception {
        mockMvc.perform(post("/commandes")
                        .with(csrf())
                        .param("numero", "CMD-WEB-" + UUID.randomUUID())
                        .param("clientId", client.getId().toString())
                        .param("dateCommande", "2026-09-15")
                        .param("datePrevueLivraison", "2026-09-30")
                        .param("priorite", "NORMALE")
                        .param("lignes[0].produitId", produit.getId().toString())
                        .param("lignes[0].quantite", "10")
                        .param("lignes[0].taille", "M")
                        .param("lignes[0].couleur", "Bleu")
                        .param("lignes[0].prixUnitaire", "12.50"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/commandes/*"));
    }

    @Test
    @WithMockUser(roles = "OPERATOR")
    void creationCommandeInterditePourOperator() throws Exception {
        mockMvc.perform(post("/commandes")
                        .with(csrf())
                        .param("numero", "CMD-WEB-BLOCKED")
                        .param("clientId", client.getId().toString())
                        .param("dateCommande", "2026-09-15")
                        .param("priorite", "NORMALE")
                        .param("lignes[0].produitId", produit.getId().toString())
                        .param("lignes[0].quantite", "10")
                        .param("lignes[0].prixUnitaire", "12.50"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void formulaireAvecErreursResteSurLaVue() throws Exception {
        mockMvc.perform(post("/commandes")
                        .with(csrf())
                        .param("numero", "")
                        .param("clientId", "")
                        .param("dateCommande", "")
                        .param("priorite", "")
                        .param("lignes[0].produitId", "")
                        .param("lignes[0].quantite", "")
                        .param("lignes[0].prixUnitaire", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("commande-form"))
                .andExpect(model().attributeHasFieldErrors("commandeForm", "numero", "clientId", "dateCommande", "priorite"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void listeCommandesAfficheLeLienVoirDetail() throws Exception {
        commandeService.create(CreateCommandeRequest.builder()
                .numero("CMD-LIST-" + UUID.randomUUID())
                .clientId(client.getId())
                .dateCommande(LocalDate.now())
                .datePrevueLivraison(LocalDate.now().plusDays(5))
                .priorite(PrioriteCommande.NORMALE)
                .lignes(List.of(CreateLigneCommandeRequest.builder()
                        .produitId(produit.getId())
                        .quantite(10)
                        .prixUnitaire(new BigDecimal("12.50"))
                        .build()))
                .build());

        mockMvc.perform(get("/commandes"))
                .andExpect(status().isOk())
                .andExpect(view().name("commandes"))
                .andExpect(content().string(containsString("Voir ")))
                .andExpect(content().string(containsString("Avancement")))
                .andExpect(content().string(containsString("Livraison prevue")));;
    }

    @Test
    @WithMockUser(username = "resp-atelier", roles = "MANAGER")
    void pageDetailAfficheLeWorkflowVisuelDesEtapes() throws Exception {
        Long commandeId = commandeService.create(CreateCommandeRequest.builder()
                .numero("CMD-DETAIL-" + UUID.randomUUID())
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

        List<EtapeProductionResponse> etapes = commandeService.getEtapes(commandeId);
        assertThat(etapes).hasSize(5);
        assertThat(etapes).allMatch(etape -> etape.getDateFinPrevue() != null);
        assertThat(etapes.get(0).getResponsable()).isEqualTo("resp-atelier");

        mockMvc.perform(get("/commandes/" + commandeId))
                .andExpect(status().isOk())
                .andExpect(view().name("commande-detail"))
                .andExpect(content().string(containsString("Workflow de production")))
                .andExpect(content().string(containsString("stepper-card")))
                .andExpect(content().string(containsString("Responsable")))
                .andExpect(content().string(containsString("resp-atelier")))
                .andExpect(content().string(containsString("Fin pr")))
                .andExpect(content().string(containsString("COUPE")));
    }
}