package com.example.textile.controller.web;

import com.example.textile.dto.request.CompleteEtapeRequest;
import com.example.textile.dto.request.ControleQualiteRequest;
import com.example.textile.dto.request.CreateCommandeRequest;
import com.example.textile.dto.request.CreateLigneCommandeRequest;
import com.example.textile.dto.response.CommandeResponse;
import com.example.textile.dto.response.EtapeProductionResponse;
import com.example.textile.entity.enums.PrioriteCommande;
import com.example.textile.entity.enums.ResultatControle;
import com.example.textile.entity.enums.StatutCommande;
import com.example.textile.entity.enums.StatutEtape;
import com.example.textile.entity.enums.TypeEtape;
import com.example.textile.exception.BusinessException;
import com.example.textile.exception.InvalidOperationException;
import com.example.textile.exception.ResourceNotFoundException;
import com.example.textile.repository.ClientRepository;
import com.example.textile.repository.ProduitRepository;
import com.example.textile.repository.UtilisateurRepository;
import com.example.textile.service.CommandeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Controller
@RequestMapping("/commandes")
@RequiredArgsConstructor
public class CommandeWebController {

    private final CommandeService commandeService;
    private final ClientRepository clientRepository;
    private final ProduitRepository produitRepository;
    private final UtilisateurRepository utilisateurRepository;

    @GetMapping
    public String list(@RequestParam(defaultValue = "0") int page,
                       @RequestParam(defaultValue = "15") int size,
                       @RequestParam(defaultValue = "id,asc") String sort,
                       @RequestParam(required = false) String status,
                       @RequestParam(required = false) String priority,
                       @RequestParam(required = false) String search,
                       Model model) {
        StatutCommande statusEnum = parseEnum(status, StatutCommande.class);
        PrioriteCommande priorityEnum = parseEnum(priority, PrioriteCommande.class);
        Sort parsedSort = parseSort(sort);
        var result = commandeService.getAll(search, statusEnum, priorityEnum,
                PageRequest.of(page, size, parsedSort));

        String[] sortParts = sort.split(",");
        String sortField = sortParts[0].trim();
        String sortDir = sortParts.length > 1 ? sortParts[1].trim().toUpperCase() : "ASC";

        model.addAttribute("commandes", result.getContent());
        model.addAttribute("currentPage", result.getNumber());
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("totalElements", result.getTotalElements());
        model.addAttribute("pageSize", size);
        model.addAttribute("sortField", sortField);
        model.addAttribute("sortDir", sortDir);
        model.addAttribute("currentStatus", statusEnum);
        model.addAttribute("currentPriority", priorityEnum);
        model.addAttribute("currentSearch", search);
        model.addAttribute("statuses", StatutCommande.values());
        model.addAttribute("priorities", PrioriteCommande.values());
        return "commandes";
    }

    @GetMapping("/retards")
    public String retards(Model model) {
        List<CommandeResponse> enRetard = commandeService.getCommandesEnRetard();
        model.addAttribute("commandesEnRetard", enRetard);
        return "retards";
    }

    @GetMapping("/nouvelle")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public String nouvelle(Model model) {
        CreateCommandeRequest form = CreateCommandeRequest.builder()
                .dateCommande(LocalDate.now())
                .priorite(PrioriteCommande.NORMALE)
                .lignes(new ArrayList<>(List.of(new CreateLigneCommandeRequest())))
                .build();
        preparerFormulaire(model, form);
        return "commande-form";
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public String creer(@Valid @ModelAttribute("commandeForm") CreateCommandeRequest form,
                        BindingResult bindingResult,
                        Model model,
                        RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            preparerFormulaire(model, form);
            return "commande-form";
        }
        CommandeResponse commande = commandeService.create(form);
        ra.addFlashAttribute("successMessage", "Commande " + commande.getNumero() + " créée avec succès.");
        return "redirect:/commandes/" + commande.getId();
    }

    private void preparerFormulaire(Model model, CreateCommandeRequest form) {
        model.addAttribute("commandeForm", form);
        model.addAttribute("clients", clientRepository.findAll());
        model.addAttribute("produits", produitRepository.findAll());
        model.addAttribute("priorities", PrioriteCommande.values());
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, Model model) {
        CommandeResponse commande = commandeService.getById(id);
        List<EtapeProductionResponse> etapes = commandeService.getEtapes(id);

        EtapeProductionResponse etapeCourante = premiereEtapeAFinir(etapes);
        EtapeProductionResponse etapeControle = etapeDeType(etapes, TypeEtape.CONTROLE_QUALITE);
        boolean livraisonBloquee = etapeControle != null
                && etapeControle.getResultatControle() == ResultatControle.NON_CONFORME;
        EtapeProductionResponse etapeActionnable = (etapeCourante != null
                && etapeCourante.getType() == TypeEtape.LIVRAISON && livraisonBloquee)
                ? null : etapeCourante;

        model.addAttribute("commande", commande);
        model.addAttribute("etapes", etapes);
        model.addAttribute("retardEnJours", commandeService.getRetardEnJours(id));
        model.addAttribute("dureePrevue", commandeService.getDureePrevue(id));
        model.addAttribute("dureeReelle", commandeService.getDureeReelle(id));
        model.addAttribute("etapeCourante", etapeCourante);
        model.addAttribute("etapeActionnable", etapeActionnable);
        model.addAttribute("etapeControle", etapeControle);
        model.addAttribute("livraisonBloquee", livraisonBloquee);
        model.addAttribute("aujourdHui", LocalDate.now());
        model.addAttribute("utilisateurs", utilisateurRepository.findAll());
        model.addAttribute("quantiteCommandee", quantiteCommandee(commande));
        return "commande-detail";
    }

    @PostMapping("/{id}/etapes/{type}")
    @PreAuthorize("hasAnyRole('OPERATOR','ADMIN')")
    public String validerEtape(@PathVariable Long id,
                               @PathVariable String type,
                               @RequestParam(required = false) String resultat,
                               @RequestParam(required = false) String action,
                               @RequestParam(required = false) String responsable,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateDebut,
                               @RequestParam(required = false)
                               @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFinReelle,
                               @RequestParam(required = false) Integer quantiteProduite,
                               @RequestParam(required = false) String commentaire,
                               RedirectAttributes ra) {
        TypeEtape typeEtape;
        try {
            typeEtape = TypeEtape.valueOf(type.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            ra.addFlashAttribute("errorMessage", "Etape inconnue dans le workflow : " + type);
            return "redirect:/commandes/" + id;
        }

        try {
            if (typeEtape == TypeEtape.CONTROLE_QUALITE) {
                ResultatControle resultatControle = parseEnum(resultat, ResultatControle.class);
                if (resultatControle == null) {
                    ra.addFlashAttribute("errorMessage",
                            "Le resultat du controle qualite est obligatoire : choisissez CONFORME ou NON_CONFORME.");
                    return "redirect:/commandes/" + id;
                }
                commandeService.enregistrerControleQualite(id, ControleQualiteRequest.builder()
                        .resultat(resultatControle)
                        .commentaire(commentaire)
                        .build());
                if ("enregistrer".equalsIgnoreCase(action)) {
                    ra.addFlashAttribute("successMessage",
                            "Controle qualite enregistre : " + resultatControle + ".");
                    return "redirect:/commandes/" + id;
                }
            }

            commandeService.completeEtape(id, typeEtape, CompleteEtapeRequest.builder()
                    .responsable(responsable)
                    .dateDebut(dateDebut)
                    .dateFinReelle(dateFinReelle)
                    .quantiteProduite(quantiteProduite)
                    .commentaire(commentaire)
                    .build());
            ra.addFlashAttribute("successMessage", "Etape " + typeEtape + " validee avec succes.");
        } catch (InvalidOperationException | BusinessException | ResourceNotFoundException ex) {
            ra.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/commandes/" + id;
    }

    // Préparation du modèle (aucune règle métier) : étape courante = première étape non
    // terminée dont l'étape précédente est terminée ; l'étape actionnable tient compte
    // de l'état du contrôle qualité (livraison bloquée si NON_CONFORME).
    private EtapeProductionResponse premiereEtapeAFinir(List<EtapeProductionResponse> etapes) {
        for (int i = 0; i < etapes.size(); i++) {
            EtapeProductionResponse etape = etapes.get(i);
            boolean nonTerminee = etape.getStatut() != StatutEtape.TERMINEE
                    && etape.getStatut() != StatutEtape.ANNULEE;
            boolean precedenteTerminee = i == 0
                    || etapes.get(i - 1).getStatut() == StatutEtape.TERMINEE;
            if (nonTerminee && precedenteTerminee) {
                return etape;
            }
        }
        return null;
    }

    private EtapeProductionResponse etapeDeType(List<EtapeProductionResponse> etapes, TypeEtape type) {
        return etapes.stream()
                .filter(etape -> etape.getType() == type)
                .findFirst()
                .orElse(null);
    }

    private int quantiteCommandee(CommandeResponse commande) {
        if (commande.getLignes() == null) {
            return 0;
        }
        return commande.getLignes().stream()
                .mapToInt(ligne -> ligne.getQuantite() != null ? ligne.getQuantite() : 0)
                .sum();
    }

    @GetMapping("/{id}/production")
    @PreAuthorize("hasAnyRole('OPERATOR','MANAGER','ADMIN')")
    public String production(@PathVariable Long id, Model model) {
        CommandeResponse commande = commandeService.getById(id);
        List<EtapeProductionResponse> etapes = commandeService.getEtapes(id);
        int totalQuantite = commande.getLignes().stream()
                .mapToInt(ligne -> ligne.getQuantite() != null ? ligne.getQuantite() : 0)
                .sum();

        model.addAttribute("commande", commande);
        model.addAttribute("etapes", etapes);
        model.addAttribute("totalQuantite", totalQuantite);
        model.addAttribute("typesModifiables",
                List.of(TypeEtape.COUPE, TypeEtape.COUTURE, TypeEtape.FINITION));
        return "production";
    }

    @PostMapping("/{id}/production/start")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public String demarrerProduction(@PathVariable Long id, RedirectAttributes ra) {
        commandeService.startProduction(id);
        ra.addFlashAttribute("successMessage", "Production demarree pour la commande.");
        return "redirect:/commandes/" + id + "/production";
    }

    @PostMapping("/{id}/production/steps/{step}/complete")
    @PreAuthorize("hasAnyRole('ADMIN','MANAGER') or (hasRole('OPERATOR') and #step.toUpperCase() in {'COUPE','COUTURE','FINITION'})")
    public String completerEtape(@PathVariable Long id,
                                 @PathVariable String step,
                                 @RequestParam Integer quantiteProduite,
                                 @RequestParam(required = false) String commentaire,
                                 RedirectAttributes ra) {
        TypeEtape type = TypeEtape.valueOf(step.toUpperCase());
        commandeService.completeEtape(id, type, CompleteEtapeRequest.builder()
                .dateFinReelle(LocalDate.now())
                .quantiteProduite(quantiteProduite)
                .commentaire(commentaire)
                .build());
        ra.addFlashAttribute("successMessage", "Etape " + type + " validee avec succes.");
        return "redirect:/commandes/" + id;
    }

    @GetMapping("/{id}/qualite")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public String qualite(@PathVariable Long id, Model model) {
        CommandeResponse commande = commandeService.getById(id);
        model.addAttribute("commande", commande);
        model.addAttribute("controle", commandeService.getEtapes(id).stream()
                .filter(etape -> etape.getType() == TypeEtape.CONTROLE_QUALITE)
                .findFirst()
                .orElse(null));
        model.addAttribute("resultats", ResultatControle.values());
        return "qualite";
    }

    @PostMapping("/{id}/qualite")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public String enregistrerControle(@PathVariable Long id,
                                      @RequestParam ResultatControle resultat,
                                      @RequestParam(required = false) String commentaire,
                                      RedirectAttributes ra) {
        commandeService.enregistrerControleQualite(id, ControleQualiteRequest.builder()
                .resultat(resultat)
                .commentaire(commentaire)
                .build());
        ra.addFlashAttribute("successMessage",
                "Controle qualite enregistre : " + resultat + ".");
        return "redirect:/commandes/" + id + "/qualite";
    }

    @GetMapping("/{id}/livraisons")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public String livraisons(@PathVariable Long id, Model model) {
        CommandeResponse commande = commandeService.getById(id);
        List<EtapeProductionResponse> etapes = commandeService.getEtapes(id);

        EtapeProductionResponse livraison = etapes.stream()
                .filter(e -> e.getType() == TypeEtape.LIVRAISON)
                .findFirst()
                .orElse(null);
        EtapeProductionResponse controle = etapes.stream()
                .filter(e -> e.getType() == TypeEtape.CONTROLE_QUALITE)
                .findFirst()
                .orElse(null);

        model.addAttribute("commande", commande);
        model.addAttribute("etapes", etapes);
        model.addAttribute("etapeLivraison", livraison);
        model.addAttribute("controleQualite", controle);
        return "livraisons";
    }

    @PostMapping("/{id}/livraisons")
    @PreAuthorize("hasAnyRole('MANAGER','ADMIN')")
    public String effectuerLivraison(@PathVariable Long id,
                                     @RequestParam(required = false) String commentaire,
                                     RedirectAttributes ra) {
        commandeService.completeEtape(id, TypeEtape.LIVRAISON, CompleteEtapeRequest.builder()
                .dateFinReelle(LocalDate.now())
                .commentaire(commentaire)
                .build());
        ra.addFlashAttribute("successMessage", "Commande livree avec succes.");
        return "redirect:/commandes/" + id + "/livraisons";
    }

    private <E extends Enum<E>> E parseEnum(String value, Class<E> enumClass) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(enumClass, value.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Sort parseSort(String sort) {
        String[] parts = sort.split(",");
        String property = parts[0].trim();
        List<String> allowed = List.of("id", "numero", "dateCommande",
                "datePrevueLivraison", "priorite", "statut");
        if (!allowed.contains(property)) {
            property = "id";
        }
        Sort.Direction direction = parts.length > 1
                && "desc".equalsIgnoreCase(parts[1].trim())
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        return Sort.by(direction, property);
    }
}
