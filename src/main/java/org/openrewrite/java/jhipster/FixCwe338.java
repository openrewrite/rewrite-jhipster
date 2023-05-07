/*
 * Copyright 2021 the original author or authors.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * https://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openrewrite.java.jhipster;

import org.openrewrite.*;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.JavaParser;
import org.openrewrite.java.JavaTemplate;
import org.openrewrite.java.format.AutoFormat;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.Statement;
import org.openrewrite.marker.SearchResult;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toList;

public class FixCwe338 extends Recipe {

    private static final JavaParser.Builder<?, ?> JAVA_PARSER =
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
                            )));

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
    public Duration getEstimatedEffortPerOccurrence() {
        return Duration.ofMinutes(5);
    }

    @Override
    public TreeVisitor<?, ExecutionContext> getVisitor() {
        return Preconditions.check(new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext ctx) {
                if (cu.getPackageDeclaration() == null) {
                    return cu;
                }
                return super.visitCompilationUnit(cu, ctx);
            }

            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration cd, ExecutionContext ctx) {
                if ("RandomUtil".equals(cd.getSimpleName())) {
                    return SearchResult.found(cd);
                }
                return cd;
            }
        }, new JavaIsoVisitor<ExecutionContext>() {
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext ctx) {
                // If the SECURE_RANDOM field already exists the refactoring has already been completed
                boolean fieldExists = classDecl.getBody().getStatements().stream()
                        .filter(J.VariableDeclarations.class::isInstance)
                        .map(J.VariableDeclarations.class::cast)
                        .filter(it -> it.getVariables().size() == 1)
                        .map(it -> it.getVariables().get(0))
                        .anyMatch(it -> "SECURE_RANDOM".equals(it.getSimpleName()));
                if (fieldExists) {
                    return classDecl;
                }

                J.ClassDeclaration cd = super.visitClassDeclaration(classDecl, ctx);

                // Remove any existing fields
                cd = cd.withBody(cd.getBody().withStatements(cd.getBody().getStatements().stream()
                        .filter(it -> !(it instanceof J.VariableDeclarations))
                        .collect(toList())));

                // Add method, fields, static initializer
                // Putting the method first because we're going to move the fields & initializer to the start of the class in the next step
                cd = cd.withBody(cd.getBody().withTemplate(
                        JavaTemplate.builder(this::getCursor, "private static String generateRandomAlphanumericString() {\n" +
                                        "    return RandomStringUtils.random(DEF_COUNT, 0, 0, true, true, null, SECURE_RANDOM);\n" +
                                        "}\n" +
                                        "private static final SecureRandom SECURE_RANDOM = new SecureRandom();\n" +
                                        "private static final int DEF_COUNT = 20;\n\n" +
                                        "static {\n" +
                                        "    SECURE_RANDOM.nextBytes(new byte[64]);\n" +
                                        "}\n"
                                )
                                .javaParser(JAVA_PARSER)
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
            public J.Import visitImport(J.Import _import, ExecutionContext ctx) {
                if ("org.apache.commons.lang".equals(_import.getPackageName())) {
                    getCursor().putMessage(COMMONS_LANG_2, true);
                }
                return _import;
            }

            @Override
            public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, ExecutionContext ctx) {
                return m.withTemplate(JavaTemplate.builder(this::getCursor, "generateRandomAlphanumericString()")
                                .javaParser(JAVA_PARSER)
                                .build(),
                        m.getCoordinates().replace());
            }
        });
    }
}
