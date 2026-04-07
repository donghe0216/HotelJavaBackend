package com.example.HotelBooking.security;

import com.example.HotelBooking.exceptions.CustomAccessDenialHandler;
import com.example.HotelBooking.exceptions.CustomAuthenticationEntryPoint;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityFilter {


    private final AuthFilter authFilter;

    private final CustomAccessDenialHandler customAccessDenialHandler;

    private final CustomAuthenticationEntryPoint customAuthenticationEntryPoint;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {

        // CSRF disabled: stateless JWT auth uses no session cookies, so CSRF tokens are not needed
        httpSecurity.csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .exceptionHandling(exception ->
                        exception.accessDeniedHandler(customAccessDenialHandler)
                                .authenticationEntryPoint(customAuthenticationEntryPoint)
                )
                .authorizeHttpRequests(request -> request
                        // Public endpoints — no token required
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/rooms/all", "/api/rooms/types",
                                         "/api/rooms/available", "/api/rooms/search").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/rooms/{id}").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/bookings/{reference}").permitAll()
                        .requestMatchers("/actuator/health").permitAll()
                        // Admin-only endpoints — checked at URL layer so Spring MVC
                        // never reaches argument resolution for unauthorised callers
                        .requestMatchers(HttpMethod.POST,   "/api/rooms/add").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/api/rooms/update").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/rooms/delete/**").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.GET,    "/api/bookings/all").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.PUT,    "/api/bookings/update").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.GET,    "/api/users/all").hasAuthority("ADMIN")
                        .requestMatchers(HttpMethod.GET,    "/api/notifications/all").hasAuthority("ADMIN")
                        // Everything else requires at least a valid token
                        .anyRequest().authenticated()
                )
                .sessionManagement(manager -> manager.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(authFilter, UsernamePasswordAuthenticationFilter.class);
        return httpSecurity.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authenticationConfiguration)
            throws Exception {

        return authenticationConfiguration.getAuthenticationManager();
    }


}
