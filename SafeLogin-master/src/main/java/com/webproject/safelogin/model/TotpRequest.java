package com.webproject.safelogin.model;

public class TotpRequest {
    private int code;

    public TotpRequest() {
    }

    public TotpRequest(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }
}
