package com.vmware.vsphere.client.vsan.base.cache;

import com.vmware.vise.security.ClientSessionEndListener;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class TimeBasedCacheManager<K, V extends Cacheable<V>> implements ClientSessionEndListener {
   private static final Log _logger = LogFactory.getLog(TimeBasedCacheManager.class);
   private final int expirationTimeMin;
   private final int expirationTimeMax;
   private final int trustPeriod;
   private final int cleanThreshold;
   private ConcurrentHashMap<String, ConcurrentHashMap<String, TimeBasedCacheEntry<V>>> _sessionMap = new ConcurrentHashMap();

   public TimeBasedCacheManager(int expirationTimeMin, int expirationTimeMax, int trustPeriod, int cleanThreshold) {
      Validate.isTrue(expirationTimeMin > 0);
      Validate.isTrue(expirationTimeMax > expirationTimeMin);
      Validate.isTrue(trustPeriod >= 0);
      Validate.isTrue(cleanThreshold > 0);
      this.expirationTimeMin = expirationTimeMin;
      this.expirationTimeMax = expirationTimeMax;
      this.trustPeriod = trustPeriod;
      this.cleanThreshold = cleanThreshold;
   }

   protected V get(K keyObj, TimeBasedCacheManager.CacheType type) {
      ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();

      Cacheable var7;
      try {
         Thread.currentThread().setContextClassLoader(TimeBasedCacheManager.class.getClassLoader());
         this.clean();
         String key = this.getKey(keyObj, type);
         if (StringUtils.isEmpty(key)) {
            _logger.warn("Cannot generate a key from object: " + keyObj);
            return null;
         }

         TimeBasedCacheEntry<V> result = this.getCacheEntry(key, keyObj, type);
         if (result == null) {
            return null;
         }

         var7 = result.get();
      } finally {
         Thread.currentThread().setContextClassLoader(oldClassLoader);
      }

      return var7;
   }

   protected abstract String sessionKey();

   protected abstract String getKey(K var1, TimeBasedCacheManager.CacheType var2);

   protected abstract TimeBasedCacheEntry<V> createEntry(K var1, TimeBasedCacheManager.CacheType var2);

   private TimeBasedCacheEntry<V> getCacheEntry(String key, K keyObj, TimeBasedCacheManager.CacheType type) {
      ConcurrentHashMap<String, TimeBasedCacheEntry<V>> cache = this.getSessionCache();
      TimeBasedCacheEntry<V> result = (TimeBasedCacheEntry)cache.get(key);
      if (result == null) {
         TimeBasedCacheEntry<V> newEntity = this.createEntry(keyObj, type);
         if (newEntity == null) {
            _logger.warn("Cannot create a cache entry for key object: " + keyObj);
            return null;
         }

         int expirationTime = this.calculateExpirationTime();
         newEntity.setExpirationTime(expirationTime);
         newEntity.setTrustPeriod(this.trustPeriod);
         result = (TimeBasedCacheEntry)cache.putIfAbsent(key, newEntity);
         if (result == null) {
            _logger.debug("Cache entry created: {" + key + "} => {" + newEntity + "}");
            result = newEntity;
         }
      }

      return result;
   }

   private ConcurrentHashMap<String, TimeBasedCacheEntry<V>> getSessionCache() {
      ConcurrentHashMap<String, TimeBasedCacheEntry<V>> cache = (ConcurrentHashMap)this._sessionMap.get(this.sessionKey());
      if (cache == null) {
         ConcurrentHashMap<String, TimeBasedCacheEntry<V>> newCache = new ConcurrentHashMap();
         cache = (ConcurrentHashMap)this._sessionMap.putIfAbsent(this.sessionKey(), newCache);
         if (cache == null) {
            _logger.debug("Session entry created: {" + this.sessionKey() + "} => {" + newCache + "}");
            cache = newCache;
         }
      }

      return cache;
   }

   private int calculateExpirationTime() {
      return ThreadLocalRandom.current().nextInt(this.expirationTimeMin, this.expirationTimeMax);
   }

   public void sessionEnded(String clientId) {
      if (this._sessionMap.containsKey(clientId)) {
         this._sessionMap.remove(clientId);
      }

   }

   public void shutdown() {
      this._sessionMap.clear();
   }

   private void clean() {
      ConcurrentHashMap<String, TimeBasedCacheEntry<V>> cache = (ConcurrentHashMap)this._sessionMap.get(this.sessionKey());
      if (cache != null && cache.size() >= this.cleanThreshold) {
         synchronized(cache) {
            Iterator var4 = cache.entrySet().iterator();

            while(var4.hasNext()) {
               Entry<String, TimeBasedCacheEntry<V>> entry = (Entry)var4.next();
               String key = (String)entry.getKey();
               TimeBasedCacheEntry<V> value = (TimeBasedCacheEntry)entry.getValue();
               if (value.isExpired()) {
                  cache.remove(key);
               }
            }

         }
      }
   }

   protected interface CacheType {
   }
}
