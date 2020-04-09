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
package org.apache.felix.atomos.utils.core.plugins.finaliser.ni;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import org.apache.felix.atomos.utils.api.Context;
import org.apache.felix.atomos.utils.api.FileType;
import org.apache.felix.atomos.utils.api.plugin.FinalPlugin;
import org.apache.felix.atomos.utils.substrate.api.NativeImageArguments;
import org.apache.felix.atomos.utils.substrate.api.NativeImageCli;
import org.apache.felix.atomos.utils.substrate.api.NativeImageConfigJsonProvider;
import org.apache.felix.atomos.utils.substrate.api.dynproxy.DynamicProxyConfiguration;
import org.apache.felix.atomos.utils.substrate.api.reflect.ReflectionConfiguration;
import org.apache.felix.atomos.utils.substrate.api.resource.ResourceConfiguration;

public class NativeImagePlugin implements FinalPlugin<NativeImageBuilderConfig>
{
    private NativeImageBuilderConfig config;

    @Override
    public void doFinal(Context context)
    {
        try
        {
            // prepare the Directorys
            Path native_image_build = config.nativeImageOutputDirectory().resolve(
                "native_image_build");
            Files.createDirectories(native_image_build);
            Path native_image_build_timed = native_image_build.resolve(
                System.currentTimeMillis() + "");
            Files.createDirectories(native_image_build_timed);
            Path cpDir = native_image_build_timed.resolve("cp");
            Files.createDirectories(cpDir);
            Path cfgDir = native_image_build_timed.resolve("cfg");
            Files.createDirectories(cfgDir);
            Path binDir = native_image_build_timed.resolve("bin");
            Files.createDirectories(binDir);

            //prepare classpath
            List<Path> classpath = context.getFiles(FileType.ARTIFACT,
                FileType.INDEX_JAR).collect(Collectors.toList());
            System.out.println(native_image_build_timed);
            List<Path> copyOfClassPath = new ArrayList<>();
            classpath.forEach(p -> {
                try
                {
                    Path newPath = Files.copy(p, cpDir.resolve(p.getFileName()));
                    copyOfClassPath.add(newPath);
                }
                catch (IOException e)
                {
                    throw new UncheckedIOException(e);
                }
            });
            // prepare configuration files
            DynamicProxyConfiguration dynPrC = context.getDynamicProxyConfig();
            String sDynPrC = NativeImageConfigJsonProvider.newInstance().json(dynPrC);
            Path pDynPrC = Files.write(cfgDir.resolve("DynamicProxyConfig.json"),
                sDynPrC.getBytes(StandardCharsets.UTF_8));
            ReflectionConfiguration refCs = context.getReflectConfig();
            String sRefCs = NativeImageConfigJsonProvider.newInstance().json(refCs);
            Path pRefCs = Files.write(cfgDir.resolve("graal_reflect_config.json"),
                sRefCs.getBytes(StandardCharsets.UTF_8));
            ResourceConfiguration resC = context.getResourceConfig();
            String sResC = NativeImageConfigJsonProvider.newInstance().json(resC);
            Path pResC = Files.write(cfgDir.resolve("graal_resource_config.json"),
                sResC.getBytes(StandardCharsets.UTF_8));

            // build arguments
            NativeImageArguments arguments = NativeImageArguments.builder().imageName(
                config.nativeImageApplicationName()).allowIncompleteClasspath(true)//
                .classPathFiles(Optional.ofNullable(copyOfClassPath))//
                .debugAttach(true)//
                .dynamicProxyConfigurationFile(Optional.of(pDynPrC))//
                .dynamicProxyConfigurationFiles(
                    Optional.of(config.dynamicProxyConfigurationFiles()))//
                .initializeAtBuildTimePackages(Optional.of(resC.getResourcePackages()))//
                .initializeAtBuildTimePackages(Optional.ofNullable(
                    config.nativeImageAdditionalInitializeAtBuildTime()))//
                .mainClass(config.nativeImageMainClass())//
                .noFallback(Optional.ofNullable(config.noFallback()).orElse(true))//
                .reflectionConfigurationFile(Optional.of(pRefCs))//
                .reflectionConfigurationFiles(
                    Optional.of(config.reflectionConfigurationFiles()))//
                .reportExceptionStackTraces(true)//
                .reportUnsupportedElementsAtRuntime(true)//
                .resourceConfigurationFile(Optional.of(pResC))//
                .resourceConfigurationFiles(
                    Optional.of(config.resourceConfigurationFiles()))//
                .vmFlags(Optional.ofNullable(config.nativeImageVmFlags()))//
                .vmSystemProperties(
                    Optional.ofNullable(config.nativeImageVmSystemProperties()))//
                .build();

            //try to find NativeImageCli
            Optional<NativeImageCli> nOptional = NativeImageCli.newInstanceFindNativeImageExecutable(
                config.nativeImageExecutable());

            //execute build an native image
            nOptional.ifPresent(cli -> {
                try
                {
                    Path binFile = cli.execute(binDir, arguments);
                    context.addFile(binFile, FileType.NATIVE_IMAGE_BINARY);
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            });

        }
        catch (Exception e)
        {
            e.printStackTrace();
        }
    }

    @Override
    public void init(NativeImageBuilderConfig config)
    {
        this.config = config;
    }
}