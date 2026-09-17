package com.tiendatech.productos.contract;

import au.com.dius.pact.provider.junit5.HttpTestTarget;
import au.com.dius.pact.provider.junit5.PactVerificationContext;
import au.com.dius.pact.provider.junit5.PactVerificationInvocationContextProvider;
import au.com.dius.pact.provider.junitsupport.Provider;
import au.com.dius.pact.provider.junitsupport.State;
import au.com.dius.pact.provider.junitsupport.loader.PactFolder;
import com.tiendatech.productos.application.ProductoService;
import com.tiendatech.productos.domain.ProductoResumen;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestTemplate;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;
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
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

/**
 * Verificacion de proveedor Pact contra el pact local congelado (sin broker):
 * monta el ProductoController real igual que OpenApiExportTest para el #4
 * (mismo escaneo de @Controller + mocks genericos de sus dependencias), sin
 * base de datos ni servidor HTTP real.
 */
@Provider("productos-service")
@PactFolder("../../tests/contract/pacts")
@EnabledIfSystemProperty(named = "pact.verify", matches = "true")
class ProductosProviderVerificationTest {

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class VerificationApplication {
        @Bean
        static BeanDefinitionRegistryPostProcessor controllers() {
            return registry -> {
                var scanner = new ClassPathScanningCandidateComponentProvider(false);
                scanner.addIncludeFilter(new AnnotationTypeFilter(Controller.class));
                // El envoltorio uniforme {status,data,message,timestamp} lo aplica
                // ApiResponseAdvice, que es @RestControllerAdvice (meta-anota
                // @ControllerAdvice, no @Controller). Sin esta linea el arnes nunca
                // lo registra como bean, Spring nunca arma la cadena
                // ResponseBodyAdvice, y la verificacion de Pact compara contra la
                // respuesta cruda del controlador en vez del envoltorio real.
                scanner.addIncludeFilter(new AnnotationTypeFilter(ControllerAdvice.class));
                Set<Class<?>> dependencies = new HashSet<>();
                for (var candidate : scanner.findCandidateComponents("com.tiendatech")) {
                    try {
                        Class<?> controller = Class.forName(candidate.getBeanClassName());
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

        private static <T> void registerMock(BeanDefinitionRegistry registry, Class<T> type) {
            ((org.springframework.beans.factory.config.ConfigurableListableBeanFactory) registry)
                    .registerSingleton(type.getName(), mock(type));
        }
    }

    static ConfigurableApplicationContext appContext;
    static int port;

    @BeforeAll
    static void startContext() {
        List<String> excluded = new ArrayList<>();
        for (String name : ImportCandidates.load(AutoConfiguration.class,
                ProductosProviderVerificationTest.class.getClassLoader())) {
            if (!name.matches(".*\\.(JacksonAutoConfiguration|Jackson2AutoConfiguration|HttpMessageConvertersAutoConfiguration|"
                    + "WebMvcAutoConfiguration|DispatcherServletAutoConfiguration|ServletWebServerFactoryAutoConfiguration|"
                    + "TomcatServletWebServerAutoConfiguration|"
                    + "ValidationAutoConfiguration|PropertyPlaceholderAutoConfiguration)$")) excluded.add(name);
        }
        // A real embedded servlet container (not a fake MockServletContext) is used so
        // no pact-jvm Spring-version-specific MockMvc target module is needed: it must
        // match whichever Spring Framework major version this module's Boot parent pulls in.
        var app = new SpringApplication(VerificationApplication.class);
        app.setDefaultProperties(Map.of(
                "spring.autoconfigure.exclude", String.join(",", excluded),
                "spring.config.location", "optional:classpath:/pact-verify-only.properties",
                "server.port", "0"));
        appContext = app.run();
        port = appContext.getEnvironment().getProperty("local.server.port", Integer.class);
    }

    @AfterAll
    static void stopContext() {
        if (appContext != null) appContext.close();
    }

    @BeforeEach
    void setTarget(PactVerificationContext context) {
        context.setTarget(new HttpTestTarget("localhost", port));
    }

    @TestTemplate
    @ExtendWith(PactVerificationInvocationContextProvider.class)
    void pactVerificationTestTemplate(PactVerificationContext context) {
        context.verifyInteraction();
    }

    @State("existen productos habilitados")
    void existenProductosHabilitados() {
        ProductoService productoService = appContext.getBean(ProductoService.class);
        ProductoResumen procesador = new ProductoResumen(
                1L, "Procesador Ryzen 7", new BigDecimal("349.99"), null, null,
                10, null, null, null, null, true, null);
        when(productoService.listar(0, 12)).thenReturn(List.of(procesador));
    }
}
