package com.ztam.keycloak;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import org.keycloak.authentication.AuthenticationFlowContext;
import org.keycloak.authentication.AuthenticationFlowError;
import org.keycloak.authentication.Authenticator;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.RealmModel;
import org.keycloak.models.RoleModel;
import org.keycloak.models.UserModel;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RestAuthenticator implements Authenticator {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public void authenticate(AuthenticationFlowContext context) {
        Response challenge = context.form().createLoginUsernamePassword();
        context.challenge(challenge);
    }

    @Override
    public void action(AuthenticationFlowContext context) {
        MultivaluedMap<String, String> formData = context.getHttpRequest().getDecodedFormParameters();
        String username = formData.getFirst("username");
        String password = formData.getFirst("password");

        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            Response challenge = context.form().setError("Missing credentials").createLoginUsernamePassword();
            context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
            return;
        }

        String verifyUrl = configValue(context, RestAuthenticatorFactory.VERIFY_URL,
                System.getenv("KC_SPI_REST_AUTHENTICATOR_VERIFY_URL"));
        String apiKey = configValue(context, RestAuthenticatorFactory.API_KEY,
                System.getenv("KC_SPI_REST_AUTHENTICATOR_API_KEY"));

        try {
            VerifyResponse verify = doVerify(verifyUrl, apiKey, username, password);
            if (!verify.valid) {
                Response challenge = context.form().setError("Invalid username or password")
                        .createLoginUsernamePassword();
                context.failureChallenge(AuthenticationFlowError.INVALID_CREDENTIALS, challenge);
                return;
            }

            UserModel user = upsertUser(context.getSession(), context.getRealm(), username, verify);
            context.setUser(user);
            context.success();
        } catch (Exception e) {
            Response challenge = context.form().setError("Login service unavailable").createLoginUsernamePassword();
            context.failureChallenge(AuthenticationFlowError.INTERNAL_ERROR, challenge);
        }
    }

    private String configValue(AuthenticationFlowContext context, String key, String fallback) {
        if (context.getAuthenticatorConfig() != null
                && context.getAuthenticatorConfig().getConfig() != null
                && context.getAuthenticatorConfig().getConfig().get(key) != null
                && !context.getAuthenticatorConfig().getConfig().get(key).isBlank()) {
            return context.getAuthenticatorConfig().getConfig().get(key);
        }
        return fallback;
    }

    private VerifyResponse doVerify(String verifyUrl, String apiKey, String username, String password)
            throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        Map<String, String> body = new HashMap<>();
        body.put("username", username);
        body.put("password", password);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(verifyUrl))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            VerifyResponse invalid = new VerifyResponse();
            invalid.valid = false;
            return invalid;
        }
        return MAPPER.readValue(response.body(), VerifyResponse.class);
    }

    private UserModel upsertUser(KeycloakSession session, RealmModel realm, String username, VerifyResponse verify) {
        UserModel user = session.users().getUserByUsername(realm, username);
        if (user == null) {
            user = session.users().addUser(realm, username);
        }

        user.setEnabled(true);
        user.setEmail(verify.email);
        user.setFirstName(verify.name);
        user.setSingleAttribute("db_user_id", String.valueOf(verify.userId));

        List<String> roles = verify.roles != null ? verify.roles : List.of();
        for (String roleName : roles) {
            RoleModel role = realm.getRole(roleName);
            if (role != null && !user.hasRole(role)) {
                user.grantRole(role);
            }
        }

        return user;
    }

    @Override
    public boolean requiresUser() {
        return false;
    }

    @Override
    public boolean configuredFor(KeycloakSession session, RealmModel realm, UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, RealmModel realm, UserModel user) {}

    @Override
    public void close() {}
}
