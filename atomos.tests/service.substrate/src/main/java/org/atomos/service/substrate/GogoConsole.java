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
package org.atomos.service.substrate;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.atomos.framework.AtomosRuntime;
import org.osgi.framework.BundleContext;
import org.osgi.framework.BundleException;
import org.osgi.framework.launch.Framework;
import org.osgi.service.log.LogEntry;
import org.osgi.service.log.LogLevel;
import org.osgi.service.log.LogReaderService;
import org.osgi.service.log.admin.LoggerContext;

public class GogoConsole {
	public static void main(String[] args) throws BundleException, ClassNotFoundException {
		long start = System.nanoTime();

		AtomosRuntime atomosRuntime = AtomosRuntime.newAtomosRuntime();
		Map<String, String> config = AtomosRuntime.getConfiguration(args);
		config.putIfAbsent(LoggerContext.LOGGER_CONTEXT_DEFAULT_LOGLEVEL, LogLevel.AUDIT.name());
		Framework framework = atomosRuntime.newFramework(config);
		framework.init();
		BundleContext bc = framework.getBundleContext();
		LogReaderService logReader = bc.getService(bc.getServiceReference(LogReaderService.class));
		logReader.addLogListener((e) -> {
			System.out.println(getLogMessage(e));
		});
		framework.start();

		long total = System.nanoTime() - start;
		System.out.println("Total time: " + TimeUnit.NANOSECONDS.toMillis(total));

		if (Arrays.asList(args).contains("-exit")) {
			System.exit(0);
		}
	}

	private static String getLogMessage(LogEntry e) {
		StringBuilder builder = new StringBuilder(e.getMessage());
		if (e.getBundle() != null) {
			builder.append(" - bundle: " + e.getBundle());
		}
		if (e.getServiceReference() != null) {
			builder.append(" - service: " + e.getServiceReference());
		}
		return builder.toString();
	}
}
