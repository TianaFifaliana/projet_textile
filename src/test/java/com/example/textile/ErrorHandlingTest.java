package com.example.textile;

import com.example.textile.dto.request.CreateCommandeRequest;
import com.example.textile.exception.BusinessException;
import com.example.textile.exception.ConflictException;
import com.example.textile.exception.GlobalExceptionHandler;
import com.example.textile.exception.InvalidOperationException;
import com.example.textile.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ErrorHandlingTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new ErrorTestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void resourceNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("La commande 42 est introuvable"))
                .andExpect(jsonPath("$.path").value("/test/not-found"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors").doesNotExist());
    }

    @Test
    void conflictReturns409() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.message").value("Le numéro CMD-001 est déjà utilisé"))
                .andExpect(jsonPath("$.path").value("/test/conflict"));
    }

    @Test
    void businessAndInvalidOperationReturn400() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Impossible de clôturer l'étape"))
                .andExpect(jsonPath("$.path").value("/test/business"));

        mockMvc.perform(get("/test/invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Opération non autorisée dans l'état actuel"));
    }

    @Test
    void validationReturns400WithFieldDetails() throws Exception {
        mockMvc.perform(post("/test/create")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/test/create"))
                .andExpect(jsonPath("$.errors.numero").value("Le numéro de la commande est obligatoire"))
                .andExpect(jsonPath("$.errors.clientId").value("Le client est obligatoire"))
                .andExpect(jsonPath("$.errors.dateCommande").value("La date de commande est obligatoire"))
                .andExpect(jsonPath("$.errors.priorite").value("La priorité est obligatoire"));
    }

    @RestController
    static class ErrorTestController {

        @GetMapping("/test/not-found")
        public void notFound() {
            throw new ResourceNotFoundException("La commande 42 est introuvable");
        }

        @GetMapping("/test/conflict")
        public void conflict() {
            throw new ConflictException("Le numéro CMD-001 est déjà utilisé");
        }

        @GetMapping("/test/business")
        public void business() {
            throw new BusinessException("Impossible de clôturer l'étape");
        }

        @GetMapping("/test/invalid")
        public void invalid() {
            throw new InvalidOperationException("Opération non autorisée dans l'état actuel");
        }

        @PostMapping("/test/create")
        public String create(@Valid @RequestBody CreateCommandeRequest request) {
            return "ok";
        }
    }
}