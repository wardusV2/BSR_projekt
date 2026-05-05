package com.webproject.safelogin.service;

import com.webproject.safelogin.model.User;
import com.webproject.safelogin.model.Video;
import com.webproject.safelogin.repository.UserRepository;
import com.webproject.safelogin.repository.VideoRepository;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class LikeService {

    private final UserRepository userRepository;
    private final VideoRepository videoRepository;

    public LikeService(UserRepository userRepository, VideoRepository videoRepository) {
        this.userRepository = userRepository;
        this.videoRepository = videoRepository;
    }
    public void likeVideo(int userId, int videoId) {
        User user = userRepository.findById(userId).orElseThrow();
        Video video = videoRepository.findById(videoId).orElseThrow();

        user.getLikedVideos().add(video);
        userRepository.save(user);
    }

    public void unlikeVideo(int userId, int videoId) {
        User user = userRepository.findById(userId).orElseThrow();
        Video video = videoRepository.findById(videoId).orElseThrow();

        user.getLikedVideos().remove(video);
        userRepository.save(user);
    }

    public Set<Video> getLikedVideos(int userId) {
        return userRepository.findById(userId)
                .orElseThrow()
                .getLikedVideos();
    }
}
