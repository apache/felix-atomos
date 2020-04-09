
package org.apache.felix.atomos.utils.core;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.apache.felix.atomos.utils.api.Config;
import org.apache.felix.atomos.utils.api.FileType;
import org.apache.felix.atomos.utils.core.plugins.collector.PathCollectorPluginConfig;
import org.apache.felix.atomos.utils.core.plugins.finaliser.ni.NativeImageBuilderConfig;
import org.apache.felix.atomos.utils.core.plugins.index.IndexPluginConfig;


public class GlobalTestConfig implements Config, PathCollectorPluginConfig, NativeImageBuilderConfig, IndexPluginConfig
{


    private final FileType fileType;
    private final List<Path> files;
    private final Path tempPath;

    public GlobalTestConfig(FileType fileType, List<Path> files, Path tempPath)
    {
        this.fileType = fileType;
        this.files = files;
        this.tempPath = tempPath;
    }

    @Override
    public List<Path> paths()
    {

        return files;
    }

    @Override
    public List<String> filters()
    {
        return null;
    }

    @Override
    public FileType fileType()
    {
        return fileType;
    }


    @Override
    public Path nativeImageExecutable()
    {
        return null;
    }

    @Override
    public String nativeImageApplicationName()
    {
        return null;
    }

    @Override
    public List<String> nativeImageAdditionalInitializeAtBuildTime()
    {
        return null;
    }

    @Override
    public String nativeImageMainClass()
    {

        return null;
    }

    @Override
    public List<String> nativeImageVmFlags()
    {
        return null;
    }

    @Override
    public Map<String, String> nativeImageVmSystemProperties()
    {
        return null;
    }

    @Override
    public Path indexOutputDirectory()
    {
        return null;
    }

    @Override
    public Path nativeImageOutputDirectory()
    {

        return tempPath;
    }

    @Override
    public Boolean noFallback()
    {
        return true;
    }

    @Override
    public List<Path> dynamicProxyConfigurationFiles()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Path> reflectionConfigurationFiles()
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public List<Path> resourceConfigurationFiles()
    {
        // TODO Auto-generated method stub
        return null;
    }
}
