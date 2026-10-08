package com.example.textile.mapper;

import com.example.textile.dto.response.EtapeProductionResponse;
import com.example.textile.entity.EtapeProduction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface EtapeProductionMapper {

    @Mapping(target = "resultatControle", source = "controleQualite.resultat")
    @Mapping(target = "commentaireControle", source = "controleQualite.commentaire")
    EtapeProductionResponse toResponse(EtapeProduction etapeProduction);

    @Mapping(target = "commande", ignore = true)
    @Mapping(target = "controleQualite.resultat", source = "resultatControle")
    @Mapping(target = "controleQualite.commentaire", source = "commentaireControle")
    EtapeProduction toEntity(EtapeProductionResponse etapeProductionResponse);
}