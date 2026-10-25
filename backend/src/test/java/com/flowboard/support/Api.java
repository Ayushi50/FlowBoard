package com.flowboard.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

/** Thin JSON helper around MockMvc so tests read like API scripts. */
@TestComponent
public class Api {

    public record Session(String token, UUID userId, String email) {
    }

    public record Result(int status, JsonNode body) {
        public String text(String field) {
            return body.path(field).asText();
        }

        public UUID id() {
            return UUID.fromString(body.path("id").asText());
        }
    }

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    public Session register(String name, String workspaceName) {
        String email = "u" + UUID.randomUUID().toString().substring(0, 8) + "@test.dev";
        Map<String, Object> body = new java.util.HashMap<>(Map.of("email", email, "password", "Password123!", "displayName", name));
        if (workspaceName != null) {
            body.put("workspaceName", workspaceName);
        }
        Result r = post(null, "/api/v1/auth/register", body);
        return new Session(r.body().path("token").asText(), UUID.fromString(r.body().path("user").path("id").asText()), email);
    }

    public UUID firstWorkspace(Session s) {
        return UUID.fromString(get(s, "/api/v1/workspaces").body().get(0).path("id").asText());
    }

    public Result createProject(Session s, UUID ws, String key, UUID... memberIds) {
        return post(s, "/api/v1/workspaces/" + ws + "/projects",
                Map.of("name", "Project " + key, "key", key, "memberIds", java.util.Arrays.asList(memberIds)));
    }

    public Result createTask(Session s, UUID project, String title, Map<String, Object> extra) {
        Map<String, Object> body = new java.util.HashMap<>(extra);
        body.put("title", title);
        return post(s, "/api/v1/projects/" + project + "/tasks", body);
    }

    public Result get(Session s, String url) {
        return exec(MockMvcRequestBuilders.get(url), s, null);
    }

    public Result post(Session s, String url, Object body) {
        return exec(MockMvcRequestBuilders.post(url), s, body);
    }

    public Result postWithHeader(Session s, String url, Object body, String header, String value) {
        return exec(MockMvcRequestBuilders.post(url).header(header, value), s, body);
    }

    public Result put(Session s, String url, Object body) {
        return exec(MockMvcRequestBuilders.put(url), s, body);
    }

    public Result patch(Session s, String url, Object body) {
        return exec(MockMvcRequestBuilders.patch(url), s, body);
    }

    public Result delete(Session s, String url) {
        return exec(MockMvcRequestBuilders.delete(url), s, null);
    }

    private Result exec(MockHttpServletRequestBuilder b, Session s, Object body) {
        try {
            if (s != null) {
                b.header("Authorization", "Bearer " + s.token());
            }
            if (body != null) {
                b.contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(body));
            }
            MvcResult res = mvc.perform(b).andReturn();
            String content = res.getResponse().getContentAsString();
            return new Result(res.getResponse().getStatus(), content.isBlank() ? mapper.createObjectNode() : mapper.readTree(content));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
