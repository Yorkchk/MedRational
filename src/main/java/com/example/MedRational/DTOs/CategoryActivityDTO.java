package com.example.MedRational.DTOs;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryActivityDTO {
    private Long categoryId;
    private String categoryName;
    private long downloads;
    private long views;
    private long favorites;
}
