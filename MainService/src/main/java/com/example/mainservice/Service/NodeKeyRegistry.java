package com.example.mainservice.Service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * Ładuje klucze publiczne satelit przy starcie MainService
 * i rejestruje je w TrustStore WbftAlgorithm.
 *
 * Konfiguracja (application.properties):
 * ────────────────────────────────────────
 * wbft.keys.path=config/satellite-keys.properties
 *
 * Format pliku satellite-keys.properties:
 * ────────────────────────────────────────
 * Service1=MFkwEwYHKo...Base64EncodedPublicKey...
 * Service2=MFkwEwYHKo...Base64EncodedPublicKey...
 * ...
 *
 * Klucze generowane przez każdą satelitę przy pierwszym starcie
 * i zapisywane do pliku (patrz SatelliteKeyManager).
 *
 * W produkcji zastąp plik konfiguracyjny przez:
 * - HashiCorp Vault (Spring Cloud Vault)
 * - AWS Secrets Manager
 * - Kubernetes Secrets
 */
@Component
public class NodeKeyRegistry {

    private static final Logger logger = LoggerFactory.getLogger(NodeKeyRegistry.class);

    @Value("${wbft.keys.path:C:\\Users\\wardusV2\\Documents\\GitHub\\BSR_projekt\\MainService\\src\\main\\java\\com\\example\\mainservice\\Config\\satellite-keys.properties}")
    private String keysFilePath;
    private final WbftAlgorithm wbftAlgorithm;

    public NodeKeyRegistry(WbftAlgorithm wbftAlgorithm) {
        this.wbftAlgorithm = wbftAlgorithm;
    }

    @PostConstruct
    public void loadKeys() {
        Path path = Path.of(keysFilePath);

        if (!Files.exists(path)) {
            logger.error("NodeKeyRegistry: plik kluczy nie istnieje: {}", path.toAbsolutePath());
            logger.error("Uruchom SatelliteKeyManager.generateAndSaveKeys() na każdej satelicie");
            return;
        }

        try {
            java.util.Properties props = new java.util.Properties();
            props.load(Files.newInputStream(path));

            int registered = 0;
            for (Map.Entry<Object, Object> entry : props.entrySet()) {
                String serviceName = entry.getKey().toString().trim();
                String publicKeyB64 = entry.getValue().toString().trim();
                try {
                    wbftAlgorithm.registerNode(serviceName, publicKeyB64);
                    registered++;
                } catch (Exception e) {
                    logger.error("Błąd rejestracji klucza dla {}: {}", serviceName, e.getMessage());
                }
            }

            logger.info("NodeKeyRegistry: załadowano {} kluczy publicznych z {}",
                    registered, path.toAbsolutePath());

        } catch (IOException e) {
            logger.error("NodeKeyRegistry: błąd odczytu pliku kluczy: {}", e.getMessage());
        }
    }
}