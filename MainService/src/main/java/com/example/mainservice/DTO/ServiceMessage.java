package com.example.mainservice.DTO;

/**
 * Głos węzła w protokole WBFT.
 *
 * Pola dodane na potrzeby warstwy bezpieczeństwa:
 * ─────────────────────────────────────────────────
 * - signature  : podpis ECDSA (Base64) payloadu głosu
 * - timestamp  : czas wysłania głosu (Unix ms) – anty-replay
 *
 * Payload podpisywany przez węzeł:
 *   serviceName|category|weight|timestamp
 */
public class ServiceMessage {

    private String serviceName;
    private Object content;
    private double weight;

    /**
     * Podpis cyfrowy ECDSA zakodowany w Base64.
     * Payload: serviceName|category|weight|timestamp
     */
    private String signature;

    /**
     * Timestamp wysłania głosu (Unix epoch ms).
     * Głosy starsze niż MAX_VOTE_AGE_MS są odrzucane przez WBFT.
     */
    private long timestamp;

    // ── Konstruktory ──────────────────────────────────────────────────────────

    /** Wymagany przez Jackson do deserializacji z RabbitMQ. */
    public ServiceMessage() {}

    /** Konstruktor dla satelit BEZ security (wsteczna kompatybilność). */
    public ServiceMessage(String serviceName, Object content, double weight) {
        this.serviceName = serviceName;
        this.content     = content;
        this.weight      = weight;
        this.timestamp   = System.currentTimeMillis();
    }

    /** Konstruktor dla satelit Z warstwą security. */
    public ServiceMessage(String serviceName, Object content, double weight,
                          String signature, long timestamp) {
        this.serviceName = serviceName;
        this.content     = content;
        this.weight      = weight;
        this.signature   = signature;
        this.timestamp   = timestamp;
    }

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getServiceName()           { return serviceName; }
    public void   setServiceName(String s)   { this.serviceName = s; }

    public Object getContent()               { return content; }
    public void   setContent(Object c)       { this.content = c; }

    public double getWeight()                { return weight; }
    public void   setWeight(double w)        { this.weight = w; }

    public String getSignature()             { return signature; }
    public void   setSignature(String s)     { this.signature = s; }

    public long   getTimestamp()             { return timestamp; }
    public void   setTimestamp(long t)       { this.timestamp = t; }
}