package org.openrewrite.java.jhipster;

import org.openrewrite.ExecutionContext;
import org.openrewrite.Parser;
import org.openrewrite.Recipe;
import org.openrewrite.TreeVisitor;
import org.openrewrite.internal.lang.Nullable;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.format.AutoFormat;
import org.openrewrite.java.marker.JavaSearchResult;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class FixCwe338 extends Recipe {

    private static final ThreadLocal<JavaParser> JAVA_PARSER = ThreadLocal.withInitial(() ->
            JavaParser.fromJavaVersion()
                    .dependsOn(Arrays.asList(
                            Parser.Input.fromString(
                                    "package org.apache.commons.lang;\n" +
                                            "import java.util.Random;\n" +
                                            "public class RandomStringUtils {\n" +
                                            "  public static String random(int count, int start, int end, boolean letters, boolean numbers, char[] chars, Random random) {}\n" +
                                            "}\n"),
                            Parser.Input.fromString(
                                    "package org.apache.commons.lang3;\n" +
                                            "import java.util.Random;\n" +
                                            "public class RandomStringUtils {\n" +
                                            "  public static String random(int count, int start, int end, boolean letters, boolean numbers, char[] chars, Random random) {}\n" +
                                            "}\n"
                            )))
                    .build());

    private static final String COMMONS_LANG_2 = "COMMONS_LANG_2";

    @Override
    public String getDisplayName() {
        return "Fix CWE-338 with `SecureRandom`";
    }

    @Override
    public String getDescription() {
        return "Use a cryptographically strong pseudo-random number generator (PRNG).";
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getSingleSourceApplicableTest() {
        // Look for classes named RandomUtil
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                if (cu.getPackageDeclaration() == null) {
                    return cu;
                }
                return super.visitCompilationUnit(cu, executionContext);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext executionContext) {
                if (cd.getSimpleName().equals("RandomUtil")) {
                    return cd.withMarkers(cd.getMarkers().addIfAbsent(new JavaSearchResult(FixCwe338.this)));
                }
                return cd;
            }
        };
    }

    @Override
    protected JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                // If the SECURE_RANDOM field already exists the refactoring has already been completed
                boolean fieldExists = classDecl.getBody().getStatements().stream()
                        .filter(it -> it instanceof J.VariableDeclarations)
                        .map(J.VariableDeclarations.class::cast)
                        .filter(it -> it.getVariables().size() == 1)
                        .map(it -> it.getVariables().get(0))
                        .anyMatch(it -> it.getSimpleName().equals("SECURE_RANDOM"));
                if (fieldExists) {
                    return classDecl;
                }

                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, executionContext);

                // Remove any existing fields
                cd = cd.withBody(cd.getBody().withStatements(cd.getBody().getStatements().stream()
                        .filter(it -> !(it instanceof J.VariableDeclarations))
                        .collect(toList())));

                // Add method, fields, static initializer
                // Putting the method first because we're going to move the fields & initializer to the start of the class in the next step
                cd = cd.withBody(cd.getBody().withTemplate(
                        template("private static String generateRandomAlphanumericString() {\n" +
                                "    return RandomStringUtils.random(DEF_COUNT, 0, 0, true, true, null, SECURE_RANDOM);\n" +
                                "}\n" +
                                "private static final SecureRandom SECURE_RANDOM = new SecureRandom();\n" +
                                "private static final int DEF_COUNT = 20;\n\n" +
                                "static {\n" +
                                "    SECURE_RANDOM.nextBytes(new byte[64]);\n" +
                                "}\n"
                        )
                                .javaParser(JAVA_PARSER::get)
                                .imports("java.security.SecureRandom")
                                .build(),
                        cd.getBody().getCoordinates().lastStatement()));
                maybeAddImport("java.security.SecureRandom");

                // Move the fields and static initializer newly added statements to the beginning of the class body
                List<Statement> existingStatements = cd.getBody().getStatements();
                List<Statement> reorderedStatements = Stream.concat(
                        existingStatements.subList(existingStatements.size() - 3, existingStatements.size()).stream(),
                        existingStatements.subList(0, existingStatements.size() - 3).stream()
                ).collect(toList());
                cd = cd.withBody(cd.getBody().withStatements(reorderedStatements));

                // visitImport() will have put a message on the cursor if there is a commons-lang 2 import
                String randomStringUtilsFqn;
                if (getCursor().pollMessage(COMMONS_LANG_2) == null) {
                    randomStringUtilsFqn = "org.apache.commons.lang3.RandomStringUtils";
                } else {
                    randomStringUtilsFqn = "org.apache.commons.lang.RandomStringUtils";
                }
                maybeAddImport(randomStringUtilsFqn);
                doAfterVisit(new AutoFormat());
                return cd;
            }

            @Override
            public J.Import visitImport(J.Import _import, ExecutionContext executionContext) {
                if (_import.getPackageName().equals("org.apache.commons.lang")) {
                    getCursor().putMessage(COMMONS_LANG_2, true);
                }
                return _import;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, ExecutionContext executionContext) {
                return m.withTemplate(template("generateRandomAlphanumericString()")
                                .javaParser(JAVA_PARSER::get)
                                .build(),
                        m.getCoordinates().replace());
            }
        };
    }
}
