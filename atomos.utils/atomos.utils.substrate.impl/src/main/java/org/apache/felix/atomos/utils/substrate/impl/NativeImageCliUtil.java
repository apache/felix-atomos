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

    public static final String GRAALVM_HOME = "GRAALVM_HOME";

    private static final String JAVA_HOME = "java.home";

    public static final String OS;
    private static final String EXECUTABLE;
    static {
    	OS = System.getProperty("os.name");
    	EXECUTABLE =  isWindows() ? "native-image.cmd" : "native-image";
    }

    public static boolean isWindows() {
    	return (OS != null && OS.startsWith("Windows"));
    }
    
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
            throw new Exception("Missing native image executable. Set '" + GRAALVM_HOME
                + "' with the path as an environment variable");
        }

        String resultFileName = arguments.name();
        if (isWindows()) {
        	resultFileName += ".exe";
        }
        final Path resultFile = executionDir.resolve(resultFileName);

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
            Optional<Path> oExec = findNativeImageExecutable(Paths.get(EXECUTABLE));
            if (oExec.isPresent())
            {
                return oExec;
            }
            if (System.getenv(GRAALVM_HOME) != null)
            {
                oExec = findNativeImageExecutable(Paths.get(System.getenv(GRAALVM_HOME)));
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
                path.resolve(EXECUTABLE));
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
                    l -> l.contains("GraalVM")).findFirst();
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
