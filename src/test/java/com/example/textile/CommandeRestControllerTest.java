package com.example.textile;

import com.example.textile.controller.rest.CommandeRestController;
import com.example.textile.dto.request.CompleteEtapeRequest;
import com.example.textile.dto.request.ControleQualiteRequest;
import com.example.textile.dto.request.CreateCommandeRequest;
import com.example.textile.dto.response.CommandeResponse;
import com.example.textile.dto.response.EtapeProductionResponse;
import com.example.textile.entity.enums.PrioriteCommande;
import com.example.textile.entity.enums.ResultatControle;
import com.example.textile.entity.enums.StatutCommande;
import com.example.textile.entity.enums.StatutEtape;
import com.example.textile.entity.enums.TypeEtape;
import com.example.textile.exception.ConflictException;
import com.example.textile.exception.GlobalExceptionHandler;
import com.example.textile.exception.ResourceNotFoundException;
import com.example.textile.service.CommandeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CommandeRestControllerTest {

    private final CommandeService commandeService = mock(CommandeService.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommandeRestController(commandeService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    private String validCreatePayload() {
        return """
                {
                  "numero": "CMD-001",
                  "clientId": 1,
                  "dateCommande": "2026-09-09",
                  "datePrevueLivraison": "2026-09-20",
                  "priorite": "NORMALE",
                  "lignes": [
                    { "produitId": 1, "quantite": 10, "prixUnitaire": 5.00 }
                  ]
                }
                """;
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        when(commandeService.create(any(CreateCommandeRequest.class)))
                .thenReturn(CommandeResponse.builder().id(1L).numero("CMD-001").build());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("/api/orders/1")))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.numero").value("CMD-001"));
    }

    @Test
    void createInvalidBodyReturns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errors.numero").value("Le numéro de la commande est obligatoire"))
                .andExpect(jsonPath("$.errors.lignes").exists());
    }

    @Test
    void createDuplicateNumberReturns409() throws Exception {
        when(commandeService.create(any(CreateCommandeRequest.class)))
                .thenThrow(new ConflictException("Le numéro CMD-001 est déjà utilisé"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validCreatePayload()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Le numéro CMD-001 est déjà utilisé"));
    }

    @Test
    void getByIdNotFoundReturns404() throws Exception {
        when(commandeService.getById(99L))
                .thenThrow(new ResourceNotFoundException("La commande 99 est introuvable"));

        mockMvc.perform(get("/api/orders/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("La commande 99 est introuvable"));
    }

    @Test
    void getAllDelegatesFiltersAndSorting() throws Exception {
        Page<CommandeResponse> page = new PageImpl<>(List.of(), PageRequest.of(0, 20), 0);
        when(commandeService.getAll(eq("CMD"), eq(StatutCommande.EN_PRODUCTION),
                eq(PrioriteCommande.URGENTE), any(PageRequest.class))).thenReturn(page);

        mockMvc.perform(get("/api/orders")
                        .param("page", "1")
                        .param("size", "10")
                        .param("sort", "priorite,desc")
                        .param("status", StatutCommande.EN_PRODUCTION.name())
                        .param("priority", PrioriteCommande.URGENTE.name())
                        .param("search", "CMD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").value(0));

        ArgumentCaptor<PageRequest> pageRequestCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(commandeService).getAll(eq("CMD"), eq(StatutCommande.EN_PRODUCTION),
                eq(PrioriteCommande.URGENTE), pageRequestCaptor.capture());
        assertThat(pageRequestCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageRequestCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageRequestCaptor.getValue().getSort().toString()).contains("priorite: DESC");
    }

    @Test
    void startProductionReturnsCommandUpdated() throws Exception {
        CommandeResponse enProduction = CommandeResponse.builder().id(1L).statut(StatutCommande.EN_PRODUCTION).build();
        when(commandeService.startProduction(1L)).thenReturn(enProduction);

        mockMvc.perform(post("/api/orders/1/production/start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("EN_PRODUCTION"));
    }

    @Test
    void getProductionReturnsSteps() throws Exception {
        EtapeProductionResponse coupe = EtapeProductionResponse.builder()
                .id(10L).type(TypeEtape.COUPE).statut(StatutEtape.TERMINEE).build();
        when(commandeService.getEtapes(1L)).thenReturn(List.of(coupe));

        mockMvc.perform(get("/api/orders/1/production"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("COUPE"))
                .andExpect(jsonPath("$[0].statut").value("TERMINEE"));
    }

    @Test
    void completeStepDelegatesAndReturnsStep() throws Exception {
        EtapeProductionResponse terminee = EtapeProductionResponse.builder()
                .id(10L).type(TypeEtape.COUTURE).statut(StatutEtape.TERMINEE).build();
        when(commandeService.completeEtape(eq(1L), eq(TypeEtape.COUTURE), any(CompleteEtapeRequest.class)))
                .thenReturn(terminee);

        mockMvc.perform(post("/api/orders/1/production/steps/COUTURE/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateFinReelle\": \"2026-09-10\", \"quantiteProduite\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("COUTURE"))
                .andExpect(jsonPath("$.statut").value("TERMINEE"));
    }

    @Test
    void completeStepUnknownTypeReturns400() throws Exception {
        mockMvc.perform(post("/api/orders/1/production/steps/INCONNUE/complete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateFinReelle\": \"2026-09-10\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void getDelayedReturnsOrders() throws Exception {
        CommandeResponse retard = CommandeResponse.builder().id(2L).statut(StatutCommande.EN_PRODUCTION).build();
        when(commandeService.getCommandesEnRetard()).thenReturn(List.of(retard));

        mockMvc.perform(get("/api/orders/delayed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(2));
    }

    @Test
    void qualityControlDelegates() throws Exception {
        EtapeProductionResponse controle = EtapeProductionResponse.builder()
                .id(13L).type(TypeEtape.CONTROLE_QUALITE)
                .resultatControle(ResultatControle.CONFORME).build();
        when(commandeService.enregistrerControleQualite(eq(1L), any(ControleQualiteRequest.class)))
                .thenReturn(controle);

        mockMvc.perform(post("/api/orders/1/quality-control")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resultat\": \"CONFORME\", \"commentaire\": \"conforme\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultatControle").value("CONFORME"));
    }

    @Test
    void deliveryCompletesLivraisonAndReturnsCommand() throws Exception {
        CommandeResponse livree = CommandeResponse.builder().id(1L).statut(StatutCommande.LIVREE).build();
        when(commandeService.completeEtape(eq(1L), eq(TypeEtape.LIVRAISON), any(CompleteEtapeRequest.class)))
                .thenReturn(EtapeProductionResponse.builder().build());
        when(commandeService.getById(1L)).thenReturn(livree);

        mockMvc.perform(post("/api/orders/1/delivery")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateFinReelle\": \"2026-09-20\", \"quantiteProduite\": 10}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statut").value("LIVREE"));
    }
}