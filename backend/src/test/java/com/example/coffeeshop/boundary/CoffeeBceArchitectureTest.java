package com.example.coffeeshop.boundary;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import org.junit.jupiter.api.Test;

/**
 * Executable form of the BCE import matrix (spec section 1.2) and the "no entity type in a
 * {@code @Path} signature" rule (spec section 1.1). The QA card can verify those by inspection;
 * this test makes them fail the build instead of rotting quietly.
 *
 * <p>Import rules are checked on the sources (a textual scan is what a reviewer would do), the
 * signature rule on the compiled classes (so it cannot be fooled by formatting).
 */
class CoffeeBceArchitectureTest {

    private static final Path SOURCES = Path.of("src", "main", "java", "com", "example", "coffeeshop");

    private static final String ENTITY_PACKAGE = "com.example.coffeeshop.entity";

    @Test
    void controlImportsNeitherBoundaryNorHttpAndNoPersistenceVendor() throws IOException {
        List<Path> files = sourcesIn("control");

        assertFalse(files.isEmpty(), "control sources must exist");
        assertNoForbiddenToken(files, "com.example.coffeeshop.boundary", "a control class must not see the boundary layer");
        assertNoForbiddenToken(files, "jakarta.ws.rs", "control must know nothing about HTTP");
        assertNoForbiddenToken(files, "io.quarkus.panache.common", "control must not depend on Panache query types");
        assertNoForbiddenToken(files, "org.hibernate", "control must stay on JPA + java.sql, no vendor API");
    }

    @Test
    void boundaryImportsNoPersistenceApiAndNoRepository() throws IOException {
        List<Path> files = sourcesIn("boundary");

        assertFalse(files.isEmpty(), "boundary sources must exist");
        assertNoForbiddenToken(files, "jakarta.persistence", "boundary must not touch the persistence API");
        assertNoForbiddenToken(files, "CoffeeRepository", "boundary must not see the repository");
        assertNoForbiddenToken(files, "io.quarkus.hibernate.orm.panache", "boundary must not see Panache");
    }

    @Test
    void entityImportsNeitherOuterLayer() throws IOException {
        List<Path> files = sourcesIn("entity");

        assertFalse(files.isEmpty(), "entity sources must exist");
        assertNoForbiddenToken(files, "com.example.coffeeshop.control", "the entity must not know control");
        assertNoForbiddenToken(files, "com.example.coffeeshop.boundary", "the entity must not know boundary");
        assertNoForbiddenToken(files, "jakarta.ws.rs", "the entity must not know HTTP");
    }

    @Test
    void noEntityTypeAppearsInAnyPathMethodSignature() throws Exception {
        List<Class<?>> resources = new ArrayList<>();
        for (Path file : sourcesIn("boundary")) {
            String relative = SOURCES.relativize(file).toString().replace('/', '.').replace(".java", "");
            Class<?> candidate = Class.forName("com.example.coffeeshop." + relative, false,
                    CoffeeBceArchitectureTest.class.getClassLoader());
            if (candidate.isAnnotationPresent(jakarta.ws.rs.Path.class)) {
                resources.add(candidate);
            }
        }

        assertTrue(resources.contains(CoffeeResource.class), "the coffee resource must be scanned");

        for (Class<?> resource : resources) {
            for (Method method : resource.getDeclaredMethods()) {
                if (!isEndpoint(method)) {
                    continue;
                }
                assertNotEntity(method.getReturnType(), resource, method);
                for (Class<?> parameter : method.getParameterTypes()) {
                    assertNotEntity(parameter, resource, method);
                }
            }
        }
    }

    @Test
    void theResourceOnlyCallsControlThroughTheService() throws IOException {
        String resource = Files.readString(SOURCES.resolve("boundary").resolve("CoffeeResource.java"));

        assertTrue(resource.contains("private final CoffeeService coffeeService"),
                "the resource's only collaborator must be the control service");
        assertFalse(resource.contains("@Transactional"),
                "transactions belong to control, never to the resource");
    }

    // ------------------------------------------------------------------ helpers

    private static boolean isEndpoint(Method method) {
        return method.isAnnotationPresent(GET.class) || method.isAnnotationPresent(POST.class)
                || method.isAnnotationPresent(PUT.class) || method.isAnnotationPresent(DELETE.class);
    }

    private static void assertNotEntity(Class<?> type, Class<?> resource, Method method) {
        assertFalse(ENTITY_PACKAGE.equals(type.getPackageName()),
                resource.getSimpleName() + "#" + method.getName()
                        + " exposes the entity " + type.getSimpleName() + " on the HTTP edge");
    }

    /**
     * Checks the <em>import</em> lines: the spec's rule is an import matrix (section 1.2), and a
     * prose mention of a forbidden type in a comment (explaining why it is forbidden) is not a
     * dependency.
     */
    private static void assertNoForbiddenToken(List<Path> files, String token, String why) throws IOException {
        for (Path file : files) {
            for (String line : Files.readAllLines(file)) {
                String trimmed = line.trim();
                if (trimmed.startsWith("import ") && trimmed.contains(token)) {
                    throw new AssertionError(file + " imports '" + token + "': " + why);
                }
            }
        }
    }

    private static List<Path> sourcesIn(String layer) throws IOException {
        Path root = SOURCES.resolve(layer);
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
    }
}
