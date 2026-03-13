package com.ztam.keycloak;

import java.util.List;

import org.keycloak.Config;
import org.keycloak.authentication.Authenticator;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.models.AuthenticationExecutionModel;
import org.keycloak.models.KeycloakSession;
import org.keycloak.models.KeycloakSessionFactory;
import org.keycloak.provider.ProviderConfigProperty;

public class RestAuthenticatorFactory implements AuthenticatorFactory {
    public static final String PROVIDER_ID = "rest-authenticator";
    public static final String VERIFY_URL = "verify_url";
    public static final String API_KEY = "api_key";

    @Override
    public Authenticator create(KeycloakSession session) {
        return new RestAuthenticator();
    }

    @Override
    public void init(Config.Scope config) {}

    @Override
    public void postInit(KeycloakSessionFactory factory) {}

    @Override
    public void close() {}

    @Override
    public String getId() {
        return PROVIDER_ID;
    }

    @Override
    public String getDisplayType() {
        return "Legacy REST Username/Password";
    }

    @Override
    public String getHelpText() {
        return "Validates credentials against external REST endpoint";
    }

    @Override
    public boolean isConfigurable() {
        return true;
    }

    @Override
    public AuthenticationExecutionModel.Requirement[] getRequirementChoices() {
        return new AuthenticationExecutionModel.Requirement[]{
            AuthenticationExecutionModel.Requirement.REQUIRED,
            AuthenticationExecutionModel.Requirement.DISABLED
        };
    }

    @Override
    public boolean isUserSetupAllowed() {
        return false;
    }

    @Override
    public List<ProviderConfigProperty> getConfigProperties() {
        ProviderConfigProperty verifyUrl = new ProviderConfigProperty();
        verifyUrl.setName(VERIFY_URL);
        verifyUrl.setLabel("Verify URL");
        verifyUrl.setType(ProviderConfigProperty.STRING_TYPE);

        ProviderConfigProperty apiKey = new ProviderConfigProperty();
        apiKey.setName(API_KEY);
        apiKey.setLabel("API key");
        apiKey.setType(ProviderConfigProperty.PASSWORD);

        return List.of(verifyUrl, apiKey);
    }

    @Override
    public String getReferenceCategory() {
        return "legacy-rest-auth";
    }

    @Override
    public boolean configuredFor(KeycloakSession session, org.keycloak.models.RealmModel realm,
                                 org.keycloak.models.UserModel user) {
        return true;
    }

    @Override
    public void setRequiredActions(KeycloakSession session, org.keycloak.models.RealmModel realm,
                                   org.keycloak.models.UserModel user) {}
}
