package com.example.textile.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AlerteLivraisonResponse {

    private CommandeResponse commande;
    private long joursRestants;
    private String libelleDelai;
}
