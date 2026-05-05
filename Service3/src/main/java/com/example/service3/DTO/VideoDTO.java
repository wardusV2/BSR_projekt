package com.example.service3.DTO;

public record VideoDTO(
        int id,
        String title,
        String url,
        Integer ownerId,
        String ownerNick,
        String category
) {
}
