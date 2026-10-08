package com.example.textile.service.impl;

import com.example.textile.dto.request.UtilisateurRequest;
import com.example.textile.dto.response.UtilisateurResponse;
import com.example.textile.entity.Utilisateur;
import com.example.textile.entity.enums.RoleUtilisateur;
import com.example.textile.exception.ConflictException;
import com.example.textile.exception.InvalidOperationException;
import com.example.textile.exception.ResourceNotFoundException;
import com.example.textile.repository.UtilisateurRepository;
import com.example.textile.service.UtilisateurService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UtilisateurServiceImpl implements UtilisateurService {

    private static final int PASSWORD_MIN_LENGTH = 6;

    private final UtilisateurRepository utilisateurRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public List<UtilisateurResponse> findAll() {
        return utilisateurRepository.findAll(Sort.by("id")).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public UtilisateurResponse getById(Long id) {
        return toResponse(findOrThrow(id));
    }

    @Override
    @Transactional
    public UtilisateurResponse create(UtilisateurRequest request) {
        if (utilisateurRepository.existsByUsername(request.getUsername())) {
            throw new ConflictException("Le nom d'utilisateur '" + request.getUsername() + "' est déjà utilisé");
        }
        if (request.getPassword() == null || request.getPassword().isBlank()) {
            throw new InvalidOperationException("Le mot de passe est obligatoire à la création d'un compte");
        }
        if (request.getPassword().length() < PASSWORD_MIN_LENGTH) {
            throw new InvalidOperationException("Le mot de passe doit contenir au moins " + PASSWORD_MIN_LENGTH + " caractères");
        }

        Utilisateur utilisateur = Utilisateur.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .prenom(request.getPrenom())
                .nom(request.getNom())
                .actif(request.isActif())
                .role(request.getRole())
                .build();
        return toResponse(utilisateurRepository.save(utilisateur));
    }

    @Override
    @Transactional
    public UtilisateurResponse update(Long id, UtilisateurRequest request) {
        Utilisateur utilisateur = findOrThrow(id);

        utilisateurRepository.findByUsername(request.getUsername())
                .filter(existant -> !existant.getId().equals(id))
                .ifPresent(existant -> {
                    throw new ConflictException("Le nom d'utilisateur '" + request.getUsername() + "' est déjà utilisé");
                });

        utilisateur.setUsername(request.getUsername());
        utilisateur.setPrenom(request.getPrenom());
        utilisateur.setNom(request.getNom());
        utilisateur.setRole(request.getRole());
        utilisateur.setActif(request.isActif());

        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            if (request.getPassword().length() < PASSWORD_MIN_LENGTH) {
                throw new InvalidOperationException("Le mot de passe doit contenir au moins " + PASSWORD_MIN_LENGTH + " caractères");
            }
            utilisateur.setPassword(passwordEncoder.encode(request.getPassword()));
        }

        return toResponse(utilisateur);
    }

    @Override
    @Transactional
    public UtilisateurResponse setActif(Long id, boolean actif) {
        Utilisateur utilisateur = findOrThrow(id);

        if (!actif && utilisateur.getRole() == RoleUtilisateur.ADMIN) {
            long administrateursActifs = utilisateurRepository.findAll().stream()
                    .filter(u -> u.getRole() == RoleUtilisateur.ADMIN && u.isActif())
                    .count();
            if (administrateursActifs <= 1) {
                throw new InvalidOperationException("Impossible de désactiver le dernier administrateur actif.");
            }
        }

        utilisateur.setActif(actif);
        return toResponse(utilisateur);
    }

    private Utilisateur findOrThrow(Long id) {
        return utilisateurRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("L'utilisateur " + id + " est introuvable"));
    }

    private UtilisateurResponse toResponse(Utilisateur utilisateur) {
        return UtilisateurResponse.builder()
                .id(utilisateur.getId())
                .username(utilisateur.getUsername())
                .prenom(utilisateur.getPrenom())
                .nom(utilisateur.getNom())
                .actif(utilisateur.isActif())
                .role(utilisateur.getRole())
                .build();
    }
}