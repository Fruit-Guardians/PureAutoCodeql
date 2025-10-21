package com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.EqualityLock;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CachedResourceFactory<R extends Resource, S> implements ResourceFactory<R, S> {
   protected static final long GC_TIMEOUT = 30000L;
   private static final Logger logger = LoggerFactory.getLogger(CachedResourceFactory.class);
   protected final Map<S, CacheEntry<R>> cache = new HashMap();
   protected final ResourceFactory<R, S> factory;
   protected volatile boolean isShutdown = false;
   protected final EqualityLock locks = new EqualityLock();

   public CachedResourceFactory(ResourceFactory<R, S> factory) {
      this.factory = factory;
   }

   public R acquire(final S settings) {
      CacheEntry<R> entry = null;
      Object entryLock = null;
      synchronized(this) {
         if (this.isShutdown) {
            throw new IllegalStateException("Attempt to acquire resource from a shutdown factory");
         }

         entry = (CacheEntry)this.cache.get(settings);
         if (entry != null) {
            entry.incRefCount();
            logger.debug("Acquired cached connection (RC={}): {}", entry.getRefCount(), entry.getResource());
            return (Resource)entry.getResource();
         }

         entryLock = this.locks.getLock(settings);
      }

      synchronized(entryLock) {
         label52: {
            Resource var10000;
            synchronized(this) {
               entry = (CacheEntry)this.cache.get(settings);
               if (entry == null) {
                  break label52;
               }

               entry.incRefCount();
               logger.debug("Acquired cached connection (RC={}): {}", entry.getRefCount(), entry.getResource());
               var10000 = (Resource)entry.getResource();
            }

            return var10000;
         }

         R resource = this.factory.acquire(settings);
         Runnable parentCloseHandler = resource.getCloseHandler();
         resource.setCloseHandler(new Runnable() {
            public void run() {
               CachedResourceFactory.this.release(settings);
            }
         });
         entry = new CacheEntry(resource, parentCloseHandler);
         logger.debug("Acquired connection from factory (RC={}): {}", entry.getRefCount(), entry.getResource());
         synchronized(this) {
            this.cache.put(settings, entry);
            this.notify();
         }

         return resource;
      }
   }

   protected synchronized void release(S settings) {
      CacheEntry<R> entry = (CacheEntry)this.cache.get(settings);
      if (entry == null) {
         logger.warn("Not found in cache: " + settings);
      } else {
         entry.decRefCount();
         this.notify();
         logger.debug("Released connection (RC={}): {}", entry.getRefCount(), entry.getResource());
      }
   }

   public R evict(S settings) {
      CacheEntry<R> entry = null;
      synchronized(this) {
         entry = (CacheEntry)this.cache.get(settings);
         if (entry == null) {
            throw new IllegalStateException("Evicting a resource which is not in the cache!");
         }

         if (entry.getRefCount() > 0) {
            logger.debug("Connection won't be evicted, ref-count is {}: {}", entry.getRefCount(), entry.getResource());
            return null;
         }

         this.cache.remove(settings);
         this.locks.evict(settings);
         logger.debug("Evicted connection {} ref-count 0 reached at {}", entry.getResource(), new Date(entry.getLastReleaseTime()));
      }

      if (entry.getParentCloseHandler() != null) {
         logger.trace("Invoking original close handler to close the connection: {}", entry.getResource());

         try {
            entry.getParentCloseHandler().run();
         } catch (Exception var4) {
            logger.warn("Ignoring unsuccessful connection close: {}", entry.getResource(), var4);
         }
      } else {
         logger.trace("No original close handler to invoke: {}", entry.getResource());
      }

      return (Resource)entry.getResource();
   }

   public List<R> evictAll(long releasedBefore) {
      logger.debug("Evicting all entries that have 0 ref-count since " + new Date(releasedBefore));
      List<CacheEntry<R>> evictedEntries = new ArrayList();
      synchronized(this) {
         Iterator it = this.cache.keySet().iterator();

         while(true) {
            if (!it.hasNext()) {
               break;
            }

            S settings = it.next();
            CacheEntry<R> entry = (CacheEntry)this.cache.get(settings);
            if (entry.getRefCount() <= 0 && entry.getLastReleaseTime() <= releasedBefore) {
               logger.trace("Evicting entry: {}", entry);
               evictedEntries.add(entry);
               it.remove();
               this.locks.evict(settings);
            } else {
               logger.trace("Not evicting entry, not applicable: {}", entry);
            }
         }
      }

      List<R> result = new ArrayList();

      CacheEntry entry;
      for(Iterator var11 = evictedEntries.iterator(); var11.hasNext(); result.add((Resource)entry.getResource())) {
         entry = (CacheEntry)var11.next();
         logger.trace("Closing evicted entity: {}", entry);

         try {
            if (entry.getParentCloseHandler() != null) {
               entry.getParentCloseHandler().run();
            }
         } catch (RuntimeException var8) {
            logger.warn("Ignoring unsuccessful resource close: {}", entry, var8);
         }
      }

      logger.debug("Evicted {} entries.", evictedEntries.size());
      return result;
   }

   public synchronized void gc() {
      this.gcImpl();
   }

   protected void gcImpl() {
      long now = System.currentTimeMillis();

      for(long deadline = now + 30000L; now <= deadline; now = System.currentTimeMillis()) {
         List<S> settings = new ArrayList(this.cache.keySet());
         Iterator var7 = settings.iterator();

         while(var7.hasNext()) {
            S setting = (Object)var7.next();
            this.evict(setting);
         }

         if (this.cache.isEmpty()) {
            return;
         }

         try {
            this.wait(deadline - now);
         } catch (InterruptedException var8) {
            logger.warn("Garbage collector interrupted while waiting!");
         }
      }

      logger.warn("Garbage collector was unable to collect " + this.cache.size() + " entries, which are still in use");
   }

   public synchronized void shutdown() {
      logger.debug("Shut-down initiated, evicting all cached entities.");
      this.isShutdown = true;
      this.gcImpl();
   }
}
