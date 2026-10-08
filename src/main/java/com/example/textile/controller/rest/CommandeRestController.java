package com.example.textile.controller.rest;

import com.example.textile.dto.request.CompleteEtapeRequest;
import com.example.textile.dto.request.ControleQualiteRequest;
import com.example.textile.dto.request.CreateCommandeRequest;
import com.example.textile.dto.response.CommandeResponse;
import com.example.textile.dto.response.EtapeProductionResponse;
import com.example.textile.entity.enums.PrioriteCommande;
import com.example.textile.entity.enums.StatutCommande;
import com.example.textile.entity.enums.TypeEtape;
import com.example.textile.exception.InvalidOperationException;
import com.example.textile.service.CommandeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Validated
public class CommandeRestController {

    private static final List<String> SORTABLE_PROPERTIES = List.of(
            "id", "numero", "dateCommande", "datePrevueLivraison", "priorite", "statut");

    private final CommandeService commandeService;

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public ResponseEntity<CommandeResponse> create(@Valid @RequestBody CreateCommandeRequest request) {
        CommandeResponse created = commandeService.create(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.getId())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    public Page<CommandeResponse> getAll(
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "Le numéro de page doit être positif ou nul") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "La taille de page doit être d'au moins 1") @Max(value = 100, message = "La taille de page ne doit pas dépasser 100") int size,
            @RequestParam(defaultValue = "id,asc") String sort,
            @RequestParam(required = false) StatutCommande status,
            @RequestParam(required = false) PrioriteCommande priority,
            @RequestParam(required = false) String search) {
        return commandeService.getAll(search, status, priority, PageRequest.of(page, size, parseSort(sort)));
    }

    @GetMapping("/{id}")
    public CommandeResponse getById(@PathVariable Long id) {
        return commandeService.getById(id);
    }

    @GetMapping("/delayed")
    public List<CommandeResponse> getDelayed() {
        return commandeService.getCommandesEnRetard();
    }

    @PostMapping("/{id}/production/start")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CommandeResponse startProduction(@PathVariable Long id) {
        return commandeService.startProduction(id);
    }

    @GetMapping("/{id}/production")
    public List<EtapeProductionResponse> getProduction(@PathVariable Long id) {
        return commandeService.getEtapes(id);
    }

    @PostMapping("/{id}/production/steps/{step}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER') or (hasRole('OPERATOR') and #step.toUpperCase() in {'COUPE','COUTURE','FINITION'})")
    public EtapeProductionResponse completeStep(@PathVariable Long id,
                                                @PathVariable String step,
                                                @Valid @RequestBody CompleteEtapeRequest request) {
        return commandeService.completeEtape(id, parseStep(step), request);
    }

    @PostMapping("/{id}/quality-control")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public EtapeProductionResponse qualityControl(@PathVariable Long id,
                                                  @Valid @RequestBody ControleQualiteRequest request) {
        return commandeService.enregistrerControleQualite(id, request);
    }

    @PostMapping("/{id}/delivery")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER')")
    public CommandeResponse deliver(@PathVariable Long id,
                                    @Valid @RequestBody CompleteEtapeRequest request) {
        commandeService.completeEtape(id, TypeEtape.LIVRAISON, request);
        return commandeService.getById(id);
    }

    private TypeEtape parseStep(String step) {
        try {
            return TypeEtape.valueOf(step.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidOperationException("Étape inconnue dans le workflow : " + step);
        }
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        if (!SORTABLE_PROPERTIES.contains(property)) {
            property = "id";
        }
        Sort.Direction direction = parts.length > 1 && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC
                : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}