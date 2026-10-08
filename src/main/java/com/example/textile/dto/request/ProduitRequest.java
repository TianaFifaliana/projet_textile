package com.example.textile.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProduitRequest {

    @NotBlank(message = "Le nom du produit est obligatoire")
    @Size(max = 100, message = "Le nom ne doit pas dépasser 100 caractères")
    private String nom;

    @NotBlank(message = "La référence est obligatoire")
    @Size(max = 50, message = "La référence ne doit pas dépasser 50 caractères")
    private String reference;

    @Size(max = 500, message = "La description ne doit pas dépasser 500 caractères")
    private String description;

    @NotNull(message = "Le prix de base est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "Le prix de base doit être strictement positif")
    @Digits(integer = 8, fraction = 2, message = "Le prix de base est invalide")
    private BigDecimal prixBase;
}