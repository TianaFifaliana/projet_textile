package com.example.textile.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
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
public class CreateLigneCommandeRequest {

    @NotNull(message = "Le produit est obligatoire")
    @Positive(message = "L'identifiant du produit doit être positif")
    private Long produitId;

    @NotNull(message = "La quantité est obligatoire")
    @Positive(message = "La quantité doit être supérieure à zéro")
    private Integer quantite;

    @Size(max = 20, message = "La taille ne doit pas dépasser 20 caractères")
    private String taille;

    @Size(max = 30, message = "La couleur ne doit pas dépasser 30 caractères")
    private String couleur;

    @NotNull(message = "Le prix unitaire est obligatoire")
    @DecimalMin(value = "0.0", inclusive = false, message = "Le prix unitaire doit être supérieur à zéro")
    @Digits(integer = 8, fraction = 2, message = "Le prix unitaire doit comporter au plus 8 chiffres et 2 décimales")
    private BigDecimal prixUnitaire;
}