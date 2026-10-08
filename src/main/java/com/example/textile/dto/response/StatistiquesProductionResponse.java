package com.example.textile.dto.response;

import com.example.textile.entity.enums.PrioriteCommande;
import com.example.textile.entity.enums.StatutCommande;
import com.example.textile.entity.enums.TypeEtape;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StatistiquesProductionResponse {

    private long totalCommandes;
    private long nombreEnRetard;
    private double tauxEnRetard;
    private Map<StatutCommande, Long> commandesParStatut;
    private Map<PrioriteCommande, Long> commandesParPriorite;
    private Map<TypeEtape, Long> quantitesParEtape;
}