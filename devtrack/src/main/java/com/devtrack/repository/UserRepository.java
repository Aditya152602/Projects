package com.devtrack.repository;

import com.devtrack.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> { // CRUD + paging for free
    Optional<User> findByEmail(String email);   // Spring Data writes the SQL from the method name
    boolean existsByEmail(String email);
}
