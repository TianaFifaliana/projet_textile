package com.example.textile.mapper;

import com.example.textile.dto.response.LigneCommandeResponse;
import com.example.textile.entity.LigneCommande;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", uses = ProduitMapper.class, unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface LigneCommandeMapper {

    LigneCommandeResponse toResponse(LigneCommande ligneCommande);

    @Mapping(target = "commande", ignore = true)
    LigneCommande toEntity(LigneCommandeResponse ligneCommandeResponse);
}