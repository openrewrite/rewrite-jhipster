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
package org.openrewrite.java.jhipster

import org.junit.jupiter.api.Test
import org.openrewrite.Recipe
import org.openrewrite.java.JavaParser
import org.openrewrite.java.JavaRecipeTest

class FixCwe338Test : JavaRecipeTest {
    override val parser: JavaParser = JavaParser.fromJavaVersion()
        .logCompilationWarningsAndErrors(false)
        .classpath("commons-lang", "commons-lang3")
        .build()

    override val recipe: Recipe
        get() = FixCwe338()

    @Test
    fun cwe338CommonsLang2() = assertChanged(
        before = """
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
        after = """
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
        """,
        typeValidation = { methodInvocations = false }
    )

    @Test
    fun cwe338() = assertChanged(
        before = """
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
        after = """
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
        """,
        typeValidation = { methodInvocations = false }
    )
}
