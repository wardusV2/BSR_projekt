package com.example.service3.Service;

import com.example.service3.DTO.LikedVideoDTO;
import com.example.service3.Fault.FaultState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Testy pomocniczych metod SatelliteClient bez uruchamiania pętli schedulera.
 * Klasa używa ReflectionTestUtils do wywołania metod prywatnych i ustawienia pól.
 */
class SatelliteClientHelperTest {

    private SatelliteClient satelliteClient;
    private FaultState      faultState;

    @BeforeEach
    void setUp() {
        faultState = new FaultState();
        RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);

        // Nie wywołujemy @PostConstruct – pomijamy loadOrCreateKeys() i startLoop()
        satelliteClient = new SatelliteClient(rabbitTemplate, faultState);
        ReflectionTestUtils.setField(satelliteClient, "serviceName", "Service3");
        ReflectionTestUtils.setField(satelliteClient, "weight", 1.0);
    }

    // ── calculateCategoryFromLikes ────────────────────────────────────────────

    @Test
    void calculateCategory_emptyList_shouldReturnOther() {
        String result = invokeCalculateCategory(List.of());

        assertThat(result).isEqualTo("OTHER");
    }

    @Test
    void calculateCategory_singleVideo_shouldReturnItsCategory() {
        List<LikedVideoDTO> videos = List.of(
                new LikedVideoDTO(1, "Video1", "SPORT")
        );

        String result = invokeCalculateCategory(videos);

        assertThat(result).isEqualTo("SPORT");
    }

    @Test
    void calculateCategory_mostFrequentCategory_shouldWin() {
        List<LikedVideoDTO> videos = List.of(
                new LikedVideoDTO(1, "V1", "MUSIC"),
                new LikedVideoDTO(2, "V2", "SPORT"),
                new LikedVideoDTO(3, "V3", "MUSIC"),
                new LikedVideoDTO(4, "V4", "MUSIC")
        );

        String result = invokeCalculateCategory(videos);

        assertThat(result).isEqualTo("MUSIC");
    }

    @Test
    void calculateCategory_allNullCategories_shouldReturnOther() {
        List<LikedVideoDTO> videos = List.of(
                new LikedVideoDTO(1, "V1", null),
                new LikedVideoDTO(2, "V2", null)
        );

        String result = invokeCalculateCategory(videos);

        assertThat(result).isEqualTo("OTHER");
    }

    @Test
    void calculateCategory_mixOfNullAndValid_shouldIgnoreNulls() {
        List<LikedVideoDTO> videos = List.of(
                new LikedVideoDTO(1, "V1", null),
                new LikedVideoDTO(2, "V2", "GAMING"),
                new LikedVideoDTO(3, "V3", "GAMING")
        );

        String result = invokeCalculateCategory(videos);

        assertThat(result).isEqualTo("GAMING");
    }

    @Test
    void calculateCategory_tiebreaker_shouldReturnOneOfTied() {
        List<LikedVideoDTO> videos = List.of(
                new LikedVideoDTO(1, "V1", "NEWS"),
                new LikedVideoDTO(2, "V2", "POLITICS")
        );

        String result = invokeCalculateCategory(videos);

        assertThat(result).isIn("NEWS", "POLITICS");
    }

    // ── applyByzantine ────────────────────────────────────────────────────────

    @Test
    void applyByzantine_faultTypeNone_shouldReturnOriginalCategory() {
        faultState.set(FaultState.FaultConfig.none());

        String result = invokeApplyByzantine("SPORT");

        assertThat(result).isEqualTo("SPORT");
    }

    @Test
    void applyByzantine_faultTypeDelay_shouldReturnOriginalCategory() {
        faultState.set(new FaultState.FaultConfig(FaultState.FaultType.DELAY, 100, 0, null));

        String result = invokeApplyByzantine("MUSIC");

        assertThat(result).isEqualTo("MUSIC");
    }

    @Test
    void applyByzantine_byzantineInvalid_shouldReturnInvalidMarker() {
        faultState.set(new FaultState.FaultConfig(FaultState.FaultType.BYZANTINE, 0, 0, "INVALID"));

        String result = invokeApplyByzantine("SPORT");

        assertThat(result).isEqualTo("###INVALID###");
    }

    @Test
    void applyByzantine_byzantineNull_shouldReturnNull() {
        faultState.set(new FaultState.FaultConfig(FaultState.FaultType.BYZANTINE, 0, 0, "NULL"));

        String result = invokeApplyByzantine("SPORT");

        assertThat(result).isNull();
    }

    @Test
    void applyByzantine_byzantineFixedCategory_shouldReturnThatCategory() {
        faultState.set(new FaultState.FaultConfig(FaultState.FaultType.BYZANTINE, 0, 0, "POLITICS"));

        String result = invokeApplyByzantine("SPORT");

        assertThat(result).isEqualTo("POLITICS");
    }

    @RepeatedTest(20)
    void applyByzantine_byzantineRandom_shouldReturnKnownCategory() {
        faultState.set(new FaultState.FaultConfig(FaultState.FaultType.BYZANTINE, 0, 0, "RANDOM"));

        String result = invokeApplyByzantine("SPORT");

        assertThat(result).isIn("SPORT", "MUSIC", "NEWS", "GAMING", "POLITICS");
    }

    // ── shouldDrop ────────────────────────────────────────────────────────────

    @Test
    void shouldDrop_faultTypeNone_shouldNeverDrop() {
        faultState.set(FaultState.FaultConfig.none());

        boolean dropped = invokeShouldDrop();

        assertThat(dropped).isFalse();
    }

    @Test
    void shouldDrop_dropRate0_shouldNeverDrop() {
        faultState.set(new FaultState.FaultConfig(FaultState.FaultType.DROP, 0, 0, null));

        boolean dropped = invokeShouldDrop();

        assertThat(dropped).isFalse();
    }

    @Test
    void shouldDrop_dropRate100_shouldAlwaysDrop() {
        faultState.set(new FaultState.FaultConfig(FaultState.FaultType.DROP, 0, 100, null));

        // przy dropRate=100 każde wywołanie powinno zwrócić true
        for (int i = 0; i < 20; i++) {
            assertThat(invokeShouldDrop()).isTrue();
        }
    }

    @Test
    void shouldDrop_faultTypeDelay_shouldNeverDrop() {
        faultState.set(new FaultState.FaultConfig(FaultState.FaultType.DELAY, 500, 0, null));

        assertThat(invokeShouldDrop()).isFalse();
    }

    // ── getHealthStatus ────────────────────────────────────────────────────────

    @Test
    void getHealthStatus_initial_messagesSentShouldBeZero() {
        SatelliteClient.HealthStatus status = satelliteClient.getHealthStatus();

        assertThat(status.messagesSent()).isEqualTo(0);
    }

    @Test
    void getHealthStatus_shouldReturnConfiguredServiceName() {
        SatelliteClient.HealthStatus status = satelliteClient.getHealthStatus();

        assertThat(status.serviceName()).isEqualTo("Service3");
    }

    @Test
    void getHealthStatus_initial_failureReasonShouldBeNull() {
        SatelliteClient.HealthStatus status = satelliteClient.getHealthStatus();

        assertThat(status.failureReason()).isNull();
    }

    // ── Pomocnicze metody refleksji ───────────────────────────────────────────

    private String invokeCalculateCategory(List<LikedVideoDTO> videos) {
        return (String) ReflectionTestUtils.invokeMethod(
                satelliteClient,
                "calculateCategoryFromLikes",
                videos
        );
    }

    private String invokeApplyByzantine(String category) {
        return (String) ReflectionTestUtils.invokeMethod(
                satelliteClient,
                "applyByzantine",
                category
        );
    }

    private boolean invokeShouldDrop() {
        return (boolean) ReflectionTestUtils.invokeMethod(
                satelliteClient,
                "shouldDrop"
        );
    }
}
