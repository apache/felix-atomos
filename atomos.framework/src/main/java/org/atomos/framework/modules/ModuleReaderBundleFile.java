/*******************************************************************************
 * Copyright (c) 2018 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.atomos.framework.modules;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.module.ModuleReader;
import java.lang.module.ModuleReference;
import java.net.URI;
import java.nio.ByteBuffer;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Enumeration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import org.eclipse.osgi.container.ModuleContainerAdaptor.ContainerEvent;
import org.eclipse.osgi.container.ModuleRevision;
import org.eclipse.osgi.internal.debug.Debug;
import org.eclipse.osgi.internal.framework.EquinoxEventPublisher;
import org.eclipse.osgi.internal.messages.Msg;
import org.eclipse.osgi.storage.BundleInfo;
import org.eclipse.osgi.storage.Storage.StorageException;
import org.eclipse.osgi.storage.bundlefile.BundleEntry;
import org.eclipse.osgi.storage.bundlefile.BundleFile;
import org.eclipse.osgi.storage.bundlefile.MRUBundleFileList;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.FrameworkEvent;
// NOTE this was copied from Equinox ZipBundleFile to handle MRUBundleFileList
// TODO should look at enhancing ZipBundleFile to have an abstract class that handles
// the MRUBundleFileList logic
public class ModuleReaderBundleFile extends BundleFile {
	// A reentrant lock is used here (instead of intrinsic synchronization)
	// to allow the lock conditional held
	// see lockOpen() and getZipFile()
	private final ReentrantLock openLock = new ReentrantLock();
	private final Condition refCondition = openLock.newCondition();
	private final MRUBundleFileList mruList;
	private final BundleInfo.Generation generation;
	private final Debug debug;
	private final ModuleReference reference;
	private volatile ModuleReader reader = null;
	private volatile boolean closed = true;
	private int referenceCount = 0;

	public ModuleReaderBundleFile(ModuleReference reference, File basefile, BundleInfo.Generation generation, MRUBundleFileList mruList, Debug debug) {
		super(basefile);
		this.reference = reference;
		this.debug = debug;
		this.generation = generation;
		this.closed = true;
		this.mruList = mruList;
	}
	/**
	 * Checks if the reader is open
	 * @return true if the reader is open
	 */
	private boolean lockOpen() {
		try {
			return getReader(true) != null;
		} catch (IOException e) {
			if (generation != null) {
				ModuleRevision r = generation.getRevision();
				if (r != null) {
					ContainerEvent eventType = ContainerEvent.ERROR;
					// If the revision has been removed from the list of revisions then it has been deleted
					// because the bundle has been uninstalled or updated
					if (!r.getRevisions().getModuleRevisions().contains(r)) {
						// instead of filling the log with errors about missing files from 
						// uninstalled/updated bundles just give it an info level
						eventType = ContainerEvent.INFO;
					}
					generation.getBundleInfo().getStorage().getAdaptor().publishContainerEvent(eventType, r.getRevisions().getModule(), e);
				}
			}
			// TODO not sure if throwing a runtime exception is better
			// throw new RuntimeException("Failed to open bundle file.", e);
			return false;
		}
	}

	/**
	 * Returns an open reader for this bundle file.  If an open
	 * reader does not exist then a new one is created and
	 * returned.
	 * @param keepLock true if the open reader lock should be retained
	 * @return an open reader for this bundle
	 * @throws IOException
	 */
	private ModuleReader getReader(boolean keepLock) throws IOException {
		openLock.lock();
		try {
			if (closed) {
				boolean needBackPressure = mruListAdd();
				if (needBackPressure) {
					// release lock before applying back pressure
					openLock.unlock();
					try {
						mruListApplyBackPressure();
					} finally {
						// get lock back after back pressure
						openLock.lock();
					}
				}
				// check close again after getting open lock again
				if (closed) {
					// always add again if back pressure was applied in case
					// the bundle file got removed while releasing the open lock
					if (needBackPressure) {
						mruListAdd();
					}
					// This can throw an IO exception resulting in closed remaining true on exit
					reader = doPrivOpenReader();
					closed = false;
				}
			} else {
				mruListUse();
			}
			return reader;
		} finally {
			if (!keepLock || closed) {
				openLock.unlock();
			}
		}
	}

	private ModuleReader doPrivOpenReader() throws IOException {
		try {
			return AccessController.doPrivileged((PrivilegedExceptionAction<ModuleReader>)() -> reference.open());
		} catch (PrivilegedActionException e) {
			if (e.getException() instanceof IOException)
				throw (IOException) e.getException();
			throw (RuntimeException) e.getException();
		}
	}

	/**
	* Returns a URI for the bundle file. Must be called while holding the open lock.
	* This method does not ensure that the reader is opened. Callers may need to call getREader() prior to calling this 
	* method.
	* @param path the path to an entry
	* @return a URI or null if the entry does not exist
	 * @throws IOException 
	*/
	private Optional<URI> find(String path) throws IOException {
		if (path.length() > 0 && path.charAt(0) == '/') {
			path = path.substring(1);
		}
		return reader.find(path);
	}

	File extractDirectory(String dirName) throws IOException {
		if (!lockOpen()) {
			return null;
		}
		try {
			for (String entryPath : reader.list().sorted().collect(Collectors.toList())) {
				if (entryPath.startsWith(dirName) && !entryPath.endsWith("/")) //$NON-NLS-1$
					getFile(entryPath, false);
			}
			return getExtractFile(dirName);
		} finally {
			openLock.unlock();
		}
	}

	private File getExtractFile(String entryName) {
		if (generation == null)
			return null;
		return generation.getExtractFile(".cp", entryName); //$NON-NLS-1$
	}

	private boolean isMruListClosing() {
		return this.mruList != null && this.mruList.isClosing(this);
	}

	private boolean isMruEnabled() {
		return this.mruList != null && this.mruList.isEnabled();
	}

	private void mruListRemove() {
		if (this.mruList != null) {
			this.mruList.remove(this);
		}
	}

	private void mruListUse() {
		if (this.mruList != null) {
			mruList.use(this);
		}
	}

	private void mruListApplyBackPressure() {
		if (this.mruList != null) {
			this.mruList.applyBackpressure();
		}
	}

	private boolean mruListAdd() {
		if (this.mruList != null) {
			return mruList.add(this);
		}
		return false;
	}

	@Override
	public File getFile(String path, boolean nativeCode) {
		if (!lockOpen()) {
			return null;
		}
		try {
			Optional<URI> found = find(path);
			if (found.isEmpty()) {
				return null;
			}
			URI uri = found.get();

			try {
				File nested = getExtractFile(path);
				if (nested != null) {
					if (nested.exists()) {
						/* the entry is already cached */
						if (debug.DEBUG_BUNDLE_FILE)
							Debug.println("File already present: " + nested.getPath()); //$NON-NLS-1$
						if (nested.isDirectory())
							// must ensure the complete directory is extracted (bug 182585)
							extractDirectory(uri.getPath());
					} else {
						if (uri.getPath().endsWith("/")) { //$NON-NLS-1$
							nested.mkdirs();
							if (!nested.isDirectory()) {
								if (debug.DEBUG_BUNDLE_FILE)
									Debug.println("Unable to create directory: " + nested.getPath()); //$NON-NLS-1$
								throw new IOException(NLS.bind(Msg.ADAPTOR_DIRECTORY_CREATE_EXCEPTION, nested.getAbsolutePath()));
							}
							extractDirectory(uri.getPath());
						} else {
							Optional<InputStream> in = reader.open(uri.getPath());
							if (in.isEmpty())
								return null;
							generation.storeContent(nested, in.get(), nativeCode);
						}
					}

					return nested;
				}
			} catch (StorageException e) {
				if (debug.DEBUG_BUNDLE_FILE)
					Debug.printStackTrace(e);
				EquinoxEventPublisher publisher = generation.getBundleInfo().getStorage().getConfiguration().getHookRegistry().getContainer().getEventPublisher();
				publisher.publishFrameworkEvent(FrameworkEvent.ERROR, generation.getRevision().getBundle(), e);
			}
		} catch (IOException e) {
			if (debug.DEBUG_BUNDLE_FILE)
				Debug.printStackTrace(e);
			EquinoxEventPublisher publisher = generation.getBundleInfo().getStorage().getConfiguration().getHookRegistry().getContainer().getEventPublisher();
			publisher.publishFrameworkEvent(FrameworkEvent.ERROR, generation.getRevision().getBundle(), e);
		} finally {
			openLock.unlock();
		}
		return null;
	}

	public boolean containsDir(String dir) {
		if (!lockOpen()) {
			return false;
		}
		try {
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

			for (String entryPath : reader.list().sorted().collect(Collectors.toList())) {
				if (entryPath.startsWith(dir)) {
					return true;
				}
			}
		} catch (IOException e) {
			return false;
		} finally {
			openLock.unlock();
		}
		return false;
	}

	@Override
	public BundleEntry getEntry(String path) {
		if (!lockOpen()) {
			return null;
		}
		try {
			Optional<URI> found;
			try {
				found = find(path);
			} catch (IOException e) {
				return null;
			}
			if (found.isEmpty()) {
				return null;
			}

			return new ModuleReaderBundleEntry(this, path, found.get());
		} finally {
			openLock.unlock();
		}
	}

	@Override
	public Enumeration<String> getEntryPaths(String path, boolean recurse) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void close() throws IOException {
		openLock.lock();
		try {
			if (!closed) {
				if (referenceCount > 0 && isMruListClosing()) {
					// there are some opened streams to this BundleFile still;
					// wait for them all to close because this is being closed by the MRUBundleFileList
					try {
						refCondition.await(1000, TimeUnit.MICROSECONDS); // timeout after 1 second
					} catch (InterruptedException e) {
						// do nothing for now ...
					}
					if (referenceCount != 0 || closed)
						// either another thread closed the bundle file or we timed waiting for all the reference inputstreams to close
						// If the referenceCount did not reach zero then this bundle file will remain open until the
						// bundle file is closed explicitly (i.e. bundle is updated/uninstalled or framework is shutdown)
						return;

				}
				closed = true;
				reader.close();
				mruListRemove();
				reader = null;
			}
		} finally {
			openLock.unlock();
		}

	}

	@Override
	public void open() throws IOException {
		getReader(false);

	}
	void incrementReference() {
		openLock.lock();
		try {
			referenceCount += 1;
		} finally {
			openLock.unlock();
		}
	}

	void decrementReference() {
		openLock.lock();
		try {
			referenceCount = Math.max(0, referenceCount - 1);
			// only notify if the referenceCount is zero.
			if (referenceCount == 0)
				refCondition.signal();
		} finally {
			openLock.unlock();
		}
	}

	InputStream getInputStream(String path) throws IOException {
		if (!lockOpen()) {
			throw new IOException("Failed to open module file."); //$NON-NLS-1$
		}
		try {
			InputStream stream = reader.open(path).get();
			if (isMruEnabled()) {
				stream = new BundleEntryInputStream(stream);
			}
			return stream;
		} finally {
			openLock.unlock();
		}
	}

	byte[] getBytes(String path) throws IOException {
		if (!lockOpen()) {
			throw new IOException("Failed to open module file."); //$NON-NLS-1$
		}
		try {
			Optional<ByteBuffer> buf = reader.read(path);
			return buf.get().array();
		} finally {
			openLock.unlock();
		}
	}

	private class BundleEntryInputStream extends FilterInputStream {

		private boolean streamClosed = false;

		public BundleEntryInputStream(InputStream stream) {
			super(stream);
			incrementReference();
		}

		public int available() throws IOException {
			try {
				return super.available();
			} catch (IOException e) {
				throw enrichExceptionWithBaseFile(e);
			}
		}

		public void close() throws IOException {
			try {
				super.close();
			} catch (IOException e) {
				throw enrichExceptionWithBaseFile(e);
			} finally {
				synchronized (this) {
					if (streamClosed)
						return;
					streamClosed = true;
				}
				decrementReference();
			}
		}

		public int read() throws IOException {
			try {
				return super.read();
			} catch (IOException e) {
				throw enrichExceptionWithBaseFile(e);
			}
		}

		public int read(byte[] var0, int var1, int var2) throws IOException {
			try {
				return super.read(var0, var1, var2);
			} catch (IOException e) {
				throw enrichExceptionWithBaseFile(e);
			}
		}

		public int read(byte[] var0) throws IOException {
			try {
				return super.read(var0);
			} catch (IOException e) {
				throw enrichExceptionWithBaseFile(e);
			}
		}

		public void reset() throws IOException {
			try {
				super.reset();
			} catch (IOException e) {
				throw enrichExceptionWithBaseFile(e);
			}
		}

		public long skip(long var0) throws IOException {
			try {
				return super.skip(var0);
			} catch (IOException e) {
				throw enrichExceptionWithBaseFile(e);
			}
		}

		private IOException enrichExceptionWithBaseFile(IOException e) {
			return new IOException(getBaseFile().toString(), e);
		}
	}
}
