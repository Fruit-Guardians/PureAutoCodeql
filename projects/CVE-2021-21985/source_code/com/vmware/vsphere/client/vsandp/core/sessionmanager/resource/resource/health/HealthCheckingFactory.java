package com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.health;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.CacheEntry;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.CachedResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.Resource;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.CheckedRunnable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class HealthCheckingFactory<R extends Resource, S> extends CachedResourceFactory<R, S> {
   private static final Log logger = LogFactory.getLog(HealthCheckingFactory.class);
   protected final ScheduledExecutorService scheduler;
   protected final long timeout;
   protected final IHealthMonitor<R, ? super S> monitor;
   protected final ScheduledFuture<?> pinger;
   protected final ScheduledFuture<?> evictor;

   public HealthCheckingFactory(final ResourceFactory<R, S> parentFactory, final IHealthMonitor<R, ? super S> monitor, ScheduledExecutorService scheduler, long delay, long timeout, final long retention) {
      super(new ResourceFactory<R, S>() {
         public R acquire(final S settings) {
            final Resource resource;
            Exception e;
            try {
               resource = parentFactory.acquire(settings);
            } catch (Exception var8) {
               e = var8;

               try {
                  monitor.onError((Resource)null, settings, e);
               } catch (Exception var5) {
                  HealthCheckingFactory.logger.warn("onError failure", var5);
               }

               throw var8;
            }

            try {
               monitor.onCreated(resource, settings);
            } catch (Exception var7) {
               e = var7;

               try {
                  HealthCheckingFactory.logger.warn("Closing resource due to an onCreated handler failure", e);
                  resource.close();
               } catch (Exception var6) {
                  HealthCheckingFactory.logger.warn("Could not dispose of resource", var6);
               }

               throw var7;
            }

            final Runnable originalCloseHandler = resource.getCloseHandler();
            resource.setCloseHandler(new Runnable() {
               public void run() {
                  if (originalCloseHandler != null) {
                     originalCloseHandler.run();
                  }

                  monitor.onDisposed(resource, settings);
               }
            });
            return resource;
         }
      });
      this.scheduler = scheduler;
      this.timeout = timeout;
      this.monitor = monitor;
      this.pinger = scheduler.scheduleWithFixedDelay(new Runnable() {
         public void run() {
            HealthCheckingFactory.this.checkEntries();
         }
      }, delay, delay, TimeUnit.MILLISECONDS);
      if (retention > 0L) {
         this.evictor = scheduler.scheduleWithFixedDelay(new Runnable() {
            public void run() {
               HealthCheckingFactory.this.evictAll(System.currentTimeMillis() - retention);
            }
         }, retention, retention, TimeUnit.MILLISECONDS);
      } else {
         this.evictor = null;
      }

   }

   public void checkEntries() {
      try {
         this.checkEntriesImpl();
      } catch (Exception var2) {
         CheckedRunnable.handle(var2);
      }

   }

   protected void checkEntriesImpl() {
      List<HealthCheckingFactory<R, S>.CheckInProgress> checks = new ArrayList();
      Map<S, CacheEntry<R>> snapshot = new HashMap();
      synchronized(this) {
         snapshot.putAll(this.cache);
      }

      Iterator var4 = snapshot.entrySet().iterator();

      while(var4.hasNext()) {
         Entry entry = (Entry)var4.next();

         try {
            final S settings = entry.getKey();
            final R resource = (Resource)((CacheEntry)entry.getValue()).getResource();
            checks.add(new HealthCheckingFactory.CheckInProgress(settings, this.scheduler.submit(new Runnable() {
               public void run() {
                  try {
                     HealthCheckingFactory.this.monitor.check(resource, settings);
                  } catch (RuntimeException var2) {
                     throw var2;
                  } catch (Exception var3) {
                     throw new RuntimeException("Health-check failed.", var3);
                  }
               }
            })));
         } catch (RejectedExecutionException var27) {
            logger.warn("Could not schedule check for entry " + entry.getKey(), var27);
            return;
         }
      }

      List<HealthCheckingFactory<R, S>.BrokenEntry> brokenEntries = new ArrayList();
      long timeout = 30000L;
      long deadline = System.currentTimeMillis() + timeout;
      Iterator var9 = checks.iterator();

      while(var9.hasNext()) {
         HealthCheckingFactory.CheckInProgress check = (HealthCheckingFactory.CheckInProgress)var9.next();

         try {
            logger.debug(String.format("Awaiting check for %s, with timeout %s", check.getSettings(), timeout));
            check.getFuture().get(timeout, TimeUnit.MILLISECONDS);
            logger.debug("Resource is healthy " + check.getSettings());
         } catch (InterruptedException var24) {
            logger.debug("Interrupted while checking resource: " + check.getSettings(), var24.getCause());
            brokenEntries.add(new HealthCheckingFactory.BrokenEntry(check.getSettings(), var24.getCause()));
         } catch (ExecutionException var25) {
            logger.debug("Resource is broken: " + check.getSettings(), var25.getCause());
            brokenEntries.add(new HealthCheckingFactory.BrokenEntry(check.getSettings(), var25.getCause()));
         } catch (TimeoutException var26) {
            logger.debug("Timeout while waiting for health check for resource: " + check.getSettings());
            brokenEntries.add(new HealthCheckingFactory.BrokenEntry(check.getSettings(), var26.getCause()));
         } finally {
            timeout = deadline - System.currentTimeMillis();
            if (timeout < 0L) {
               timeout = 0L;
            }

         }
      }

      var9 = brokenEntries.iterator();

      while(var9.hasNext()) {
         HealthCheckingFactory<R, S>.BrokenEntry brokenEntry = (HealthCheckingFactory.BrokenEntry)var9.next();
         CacheEntry entry = (CacheEntry)this.cache.get(brokenEntry.settings);

         try {
            this.monitor.onError(entry == null ? null : (Resource)entry.getResource(), brokenEntry.settings, brokenEntry.error);
         } catch (Exception var23) {
            logger.warn("onError failure", var23);
         }
      }

      List<Runnable> closeHandlers = new ArrayList();
      synchronized(this) {
         Iterator var11 = brokenEntries.iterator();

         while(var11.hasNext()) {
            HealthCheckingFactory<R, S>.BrokenEntry brokenEntry = (HealthCheckingFactory.BrokenEntry)var11.next();
            CacheEntry<R> entry = (CacheEntry)this.cache.get(brokenEntry.settings);
            if (entry != null) {
               if (entry.getRefCount() > 0) {
                  logger.debug(String.format("Evicting broken resource: %s, with non-zero refcount: %s", entry.getResource(), entry.getRefCount()));
               }

               this.cache.remove(brokenEntry.settings);
               this.locks.evict(brokenEntry.settings);
               if (entry.getParentCloseHandler() != null) {
                  closeHandlers.add(entry.getParentCloseHandler());
               }

               logger.debug("Evicted broken resource " + entry.getResource());
            }
         }

         this.notify();
      }

      Iterator var38 = closeHandlers.iterator();

      while(var38.hasNext()) {
         Runnable closeHandler = (Runnable)var38.next();

         try {
            closeHandler.run();
         } catch (Exception var22) {
            logger.warn("Ignoring unsuccessful disposal: {}", var22);
         }
      }

   }

   public synchronized void shutdown() {
      this.pinger.cancel(false);
      if (this.evictor != null) {
         this.evictor.cancel(false);
      }

      super.shutdown();
   }

   protected class BrokenEntry {
      public final S settings;
      public Throwable error;

      public BrokenEntry(S settings, Throwable error) {
         this.settings = settings;
         this.error = error;
      }
   }

   protected class CheckInProgress {
      protected S settings;
      protected Future<?> future;

      public CheckInProgress(S settings, Future<?> future) {
         this.settings = settings;
         this.future = future;
      }

      public S getSettings() {
         return this.settings;
      }

      public Future<?> getFuture() {
         return this.future;
      }
   }
}
