package com.example.textile.service;

import com.example.textile.dto.request.UtilisateurRequest;
import com.example.textile.dto.response.UtilisateurResponse;

import java.util.List;

public interface UtilisateurService {

    List<UtilisateurResponse> findAll();

    UtilisateurResponse getById(Long id);

    UtilisateurResponse create(UtilisateurRequest request);

    UtilisateurResponse update(Long id, UtilisateurRequest request);

    UtilisateurResponse setActif(Long id, boolean actif);
}