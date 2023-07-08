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

import org.junit.jupiter.api.Test;
import org.openrewrite.DocumentExample;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

class FixCwe338Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        spec.parser(JavaParser.fromJavaVersion()
                .classpath("commons-lang", "commons-lang3"))
                .typeValidationOptions(new TypeValidation().methodInvocations(false))
                .recipe(new FixCwe338());
    }

    @DocumentExample
    @Test
    void cwe338CommonsLang2() {
        rewriteRun(
            java("""
                    package au.com.suncorp.easyshare.services.util;
                    import org.apache.commons.lang.RandomStringUtils;
                    public final class RandomUtil {
                        private RandomUtil() {
                        }
                        public static String generateString(int count) {
                            return RandomStringUtils.randomAlphanumeric(count);
                        }
                    }
                """,
                """
                    package au.com.suncorp.easyshare.services.util;

                    import org.apache.commons.lang.RandomStringUtils;

                    import java.security.SecureRandom;

                    public final class RandomUtil {
                        private static final SecureRandom SECURE_RANDOM = new SecureRandom();
                        private static final int DEF_COUNT = 20;

                        static {
                            SECURE_RANDOM.nextBytes(new byte[64]);
                        }

                        private RandomUtil() {
                        }

                        public static String generateString(int count) {
                            return generateRandomAlphanumericString();
                        }

                        private static String generateRandomAlphanumericString() {
                            return RandomStringUtils.random(DEF_COUNT, 0, 0, true, true, null, SECURE_RANDOM);
                        }
                    }
                """
            )
        );
    }

    @Test
    void cwe338() {
        rewriteRun(
            java("""
                package io.moderne.service.util;

                import org.apache.commons.lang3.RandomStringUtils;

                public class RandomUtil {
                    private static final int DEF_COUNT = 20;

                    private RandomUtil() {
                    }

                    public static String generatePassword() {
                        return RandomStringUtils.randomAlphanumeric(DEF_COUNT);
                    }

                    public static String generateActivationKey() {
                        return RandomStringUtils.randomNumeric(DEF_COUNT);
                    }

                    public static String generateResetKey() {
                        return RandomStringUtils.randomNumeric(DEF_COUNT);
                    }

                    public static String generateSeriesData() {
                        return RandomStringUtils.randomAlphanumeric(DEF_COUNT);
                    }

                    public static String generateTokenData() {
                        return RandomStringUtils.randomAlphanumeric(DEF_COUNT);
                    }
                }
            """,
            """
                package io.moderne.service.util;

                import org.apache.commons.lang3.RandomStringUtils;

                import java.security.SecureRandom;

                public class RandomUtil {
                    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
                    private static final int DEF_COUNT = 20;

                    static {
                        SECURE_RANDOM.nextBytes(new byte[64]);
                    }

                    private RandomUtil() {
                    }

                    public static String generatePassword() {
                        return generateRandomAlphanumericString();
                    }

                    public static String generateActivationKey() {
                        return generateRandomAlphanumericString();
                    }

                    public static String generateResetKey() {
                        return generateRandomAlphanumericString();
                    }

                    public static String generateSeriesData() {
                        return generateRandomAlphanumericString();
                    }

                    public static String generateTokenData() {
                        return generateRandomAlphanumericString();
                    }

                    private static String generateRandomAlphanumericString() {
                        return RandomStringUtils.random(DEF_COUNT, 0, 0, true, true, null, SECURE_RANDOM);
                    }
                }
            """)
        );
    }
}
