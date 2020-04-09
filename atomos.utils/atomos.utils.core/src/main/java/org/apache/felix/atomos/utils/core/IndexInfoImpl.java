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
package org.apache.felix.atomos.utils.core;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apache.felix.atomos.utils.api.IndexInfo;

public class IndexInfoImpl implements IndexInfo
{
    private String bsn;

    private List<String> files = new ArrayList<>();
    private String id;
    private Path out;
    private Path path;
    private String version;

    String getBsn()
    {
        return bsn;
    }

    @Override
    public String getBundleSymbolicName()
    {
        return bsn;
    }

    @Override
    public List<String> getFiles()
    {
        return files;
    }

    @Override
    public String getId()
    {
        return id;
    }

    @Override
    public Path getOut()
    {
        return out;
    }

    @Override
    public Path getPath()
    {
        return path;
    }

    @Override
    public String getVersion()
    {
        return version;
    }

    public void setBsn(String bsn)
    {
        this.bsn = bsn;
    }

    public void setFiles(List<String> files)
    {
        this.files = files;
    }

    public void setId(String id)
    {
        this.id = id;
    }

    public void setOut(Path out)
    {
        this.out = out;
    }

    public void setPath(Path path)
    {
        this.path = path;
    }

    public void setVersion(String version)
    {
        this.version = version;
    }

}