package com.vmware.vsphere.client.vsan.base.cache;

import java.util.Date;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class TimeBasedCacheEntry<T extends Cacheable<T>> {
   private int expirationTime;
   private int trustPeriod;
   private long lastTimeValidated;
   private long lastTimeUpdated;
   private volatile T value;
   private String lastValidationToken;
   private static final Log logger = LogFactory.getLog(TimeBasedCacheEntry.class);

   public synchronized T get() {
      if (!this.isValid() || this.isExpired() || this.value == null) {
         this.value = this.load();
         this.lastTimeUpdated = this.lastTimeValidated = now();
      }

      return (Cacheable)this.value.clone();
   }

   protected abstract T load();

   protected abstract String getValidationToken();

   public boolean isExpired() {
      return now() - (long)this.expirationTime > this.lastTimeUpdated;
   }

   public void setExpirationTime(int expirationTime) {
      this.expirationTime = expirationTime;
   }

   public void setTrustPeriod(int trustPeriod) {
      this.trustPeriod = trustPeriod;
   }

   private synchronized boolean isValid() {
      long now = now();
      if (now - (long)this.trustPeriod < this.lastTimeValidated) {
         return true;
      } else {
         try {
            this.lastTimeValidated = now;
            String newValidationToken = this.getValidationToken();
            if (this.lastValidationToken == null) {
               this.lastValidationToken = newValidationToken;
               return newValidationToken == null;
            } else {
               boolean isSameToken = this.lastValidationToken.equals(newValidationToken);
               this.lastValidationToken = newValidationToken;
               return isSameToken;
            }
         } catch (Exception var5) {
            logger.error("Unable to get the validation token - invalidating the value", var5);
            return false;
         }
      }
   }

   private static long now() {
      return (new Date()).getTime();
   }
}
