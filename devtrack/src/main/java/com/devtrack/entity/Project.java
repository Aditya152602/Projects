package com.devtrack.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "projects")
@Getter @Setter @NoArgsConstructor
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 1000)
    private String description;

    @CreationTimestamp                    // Hibernate fills this on insert
    @Column(updatable = false)
    private LocalDateTime createdAt;

    // MANY projects -> ONE owner. LAZY = owner row loaded only when accessed
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id")        // FK column in projects table
    private User owner;

    // MANY-TO-MANY: users who can work in this project besides the owner
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "project_members",  // join table
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id"))
    private Set<User> members = new HashSet<>();

    // ONE project -> MANY tasks. mappedBy = FK lives on Task.project
    // cascade ALL + orphanRemoval: deleting project deletes its tasks
    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks = new ArrayList<>();
}
