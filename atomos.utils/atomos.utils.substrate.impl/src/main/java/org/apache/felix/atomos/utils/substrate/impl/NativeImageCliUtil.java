/*
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
package org.apache.felix.atomos.utils.substrate.impl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.felix.atomos.utils.substrate.api.NativeImageArguments;

public class NativeImageCliUtil
{

    private static final String GRAAL_HOME = "GRAAL_HOME";

    private static final String JAVA_HOME = "java.home";

    public static Path execute(final Path outputDir,
        final BaseNativeImageArguments arguments) throws Exception
    {
        return execute(null, outputDir, arguments);
    }

    public static Path execute(final Path nativeImageExec, final Path executionDir,
        final NativeImageArguments arguments) throws Exception
    {

        final Optional<Path> exec = findNativeImageExecutable(nativeImageExec);

        if (exec.isEmpty())
        {
            throw new Exception("Missing native image executable. Set '" + GRAAL_HOME
                + "' with the path as an environment variable");
        }

        final Path resultFile = executionDir.resolve(arguments.name());

        final List<String> commands = new ArrayList<>();
        commands.add(exec.get().toAbsolutePath().toString());
        commands.addAll(arguments.arguments());

        final ProcessBuilder pB = new ProcessBuilder(commands);
        pB.inheritIO();
        pB.directory(executionDir.toFile());

        final String cmds = pB.command().stream().collect(Collectors.joining(" "));

        System.out.println(cmds);

        final Process process = pB.start();
        final int exitValue = process.waitFor();
        if (exitValue != 0)
        {
            throw new Exception("native-image returns exit value: " + exitValue);
        }
        if (Files.exists(resultFile))
        {
            return resultFile;
        }
        throw new Exception(
            "native-image could not be found: " + resultFile.toAbsolutePath().toString());

    }

    public static Optional<Path> findNativeImageExecutable()
    {
        return findNativeImageExecutable(null);

    }

    public static Optional<Path> findNativeImageExecutable(final Path path)
    {
        if (path == null)
        {
            Optional<Path> oExec = findNativeImageExecutable(Paths.get("native-image"));
            if (oExec.isPresent())
            {
                return oExec;
            }
            if (System.getenv(GRAAL_HOME) != null)
            {
                oExec = findNativeImageExecutable(Paths.get(System.getenv(GRAAL_HOME)));
                if (oExec.isPresent())
                {
                    return oExec;
                }
            }
            if (System.getProperty(JAVA_HOME) != null)
            {
                oExec = findNativeImageExecutable(
                    Paths.get(System.getProperty(JAVA_HOME)));
                if (oExec.isPresent())
                {
                    return oExec;
                }
            }
            return Optional.empty();
        }
        else if (!Files.exists(path))
        {
            return Optional.empty();
        }
        else if (Files.isDirectory(path))
        {
            final Optional<Path> candidate = findNativeImageExecutable(
                path.resolve("native-image"));
            if (candidate.isPresent())
            {
                return candidate;
            }
            return findNativeImageExecutable(path.resolve("bin"));
        }
        else //file o
        {

            final String version = getVersion(path);
            if (version != null)
            {
                return Optional.of(path);
            }

        }
        return Optional.empty();

    }

    public static String getVersion(final Path exec)
    {
        try
        {
            final ProcessBuilder processBuilder = new ProcessBuilder(exec.toString(),
                "--version");
            final Process versionProcess = processBuilder.start();
            try (final Stream<String> lines = new BufferedReader(
                new InputStreamReader(versionProcess.getInputStream())).lines())
            {
                final Optional<String> versionLine = lines.filter(
                    l -> l.contains("GraalVM Version")).findFirst();
                if (versionLine.isPresent())
                {
                    return versionLine.get();
                }
            }
        }
        catch (final IOException e)
        {
            e.printStackTrace();
        }
        return null;
    }
}
