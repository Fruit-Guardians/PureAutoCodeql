package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.executor;

import java.util.concurrent.TimeUnit;

public class ExecutorSettings {
   protected final int initialThreads;
   protected final int maxThreads;
   protected final long keepAliveTime;
   protected final TimeUnit keepAliveUnit;

   public ExecutorSettings(int initialThreads, int maxThreads) {
      this(initialThreads, maxThreads, 30L, TimeUnit.SECONDS);
   }

   public ExecutorSettings(int initialThreads, int maxThreads, long keepAliveTime, TimeUnit keepAliveUnit) {
      this.initialThreads = initialThreads;
      this.maxThreads = maxThreads;
      this.keepAliveTime = keepAliveTime;
      this.keepAliveUnit = keepAliveUnit;
   }

   public int getInitialThreads() {
      return this.initialThreads;
   }

   public int getMaxThreads() {
      return this.maxThreads;
   }

   public long getKeepAliveTime() {
      return this.keepAliveTime;
   }

   public TimeUnit getKeepAliveUnit() {
      return this.keepAliveUnit;
   }

   public int hashCode() {
      return (int)((long)(this.initialThreads + 43 * this.maxThreads) + 43L * this.keepAliveUnit.toMillis(this.keepAliveTime));
   }

   public boolean equals(Object obj) {
      if (obj != null && obj instanceof ExecutorSettings) {
         ExecutorSettings other = (ExecutorSettings)obj;
         if (this.initialThreads == other.initialThreads && this.maxThreads == other.maxThreads && this.keepAliveTime == other.keepAliveTime) {
            return this.keepAliveUnit == null && other.keepAliveUnit == null || this.keepAliveUnit != null && other.keepAliveUnit != null && this.keepAliveUnit.equals(other.keepAliveUnit);
         } else {
            return false;
         }
      } else {
         return false;
      }
   }

   public String toString() {
      return String.format("ExecutorSettings [initialThreads=%s, maxThreads=%s, keepAliveTime=%s, keepAliveUnit=%s]", this.initialThreads, this.maxThreads, this.keepAliveTime, this.keepAliveUnit);
   }
}
