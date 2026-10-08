package com.example.textile.security;

import com.example.textile.service.UtilisateurDetailsService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final UtilisateurDetailsService utilisateurDetailsService;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .authorizeHttpRequests(auth -> auth
                        // ===== Ressources publiques =====
                        .requestMatchers("/login", "/css/**", "/js/**", "/img/**", "/error", "/h2-console/**").permitAll()

                        // ===== Pages Thymeleaf =====
                        // Validation du contrôle qualité : MANAGER, ADMIN
                        .requestMatchers(HttpMethod.GET, "/commandes/*/qualite").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/commandes/*/qualite").hasAnyRole("MANAGER", "ADMIN")
                        // Gestion des livraisons : MANAGER, ADMIN
                        .requestMatchers(HttpMethod.GET, "/commandes/*/livraisons").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/commandes/*/livraisons").hasAnyRole("MANAGER", "ADMIN")
                        // Mise à jour des étapes de production : OPERATOR, MANAGER, ADMIN
                        .requestMatchers(HttpMethod.GET, "/commandes/*/production").hasAnyRole("OPERATOR", "MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/commandes/*/production/**").hasAnyRole("OPERATOR", "MANAGER", "ADMIN")
                        // Démarrage production et livraison : MANAGER, ADMIN
                        .requestMatchers(HttpMethod.POST, "/commandes/*/production/start", "/commandes/*/delivery")
                        .hasAnyRole("MANAGER", "ADMIN")
                        // Statistiques : MANAGER, ADMIN
                        .requestMatchers("/statistiques").hasAnyRole("MANAGER", "ADMIN")
                        // Configuration (produits, clients) : ADMIN uniquement
                        .requestMatchers("/configuration/**").hasRole("ADMIN")
                        // Création d'une commande via l'interface : MANAGER, ADMIN uniquement
                        .requestMatchers(HttpMethod.GET, "/commandes/nouvelle").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/commandes").hasAnyRole("MANAGER", "ADMIN")
                        // Consultation (tous les utilisateurs connectés, y compris USER)
                        .requestMatchers(HttpMethod.GET, "/", "/commandes", "/commandes/**").authenticated()

                        // ===== API REST =====
                        // Création / mise à jour / suppression de commandes : MANAGER, ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/orders*").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/orders/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/orders/**").hasAnyRole("MANAGER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/orders/**").hasAnyRole("MANAGER", "ADMIN")
                        // Démarrage de la production : MANAGER, ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/production/start").hasAnyRole("MANAGER", "ADMIN")
                        // Validation d'une étape de production : OPERATOR, MANAGER, ADMIN
                        // (l'étape CONTROLE_QUALITE et LIVRAISON restent réservées via @PreAuthorize)
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/production/steps/*/complete")
                        .hasAnyRole("OPERATOR", "MANAGER", "ADMIN")
                        // Contrôle qualité : MANAGER, ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/quality-control").hasAnyRole("MANAGER", "ADMIN")
                        // Livraison : MANAGER, ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/orders/*/delivery").hasAnyRole("MANAGER", "ADMIN")
                        // Consultation des commandes : tous les utilisateurs connectés
                        .requestMatchers(HttpMethod.GET, "/api/orders/**").authenticated()
                        // Configuration produits/clients via API : ADMIN uniquement
                        .requestMatchers(HttpMethod.POST, "/api/clients/**", "/api/produits/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/clients/**", "/api/produits/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/clients/**", "/api/produits/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/clients/**", "/api/produits/**").authenticated()

                        // ===== Tout le reste =====
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login?logout")
                        .invalidateHttpSession(true)
                        .deleteCookies("JSESSIONID")
                        .permitAll()
                )
                .exceptionHandling(ex -> ex
                        .accessDeniedPage("/access-denied")
                        .authenticationEntryPoint((request, response, authException) -> {
                            if (request.getRequestURI().startsWith("/api/")) {
                                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                                response.setContentType("application/json;charset=UTF-8");
                                response.getWriter().write("{\"message\":\"Authentification requise\"}");
                            } else {
                                response.sendRedirect("/login");
                            }
                        })
                )
                .userDetailsService(utilisateurDetailsService);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}