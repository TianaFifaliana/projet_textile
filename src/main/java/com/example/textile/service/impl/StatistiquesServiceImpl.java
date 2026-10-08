package com.example.textile.service.impl;

import com.example.textile.dto.response.StatistiquesProductionResponse;
import com.example.textile.entity.enums.PrioriteCommande;
import com.example.textile.entity.enums.StatutCommande;
import com.example.textile.entity.enums.TypeEtape;
import com.example.textile.repository.CommandeRepository;
import com.example.textile.repository.EtapeProductionRepository;
import com.example.textile.service.StatistiquesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class StatistiquesServiceImpl implements StatistiquesService {

    private final CommandeRepository commandeRepository;
    private final EtapeProductionRepository etapeProductionRepository;

    @Override
    @Transactional(readOnly = true)
    public StatistiquesProductionResponse getStatistiquesProduction() {
        long totalCommandes = commandeRepository.count();
        long nombreEnRetard = commandeRepository
                .countByDatePrevueLivraisonBeforeAndStatutNot(LocalDate.now(), StatutCommande.LIVREE);
        double tauxEnRetard = totalCommandes == 0 ? 0.0
                : Math.round((nombreEnRetard * 100.0 / totalCommandes) * 100.0) / 100.0;

        Map<StatutCommande, Long> commandesParStatut = new LinkedHashMap<>();
        for (StatutCommande statut : StatutCommande.values()) {
            commandesParStatut.put(statut, commandeRepository.countByStatut(statut));
        }

        Map<PrioriteCommande, Long> commandesParPriorite = new LinkedHashMap<>();
        for (PrioriteCommande priorite : PrioriteCommande.values()) {
            commandesParPriorite.put(priorite, commandeRepository.countByPriorite(priorite));
        }

        Map<TypeEtape, Long> quantitesParEtape = new LinkedHashMap<>();
        for (TypeEtape type : TypeEtape.values()) {
            quantitesParEtape.put(type, 0L);
        }
        for (Object[] ligne : etapeProductionRepository.sumQuantitesProduiteParType()) {
            quantitesParEtape.put((TypeEtape) ligne[0], ((Number) ligne[1]).longValue());
        }

        return StatistiquesProductionResponse.builder()
                .totalCommandes(totalCommandes)
                .nombreEnRetard(nombreEnRetard)
                .tauxEnRetard(tauxEnRetard)
                .commandesParStatut(commandesParStatut)
                .commandesParPriorite(commandesParPriorite)
                .quantitesParEtape(quantitesParEtape)
                .build();
    }
}