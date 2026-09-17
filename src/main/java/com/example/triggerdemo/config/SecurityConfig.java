package com.example.triggerdemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public UserDetailsService users(PasswordEncoder encoder) {
        var admin = User.withUsername("admin")
                .password(encoder.encode("admin123"))
                .roles("ADMIN")
                .build();

        var manager = User.withUsername("manager")
                .password(encoder.encode("manager123"))
                .roles("MANAGER")
                .build();

        return new InMemoryUserDetailsManager(admin, manager);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public org.springframework.security.web.SecurityFilterChain securityFilterChain(HttpSecurity http)
            throws Exception {
        http
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/css/**").permitAll()
                .anyRequest().authenticated())
            .formLogin(form -> form
                .defaultSuccessUrl("/", true)
                .permitAll())
            .logout(logout -> logout
                    .logoutSuccessUrl("/")
                    .permitAll());
        return http.build();
    }
}
