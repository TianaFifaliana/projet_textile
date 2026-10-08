package com.example.textile.dto.response;

import com.example.textile.entity.enums.RoleUtilisateur;
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
public class UtilisateurResponse {

    private Long id;
    private String username;
    private String prenom;
    private String nom;
    private boolean actif;
    private RoleUtilisateur role;
}