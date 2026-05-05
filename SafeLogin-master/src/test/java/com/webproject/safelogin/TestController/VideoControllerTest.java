package com.webproject.safelogin.TestController;

import com.webproject.safelogin.controller.VideoController;
import com.webproject.safelogin.model.User;
import com.webproject.safelogin.model.Video;
import com.webproject.safelogin.repository.VideoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VideoController.class)
public class VideoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VideoRepository videoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void addVideo_ShouldReturnOk() throws Exception {
        User user = new User();
        user.setId(1);
        user.setNick("nick");

        Video video = new Video();
        video.setId(1);
        video.setTitle("Test Video");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("userId", 1);
        requestBody.put("videoId", 2);
        requestBody.put("content", "Test comment");

        // symulujemy zachowanie repozytorium - zwraca ten sam video po zapisie
        when(videoRepository.save(any(Video.class))).thenReturn(video);

        mockMvc.perform(post("/addVideo")
                        .with(user("testUser").roles("USER"))  // zalogowany użytkownik
                        .with(csrf())                          // token CSRF
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(video)))
                .andExpect(status().isOk())
                .andExpect(content().string("Video saved"));
    }

    @Test
    void getVideo_WhenVideoExists_ShouldReturnVideo() throws Exception {
        Video video = new Video();
        video.setId(1);
        video.setTitle("Test Video");

        when(videoRepository.findById(1)).thenReturn(Optional.of(video));

        mockMvc.perform(get("/getVideo/1")
                        .with(user("testUser").roles("USER")))  // jeśli dostęp wymaga logowania
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.title").value("Test Video"));
    }

    @Test
    void getVideo_WhenVideoNotFound_ShouldReturnNotFound() throws Exception {
        when(videoRepository.findById(999)).thenReturn(Optional.empty());

        mockMvc.perform(get("/getVideo/999")
                        .with(user("testUser").roles("USER")))
                .andExpect(status().isNotFound());
    }
}

