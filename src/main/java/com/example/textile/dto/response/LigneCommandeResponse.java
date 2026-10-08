package com.example.textile.dto.response;

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
public class LigneCommandeResponse {

    private Long id;
    private ProduitResponse produit;
    private Integer quantite;
    private String taille;
    private String couleur;
    private BigDecimal prixUnitaire;
    private Integer quantiteProduite;
}