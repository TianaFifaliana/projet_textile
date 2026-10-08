package com.example.textile.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {

    private long nombreEnCours;
    private long nombreEnRetard;
    private long nombreTerminees;
    private long nombreLivreesMois;

    @Builder.Default
    private List<CommandeResponse> commandesUrgentes = new ArrayList<>();

    // Libellé d'étape (français) -> nombre de commandes actuellement sur cette étape.
    @Builder.Default
    private Map<String, Long> commandesParEtape = new LinkedHashMap<>();

    @Builder.Default
    private List<AlerteControleQualiteResponse> controlesNonConformes = new ArrayList<>();

    @Builder.Default
    private List<AlerteLivraisonResponse> livraisonsProches = new ArrayList<>();
}
