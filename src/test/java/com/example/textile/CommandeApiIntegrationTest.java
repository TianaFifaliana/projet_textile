package com.example.textile;

import com.example.textile.entity.Client;
import com.example.textile.entity.Produit;
import com.example.textile.repository.ClientRepository;
import com.example.textile.repository.ProduitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class CommandeApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ClientRepository clientRepository;

    @Autowired
    private ProduitRepository produitRepository;

    private Client client;
    private Produit produit;

    @BeforeEach
    void setUp() {
        client = clientRepository.save(Client.builder()
                .nom("Client Integration")
                .email("client-it-" + UUID.randomUUID() + "@example.com")
                .build());
        produit = produitRepository.save(Produit.builder()
                .nom("Chemise")
                .reference("REF-" + UUID.randomUUID())
                .prixBase(new BigDecimal("20.00"))
                .build());
    }

    private String payload(String numero) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "numero", numero,
                "clientId", client.getId(),
                "dateCommande", "2026-09-09",
                "datePrevueLivraison", "2026-09-20",
                "priorite", "NORMALE",
                "lignes", List.of(Map.of(
                        "produitId", produit.getId(),
                        "quantite", 10,
                        "prixUnitaire", 12.50))));
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void createOrderReturns201WithLocationAndInitialStatut() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("CMD-IT-1")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.numero").value("CMD-IT-1"))
                .andExpect(jsonPath("$.statut").value("EN_ATTENTE"))
                .andExpect(jsonPath("$.client.nom").value(client.getNom()))
                .andExpect(jsonPath("$.lignes[0].produit.reference").value(produit.getReference()))
                .andExpect(jsonPath("$.id").exists())
                .andReturn();

        long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        String location = result.getResponse().getHeader("Location");
        assertThat(location).endsWith("/api/orders/" + id);
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void createOrderInvalidBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.numero").value("Le numéro de la commande est obligatoire"))
                .andExpect(jsonPath("$.errors.clientId").exists())
                .andExpect(jsonPath("$.errors.lignes").exists());
    }

    @Test
    @WithMockUser(roles = "MANAGER")
    void createOrderDuplicateNumeroReturns409() throws Exception {
        String numero = "CMD-IT-DUP";

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(numero)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload(numero)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value(containsString("déjà utilisé")));
    }

    @Test
    void createOrderUnauthenticatedReturns401() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload("CMD-IT-ANON")))
                .andExpect(status().isUnauthorized());
    }
}