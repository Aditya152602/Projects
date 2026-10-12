package com.devtrack.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Entity                                   // maps class -> DB table
@Table(name = "users")                    // "user" is reserved in some DBs, so use "users"
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder // Lombok boilerplate
public class User implements UserDetails { // implementing UserDetails lets Spring Security use this entity directly

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY) // MySQL AUTO_INCREMENT
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 150) // unique index -> no duplicate emails
    private String email;

    @Column(nullable = false)
    private String password;              // always the BCrypt hash, never plain text

    @Enumerated(EnumType.STRING)          // store "ADMIN" not 1 -> safe if enum order changes
    @Column(nullable = false, length = 20)
    private Role role;

    // ---- UserDetails contract ----
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name())); // hasRole("ADMIN") looks for ROLE_ADMIN
    }

    @Override
    public String getUsername() {         // Spring Security's "username" = our email
        return email;
    }
    // isAccountNonExpired / isEnabled etc. default to true in UserDetails
}
