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
package org.apache.felix.atomos.examples.android;

import static org.apache.felix.atomos.launch.AtomosLauncher.getConfiguration;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;


import org.apache.felix.atomos.launch.AtomosLauncher;
import org.osgi.framework.BundleException;
import org.osgi.framework.Constants;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.startlevel.BundleStartLevel;

import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        final Button launch = findViewById(R.id.launch);
        final TextView output = findViewById(R.id.output);
        final AtomicReference<Framework> current = new AtomicReference<>();
        File storage = getDir("framework-store", MODE_PRIVATE);
        final String[] args = new String[]{
                Constants.FRAMEWORK_STORAGE + '=' + storage.getAbsolutePath(),
                "gosh.args=--noshutdown"
        };

        launch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Thread t = new Thread("Connect") {
                    public void run() {
                        try {
                            Framework framework = current.updateAndGet(new UnaryOperator<Framework>() {
                                @Override
                                public Framework apply(Framework f) {
                                    try {
                                        if (f != null) {
                                            setText("Stopping Atomos!\n", output);
                                            f.stop();
                                            f.waitForStop(5 * 60000);
                                            appendText("Starting Atomos!\n", output);
                                        } else {
                                            setText("Starting Atomos!\n", output);
                                        }

                                        f = AtomosLauncher.launch(getConfiguration(args));
                                        return f;
                                    } catch (InterruptedException | BundleException e) {
                                        throw new RuntimeException(e);
                                    }
                                }
                            });

                            StringBuilder builder = new StringBuilder();
                            builder.append("Started Atomos:\n");
                            builder.append("   ID|State      |Level|Symbolic name\n");
                            for (org.osgi.framework.Bundle b : framework.getBundleContext().getBundles()) {
                                builder.append(id(b) + '|' + state(b) + '|' + level(b) + '|' + name(b) + '\n');
                            }
                            appendText(builder.toString(), output);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                };
                t.start();
            }
        });
    }

    void setText(String s, TextView output) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                output.setText(s);
            }
        });
    }
    void appendText(String s, TextView output) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                output.append(s);
            }
        });
    }
    private String id(org.osgi.framework.Bundle b) {
        return pad(Long.toString(b.getBundleId()), 5, true);
    }

    private String state(org.osgi.framework.Bundle b) {
        switch (b.getState()) {
            case org.osgi.framework.Bundle.ACTIVE :
                return pad("Active", 11, false);
            case org.osgi.framework.Bundle.INSTALLED :
                return pad("Installed", 11, false);
            case org.osgi.framework.Bundle.RESOLVED :
                return pad("Resolved", 11, false);
            case org.osgi.framework.Bundle.STARTING :
                return pad("Starting", 11, false);
            case org.osgi.framework.Bundle.STOPPING :
                return pad("Stopping", 11, false);
            case org.osgi.framework.Bundle.UNINSTALLED :
                return pad("Uninstalled", 11, false);
            default :
                return pad("Unknown", 11, false);
        }
    }

    private String level(org.osgi.framework.Bundle b) {
        return pad(Integer.toString(b.adapt(BundleStartLevel.class).getStartLevel()), 5, true);
    }

    private String name(org.osgi.framework.Bundle b) {
        return b.getSymbolicName() + '|' + b.getVersion();
    }

    private String pad(String s, int size, boolean prepend) {
        StringBuffer buf = new StringBuffer(s);
        while(buf.length() < size) {
            if (prepend) {
                buf.insert(0, ' ');
            } else {
                buf.append(' ');
            }
        }
        return buf.toString();
    }
}
