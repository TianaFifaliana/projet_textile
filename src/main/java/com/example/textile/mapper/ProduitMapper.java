package com.example.textile.mapper;

import com.example.textile.dto.response.ProduitResponse;
import com.example.textile.entity.Produit;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ProduitMapper {

    ProduitResponse toResponse(Produit produit);

    Produit toEntity(ProduitResponse produitResponse);
}