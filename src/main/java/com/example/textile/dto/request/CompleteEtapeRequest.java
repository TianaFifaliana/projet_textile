package com.example.textile.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompleteEtapeRequest {

    @Size(max = 100, message = "Le responsable ne doit pas dépasser 100 caractères")
    private String responsable;

    private LocalDate dateDebut;

    @NotNull(message = "La date de fin réelle est obligatoire pour clôturer une étape")
    private LocalDate dateFinReelle;

    @Positive(message = "La quantité produite déclarée doit être positive")
    private Integer quantiteProduite;

    @Size(max = 1000, message = "Le commentaire ne doit pas dépasser 1000 caractères")
    private String commentaire;
}