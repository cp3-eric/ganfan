package com.ganfan.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class GanfanApiIntegrationTest {
    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void publicHealthAndUnauthenticatedAreSeparated() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.status").value("UP"));

        mockMvc.perform(get("/api/bootstrap"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"demo@ganfan.local\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void demoAccountCanBootstrapAndAdminIsProtected() throws Exception {
        String token = login("demo@ganfan.local", "demo123456");
        mockMvc.perform(get("/api/bootstrap").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.catalog.stalls.length()").value(10))
                .andExpect(jsonPath("$.data.state.schoolId").value("zjut-pingfeng"))
                .andExpect(jsonPath("$.data.catalog.profile.budget[0]").value(15));

        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    void validationReturnsFieldErrors() throws Exception {
        String token = registerUnique();
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/profile")
                        .header("Authorization", bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"测试\",\"spicy\":\"微辣\",\"budgetMin\":40,\"budgetMax\":20,\"sleep\":\"夜猫子\",\"social\":\"愿意拼桌\",\"dislikes\":[\"香菜\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002))
                .andExpect(jsonPath("$.data.budgetMin").exists());
    }

    @Test
    void completeUserJourneyPersistsDataAndCanReset() throws Exception {
        String token = registerUnique();
        String auth = bearer(token);

        mockMvc.perform(post("/api/favorites/stalls/s1").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorites[0]").value("s1"));

        mockMvc.perform(post("/api/meetups/m1/join").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joined.m1").value(true));
        mockMvc.perform(delete("/api/meetups/m1/join").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.joined.m1").doesNotExist());

        mockMvc.perform(post("/api/meetups").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dish\":\"测试香锅\",\"canteen\":\"家和食堂\",\"when\":\"今晚\",\"time\":\"18:30\",\"want\":2,\"budget\":25,\"tags\":[\"拼桌\"],\"note\":\"集成测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdMeetups.length()").value(1));

        MvcResult postResult = mockMvc.perform(post("/api/posts").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"集成测试打卡内容\",\"canteen\":\"家和食堂\",\"place\":\"家和食堂 1F\",\"price\":12,\"tags\":[\"早八\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalCheckins").value(24))
                .andReturn();
        String postId = json(postResult).path("data").path("createdPosts").path(0).path("id").asText();
        assertThat(postId).isNotBlank();

        mockMvc.perform(post("/api/posts/" + postId + "/like").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.likes." + postId).value(true));
        mockMvc.perform(post("/api/posts/" + postId + "/comments").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"评论测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.comments." + postId + ".length()").value(1));
        mockMvc.perform(post("/api/meetups/m1/greetings").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"一起吃饭\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.greetings.m1").value("一起吃饭"));
        mockMvc.perform(post("/api/school-applications").header("Authorization", auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"university\":\"测试大学\",\"campus\":\"本部\",\"city\":\"杭州\",\"contact\":\"张三\",\"phone\":\"13800000000\",\"email\":\"test@example.com\",\"canteens\":\"2\",\"note\":\"测试\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.applications[0].status").value("待审核"));

        mockMvc.perform(delete("/api/me/demo-data").header("Authorization", auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.createdMeetups.length()").value(0))
                .andExpect(jsonPath("$.data.createdPosts.length()").value(0))
                .andExpect(jsonPath("$.data.totalCheckins").value(23));
    }

    @Test
    void adminCanReviewApplications() throws Exception {
        String adminToken = login("admin@ganfan.local", "admin123456");
        mockMvc.perform(get("/api/admin/applications").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(get("/api/admin/users").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").isNumber());
    }

    private String login(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("email", email, "password", password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return json(result).path("data").path("token").asText();
    }

    private String registerUnique() throws Exception {
        String email = "test-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "email", email,
                                "password", "password123",
                                "name", "集成测试"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return json(result).path("data").path("token").asText();
    }

    private JsonNode json(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
