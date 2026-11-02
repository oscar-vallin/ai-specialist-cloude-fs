package com.bookstore.ecommerce.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.bookstore.ecommerce.common.ErrorCode;
import com.bookstore.ecommerce.support.IntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

class OpenApiContractTest extends IntegrationTest {

    private static JsonNode openApiRoot;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    @BeforeAll
    static void loadOpenApiSpec() throws Exception {
        Path path = Path.of("../api/openapi.yaml");
        if (!Files.exists(path)) {
            path = Path.of("api/openapi.yaml");
        }
        assertThat(Files.exists(path)).as("api/openapi.yaml must exist").isTrue();

        ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());
        try (InputStream in = Files.newInputStream(path)) {
            openApiRoot = yamlMapper.readTree(in);
        }
        assertThat(openApiRoot).isNotNull();
    }

    @Test
    void specDeclaresOpenApi3() {
        assertThat(openApiRoot.has("openapi")).isTrue();
        assertThat(openApiRoot.get("openapi").asText()).startsWith("3.");
        assertThat(openApiRoot.get("info").get("title").asText()).isNotEmpty();
    }

    @Test
    void specContainsRequiredComponentsAndSecurity() {
        JsonNode components = openApiRoot.get("components");
        assertThat(components).isNotNull();
        assertThat(components.has("securitySchemes")).isTrue();
        assertThat(components.get("securitySchemes").has("bearerAuth")).isTrue();
        assertThat(components.has("schemas")).isTrue();
        assertThat(components.has("responses")).isTrue();
    }

    @Test
    void allOpenApiErrorCodesAreDeclaredInJavaErrorCode() {
        JsonNode errorEnum = openApiRoot.path("components").path("schemas").path("ErrorCode").path("enum");
        assertThat(errorEnum.isArray()).isTrue();

        Set<String> javaCodeNames = new HashSet<>();
        for (ErrorCode code : ErrorCode.values()) {
            javaCodeNames.add(code.name());
        }

        errorEnum.forEach(node -> {
            String specCode = node.asText();
            assertThat(javaCodeNames)
                    .as("api/openapi.yaml ErrorCode '%s' must be present in Java ErrorCode enum", specCode)
                    .contains(specCode);
        });
    }

    @Test
    void allControllerEndpointsMatchOpenApiPaths() {
        JsonNode pathsNode = openApiRoot.get("paths");
        assertThat(pathsNode).isNotNull();

        Set<String> openApiPaths = new HashSet<>();
        pathsNode.fieldNames().forEachRemaining(p -> openApiPaths.add("/api/v1" + p));

        handlerMapping.getHandlerMethods().forEach((info, method) -> {
            Set<String> patterns = info.getPatternValues();
            for (String pattern : patterns) {
                if (pattern.startsWith("/api/v1")) {
                    assertThat(openApiPaths)
                            .as("Controller endpoint pattern %s must be documented in api/openapi.yaml", pattern)
                            .contains(pattern);
                }
            }
        });
    }

    @Test
    void allSchemaPropertiesFollowCamelCase() {
        JsonNode schemas = openApiRoot.path("components").path("schemas");
        assertThat(schemas.isObject()).isTrue();

        Iterator<Map.Entry<String, JsonNode>> fields = schemas.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode properties = entry.getValue().path("properties");
            if (properties.isObject()) {
                Iterator<String> propNames = properties.fieldNames();
                while (propNames.hasNext()) {
                    String propName = propNames.next();
                    assertThat(propName)
                            .as("Property %s in schema %s must be camelCase", propName, entry.getKey())
                            .matches("^[a-z][a-zA-Z0-9]*$");
                }
            }
        }
    }
}
