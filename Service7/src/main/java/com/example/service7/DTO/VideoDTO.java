package com.example.service7.DTO;

public record VideoDTO(
        int id,
        String title,
        String url,
        Integer ownerId,
        String ownerNick,
        String category
) {
    @Override
    public int id() {
        return id;
    }

    @Override
    public String title() {
        return title;
    }

    @Override
    public String url() {
        return url;
    }

    @Override
    public Integer ownerId() {
        return ownerId;
    }

    @Override
    public String ownerNick() {
        return ownerNick;
    }

    @Override
    public String category() {
        return category;
    }
}
