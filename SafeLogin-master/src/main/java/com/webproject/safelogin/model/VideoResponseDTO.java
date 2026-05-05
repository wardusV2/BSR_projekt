package com.webproject.safelogin.model;

public class VideoResponseDTO {
    private Integer id;
    private String title;
    private String url;
    private Integer ownerId;
    private String ownerNick;
    private String category;

    public VideoResponseDTO(Integer id, String title, String url, Integer ownerId, String ownerNick, String category) {
        this.id = id;
        this.title = title;
        this.url = url;
        this.ownerId = ownerId;
        this.ownerNick = ownerNick;
        this.category = category;
    }

    public Integer getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public String getUrl() {
        return url;
    }

    public Integer getOwnerId() {
        return ownerId;
    }

    public String getOwnerNick() {
        return ownerNick;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
