package com.example.textile.controller.web;

import com.example.textile.dto.response.ClientResponse;
import com.example.textile.dto.response.ProduitResponse;
import com.example.textile.mapper.ClientMapper;
import com.example.textile.mapper.ProduitMapper;
import com.example.textile.repository.ClientRepository;
import com.example.textile.repository.ProduitRepository;
import com.example.textile.service.CommandeService;
import com.example.textile.service.DashboardService;
import com.example.textile.service.StatistiquesService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class WebController {

    private final CommandeService commandeService;
    private final DashboardService dashboardService;
    private final StatistiquesService statistiquesService;
    private final ClientRepository clientRepository;
    private final ProduitRepository produitRepository;
    private final ClientMapper clientMapper;
    private final ProduitMapper produitMapper;

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        model.addAttribute("dashboard", dashboardService.getDashboard());
        return "dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/access-denied")
    public String accessDenied() {
        return "access-denied";
    }

    @GetMapping("/statistiques")
    public String statistiques(Model model) {
        model.addAttribute("stats", statistiquesService.getStatistiquesProduction());
        model.addAttribute("commandesEnRetard", commandeService.getCommandesEnRetard());
        return "statistiques";
    }

    @GetMapping("/configuration")
    @PreAuthorize("hasRole('ADMIN')")
    public String configuration(Model model) {
        List<ProduitResponse> produits = produitRepository.findAll().stream()
                .map(produitMapper::toResponse)
                .toList();
        List<ClientResponse> clients = clientRepository.findAll().stream()
                .map(clientMapper::toResponse)
                .toList();
        model.addAttribute("produits", produits);
        model.addAttribute("clients", clients);
        return "configuration";
    }
}
