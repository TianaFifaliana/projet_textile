package com.example.textile.service.impl;

import com.example.textile.dto.request.CompleteEtapeRequest;
import com.example.textile.dto.request.ControleQualiteRequest;
import com.example.textile.dto.request.CreateCommandeRequest;
import com.example.textile.dto.request.CreateLigneCommandeRequest;
import com.example.textile.dto.request.UpdateCommandeRequest;
import com.example.textile.dto.response.CommandeResponse;
import com.example.textile.dto.response.EtapeProductionResponse;
import com.example.textile.entity.Client;
import com.example.textile.entity.Commande;
import com.example.textile.entity.ControleQualite;
import com.example.textile.entity.EtapeProduction;
import com.example.textile.entity.LigneCommande;
import com.example.textile.entity.Produit;
import com.example.textile.entity.enums.PrioriteCommande;
import com.example.textile.entity.enums.ResultatControle;
import com.example.textile.entity.enums.StatutCommande;
import com.example.textile.entity.enums.StatutEtape;
import com.example.textile.entity.enums.TypeEtape;
import com.example.textile.exception.BusinessException;
import com.example.textile.exception.ConflictException;
import com.example.textile.exception.InvalidOperationException;
import com.example.textile.exception.ResourceNotFoundException;
import com.example.textile.mapper.CommandeMapper;
import com.example.textile.mapper.EtapeProductionMapper;
import com.example.textile.repository.ClientRepository;
import com.example.textile.repository.CommandeRepository;
import com.example.textile.repository.EtapeProductionRepository;
import com.example.textile.repository.ProduitRepository;
import com.example.textile.service.CommandeService;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CommandeServiceImpl implements CommandeService {

    private static final List<TypeEtape> WORKFLOW = List.of(
            TypeEtape.COUPE,
            TypeEtape.COUTURE,
            TypeEtape.FINITION,
            TypeEtape.CONTROLE_QUALITE,
            TypeEtape.LIVRAISON
    );

    private final CommandeRepository commandeRepository;
    private final ClientRepository clientRepository;
    private final ProduitRepository produitRepository;
    private final EtapeProductionRepository etapeProductionRepository;
    private final CommandeMapper commandeMapper;
    private final EtapeProductionMapper etapeProductionMapper;

    // Transaction requise : la vérification de disponibilité (numéro unique, client, produits)
    // et la persistance de la commande avec ses lignes doivent être atomiques :
    // si une validation échoue à mi-chemin, aucune donnée ne doit rester en base.
    @Override
    @Transactional
    public CommandeResponse create(CreateCommandeRequest request) {
        verifierNumeroDisponible(request.getNumero());

        Client client = getClientOuErreur(request.getClientId());

        Commande commande = commandeMapper.toEntity(request);
        commande.setClient(client);
        commande.setStatut(StatutCommande.EN_ATTENTE);

        commande.getLignes().forEach(ligne -> {
            ligne.setProduit(getProduitOuErreur(ligne.getProduit().getId()));
            ligne.setCommande(commande);
        });

        return commandeMapper.toResponse(commandeRepository.save(commande));
    }

    // Transaction requise : le remplacement des lignes (orphanRemoval) et les vérifications
    // de cohérence (statut, numéro) doivent être commutés ensemble ; une exception en cours
    // de route doit annuler la modification partielle de la commande.
    @Override
    @Transactional
    public CommandeResponse update(Long commandeId, UpdateCommandeRequest request) {
        Commande commande = getCommande(commandeId);

        verifierCommandeModifiable(commande);
        if (!commande.getNumero().equals(request.getNumero())) {
            verifierNumeroDisponible(request.getNumero());
        }

        Client client = getClientOuErreur(request.getClientId());

        commande.setNumero(request.getNumero());
        commande.setClient(client);
        commande.setDateCommande(request.getDateCommande());
        commande.setDatePrevueLivraison(request.getDatePrevueLivraison());
        commande.setPriorite(request.getPriorite());
        commande.setStatut(request.getStatut());

        List<LigneCommande> nouvellesLignes = new ArrayList<>();
        for (CreateLigneCommandeRequest ligneRequest : request.getLignes()) {
            LigneCommande ligne = commandeMapper.toLigne(ligneRequest);
            ligne.setProduit(getProduitOuErreur(ligneRequest.getProduitId()));
            ligne.setCommande(commande);
            nouvellesLignes.add(ligne);
        }
        commande.getLignes().clear();
        commande.getLignes().addAll(nouvellesLignes);

        return commandeMapper.toResponse(commandeRepository.save(commande));
    }

    @Override
    @Transactional(readOnly = true)
    public CommandeResponse getById(Long commandeId) {
        return commandeMapper.toResponse(getCommande(commandeId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CommandeResponse> getAll(String search, StatutCommande statut, PrioriteCommande priorite, Pageable pageable) {
        Specification<Commande> spec = Specification.where(null);

        if (search != null && !search.isBlank()) {
            String pattern = "%" + search.trim().toLowerCase() + "%";
            spec = spec.and((root, query, builder) -> {
                Join<Commande, Client> clientJoin = root.join("client", JoinType.LEFT);
                return builder.or(
                        builder.like(builder.lower(root.get("numero")), pattern),
                        builder.like(builder.lower(clientJoin.get("nom")), pattern));
            });
        }
        if (statut != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("statut"), statut));
        }
        if (priorite != null) {
            spec = spec.and((root, query, builder) -> builder.equal(root.get("priorite"), priorite));
        }

        return commandeRepository.findAll(spec, pageable).map(commandeMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CommandeResponse> getCommandesEnRetard() {
        return commandeRepository.findByDatePrevueLivraisonBeforeAndStatutNot(LocalDate.now(), StatutCommande.LIVREE)
                .stream()
                .map(commandeMapper::toResponse)
                .toList();
    }

    // Transaction requise : la création des 5 étapes du workflow et la bascule du statut
    // EN_ATTENTE -> EN_PRODUCTION doivent être atomiques : si la génération des étapes
    // échoue, la commande ne doit pas rester à moitié initialisée.
    @Override
    @Transactional
    public CommandeResponse startProduction(Long commandeId) {
        Commande commande = getCommande(commandeId);

        if (commande.getStatut() == StatutCommande.LIVREE || commande.getStatut() == StatutCommande.ANNULEE) {
            throw new InvalidOperationException("Impossible de démarrer la production d'une commande " + commande.getStatut());
        }
        if (!commande.getEtapesProduction().isEmpty()) {
            throw new InvalidOperationException("La production de la commande " + commande.getNumero() + " a déjà été démarrée");
        }

        LocalDate today = LocalDate.now();
        LocalDate livraisonPrevue = commande.getDatePrevueLivraison() != null
                ? commande.getDatePrevueLivraison()
                : today.plusDays(WORKFLOW.size());
        long totalJours = Math.max(0, ChronoUnit.DAYS.between(today, livraisonPrevue));
        String responsable = utilisateurCourant();

        List<EtapeProduction> etapes = new ArrayList<>();
        for (int i = 0; i < WORKFLOW.size(); i++) {
            TypeEtape type = WORKFLOW.get(i);
            EtapeProduction etape = EtapeProduction.builder()
                    .commande(commande)
                    .type(type)
                    .statut(i == 0 ? StatutEtape.EN_COURS : StatutEtape.EN_ATTENTE)
                    .dateDebut(i == 0 ? today : null)
                    .dateFinPrevue(today.plusDays((totalJours * (i + 1)) / WORKFLOW.size()))
                    .responsable(i == 0 ? responsable : null)
                    .build();
            etapes.add(etape);
        }

        commande.getEtapesProduction().clear();
        commande.getEtapesProduction().addAll(etapes);
        commande.setStatut(StatutCommande.EN_PRODUCTION);

        return commandeMapper.toResponse(commandeRepository.save(commande));
    }

    @Override
    @Transactional(readOnly = true)
    public List<EtapeProductionResponse> getEtapes(Long commandeId) {
        getCommande(commandeId);
        return etapeProductionRepository.findByCommandeIdOrderedByWorkflow(commandeId).stream()
                .map(etapeProductionMapper::toResponse)
                .toList();
    }

    // Transaction requise : cette méthode couvre la validation d'une étape ET la livraison
    // (type LIVRAISON). Les contrôles de cohérence (étape précédente, quantités, contrôle
    // qualité) puis les mises à jour de l'étape, de l'étape suivante et du statut de la
    // commande (-> LIVREE) doivent être visibles comme un seul bloc atomique : un contrôle
    // qui échoue ne doit laisser aucune étape partiellement avancée.
    @Override
    @Transactional
    public EtapeProductionResponse completeEtape(Long commandeId, TypeEtape type, CompleteEtapeRequest request) {
        Commande commande = getCommande(commandeId);
        List<EtapeProduction> etapes = etapeProductionRepository.findByCommandeIdOrderedByWorkflow(commandeId);

        if (etapes.isEmpty()) {
            throw new InvalidOperationException("La production n'a pas encore été démarrée pour la commande " + commande.getNumero());
        }

        int index = WORKFLOW.indexOf(type);
        if (index < 0) {
            throw new ResourceNotFoundException("L'étape " + type + " n'existe pas dans le workflow");
        }

        EtapeProduction etape = getEtape(etapes, type, commande.getNumero());

        if (index > 0) {
            EtapeProduction precedente = getEtape(etapes, WORKFLOW.get(index - 1), commande.getNumero());
            if (precedente.getStatut() != StatutEtape.TERMINEE) {
                throw new InvalidOperationException("Impossible de valider l'étape " + type
                        + " : l'étape précédente " + precedente.getType() + " n'est pas terminée");
            }
        }

        if (etape.getStatut() != StatutEtape.EN_COURS) {
            throw new InvalidOperationException("Seule l'étape en cours peut être validée (l'étape " + type + " est " + etape.getStatut() + ")");
        }

        if (type == TypeEtape.CONTROLE_QUALITE && (etape.getControleQualite() == null
                || etape.getControleQualite().getResultat() == null)) {
            throw new InvalidOperationException("Le résultat du contrôle qualité doit être enregistré avant de clôturer l'étape " + type);
        }

        verifierQuantites(commande, etapes, index, etape, request.getQuantiteProduite());

        if (type == TypeEtape.LIVRAISON) {
            verifierLivraisonAutorisee(commande, etapes);
        }

        if (request.getDateDebut() != null && request.getDateFinReelle() != null
                && request.getDateDebut().isAfter(request.getDateFinReelle())) {
            throw new InvalidOperationException(
                    "La date de début de l'étape " + type + " ne peut pas être postérieure à sa date de fin réelle");
        }

        String responsableSaisi = request.getResponsable() != null && !request.getResponsable().isBlank()
                ? request.getResponsable().trim()
                : null;
        String responsable = responsableSaisi != null ? responsableSaisi : utilisateurCourant();
        if (responsableSaisi != null) {
            etape.setResponsable(responsableSaisi);
        } else if (etape.getResponsable() == null && responsable != null) {
            etape.setResponsable(responsable);
        }
        if (request.getDateDebut() != null) {
            etape.setDateDebut(request.getDateDebut());
        }
        etape.setStatut(StatutEtape.TERMINEE);
        etape.setDateFinReelle(request.getDateFinReelle());
        etape.setCommentaire(request.getCommentaire());
        etape.setQuantiteProduite(request.getQuantiteProduite());

        if (index + 1 < WORKFLOW.size()) {
            EtapeProduction suivante = getEtape(etapes, WORKFLOW.get(index + 1), commande.getNumero());
            suivante.setStatut(StatutEtape.EN_COURS);
            suivante.setDateDebut(request.getDateFinReelle());
            String responsableSuivant = utilisateurCourant();
            if (suivante.getResponsable() == null && responsableSuivant != null) {
                suivante.setResponsable(responsableSuivant);
            }
        }

        if (type == TypeEtape.LIVRAISON) {
            commande.setStatut(StatutCommande.LIVREE);
        }

        return etapeProductionMapper.toResponse(etapeProductionRepository.save(etape));
    }

    // Transaction requise : l'enregistrement du résultat du contrôle qualité crée (ou met à
    // jour) le contrôle et l'attache à l'étape. La vérification de l'état de la commande et
    // la persistance du résultat doivent être atomiques pour garantir que le contrôle final
    // retenu soit cohérent (un seul enregistrement par étape).
    @Override
    @Transactional
    public EtapeProductionResponse enregistrerControleQualite(Long commandeId, ControleQualiteRequest request) {
        Commande commande = getCommande(commandeId);

        if (commande.getStatut() == StatutCommande.LIVREE || commande.getStatut() == StatutCommande.ANNULEE) {
            throw new InvalidOperationException("Impossible d'enregistrer un contrôle qualité pour une commande " + commande.getStatut());
        }

        EtapeProduction controle = etapeProductionRepository.findByCommandeIdOrderedByWorkflow(commandeId).stream()
                .filter(etape -> etape.getType() == TypeEtape.CONTROLE_QUALITE)
                .findFirst()
                .orElseThrow(() -> new InvalidOperationException(
                        "La production n'a pas encore été démarrée pour la commande " + commande.getNumero()));

        ControleQualite controleQualite = controle.getControleQualite();
        if (controleQualite == null) {
            controleQualite = new ControleQualite();
            controleQualite.setEtape(controle);
            controle.setControleQualite(controleQualite);
        }
        controleQualite.setResultat(request.getResultat());
        controleQualite.setCommentaire(request.getCommentaire());

        return etapeProductionMapper.toResponse(etapeProductionRepository.save(controle));
    }

    @Override
    @Transactional(readOnly = true)
    public long getDureePrevue(Long commandeId) {
        Commande commande = getCommande(commandeId);
        if (commande.getDateCommande() == null || commande.getDatePrevueLivraison() == null) {
            return 0;
        }
        return ChronoUnit.DAYS.between(commande.getDateCommande(), commande.getDatePrevueLivraison());
    }

    @Override
    @Transactional(readOnly = true)
    public long getDureeReelle(Long commandeId) {
        Commande commande = getCommande(commandeId);
        if (commande.getDateCommande() == null) {
            return 0;
        }
        LocalDate fin = dateLivraisonReelle(commandeId);
        return ChronoUnit.DAYS.between(commande.getDateCommande(), fin != null ? fin : LocalDate.now());
    }

    @Override
    @Transactional(readOnly = true)
    public long getRetardEnJours(Long commandeId) {
        Commande commande = getCommande(commandeId);
        LocalDate prevue = commande.getDatePrevueLivraison();
        if (prevue == null) {
            return 0;
        }
        if (commande.getStatut() == StatutCommande.LIVREE) {
            LocalDate reelle = dateLivraisonReelle(commandeId);
            return ChronoUnit.DAYS.between(prevue, reelle != null ? reelle : prevue);
        }
        return Math.max(0, ChronoUnit.DAYS.between(prevue, LocalDate.now()));
    }

    private LocalDate dateLivraisonReelle(Long commandeId) {
        return etapeProductionRepository.findByCommandeIdOrderedByWorkflow(commandeId).stream()
                .filter(etape -> etape.getType() == TypeEtape.LIVRAISON)
                .map(EtapeProduction::getDateFinReelle)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    private void verifierQuantites(Commande commande, List<EtapeProduction> etapes, int index,
                                   EtapeProduction etape, Integer quantiteDeclaree) {
        if (quantiteDeclaree == null) {
            return;
        }

        int quantiteCommandee = commande.getLignes().stream()
                .mapToInt(ligne -> ligne.getQuantite() != null ? ligne.getQuantite() : 0)
                .sum();

        if (quantiteDeclaree > quantiteCommandee) {
            throw new BusinessException("La quantité déclarée (" + quantiteDeclaree
                    + ") dépasse la quantité commandée (" + quantiteCommandee + ") pour la commande " + commande.getNumero());
        }

        if (index > 0) {
            EtapeProduction precedente = getEtape(etapes, WORKFLOW.get(index - 1), commande.getNumero());
            Integer quantitePrecedente = precedente.getQuantiteProduite();
            if (quantitePrecedente != null && quantiteDeclaree > quantitePrecedente) {
                throw new BusinessException("La quantité déclarée (" + quantiteDeclaree
                        + ") ne peut pas dépasser la quantité produite à l'étape précédente (" + quantitePrecedente + ")");
            }
        }
    }

    private void verifierLivraisonAutorisee(Commande commande, List<EtapeProduction> etapes) {
        for (int i = 0; i < WORKFLOW.size() - 1; i++) {
            EtapeProduction etape = getEtape(etapes, WORKFLOW.get(i), commande.getNumero());
            if (etape.getStatut() != StatutEtape.TERMINEE) {
                throw new InvalidOperationException("Impossible de livrer une commande non terminée : l'étape " + etape.getType()
                        + " n'est pas terminée");
            }
        }

        EtapeProduction controle = getEtape(etapes, TypeEtape.CONTROLE_QUALITE, commande.getNumero());
        if (controle.getControleQualite() == null || controle.getControleQualite().getResultat() == null) {
            throw new InvalidOperationException("Le contrôle qualité est obligatoire avant de livrer la commande " + commande.getNumero());
        }
        if (controle.getControleQualite().getResultat() == ResultatControle.NON_CONFORME) {
            throw new InvalidOperationException("Impossible de livrer la commande " + commande.getNumero()
                    + " : le contrôle qualité est NON_CONFORME");
        }
    }

    private EtapeProduction getEtape(List<EtapeProduction> etapes, TypeEtape type, String numeroCommande) {
        return etapes.stream()
                .filter(etape -> etape.getType() == type)
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Aucune étape de type " + type + " pour la commande " + numeroCommande));
    }

    private void verifierCommandeModifiable(Commande commande) {
        if (commande.getStatut() == StatutCommande.LIVREE) {
            throw new InvalidOperationException("Impossible de modifier une commande déjà livrée (" + commande.getNumero() + ")");
        }
        if (commande.getStatut() == StatutCommande.ANNULEE) {
            throw new InvalidOperationException("Impossible de modifier une commande annulée (" + commande.getNumero() + ")");
        }
    }

    private void verifierNumeroDisponible(String numero) {
        if (commandeRepository.existsByNumero(numero)) {
            throw new ConflictException("Le numéro de commande '" + numero + "' est déjà utilisé");
        }
    }

    private Client getClientOuErreur(Long clientId) {
        return clientRepository.findById(clientId)
                .orElseThrow(() -> new ResourceNotFoundException("Client introuvable avec l'identifiant " + clientId));
    }

    private Produit getProduitOuErreur(Long produitId) {
        return produitRepository.findById(produitId)
                .orElseThrow(() -> new ResourceNotFoundException("Produit introuvable avec l'identifiant " + produitId));
    }

    private Commande getCommande(Long commandeId) {
        return commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande introuvable avec l'identifiant " + commandeId));
    }

    // Utilisateur authentifié courant (null en dehors d'une requête sécurisée, ex. tests).
    private String utilisateurCourant() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        return authentication.getName();
    }
}