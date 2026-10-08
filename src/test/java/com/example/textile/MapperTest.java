package com.example.textile;

import com.example.textile.dto.request.CreateCommandeRequest;
import com.example.textile.dto.request.CreateLigneCommandeRequest;
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
import com.example.textile.mapper.CommandeMapper;
import com.example.textile.mapper.EtapeProductionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class MapperTest {

    @Autowired
    private CommandeMapper commandeMapper;

    @Autowired
    private EtapeProductionMapper etapeProductionMapper;

    @Test
    void commandeToResponseMapsListsAndNestedObjects() {
        Client client = Client.builder()
                .id(1L)
                .nom("Client Alpha")
                .build();

        Produit produit = Produit.builder()
                .id(10L)
                .nom("T-shirt")
                .reference("TS-001")
                .prixBase(new BigDecimal("15.00"))
                .build();

        LigneCommande ligne1 = LigneCommande.builder()
                .id(100L)
                .produit(produit)
                .quantite(5)
                .taille("M")
                .couleur("Rouge")
                .prixUnitaire(new BigDecimal("14.50"))
                .quantiteProduite(3)
                .build();
        LigneCommande ligne2 = LigneCommande.builder()
                .id(101L)
                .produit(produit)
                .quantite(2)
                .taille("L")
                .couleur("Noir")
                .prixUnitaire(new BigDecimal("15.00"))
                .build();

        ControleQualite controle = ControleQualite.builder()
                .id(300L)
                .resultat(ResultatControle.CONFORME)
                .commentaire("Conforme aux specs")
                .build();
        EtapeProduction etape = EtapeProduction.builder()
                .id(200L)
                .type(TypeEtape.CONTROLE_QUALITE)
                .statut(StatutEtape.TERMINEE)
                .controleQualite(controle)
                .build();

        Commande commande = Commande.builder()
                .id(1L)
                .numero("CMD-001")
                .client(client)
                .dateCommande(LocalDate.now())
                .priorite(PrioriteCommande.URGENTE)
                .statut(StatutCommande.EN_PRODUCTION)
                .lignes(List.of(ligne1, ligne2))
                .etapesProduction(List.of(etape))
                .build();

        CommandeResponse response = commandeMapper.toResponse(commande);

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getClient().getNom()).isEqualTo("Client Alpha");
        assertThat(response.getLignes()).hasSize(2);
        assertThat(response.getLignes().get(0).getProduit().getReference()).isEqualTo("TS-001");
        assertThat(response.getLignes().get(0).getQuantite()).isEqualTo(5);
        assertThat(response.getEtapes()).hasSize(1);
        assertThat(response.getEtapes().get(0).getResultatControle()).isEqualTo(ResultatControle.CONFORME);
        assertThat(response.getEtapes().get(0).getCommentaireControle()).isEqualTo("Conforme aux specs");
    }

    @Test
    void etapeToResponseAndBackMapsControleResult() {
        ControleQualite controle = ControleQualite.builder()
                .id(300L)
                .resultat(ResultatControle.NON_CONFORME)
                .commentaire("Couture défectueuse")
                .build();
        EtapeProduction etape = EtapeProduction.builder()
                .id(200L)
                .type(TypeEtape.COUTURE)
                .statut(StatutEtape.TERMINEE)
                .responsable("Atelier 2")
                .controleQualite(controle)
                .build();

        EtapeProductionResponse response = etapeProductionMapper.toResponse(etape);

        assertThat(response.getResultatControle()).isEqualTo(ResultatControle.NON_CONFORME);
        assertThat(response.getCommentaireControle()).isEqualTo("Couture défectueuse");
        assertThat(response.getType()).isEqualTo(TypeEtape.COUTURE);

        EtapeProduction back = etapeProductionMapper.toEntity(response);

        assertThat(back.getType()).isEqualTo(TypeEtape.COUTURE);
        assertThat(back.getControleQualite()).isNotNull();
        assertThat(back.getControleQualite().getResultat()).isEqualTo(ResultatControle.NON_CONFORME);
        assertThat(back.getControleQualite().getCommentaire()).isEqualTo("Couture défectueuse");
    }

    @Test
    void createRequestToCommandeMapsClientProduitAndLignes() {
        CreateLigneCommandeRequest ligneRequest = CreateLigneCommandeRequest.builder()
                .produitId(10L)
                .quantite(4)
                .taille("S")
                .couleur("Bleu")
                .prixUnitaire(new BigDecimal("12.00"))
                .build();

        CreateCommandeRequest request = CreateCommandeRequest.builder()
                .numero("CMD-002")
                .clientId(1L)
                .dateCommande(LocalDate.now())
                .priorite(PrioriteCommande.NORMALE)
                .lignes(List.of(ligneRequest))
                .build();

        Commande commande = commandeMapper.toEntity(request);

        assertThat(commande.getId()).isNull();
        assertThat(commande.getNumero()).isEqualTo("CMD-002");
        assertThat(commande.getClient()).isNotNull();
        assertThat(commande.getClient().getId()).isEqualTo(1L);
        assertThat(commande.getStatut()).isNull();
        assertThat(commande.getLignes()).hasSize(1);
        assertThat(commande.getLignes().get(0).getProduit().getId()).isEqualTo(10L);
        assertThat(commande.getLignes().get(0).getQuantite()).isEqualTo(4);
        assertThat(commande.getLignes().get(0).getCommande()).isNull();
    }
}