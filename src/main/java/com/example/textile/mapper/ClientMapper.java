package com.example.textile.mapper;

import com.example.textile.dto.response.ClientResponse;
import com.example.textile.entity.Client;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface ClientMapper {

    ClientResponse toResponse(Client client);

    Client toEntity(ClientResponse clientResponse);
}