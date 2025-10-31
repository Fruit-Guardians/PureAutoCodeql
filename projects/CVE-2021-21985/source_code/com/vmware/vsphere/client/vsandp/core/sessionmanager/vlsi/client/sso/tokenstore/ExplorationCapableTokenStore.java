package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore;

import com.vmware.vim.binding.lookup.ServiceRegistration;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.LookupSvcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.LookupSvcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcLsExplorer;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcRegistration;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExplorationCapableTokenStore extends TokenStore {
   private static final Logger logger = LoggerFactory.getLogger(ExplorationCapableTokenStore.class);
   private final TokenRetriever localSsoTokenRetriever;
   private final LookupSvcClient lsClient;

   public ExplorationCapableTokenStore(TokenRetriever localSsoTokenRetriever, LookupSvcClient lsClient) {
      this.localSsoTokenRetriever = localSsoTokenRetriever;
      this.lsClient = lsClient;
      this.exploreAndAuthenticateLocalSites();
   }

   public TokenInfo retrieveTokenInfo(String siteId) {
      this.exploreAndAuthenticateLocalSiteIfNotFound(siteId);
      return super.retrieveTokenInfo(siteId);
   }

   public TokenInfo retrieveDelegatedTokenInfo(String siteId, String delegateTo) {
      this.exploreAndAuthenticateLocalSiteIfNotFound(siteId);
      return super.retrieveDelegatedTokenInfo(siteId, delegateTo);
   }

   public TokenRetriever getRetriever(String siteId) {
      this.exploreAndAuthenticateLocalSiteIfNotFound(siteId);
      return super.getRetriever(siteId);
   }

   public boolean containsTokenFor(String siteId) {
      this.exploreAndAuthenticateLocalSiteIfNotFound(siteId);
      return super.containsTokenFor(siteId);
   }

   private void exploreAndAuthenticateLocalSiteIfNotFound(String vcUuid) {
      if (this.settings.get(vcUuid) == null) {
         UUID key = UUID.fromString(vcUuid);
         logger.warn("No token for site: {}. Will explore the local SSO for VC registration with that UUID.", key);
         Throwable var3 = null;
         Object var4 = null;

         try {
            LookupSvcConnection lsConnection = this.lsClient.getConnection();

            label248: {
               Throwable var10000;
               label251: {
                  boolean var10001;
                  try {
                     ServiceRegistration serviceRegistry = lsConnection.getServiceRegistration();
                     Map<UUID, VcRegistration> vcMap = (new VcLsExplorer(serviceRegistry)).map();
                     if (!vcMap.containsKey(key)) {
                        break label248;
                     }

                     VcRegistration vcRegistration = (VcRegistration)vcMap.get(key);
                     this.addSite(vcRegistration.getUuid().toString(), this.localSsoTokenRetriever);
                     logger.debug("Exploration found new VC site in the local SSO: {}", key);
                  } catch (Throwable var19) {
                     var10000 = var19;
                     var10001 = false;
                     break label251;
                  }

                  if (lsConnection != null) {
                     lsConnection.close();
                  }

                  label226:
                  try {
                     return;
                  } catch (Throwable var18) {
                     var10000 = var18;
                     var10001 = false;
                     break label226;
                  }
               }

               var3 = var10000;
               if (lsConnection != null) {
                  lsConnection.close();
               }

               throw var3;
            }

            if (lsConnection != null) {
               lsConnection.close();
            }
         } catch (Throwable var20) {
            if (var3 == null) {
               var3 = var20;
            } else if (var3 != var20) {
               var3.addSuppressed(var20);
            }

            throw var3;
         }

         logger.warn("Exploration failed to locate local site with UUID: {}", key);
      }
   }

   private void exploreAndAuthenticateLocalSites() {
      Throwable var1 = null;
      Object var2 = null;

      try {
         LookupSvcConnection connection = this.lsClient.getConnection();

         try {
            ServiceRegistration ls = connection.getServiceRegistration();
            Iterator var6 = (new VcLsExplorer(ls)).list().iterator();

            while(var6.hasNext()) {
               VcRegistration vcReg = (VcRegistration)var6.next();
               this.addSite(vcReg.getUuid().toString().toLowerCase(), this.localSsoTokenRetriever);
               logger.debug("Registered token for local VC: {}, retriever: {}", vcReg.getUuid(), this.localSsoTokenRetriever);
            }
         } finally {
            if (connection != null) {
               connection.close();
            }

         }

      } catch (Throwable var12) {
         if (var1 == null) {
            var1 = var12;
         } else if (var1 != var12) {
            var1.addSuppressed(var12);
         }

         throw var1;
      }
   }
}
