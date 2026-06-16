package com.example.service1.Service;

import com.example.service1.DTO.WatchHistoryDTO;
import com.example.service1.Fault.FaultState;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SatelliteClientHelperTest {

    private SatelliteClient createClient() {
        FaultState faultState = new FaultState();
        org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate =
                mock(org.springframework.amqp.rabbit.core.RabbitTemplate.class);
        return new SatelliteClient(rabbitTemplate, faultState);
    }

    private String callCalculateMostWatchedCategory(SatelliteClient client,
                                                    List<WatchHistoryDTO> history) throws Exception {
        Method method = SatelliteClient.class.getDeclaredMethod("calculateMostWatchedCategory", List.class);
        method.setAccessible(true);
        return (String) method.invoke(client, history);
    }

    @Test
    void calculateMostWatchedCategory_emptyHistory_shouldReturnNone() throws Exception {
        assertThat(callCalculateMostWatchedCategory(createClient(), Collections.emptyList()))
                .isEqualTo("NONE");
    }

    @Test
    void calculateMostWatchedCategory_singleCategory_shouldReturnIt() throws Exception {
        List<WatchHistoryDTO> history = List.of(buildEntry("Music"), buildEntry("Music"));
        assertThat(callCalculateMostWatchedCategory(createClient(), history)).isEqualTo("Music");
    }

    @Test
    void calculateMostWatchedCategory_multipleCategories_shouldReturnMostFrequent() throws Exception {
        List<WatchHistoryDTO> history = Arrays.asList(
                buildEntry("Music"), buildEntry("Sports"), buildEntry("Sports"), buildEntry("Sports"));
        assertThat(callCalculateMostWatchedCategory(createClient(), history)).isEqualTo("Sports");
    }

    @Test
    void calculateMostWatchedCategory_nullCategoryEntries_shouldBeIgnored() throws Exception {
        List<WatchHistoryDTO> history = Arrays.asList(buildEntry(null), buildEntry(null), buildEntry("Action"));
        assertThat(callCalculateMostWatchedCategory(createClient(), history)).isEqualTo("Action");
    }

    @Test
    void calculateMostWatchedCategory_allNullCategories_shouldReturnNone() throws Exception {
        List<WatchHistoryDTO> history = Arrays.asList(buildEntry(null), buildEntry(null));
        assertThat(callCalculateMostWatchedCategory(createClient(), history)).isEqualTo("NONE");
    }

    @Test
    void healthStatus_record_shouldStoreAllFields() {
        SatelliteClient.HealthStatus status = new SatelliteClient.HealthStatus("Service1", true, 77, "none");
        assertThat(status.serviceName()).isEqualTo("Service1");
        assertThat(status.loopRunning()).isTrue();
        assertThat(status.messagesSent()).isEqualTo(77);
        assertThat(status.failureReason()).isEqualTo("none");
    }

    @Test
    void getHealthStatus_beforeInit_loopShouldNotBeRunning() {
        assertThat(createClient().getHealthStatus().loopRunning()).isFalse();
    }

    private WatchHistoryDTO buildEntry(String category) {
        WatchHistoryDTO dto = new WatchHistoryDTO();
        dto.setCategory(category);
        return dto;
    }
}