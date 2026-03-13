FROM maven:3.9.9-eclipse-temurin-21 AS spi-build
WORKDIR /build
COPY spi/pom.xml /build/pom.xml
COPY spi/src /build/src
RUN mvn -q -DskipTests package

FROM quay.io/keycloak/keycloak:26.1
COPY --from=spi-build /build/target/keycloak-rest-authenticator-1.0.0.jar /opt/keycloak/providers/
COPY realm/demo-client-realm.json /opt/keycloak/data/import/demo-client-realm.json
