package com.example.textile.controller.web;

import com.example.textile.dto.request.ClientRequest;
import com.example.textile.dto.request.ProduitRequest;
import com.example.textile.entity.Client;
import com.example.textile.entity.Produit;
import com.example.textile.exception.ResourceNotFoundException;
import com.example.textile.repository.ClientRepository;
import com.example.textile.repository.CommandeRepository;
import com.example.textile.repository.LigneCommandeRepository;
import com.example.textile.repository.ProduitRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/configuration")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class ConfigurationCrudController {

    private final ProduitRepository produitRepository;
    private final ClientRepository clientRepository;
    private final LigneCommandeRepository ligneCommandeRepository;
    private final CommandeRepository commandeRepository;

    // ===== Produits =====

    @GetMapping("/produits/nouveau")
    public String nouveauProduit(Model model) {
        model.addAttribute("produitForm", new ProduitRequest());
        return "produit-form";
    }

    @PostMapping("/produits")
    public String creerProduit(@Valid @ModelAttribute("produitForm") ProduitRequest form,
                               BindingResult bindingResult,
                               Model model,
                               RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            return "produit-form";
        }
        if (produitRepository.existsByReference(form.getReference())) {
            model.addAttribute("errorMessage", "Une référence '" + form.getReference() + "' existe déjà.");
            return "produit-form";
        }
        produitRepository.save(Produit.builder()
                .nom(form.getNom())
                .reference(form.getReference())
                .description(form.getDescription())
                .prixBase(form.getPrixBase())
                .build());
        ra.addFlashAttribute("successMessage", "Produit '" + form.getReference() + "' créé avec succès.");
        return "redirect:/configuration";
    }

    @GetMapping("/produits/{id}/modifier")
    public String modifierProduit(@PathVariable Long id, Model model) {
        Produit produit = trouverProduit(id);
        model.addAttribute("produitForm", ProduitRequest.builder()
                .nom(produit.getNom())
                .reference(produit.getReference())
                .description(produit.getDescription())
                .prixBase(produit.getPrixBase())
                .build());
        model.addAttribute("produitId", id);
        return "produit-form";
    }

    @PostMapping("/produits/{id}")
    public String mettreAJourProduit(@PathVariable Long id,
                                     @Valid @ModelAttribute("produitForm") ProduitRequest form,
                                     BindingResult bindingResult,
                                     Model model,
                                     RedirectAttributes ra) {
        Produit produit = trouverProduit(id);
        if (bindingResult.hasErrors()) {
            model.addAttribute("produitId", id);
            return "produit-form";
        }
        produitRepository.findByReference(form.getReference())
                .filter(existant -> !existant.getId().equals(id))
                .ifPresent(existant -> model.addAttribute("errorMessage",
                        "Une référence '" + form.getReference() + "' existe déjà."));
        if (model.containsAttribute("errorMessage")) {
            model.addAttribute("produitId", id);
            return "produit-form";
        }
        produit.setNom(form.getNom());
        produit.setReference(form.getReference());
        produit.setDescription(form.getDescription());
        produit.setPrixBase(form.getPrixBase());
        produitRepository.save(produit);
        ra.addFlashAttribute("successMessage", "Produit '" + produit.getReference() + "' mis à jour.");
        return "redirect:/configuration";
    }

    @PostMapping("/produits/{id}/supprimer")
    public String supprimerProduit(@PathVariable Long id, RedirectAttributes ra) {
        Produit produit = trouverProduit(id);
        if (!ligneCommandeRepository.findByProduitId(id).isEmpty()) {
            ra.addFlashAttribute("errorMessage",
                    "Impossible de supprimer '" + produit.getReference() + "' : il est utilisé par une ou plusieurs commandes.");
            return "redirect:/configuration";
        }
        produitRepository.deleteById(id);
        ra.addFlashAttribute("successMessage", "Produit '" + produit.getReference() + "' supprimé.");
        return "redirect:/configuration";
    }

    // ===== Clients =====

    @GetMapping("/clients/nouveau")
    public String nouveauClient(Model model) {
        model.addAttribute("clientForm", new ClientRequest());
        return "client-form";
    }

    @PostMapping("/clients")
    public String creerClient(@Valid @ModelAttribute("clientForm") ClientRequest form,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            return "client-form";
        }
        if (form.getEmail() != null && !form.getEmail().isBlank()
                && clientRepository.existsByEmail(form.getEmail())) {
            model.addAttribute("errorMessage", "Un client avec l'email '" + form.getEmail() + "' existe déjà.");
            return "client-form";
        }
        clientRepository.save(Client.builder()
                .nom(form.getNom())
                .adresse(form.getAdresse())
                .telephone(form.getTelephone())
                .email(form.getEmail())
                .build());
        ra.addFlashAttribute("successMessage", "Client '" + form.getNom() + "' créé avec succès.");
        return "redirect:/configuration";
    }

    @GetMapping("/clients/{id}/modifier")
    public String modifierClient(@PathVariable Long id, Model model) {
        Client client = trouverClient(id);
        model.addAttribute("clientForm", ClientRequest.builder()
                .nom(client.getNom())
                .adresse(client.getAdresse())
                .telephone(client.getTelephone())
                .email(client.getEmail())
                .build());
        model.addAttribute("clientId", id);
        return "client-form";
    }

    @PostMapping("/clients/{id}")
    public String mettreAJourClient(@PathVariable Long id,
                                    @Valid @ModelAttribute("clientForm") ClientRequest form,
                                    BindingResult bindingResult,
                                    Model model,
                                    RedirectAttributes ra) {
        Client client = trouverClient(id);
        if (bindingResult.hasErrors()) {
            model.addAttribute("clientId", id);
            return "client-form";
        }
        if (form.getEmail() != null && !form.getEmail().isBlank()) {
            clientRepository.findByEmail(form.getEmail())
                    .filter(existant -> !existant.getId().equals(id))
                    .ifPresent(existant -> model.addAttribute("errorMessage",
                            "Un client avec l'email '" + form.getEmail() + "' existe déjà."));
        }
        if (model.containsAttribute("errorMessage")) {
            model.addAttribute("clientId", id);
            return "client-form";
        }
        client.setNom(form.getNom());
        client.setAdresse(form.getAdresse());
        client.setTelephone(form.getTelephone());
        client.setEmail(form.getEmail());
        clientRepository.save(client);
        ra.addFlashAttribute("successMessage", "Client '" + client.getNom() + "' mis à jour.");
        return "redirect:/configuration";
    }

    @PostMapping("/clients/{id}/supprimer")
    public String supprimerClient(@PathVariable Long id, RedirectAttributes ra) {
        Client client = trouverClient(id);
        if (commandeRepository.findByClientId(id, PageRequest.of(0, 1)).hasContent()) {
            ra.addFlashAttribute("errorMessage",
                    "Impossible de supprimer '" + client.getNom() + "' : il possède une ou plusieurs commandes.");
            return "redirect:/configuration";
        }
        clientRepository.deleteById(id);
        ra.addFlashAttribute("successMessage", "Client '" + client.getNom() + "' supprimé.");
        return "redirect:/configuration";
    }

    private Produit trouverProduit(Long id) {
        return produitRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Le produit " + id + " est introuvable"));
    }

    private Client trouverClient(Long id) {
        return clientRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Le client " + id + " est introuvable"));
    }
}