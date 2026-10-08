package com.example.textile.mapper;

import com.example.textile.dto.request.CreateCommandeRequest;
import com.example.textile.dto.request.CreateLigneCommandeRequest;
import com.example.textile.dto.response.CommandeResponse;
import com.example.textile.entity.Client;
import com.example.textile.entity.Commande;
import com.example.textile.entity.LigneCommande;
import com.example.textile.entity.Produit;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = {ClientMapper.class, LigneCommandeMapper.class, EtapeProductionMapper.class},
        unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface CommandeMapper {

    @Mapping(target = "etapes", source = "etapesProduction")
    CommandeResponse toResponse(Commande commande);

    @Mapping(target = "etapesProduction", source = "etapes")
    Commande toEntity(CommandeResponse commandeResponse);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "statut", ignore = true)
    @Mapping(target = "client", source = "clientId")
    @Mapping(target = "etapesProduction", ignore = true)
    Commande toEntity(CreateCommandeRequest request);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "commande", ignore = true)
    @Mapping(target = "produit", source = "produitId")
    @Mapping(target = "quantiteProduite", ignore = true)
    LigneCommande toLigne(CreateLigneCommandeRequest request);

    default Client toClient(Long clientId) {
        return Client.builder().id(clientId).build();
    }

    default Produit toProduit(Long produitId) {
        return Produit.builder().id(produitId).build();
    }
}