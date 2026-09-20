package com.example.MedRational.Entities;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "hashtags")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Hashtag {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @ManyToMany(mappedBy = "hashtags", fetch = FetchType.LAZY)
    @Builder.Default
    @JsonIgnore // Prevents infinite recursion during JSON serialization
    @ToString.Exclude // Prevents infinite loops in Lombok toString
    @EqualsAndHashCode.Exclude // Avoids cyclic reference in equals/hashCode
    private Set<StudyFile> files = new HashSet<>();
}