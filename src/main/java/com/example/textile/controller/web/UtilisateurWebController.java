package com.example.textile.controller.web;

import com.example.textile.dto.request.UtilisateurRequest;
import com.example.textile.dto.response.UtilisateurResponse;
import com.example.textile.entity.enums.RoleUtilisateur;
import com.example.textile.exception.ConflictException;
import com.example.textile.exception.InvalidOperationException;
import com.example.textile.service.UtilisateurService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

import java.security.Principal;

@Controller
@RequestMapping("/configuration/utilisateurs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class UtilisateurWebController {

    private final UtilisateurService utilisateurService;

    @GetMapping
    public String liste(Model model) {
        model.addAttribute("utilisateurs", utilisateurService.findAll());
        return "utilisateurs";
    }

    @GetMapping("/nouveau")
    public String nouveau(Model model) {
        model.addAttribute("utilisateurForm", new UtilisateurRequest());
        model.addAttribute("roles", RoleUtilisateur.values());
        return "utilisateur-form";
    }

    @PostMapping
    public String creer(@Valid @ModelAttribute("utilisateurForm") UtilisateurRequest form,
                        BindingResult bindingResult,
                        Model model,
                        RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("roles", RoleUtilisateur.values());
            return "utilisateur-form";
        }
        try {
            UtilisateurResponse cree = utilisateurService.create(form);
            ra.addFlashAttribute("successMessage", "Utilisateur '" + cree.getUsername() + "' créé avec succès.");
        } catch (ConflictException | InvalidOperationException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/configuration/utilisateurs";
    }

    @GetMapping("/{id}/modifier")
    public String modifier(@PathVariable Long id, Model model) {
        UtilisateurResponse utilisateur = utilisateurService.getById(id);
        model.addAttribute("utilisateurForm", UtilisateurRequest.builder()
                .username(utilisateur.getUsername())
                .prenom(utilisateur.getPrenom())
                .nom(utilisateur.getNom())
                .role(utilisateur.getRole())
                .actif(utilisateur.isActif())
                .build());
        model.addAttribute("utilisateurId", id);
        model.addAttribute("roles", RoleUtilisateur.values());
        return "utilisateur-form";
    }

    @PostMapping("/{id}")
    public String mettreAJour(@PathVariable Long id,
                              @Valid @ModelAttribute("utilisateurForm") UtilisateurRequest form,
                              BindingResult bindingResult,
                              Model model,
                              RedirectAttributes ra) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("utilisateurId", id);
            model.addAttribute("roles", RoleUtilisateur.values());
            return "utilisateur-form";
        }
        try {
            UtilisateurResponse maj = utilisateurService.update(id, form);
            ra.addFlashAttribute("successMessage", "Utilisateur '" + maj.getUsername() + "' mis à jour.");
        } catch (ConflictException | InvalidOperationException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
            return "redirect:/configuration/utilisateurs/" + id + "/modifier";
        }
        return "redirect:/configuration/utilisateurs";
    }

    @PostMapping("/{id}/desactiver")
    public String desactiver(@PathVariable Long id, RedirectAttributes ra, Principal principal) {
        UtilisateurResponse utilisateur = utilisateurService.getById(id);
        if (principal != null && utilisateur.getUsername().equals(principal.getName())) {
            ra.addFlashAttribute("errorMessage", "Vous ne pouvez pas désactiver votre propre compte.");
            return "redirect:/configuration/utilisateurs";
        }
        try {
            utilisateurService.setActif(id, false);
            ra.addFlashAttribute("successMessage", "Compte '" + utilisateur.getUsername() + "' désactivé.");
        } catch (InvalidOperationException e) {
            ra.addFlashAttribute("errorMessage", e.getMessage());
        }
        return "redirect:/configuration/utilisateurs";
    }

    @PostMapping("/{id}/activer")
    public String activer(@PathVariable Long id, RedirectAttributes ra) {
        UtilisateurResponse utilisateur = utilisateurService.getById(id);
        utilisateurService.setActif(id, true);
        ra.addFlashAttribute("successMessage", "Compte '" + utilisateur.getUsername() + "' activé.");
        return "redirect:/configuration/utilisateurs";
    }
}