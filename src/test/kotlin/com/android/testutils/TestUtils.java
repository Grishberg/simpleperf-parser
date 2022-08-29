/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.testutils;

import com.android.SdkConstants;
import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import static org.junit.Assume.assumeFalse;

/**
 * Utility methods to deal with loading the test data.
 */
public class TestUtils {

    /**
     * Kotlin version that is used in AGP integration tests. Please note that this version does not
     * have to be the same as the version of kotlinc used to build AGP (in Gradle or Bazel).
     *
     * <p>This version needs to be present in prebuilts for tests to pass (see
     * tools/base/bazel/README.md).
     */
    public static final String KOTLIN_VERSION_FOR_TESTS = "1.7.20-Beta";

    /**
     * Kotlin version used in AGP integration tests for Compose.
     *
     * <p>This may be different from {@link KOTLIN_VERSION_FOR_TESTS} because sometimes we can't
     * upgrade the test projects to the latest version of the Kotlin Gradle plugin if there isn't a
     * compatible Compose version yet.
     */
    public static final String KOTLIN_VERSION_FOR_COMPOSE_TESTS = "1.7.0";

    /**
     * The Android platform version used in the gradle-core and builder unit tests.
     *
     * <p>If changing this value, also update //tools/base/build-system:android_platform_for_tests
     */
    public static final int ANDROID_PLATFORM_FOR_AGP_UNIT_TESTS = 33;

    /**
     * Unix file-mode mask indicating that the file is executable by owner, group, and other.
     *
     * <p>See https://askubuntu.com/a/485001
     */
    public static final int UNIX_EXECUTABLE_MODE = 1 | 1 << 3 | 1 << 6;

    /** Default timeout for the {@link #eventually(Runnable)} check. */
    private static final Duration DEFAULT_EVENTUALLY_TIMEOUT = Duration.ofSeconds(10);

    /** Time to wait between checks to obtain the value of an eventually supplier. */
    private static final long EVENTUALLY_CHECK_CYCLE_TIME_MS = 10;

    private static Path workspaceRoot = null;


    /**
     * Returns the root of the entire Android Studio codebase.
     *
     * <p>From this path, you should be able to access any file in the workspace via its full path,
     * e.g.
     *
     * <p>TestUtils.getWorkspaceRoot().resolve("tools/adt/idea/android/testSrc");
     *
     * <p>TestUtils.getWorkspaceRoot().resolve("prebuilts/studio/jdk");
     *
     * <p>If this method is called by code run via IntelliJ / Gradle, it will simply walk its
     * ancestor tree looking for the WORKSPACE file at its root; if called from Bazel, it will
     * simply return the runfiles directory (which should be a mirror of the WORKSPACE root except
     * only populated with explicitly declared dependencies).
     *
     * <p>Instead of calling this directly, prefer calling {@link #resolveWorkspacePath(String)} as
     * it is more resilient to cross-platform testing.
     *
     * @throws IllegalStateException if the current directory of the test is not a subdirectory of
     *     the workspace directory when this method is called. This shouldn't happen if the test is
     *     run by Bazel or run by IntelliJ with default configuration settings (where the working
     *     directory is initialized to the module root).
     */
    @NotNull
    public static synchronized Path getWorkspaceRoot() {
        // The logic below depends on the current working directory, so we save the results and hope
        // the first call is early enough for the user.dir property to be unchanged.
        if (workspaceRoot == null) {
            // If we are using Bazel (which defines the following env vars), simply use
            // the sandboxed root they provide us.
            String workspace = System.getenv("TEST_WORKSPACE");
            String workspaceParent = System.getenv("TEST_SRCDIR");
            if (workspace != null && workspaceParent != null) {
                workspaceRoot = Paths.get(workspaceParent, workspace);

                try {
                    // Bazel munges Windows paths. Which triggers CodeInsightTestFixtureImpl
                    // ::assertFileEndsWithCaseSensitivePath. This is a (hacky?) workaround.
                    workspaceRoot = workspaceRoot.toRealPath();
                } catch (IOException exception) {
                    throw new UncheckedIOException(exception);
                }

                return workspaceRoot;
            }

            Path currDir = Paths.get("").toAbsolutePath();
            Path initialDir = currDir;

            // If we're using a non-Bazel build system. At this point, assume our working directory
            // is located underneath our codebase's root folder, so keep navigating up until we find
            // it. If we're using Bazel, we should still look to see if there's a larger outermost
            // workspace since we might be within a nested workspace.
            while (currDir != null) {
                if (Files.exists(currDir.resolve("WORKSPACE"))) {
                    workspaceRoot = currDir;
                }
                currDir = currDir.getParent();
            }

            if (workspaceRoot == null) {
                throw new IllegalStateException(
                        "Could not find WORKSPACE root. Is the original working directory a "
                                + "subdirectory of the Android Studio codebase?\n\n"
                                + "pwd = "
                                + initialDir);
            }
        }

        return workspaceRoot;
    }

    /**
     * Given a relative path to a file or directory from the base of the current workspace, returns
     * the absolute path.
     *
     * <p>For example:
     *
     * <p>TestUtils.resolveWorkspacePath("tools/adt/idea/android/testSrc");
     *
     * <p>TestUtils.resolveWorkspacePath("prebuilts/studio/jdk");
     *
     * <p>This method guarantees the file or directory exists, throwing an exception if not found,
     * so tests can safely use the file immediately after receiving it.
     *
     * <p>In order to have the same method call work on both Windows and non-Windows machines, if
     * the current OS is Windows and the target path is found with a common windows extension on it,
     * then it will automatically be returned, e.g. "/path/to/binary" -> "/path/to/binary.exe".
     *
     * @throws IllegalArgumentException if the path results in a file that's not found.
     */
    @NotNull
    public static Path resolveWorkspacePath(@NotNull String relativePath) {
        Path f = getWorkspaceRoot().resolve(relativePath);
        if (Files.exists(f)) {
            return f;
        }
/*
        if (OsType.getHostOs() == OsType.WINDOWS) {
            // This file may be a binary with a .exe extension.
            f = f.resolveSibling(f.getFileName().toString() + ".exe");
            if (Files.exists(f)) {
                return f;
            }
        }
*/
        throw new IllegalArgumentException(
                "File \"" + relativePath + "\" not found at \"" + getWorkspaceRoot() + "\"");
    }

    /**
     * Given a relative path to a file or directory from the base of the current workspace, returns
     * the absolute path.
     *
     * This method don't check if the file actually exists
     */
    @NotNull
    public static Path resolveWorkspacePathUnchecked(@NotNull String relativePath) {
        return getWorkspaceRoot().resolve(relativePath);
    }

    /** Gets the path to a specific Bazel workspace. */
    @NotNull
    public static Path getWorkspaceRoot(@NotNull String workspaceName) throws IOException {
        String pathToParent = runningFromBazel() ? ".." : "bazel-out/../../../external";
        // Canonicalize to get rid of the ".."s or symlinks.
        Path canonicalPath =
                resolveWorkspacePathUnchecked(pathToParent).toFile().getCanonicalFile().toPath();
        return canonicalPath.resolve(workspaceName);
    }

    /** Returns true if the file exists in the workspace. */
    public static boolean workspaceFileExists(@NotNull String path) {
        return Files.exists(getWorkspaceRoot().resolve(path));
    }

    /**
     * Returns the absolute {@link Path} to the {@param bin} from the current workspace, or from
     * bazel-bin if not present.
     */
    public static Path getBinPath(String bin) {
        Path path = TestUtils.resolveWorkspacePathUnchecked(bin);
        if (!Files.exists(path)) {
            // running from IJ
            path = TestUtils.resolveWorkspacePathUnchecked("bazel-bin/" + bin);
        }
        return path;
    }

    public static enum TestType {
        AGP,
        OTHER,
    }

    /** Checks if tests were started by Bazel. */
    public static boolean runningFromBazel() {
        return System.getenv().containsKey("TEST_WORKSPACE");
    }

    /** Checks if tests are running with Jdk 11 or above. */
    public static boolean runningWithJdk11Plus(String version) {
        return Integer.parseInt(version.split("\\.")[0]) >= 11;
    }

    /** Returns the prebuilt offline Maven repository used during IDE tests. */
    @NotNull
    public static Path getPrebuiltOfflineMavenRepo() {
        if (runningFromBazel()) {
            // If running with Bazel, then Maven artifacts are unzipped or linked into this
            // directory at runtime using IdeaTestSuiteBase#unzipIntoOfflineMavenRepo or
            // IdeaTestSuiteBase#linkIntoOfflineMavenRepo. Thus, we use a writeable temp
            // directory instead of //prebuilts/tools/common/m2/repository.
            // See b/148081564#comment1 for how this could be simplified in the future.
            Path dir = Paths.get(System.getProperty("java.io.tmpdir"), "offline-maven-repo");
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                throw new UncheckedIOException(
                        "Failed to create directory for offline maven repository: " + dir, e);
            }
            return dir;
        } else {
            return resolveWorkspacePath("prebuilts/tools/common/m2/repository");
        }
    }

    /**
     * Returns the remote SDK directory.
     *
     * @throws IllegalArgumentException if the path results in a file not found.
     */
    @NotNull
    public static Path getRemoteSdk() {
        return resolveWorkspacePath("prebuilts/studio/sdk/remote/dl.google.com/android/repository");
    }


    @NotNull
    public static Path getDesugarLibJar() {
        // the default version is the latest version
        return getDesugarLibJarWithVersion("1.1.5");
    }

    /**
     * Returns the path to a file in the local maven repository.
     *
     * @param path the path of the file relative to the maven repository root
     * @throws IllegalArgumentException if the path results in a file that's not found.
     */
    @NotNull
    public static Path getLocalMavenRepoFile(@NotNull String path) {
        if (runningFromBazel()) {
            return resolveWorkspacePath("../maven/repository/" + path);
        } else {
            return resolveWorkspacePath("prebuilts/tools/common/m2/repository/" + path);
        }
    }

    @NotNull
    private static Path getDesugarLibJarWithVersion(@NotNull String version) {
        return getLocalMavenRepoFile(
                "com/android/tools/desugar_jdk_libs/"
                        + version
                        + "/desugar_jdk_libs-"
                        + version
                        + ".jar");
    }

    @NotNull
    private static Path getDesugarLibConfigJarWithVersion(@NotNull String version) {
        return getLocalMavenRepoFile(
                "com/android/tools/desugar_jdk_libs_configuration/"
                        + version
                        + "/desugar_jdk_libs_configuration-"
                        + version
                        + ".jar");
    }

    @NotNull
    public static String getDesugarLibConfigContent() throws IOException {
        // the default version is the latest version
        return getDesugarLibConfigContentWithVersion("1.1.5");
    }

    @NotNull
    private static String getDesugarLibConfigContentWithVersion(String version) throws IOException {
        StringBuilder stringBuilder = new StringBuilder();

        try (JarFile jarFile = new JarFile(getDesugarLibConfigJarWithVersion(version).toFile())) {
            JarEntry jarEntry = jarFile.getJarEntry("META-INF/desugar/d8/desugar.json");
            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(jarFile.getInputStream(jarEntry)))) {
                String line = reader.readLine();
                while (line != null) {
                    stringBuilder.append(line);
                    line = reader.readLine();
                }
            }
        }
        return stringBuilder.toString();
    }


    @NotNull
    public static String getLatestAndroidPlatform() {
        return getLatestAndroidPlatform(TestType.OTHER);
    }

    @NotNull
    public static String getLatestAndroidPlatform(@NotNull TestType testType) {
        if (testType == TestType.AGP) {
            return "android-" + ANDROID_PLATFORM_FOR_AGP_UNIT_TESTS;
        }
        return "android-32";
    }

    /**
     * Sleeps the current thread for enough time to ensure that the local file system had enough
     * time to notice a "tick". This method is usually called in tests when it is necessary to
     * ensure filesystem writes are detected through timestamp modification.
     *
     * @throws InterruptedException waiting interrupted
     * @throws IOException issues creating a temporary file
     */
    public static void waitForFileSystemTick() throws InterruptedException, IOException {
        waitForFileSystemTick(getFreshTimestamp());
    }

    /**
     * Sleeps the current thread for enough time to ensure that the local file system had enough
     * time to notice a "tick". This method is usually called in tests when it is necessary to
     * ensure filesystem writes are detected through timestamp modification.
     *
     * @param currentTimestamp last timestamp read from disk
     * @throws InterruptedException waiting interrupted
     * @throws IOException issues creating a temporary file
     */
    public static void waitForFileSystemTick(long currentTimestamp)
            throws InterruptedException, IOException {
        while (getFreshTimestamp() <= currentTimestamp) {
            Thread.sleep(100);
        }
    }

    private static long getFreshTimestamp() throws IOException {
        Path notUsed = Files.createTempFile(TestUtils.class.getName(), "waitForFileSystemTick");
        try {
            return Files.getLastModifiedTime(notUsed).toMillis();
        } finally {
            Files.delete(notUsed);
        }
    }

    @NotNull
    public static String getDiff(@NotNull String before, @NotNull  String after) {
        return getDiff(before, after, 0);
    }

    @NotNull
    public static String getDiff(@NotNull String before, @NotNull  String after, int windowSize) {
        return getDiff(before.split("\n"), after.split("\n"), windowSize);
    }

    @NotNull
    public static String getDiff(@NotNull String[] before, @NotNull String[] after) {
        return getDiff(before, after, 0);
    }

    public static String getDiff(@NotNull String[] before, @NotNull String[] after,
                                 int windowSize) {
        // Based on the LCS section in http://introcs.cs.princeton.edu/java/96optimization/
        StringBuilder sb = new StringBuilder();
        int n = before.length;
        int m = after.length;

        // Compute longest common subsequence of x[i..m] and y[j..n] bottom up
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                if (before[i].equals(after[j])) {
                    lcs[i][j] = lcs[i + 1][j + 1] + 1;
                } else {
                    lcs[i][j] = Math.max(lcs[i + 1][j], lcs[i][j + 1]);
                }
            }
        }

        int i = 0;
        int j = 0;
        while ((i < n) && (j < m)) {
            if (before[i].equals(after[j])) {
                i++;
                j++;
            } else {
                sb.append("@@ -");
                sb.append(Integer.toString(i + 1));
                sb.append(" +");
                sb.append(Integer.toString(j + 1));
                sb.append('\n');

                if (windowSize > 0) {
                    for (int context = Math.max(0, i - windowSize); context < i; context++) {
                        sb.append("  ");
                        sb.append(before[context]);
                        sb.append("\n");
                    }
                }

                while (i < n && j < m && !before[i].equals(after[j])) {
                    if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                        sb.append('-');
                        if (!before[i].trim().isEmpty()) {
                            sb.append(' ');
                        }
                        sb.append(before[i]);
                        sb.append('\n');
                        i++;
                    } else {
                        sb.append('+');
                        if (!after[j].trim().isEmpty()) {
                            sb.append(' ');
                        }
                        sb.append(after[j]);
                        sb.append('\n');
                        j++;
                    }
                }

                if (windowSize > 0) {
                    for (int context = i; context < Math.min(n, i + windowSize); context++) {
                        sb.append("  ");
                        sb.append(before[context]);
                        sb.append("\n");
                    }
                }
            }
        }

        if (i < n || j < m) {
            assert i == n || j == m;
            sb.append("@@ -");
            sb.append(Integer.toString(i + 1));
            sb.append(" +");
            sb.append(Integer.toString(j + 1));
            sb.append('\n');
            for (; i < n; i++) {
                sb.append('-');
                if (!before[i].trim().isEmpty()) {
                    sb.append(' ');
                }
                sb.append(before[i]);
                sb.append('\n');
            }
            for (; j < m; j++) {
                sb.append('+');
                if (!after[j].trim().isEmpty()) {
                    sb.append(' ');
                }
                sb.append(after[j]);
                sb.append('\n');
            }
        }

        return sb.toString();
    }

    /**
     * Asserts that a runnable will eventually not throw an assertion exception. Equivalent to
     * {@link #eventually(Runnable, Duration)}, but using a default timeout
     *
     * @param runnable a description of the failure, if the condition never becomes {@code true}
     */
    public static void eventually(@NotNull Runnable runnable) {
        eventually(runnable, DEFAULT_EVENTUALLY_TIMEOUT);
    }

    /**
     * Asserts that a runnable will eventually not throw {@link AssertionError} before
     * {@code timeoutMs} milliseconds have ellapsed
     *
     * @param runnable a description of the failure, if the condition never becomes {@code true}
     * @param duration the timeout for the predicate to become true
     */
    public static void eventually(@NotNull Runnable runnable, Duration duration) {
        AssertionError lastError = null;

        Instant timeoutTime = Instant.now().plus(duration);
        while (Instant.now().isBefore(timeoutTime)) {
            try {
                runnable.run();
                return;
            } catch (AssertionError e) {
                /*
                 * It is OK to throw this. Save for later.
                 */
                lastError = e;
            }

            try {
                Thread.sleep(EVENTUALLY_CHECK_CYCLE_TIME_MS);
            } catch (InterruptedException e) {
                throw new AssertionError(e);
            }
        }

        throw new AssertionError(
                "Timed out waiting for runnable not to throw; last error was:",
                lastError);
    }

    // disable tests when running on Windows in Bazel.
    public static void disableIfOnWindowsWithBazel() {
        assumeFalse(
                (SdkConstants.currentPlatform() == SdkConstants.PLATFORM_WINDOWS)
                        && System.getenv("TEST_TMPDIR") != null);
    }


}
