package com.example.textile.service;

import com.example.textile.dto.request.CompleteEtapeRequest;
import com.example.textile.dto.request.ControleQualiteRequest;
import com.example.textile.dto.request.CreateCommandeRequest;
import com.example.textile.dto.request.UpdateCommandeRequest;
import com.example.textile.dto.response.CommandeResponse;
import com.example.textile.dto.response.EtapeProductionResponse;
import com.example.textile.entity.enums.PrioriteCommande;
import com.example.textile.entity.enums.StatutCommande;
import com.example.textile.entity.enums.TypeEtape;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CommandeService {

    CommandeResponse create(CreateCommandeRequest request);

    CommandeResponse update(Long commandeId, UpdateCommandeRequest request);

    CommandeResponse getById(Long commandeId);

    Page<CommandeResponse> getAll(String search, StatutCommande statut, PrioriteCommande priorite, Pageable pageable);

    List<CommandeResponse> getCommandesEnRetard();

    CommandeResponse startProduction(Long commandeId);

    List<EtapeProductionResponse> getEtapes(Long commandeId);

    EtapeProductionResponse completeEtape(Long commandeId, TypeEtape type, CompleteEtapeRequest request);

    EtapeProductionResponse enregistrerControleQualite(Long commandeId, ControleQualiteRequest request);

    long getDureePrevue(Long commandeId);

    long getDureeReelle(Long commandeId);

    long getRetardEnJours(Long commandeId);
}