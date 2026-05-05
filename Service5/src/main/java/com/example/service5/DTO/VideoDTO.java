package com.example.service5.DTO;

public class VideoDTO {
    private Integer id;
    private String title;
    private String url;
    private Integer ownerId;
    private String ownerNick;
    private String category;



    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Integer getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Integer ownerId) {
        this.ownerId = ownerId;
    }

    public String getOwnerNick() {
        return ownerNick;
    }

    public void setOwnerNick(String ownerNick) {
        this.ownerNick = ownerNick;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
}
