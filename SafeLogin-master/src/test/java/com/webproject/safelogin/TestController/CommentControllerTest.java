package com.webproject.safelogin.TestController;

import com.webproject.safelogin.controller.CommentController;
import com.webproject.safelogin.model.Comment;
import com.webproject.safelogin.model.User;
import com.webproject.safelogin.model.Video;
import com.webproject.safelogin.repository.UserRepository;
import com.webproject.safelogin.repository.VideoRepository;
import com.webproject.safelogin.service.CommentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.*;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;

@WebMvcTest(CommentController.class)
public class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CommentService commentService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private VideoRepository videoRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void addComment_Unauthenticated_ShouldReturnUnauthorized() throws Exception {
        Map<String, Object> request = Map.of(
                "userId", 1,
                "videoId", 1,
                "content", "Test comment"
        );

        mockMvc.perform(post("/addComment")
                        .with(csrf())  // mimo braku użytkownika potrzebny CSRF
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addComment_Authenticated_ShouldReturnComment() throws Exception {
        User user = new User();
        user.setId(1);
        Video video = new Video();
        video.setId(1);
        Comment comment = new Comment();
        comment.setId(1);
        comment.setUser(user);
        comment.setVideo(video);
        comment.setContent("Test comment");

        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(videoRepository.findById(1)).thenReturn(Optional.of(video));
        when(commentService.addComment(user, video, "Test comment")).thenReturn(comment);

        Map<String, Object> request = Map.of(
                "userId", 1,
                "videoId", 1,
                "content", "Test comment"
        );

        mockMvc.perform(post("/addComment")
                        .with(user("testUser").roles("USER"))  // uwierzytelniony użytkownik
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Test comment"))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.video.id").value(1));
    }

    @Test
    void testAddComment_Success() throws Exception {
        User user = new User();
        user.setId(1);
        user.setNick("nick");

        Video video = new Video();
        video.setId(2);
        video.setTitle("Test Video");

        Comment comment = new Comment();
        comment.setUser(user);
        comment.setVideo(video);
        comment.setContent("Test comment");

        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("userId", 1);
        requestBody.put("videoId", 2);
        requestBody.put("content", "Test comment");

        when(userRepository.findById(1)).thenReturn(Optional.of(user));
        when(videoRepository.findById(2)).thenReturn(Optional.of(video));
        when(commentService.addComment(eq(user), eq(video), eq("Test comment"))).thenReturn(comment);

        mockMvc.perform(post("/addComment")
                        .with(user("testUser").roles("USER"))  // uwierzytelniony użytkownik
                        .with(csrf())                          // token CSRF
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(requestBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Test comment"))
                .andExpect(jsonPath("$.user.id").value(1))
                .andExpect(jsonPath("$.video.id").value(2));
    }

}

