package com.tiendatech.contract;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/** Loads real MVC controllers and springdoc; collaborators are mocks, never called. */
@org.junit.jupiter.api.condition.EnabledIfSystemProperty(named = "openapi.export", matches = "true")
class OpenApiExportTest {
    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class ExportApplication {
        @Bean
        static BeanDefinitionRegistryPostProcessor controllers() {
            return registry -> {
                var scanner = new ClassPathScanningCandidateComponentProvider(false);
                // Punto 4: antes escaneaba Controller.class, que tambien matchea
                // @RestController (esta meta-anotada con @Controller). Eso era
                // invisible mientras el unico modulo con controladores era cada
                // microservicio (100% @RestController). Al integrar el gateway
                // (Apps/web/frontend) hace falta distinguir: SystemObservabilityController
                // es @RestController (API real, debe documentarse), pero WebappController
                // es @Controller puro (~15 rutas de vista/redireccion del monolito legacy,
                // no forman parte del contrato JSON). Angostar a RestController.class
                // excluye WebappController sin afectar a los 6 servicios: ninguno
                // declara @Controller puro (verificado por busqueda en todo el arbol).
                scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
                Set<Class<?>> dependencies = new HashSet<>();
                for (var candidate : scanner.findCandidateComponents("com.tiendatech")) {
                    try {
                        Class<?> controller = Class.forName(candidate.getBeanClassName());
                        for (var method : controller.getDeclaredMethods()) {
                            for (var parameter : method.getParameters()) {
                                if (parameter.getType().getSimpleName().equals("AuthenticatedUser"))
                                    SpringDocUtils.getConfig().addRequestWrapperToIgnore(parameter.getType());
                            }
                        }
                        registry.registerBeanDefinition(controller.getName(), new RootBeanDefinition(controller));
                        for (var constructor : controller.getDeclaredConstructors()) {
                            for (var parameter : constructor.getParameters()) {
                                if (!parameter.isAnnotationPresent(Value.class)) dependencies.add(parameter.getType());
                            }
                        }
                        for (var field : controller.getDeclaredFields()) {
                            if (field.isAnnotationPresent(Autowired.class)) dependencies.add(field.getType());
                        }
                    } catch (ClassNotFoundException e) { throw new IllegalStateException(e); }
                }
                for (Class<?> dependency : dependencies) registerMock(registry, dependency);
            };
        }

        @Bean
        OperationCustomizer optionalRequestBodies() {
            return (operation, handler) -> {
                for (var parameter : handler.getMethod().getParameters()) {
                    var annotation = parameter.getAnnotation(RequestBody.class);
                    if (annotation != null && operation.getRequestBody() == null) {
                        var schema = ModelConverters.getInstance().resolveAsResolvedSchema(
                                new AnnotatedType(parameter.getParameterizedType()).resolveAsRef(false)).schema;
                        if (schema == null && Map.class.isAssignableFrom(parameter.getType()))
                            schema = new io.swagger.v3.oas.models.media.MapSchema().additionalProperties(true);
                        if (schema == null) throw new AssertionError("Cannot describe request body: " + parameter);
                        operation.setRequestBody(new io.swagger.v3.oas.models.parameters.RequestBody()
                                .required(annotation.required()).content(new Content().addMediaType(
                                        "application/json", new MediaType().schema(schema))));
                    }
                }
                return operation;
            };
        }

        private static <T> void registerMock(BeanDefinitionRegistry registry, Class<T> type) {
            ((org.springframework.beans.factory.config.ConfigurableListableBeanFactory) registry)
                    .registerSingleton(type.getName(), mock(type));
        }
    }

    @Test
    void exportContractAndIndependentMvcInventory() throws Exception {
        // Only web/JSON/springdoc auto-configuration. No business component scan,
        // datasource, security filter, scheduler, telemetry or gRPC server.
        List<String> excluded = new ArrayList<>();
        for (String name : ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())) {
            if (!name.startsWith("org.springdoc.") && !name.matches(
                    ".*\\.(JacksonAutoConfiguration|Jackson2AutoConfiguration|HttpMessageConvertersAutoConfiguration|"
                    + "WebMvcAutoConfiguration|DispatcherServletAutoConfiguration|ServletWebServerFactoryAutoConfiguration|"
                    + "ValidationAutoConfiguration|PropertyPlaceholderAutoConfiguration)$")) excluded.add(name);
        }
        var app = new SpringApplication(ExportApplication.class);
        app.setApplicationContextFactory(webType -> {
            var context = new org.springframework.web.context.support.GenericWebApplicationContext();
            context.setServletContext(new org.springframework.mock.web.MockServletContext());
            return context;
        });
        app.setDefaultProperties(Map.of(
                "spring.autoconfigure.exclude", String.join(",", excluded),
                "spring.config.location", "optional:classpath:/openapi-export-only.properties",
                "server.port", "0", "springdoc.api-docs.version", "OPENAPI_3_0",
                "springdoc.writer-with-order-by-keys", "true",
                "app.upload-root", "target/openapi/uploads", "auth.cookie.domain", "localhost",
                "auth.cookie.secure", "false", "auth.cookie.samesite", "Lax"));
        try (var context = app.run()) {
            var mvc = webAppContextSetup((WebApplicationContext) context).build();
            var response = mvc.perform(get("/v3/api-docs")).andReturn().getResponse();
            if (response.getStatus() != 200) throw new AssertionError(response.getContentAsString());
            Path output = Path.of("target/openapi");
            Files.createDirectories(output);
            Files.writeString(output.resolve("raw.json"), response.getContentAsString());
            Map<String, Object> inventory = new TreeMap<>();
            context.getBean(RequestMappingHandlerMapping.class).getHandlerMethods().forEach((mapping, handler) -> {
                if (!handler.getBeanType().getName().startsWith("com.tiendatech.")) return;
                if (mapping.getMethodsCondition().getMethods().isEmpty())
                    throw new AssertionError("Declare explicit HTTP methods: " + handler);
                for (var path : mapping.getPatternValues()) {
                    for (var method : mapping.getMethodsCondition().getMethods()) {
                        boolean body = Arrays.stream(handler.getMethod().getParameters()).anyMatch(p ->
                                p.isAnnotationPresent(RequestBody.class) || p.isAnnotationPresent(
                                        org.springframework.web.bind.annotation.RequestPart.class));
                        inventory.put(method + " " + path, Map.of("requestBody", body,
                                "returnType", handler.getMethod().getGenericReturnType().getTypeName(),
                                "handler", handler.getBeanType().getName() + "#" + handler.getMethod().getName()));
                    }
                }
            });
            new ObjectMapper().writeValue(output.resolve("routes.json").toFile(), inventory);
            if (inventory.isEmpty()) throw new AssertionError("No application controllers discovered");
        }
    }
}
