package com.example.textile.dto.request;

import com.example.textile.entity.enums.PrioriteCommande;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCommandeRequest {

    @NotBlank(message = "Le numéro de la commande est obligatoire")
    @Size(max = 50, message = "Le numéro de commande ne doit pas dépasser 50 caractères")
    private String numero;

    @NotNull(message = "Le client est obligatoire")
    @Positive(message = "L'identifiant du client doit être positif")
    private Long clientId;

    @NotNull(message = "La date de commande est obligatoire")
    private LocalDate dateCommande;

    private LocalDate datePrevueLivraison;

    @NotNull(message = "La priorité est obligatoire")
    private PrioriteCommande priorite;

    @Builder.Default
    @Valid
    @Size(min = 1, message = "Une commande doit contenir au moins une ligne de commande")
    private List<CreateLigneCommandeRequest> lignes = new ArrayList<>();
}