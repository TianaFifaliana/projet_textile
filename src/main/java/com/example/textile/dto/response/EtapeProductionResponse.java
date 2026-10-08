package com.example.textile.dto.response;

import com.example.textile.entity.enums.ResultatControle;
import com.example.textile.entity.enums.StatutEtape;
import com.example.textile.entity.enums.TypeEtape;
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
public class EtapeProductionResponse {

    private Long id;
    private TypeEtape type;
    private StatutEtape statut;
    private String responsable;
    private LocalDate dateDebut;
    private LocalDate dateFinPrevue;
    private LocalDate dateFinReelle;
    private String commentaire;
    private Integer quantiteProduite;
    private ResultatControle resultatControle;
    private String commentaireControle;
}