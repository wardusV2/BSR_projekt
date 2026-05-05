package com.webproject.safelogin.service;

import com.webproject.safelogin.model.WatchHistoryDTO;
import com.webproject.safelogin.model.User;
import com.webproject.safelogin.model.Video;
import com.webproject.safelogin.model.WatchHistory;
import com.webproject.safelogin.repository.UserRepository;
import com.webproject.safelogin.repository.VideoRepository;
import com.webproject.safelogin.repository.WatchHistoryRepository;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Transactional
public class WatchHistoryService {

    private final WatchHistoryRepository historyRepository;
    private final VideoRepository videoRepository;
    private final UserRepository userRepository;

    public WatchHistoryService(
            WatchHistoryRepository historyRepository,
            VideoRepository videoRepository,
            UserRepository userRepository
    ) {
        this.historyRepository = historyRepository;
        this.videoRepository = videoRepository;
        this.userRepository = userRepository;
    }

    // zapis historii po ID użytkownika i wideo
    @Transactional
    public void saveHistory(Integer userId, Integer videoId, Long positionSeconds) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika"));
        Video video = videoRepository.findById(videoId)
                .orElseThrow(() -> new RuntimeException("Nie znaleziono wideo"));

        WatchHistory history = historyRepository
                .findByUserAndVideo(user, video)
                .orElse(new WatchHistory(user, video));

        history.setWatchedAt(LocalDateTime.now());
        history.setLastPositionSeconds(positionSeconds);

        historyRepository.save(history);
    }

    // pobranie historii po ID użytkownika
    public List<WatchHistoryDTO> getUserHistory(Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Nie znaleziono użytkownika"));

        List<WatchHistory> historyList = historyRepository.findByUserWithVideoAndOwner(user);

        return historyList.stream()
                .map(h -> {
                    Video video = h.getVideo();
                    User owner = video.getOwner();

                    // Zgodnie z obecnym konstruktorem WatchHistoryDTO (6 argumentów)
                    return new WatchHistoryDTO(
                            video.getId(),                    // videoId (Integer)
                            video.getTitle(),                 // title
                            video.getUrl(),                   // url
                            owner.getNick(),                  // ownerNick
                            video.getCategory().name(),       // Category
                            h.getWatchedAt(),                 // watchedAt
                            h.getLastPositionSeconds()        // lastPositionSeconds
                    );
                })
                .toList();
    }

}
