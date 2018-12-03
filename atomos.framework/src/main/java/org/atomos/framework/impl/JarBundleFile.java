/*******************************************************************************
 * Copyright (c) 2005, 2018 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Rob Harrop - SpringSource Inc. (bug 253942)
 *******************************************************************************/

package org.atomos.framework.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.eclipse.osgi.framework.log.FrameworkLogEntry;
import org.eclipse.osgi.internal.debug.Debug;
import org.eclipse.osgi.internal.framework.EquinoxContainer;
import org.eclipse.osgi.internal.framework.EquinoxEventPublisher;
import org.eclipse.osgi.internal.messages.Msg;
import org.eclipse.osgi.storage.BundleInfo;
import org.eclipse.osgi.storage.Storage.StorageException;
import org.eclipse.osgi.storage.bundlefile.BundleEntry;
import org.eclipse.osgi.storage.bundlefile.BundleFile;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.FrameworkEvent;

/**
 * A BundleFile that uses a ZipFile as it base file.
 */
public class JarBundleFile extends BundleFile {


	private final BundleInfo.Generation generation;

	private final Debug debug;
	/**
	 * The zip file
	 */
	private final ZipFile zipFile;

	public JarBundleFile(ZipFile zipFile, File basefile, BundleInfo.Generation generation, Debug debug) throws IOException {
		super(basefile);
		this.zipFile = zipFile;
		this.debug = debug;
		this.generation = generation;
	}

	/**
	 * Returns a ZipEntry for the bundle file. Must be called while holding the open lock.
	 * This method does not ensure that the ZipFile is opened. Callers may need to call getZipfile() prior to calling this 
	 * method.
	 * @param path the path to an entry
	 * @return a ZipEntry or null if the entry does not exist
	 */
	private ZipEntry getZipEntry(String path) {
		if (path.length() > 0 && path.charAt(0) == '/')
			path = path.substring(1);
		ZipEntry entry = zipFile.getEntry(path);
		if (entry != null && entry.getSize() == 0 && !entry.isDirectory()) {
			// work around the directory bug see bug 83542
			ZipEntry dirEntry = zipFile.getEntry(path + '/');
			if (dirEntry != null)
				entry = dirEntry;
		}
		return entry;
	}

	/**
	 * Extracts a directory and all sub content to disk
	 * @param dirName the directory name to extract
	 * @return the File used to extract the content to.  A value
	 * of <code>null</code> is returned if the directory to extract does 
	 * not exist or if content extraction is not supported.
	 */
	File extractDirectory(String dirName) {
		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		while (entries.hasMoreElements()) {
			String entryPath = entries.nextElement().getName();
			if (entryPath.startsWith(dirName) && !entryPath.endsWith("/")) //$NON-NLS-1$
				getFile(entryPath, false);
		}
		return getExtractFile(dirName);
	}

	private File getExtractFile(String entryName) {
		if (generation == null)
			return null;
		return generation.getExtractFile(".cp", entryName); //$NON-NLS-1$
	}

	public File getFile(String entry, boolean nativeCode) {
		ZipEntry zipEntry = getZipEntry(entry);
		if (zipEntry == null)
			return null;

		try {
			File nested = getExtractFile(zipEntry.getName());
			if (nested != null) {
				if (nested.exists()) {
					/* the entry is already cached */
					if (debug.DEBUG_BUNDLE_FILE)
						Debug.println("File already present: " + nested.getPath()); //$NON-NLS-1$
					if (nested.isDirectory())
						// must ensure the complete directory is extracted (bug 182585)
						extractDirectory(zipEntry.getName());
				} else {
					if (zipEntry.getName().endsWith("/")) { //$NON-NLS-1$
						nested.mkdirs();
						if (!nested.isDirectory()) {
							if (debug.DEBUG_BUNDLE_FILE)
								Debug.println("Unable to create directory: " + nested.getPath()); //$NON-NLS-1$
							throw new IOException(NLS.bind(Msg.ADAPTOR_DIRECTORY_CREATE_EXCEPTION, nested.getAbsolutePath()));
						}
						extractDirectory(zipEntry.getName());
					} else {
						InputStream in = zipFile.getInputStream(zipEntry);
						if (in == null)
							return null;
						generation.storeContent(nested, in, nativeCode);
					}
				}

				return nested;
			}
		} catch (IOException e) {
			if (debug.DEBUG_BUNDLE_FILE)
				Debug.printStackTrace(e);
			EquinoxEventPublisher publisher = generation.getBundleInfo().getStorage().getConfiguration().getHookRegistry().getContainer().getEventPublisher();
			publisher.publishFrameworkEvent(FrameworkEvent.ERROR, generation.getRevision().getBundle(), e);
		} catch (StorageException e) {
			if (debug.DEBUG_BUNDLE_FILE)
				Debug.printStackTrace(e);
			EquinoxEventPublisher publisher = generation.getBundleInfo().getStorage().getConfiguration().getHookRegistry().getContainer().getEventPublisher();
			publisher.publishFrameworkEvent(FrameworkEvent.ERROR, generation.getRevision().getBundle(), e);	
		}
		return null;
	}

	public boolean containsDir(String dir) {
		if (dir == null)
			return false;

		if (dir.length() == 0)
			return true;

		if (dir.charAt(0) == '/') {
			if (dir.length() == 1)
				return true;
			dir = dir.substring(1);
		}

		if (dir.length() > 0 && dir.charAt(dir.length() - 1) != '/')
			dir = dir + '/';

		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		ZipEntry zipEntry;
		String entryPath;
		while (entries.hasMoreElements()) {
			zipEntry = entries.nextElement();
			entryPath = zipEntry.getName();
			if (entryPath.startsWith(dir)) {
				return true;
			}
		}

		return false;
	}

	public BundleEntry getEntry(String path) {
		ZipEntry zipEntry = getZipEntry(path);
		if (zipEntry != null) {
			return new JarBundleEntry(zipEntry, this);
		}
		return null;
	}

	@Override
	public Enumeration<String> getEntryPaths(String path, boolean recurse) {
		if (path == null)
			throw new NullPointerException();

		// Strip any leading '/' off of path.
		if (path.length() > 0 && path.charAt(0) == '/')
			path = path.substring(1);
		// Append a '/', if not already there, to path if not an empty string.
		if (path.length() > 0 && path.charAt(path.length() - 1) != '/')
			path = new StringBuilder(path).append("/").toString(); //$NON-NLS-1$

		LinkedHashSet<String> result = new LinkedHashSet<>();
		// Get all zip file entries and add the ones of interest.
		Enumeration<? extends ZipEntry> entries = zipFile.entries();
		while (entries.hasMoreElements()) {
			ZipEntry zipEntry = entries.nextElement();
			String entryPath = zipEntry.getName();
			// Is the entry of possible interest? Note that 
			// string.startsWith("") == true.
			if (entryPath.startsWith(path)) {
				// If we get here, we know that the entry is either (1) equal to
				// path, (2) a file under path, or (3) a subdirectory of path.
				if (path.length() < entryPath.length()) {
					// If we get here, we know that entry is not equal to path.
					getEntryPaths(path, entryPath.substring(path.length()), recurse, result);
				}
			}
		}
		return result.size() == 0 ? null : Collections.enumeration(result);
	}

	private void getEntryPaths(String path, String entry, boolean recurse, LinkedHashSet<String> entries) {
		if (entry.length() == 0)
			return;
		int slash = entry.indexOf('/');
		if (slash == -1)
			entries.add(path + entry);
		else {
			path = path + entry.substring(0, slash + 1);
			entries.add(path);
			if (recurse)
				getEntryPaths(path, entry.substring(slash + 1), true, entries);
		}
	}

	public void close() throws IOException {
		// do nothing
	}


	public void open() throws IOException {
		// do nothing
	}

	InputStream getInputStream(ZipEntry entry) throws IOException {
		return zipFile.getInputStream(entry);
	}
}
