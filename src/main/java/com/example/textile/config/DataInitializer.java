package com.example.textile.config;

import com.example.textile.entity.Client;
import com.example.textile.entity.Produit;
import com.example.textile.entity.Utilisateur;
import com.example.textile.entity.enums.RoleUtilisateur;
import com.example.textile.repository.ClientRepository;
import com.example.textile.repository.ProduitRepository;
import com.example.textile.repository.UtilisateurRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UtilisateurRepository utilisateurRepository;
    private final ClientRepository clientRepository;
    private final ProduitRepository produitRepository;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public CommandLineRunner seedUtilisateurs() {
        return args -> {
            creerUtilisateurSiAbsent("admin", "admin123", "Admin", "Principal", RoleUtilisateur.ADMIN);
            creerUtilisateurSiAbsent("manager", "manager123", "Marie", "Gestion", RoleUtilisateur.MANAGER);
            creerUtilisateurSiAbsent("operator", "operator123", "Omar", "Production", RoleUtilisateur.OPERATOR);
            creerUtilisateurSiAbsent("user", "user123", "Ulysse", "Consultant", RoleUtilisateur.USER);

            creerClientSiAbsent("Mode Plus", "12 rue de la République, Lyon", "04 72 00 00 01", "contact@modeplus.example.com");
            creerClientSiAbsent("Textile Centre", "8 avenue des Ternes, Paris", "01 45 00 00 02", "contact@textilecentre.example.com");
            creerClientSiAbsent("Vêtements du Sud", "25 boulevard de la Plaine, Marseille", "04 91 00 00 03", "contact@vetementsdusud.example.com");

            creerProduitSiAbsent("Chemise homme", "CHEM-001", "Chemise manches longues en coton", new BigDecimal("25.00"));
            creerProduitSiAbsent("Pantalon coton", "PANT-002", "Pantalon droit en coton", new BigDecimal("35.00"));
            creerProduitSiAbsent("Robe d'été", "ROBE-003", "Robe légère en viscose", new BigDecimal("45.00"));
        };
    }

    private void creerUtilisateurSiAbsent(String username, String rawPassword, String prenom, String nom, RoleUtilisateur role) {
        if (utilisateurRepository.existsByUsername(username)) {
            return;
        }
        utilisateurRepository.save(Utilisateur.builder()
                .username(username)
                .password(passwordEncoder.encode(rawPassword))
                .prenom(prenom)
                .nom(nom)
                .actif(true)
                .role(role)
                .build());
    }

    private void creerClientSiAbsent(String nom, String adresse, String telephone, String email) {
        if (clientRepository.existsByEmail(email)) {
            return;
        }
        clientRepository.save(Client.builder()
                .nom(nom)
                .adresse(adresse)
                .telephone(telephone)
                .email(email)
                .build());
    }

    private void creerProduitSiAbsent(String nom, String reference, String description, BigDecimal prixBase) {
        if (produitRepository.existsByReference(reference)) {
            return;
        }
        produitRepository.save(Produit.builder()
                .nom(nom)
                .reference(reference)
                .description(description)
                .prixBase(prixBase)
                .build());
    }
}