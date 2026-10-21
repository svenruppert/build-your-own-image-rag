/*
 * Copyright © 2026 Sven Ruppert (sven.ruppert@gmail.com)
 *
 * Licensed under the EUPL, Version 1.2 (the "Licence");
 * you may not use this file except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *     https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package com.svenruppert.imagerag.persistence;

import com.svenruppert.dependencies.core.logger.HasLogger;
import org.eclipse.store.storage.embedded.types.EmbeddedStorage;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.nio.file.Path;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;

/**
 * Shared state of all persistence repositories: the EclipseStore storage manager,
 * the persisted {@link AppDataRoot} and the single read-write lock.
 * <p>Lock discipline (applies to every repository): reads run under {@link #read},
 * every mutation together with its {@link #store} call runs under {@link #write} or
 * {@link #writeAndGet}. A method holding the read lock must never call a method that
 * takes the write lock — {@link ReentrantReadWriteLock} cannot upgrade and would
 * deadlock. Acquiring the read lock while holding the write lock is allowed.
 */
final class PersistenceContext
    implements HasLogger {

  private final EmbeddedStorageManager storage;
  private final AppDataRoot root;
  private final boolean loadedExistingData;
  private final ReadWriteLock lock = new ReentrantReadWriteLock();

  PersistenceContext(Path storagePath) {
    AppDataRoot candidate = new AppDataRoot();
    storage = EmbeddedStorage.start(candidate, storagePath);
    Object loadedRoot = storage.root();
    if (loadedRoot instanceof AppDataRoot existingRoot) {
      root = existingRoot;
      loadedExistingData = true;
      // EclipseStore reconstructs objects via Unsafe, bypassing constructors.
      // Any field added after the first persistence run will be null in loaded data.
      // The lazy getters create the missing collections; the root is persisted once
      // afterwards to save the new field references.
      existingRoot.getApprovedImageIds();
      existingRoot.getRecentSearches();
      existingRoot.getHashIndex();
      existingRoot.getOcrResults();
      existingRoot.getSavedSearchViews();
      existingRoot.getAuditLog();
      existingRoot.getRawVectorStore();
      existingRoot.getTaxonomySuggestions();
      existingRoot.getCategoryMetadata();
      existingRoot.getTuningPresets();
      existingRoot.getClusterSuggestions();
      existingRoot.getPromptVersions();
      existingRoot.getSettingOverrides();
      storage.store(existingRoot);
    } else {
      root = candidate;
      loadedExistingData = false;
      storage.setRoot(root);
      storage.storeRoot();
      logger().info("EclipseStore initialized with fresh root");
    }
  }

  /**
   * {@code true} when the storage contained data from an earlier run.
   */
  boolean loadedExistingData() {
    return loadedExistingData;
  }

  AppDataRoot root() {
    return root;
  }

  /**
   * Persists the given object graph node. Call only while holding the write lock.
   */
  void store(Object instance) {
    storage.store(instance);
  }

  <T> T read(Supplier<T> action) {
    lock.readLock().lock();
    try {
      return action.get();
    } finally {
      lock.readLock().unlock();
    }
  }

  <T> T writeAndGet(Supplier<T> action) {
    lock.writeLock().lock();
    try {
      return action.get();
    } finally {
      lock.writeLock().unlock();
    }
  }

  void write(Runnable action) {
    lock.writeLock().lock();
    try {
      action.run();
    } finally {
      lock.writeLock().unlock();
    }
  }

  void shutdown() {
    storage.shutdown();
  }
}
