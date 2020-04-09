package org.apache.felix.atomos.utils.api;

import java.nio.file.Path;
import java.util.List;

public interface IndexInfo
{
    String getBundleSymbolicName();

    List<String> getFiles();

    String getId();

    Path getOut();

    Path getPath();

    String getVersion();

}
