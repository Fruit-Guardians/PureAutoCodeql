package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TokenStore {
   private static final Logger logger = LoggerFactory.getLogger(TokenStore.class);
   protected final Map<String, TokenRetriever> settings = new ConcurrentHashMap();
   protected volatile boolean running = true;

   public TokenInfo retrieveTokenInfo(String siteId) {
      TokenRetriever retriever = (TokenRetriever)this.settings.get(siteId);
      if (retriever == null) {
         throw new NoTokenException(siteId);
      } else {
         return retriever.retrieveToken();
      }
   }

   public TokenInfo retrieveDelegatedTokenInfo(String siteId, String delegateTo) {
      TokenRetriever retriever = (TokenRetriever)this.settings.get(siteId);
      if (retriever == null) {
         throw new NoTokenException(siteId);
      } else {
         return retriever.retrieveDelegatedToken(delegateTo);
      }
   }

   public TokenRetriever getRetriever(String siteId) {
      return (TokenRetriever)this.settings.get(siteId);
   }

   public void addSite(String siteId, TokenRetriever tokenRetriever) {
      if (!this.running) {
         throw new IllegalStateException("Cannot add site " + siteId + " because TokenStore is shutdown");
      } else {
         TokenRetriever oldRetriever = (TokenRetriever)this.settings.put(siteId, tokenRetriever);
         logger.debug("Registered a token for site {}: {}", siteId, tokenRetriever);
         if (oldRetriever != null) {
            logger.debug("Releasing overridden token for site {}: {}", siteId, oldRetriever);
            oldRetriever.shutdown();
         }

      }
   }

   public boolean containsTokenFor(String siteId) {
      return this.settings.get(siteId) != null;
   }

   public void shutdown() {
      logger.debug("TokenStore shutdown initiated.");
      this.running = false;
      this.clear();
   }

   public void clear() {
      logger.debug("Releasing all tokens.");
      int releasedTokens = 0;

      while(!this.settings.isEmpty()) {
         TokenRetriever oldRetriever = (TokenRetriever)this.settings.remove(this.settings.keySet().iterator().next());
         if (oldRetriever != null) {
            ++releasedTokens;
            oldRetriever.shutdown();
         }
      }

      logger.debug("Released {} tokens.", releasedTokens);
   }
}
