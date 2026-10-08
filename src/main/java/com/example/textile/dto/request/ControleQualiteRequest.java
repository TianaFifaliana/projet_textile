package com.example.textile.dto.request;

import com.example.textile.entity.enums.ResultatControle;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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
public class ControleQualiteRequest {

    @NotNull(message = "Le résultat du contrôle qualité est obligatoire")
    private ResultatControle resultat;

    @Size(max = 1000, message = "Le commentaire ne doit pas dépasser 1000 caractères")
    private String commentaire;
}