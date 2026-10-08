package com.example.textile.dto.response;

import com.example.textile.entity.enums.PrioriteCommande;
import com.example.textile.entity.enums.StatutCommande;
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
public class CommandeResponse {

    private Long id;
    private String numero;
    private ClientResponse client;
    private LocalDate dateCommande;
    private LocalDate datePrevueLivraison;
    private PrioriteCommande priorite;
    private StatutCommande statut;

    @Builder.Default
    private List<LigneCommandeResponse> lignes = new ArrayList<>();

    @Builder.Default
    private List<EtapeProductionResponse> etapes = new ArrayList<>();
}