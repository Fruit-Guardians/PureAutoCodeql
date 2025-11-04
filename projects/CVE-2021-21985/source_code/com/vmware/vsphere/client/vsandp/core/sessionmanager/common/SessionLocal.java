package com.vmware.vsphere.client.vsandp.core.sessionmanager.common;

import java.util.Iterator;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SessionLocal<T> {
   private static final Logger logger = LoggerFactory.getLogger(SessionLocal.class);
   private ConcurrentHashMap<String, T> sessionContext = new ConcurrentHashMap();

   protected T get() {
      String key = this.sessionKey();
      T result = this.sessionContext.get(key);
      if (result == null) {
         T newEntity = this.create();
         result = this.sessionContext.putIfAbsent(key, newEntity);
         if (result == null) {
            logger.debug("Session entry created: {} => {}", key, newEntity);
            result = newEntity;
         } else {
            this.destroy(newEntity);
         }
      }

      return result;
   }

   protected void remove(String clientId) {
      try {
         String key = clientId != null ? clientId : this.sessionKey();
         T removedEntity = this.sessionContext.remove(key);
         if (removedEntity != null) {
            this.destroy(removedEntity);
            logger.debug("Session entry dropped: {} => {}", key, removedEntity);
         }
      } catch (Exception var4) {
         logger.error("Failed to clear client's session context: {}", this, var4);
      }

   }

   protected void clear() {
      logger.debug("Dropping all session entries: {}", this.sessionContext.size());
      Iterator iterator = this.sessionContext.entrySet().iterator();

      while(iterator.hasNext()) {
         Entry<String, T> entry = (Entry)iterator.next();
         iterator.remove();
         this.destroy(entry.getValue());
         logger.debug("Session entry dropped: {} => {}", entry.getKey(), entry.getValue());
      }

   }

   protected abstract String sessionKey();

   protected abstract T create();

   protected abstract void destroy(T var1);
}
