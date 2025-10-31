package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore;

import com.vmware.vim.sso.client.DefaultTokenFactory;
import com.vmware.vim.sso.client.SamlToken;
import com.vmware.vim.sso.client.exception.InvalidTokenException;
import com.vmware.vise.usersession.UserSessionService;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.LookupSvcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.ServiceEndpoint;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.SsoAdminConnection;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class NgcTokenRetriever extends AbstractTokenRetriever {
   private UserSessionService userSessionService;

   public NgcTokenRetriever(PrivateKey privateKey, X509Certificate cert, VlsiSettings lsSettings, ResourceFactory<LookupSvcConnection, VlsiSettings> lsFactory, ResourceFactory<SsoAdminConnection, VlsiSettings> adminFactory, UserSessionService userSessionService) {
      super(privateKey, cert, lsSettings, lsFactory, adminFactory);
      this.userSessionService = userSessionService;
   }

   public TokenInfo retrieveToken() {
      String samlTokenXml = this.userSessionService.getUserSession().samlTokenXml;
      SamlToken hokToken = null;

      try {
         Throwable var3 = null;
         Object var4 = null;

         try {
            LookupSvcConnection lsConnection = (LookupSvcConnection)this.lsFactory.acquire(this.lsSettings);

            try {
               ServiceEndpoint ssoAdminEndpoint = lsConnection.getAdmin();
               VlsiSettings adminSettings = mkAdminSettings(ssoAdminEndpoint, this.lsSettings);
               Throwable var9 = null;
               Object var10 = null;

               X509Certificate[] stsCerts;
               try {
                  SsoAdminConnection ssoAdmin = (SsoAdminConnection)this.adminFactory.acquire(adminSettings);

                  try {
                     stsCerts = ssoAdmin.getSigningCerts();
                  } finally {
                     if (ssoAdmin != null) {
                        ssoAdmin.close();
                     }

                  }
               } catch (Throwable var33) {
                  if (var9 == null) {
                     var9 = var33;
                  } else if (var9 != var33) {
                     var9.addSuppressed(var33);
                  }

                  throw var9;
               }

               hokToken = DefaultTokenFactory.createToken(samlTokenXml, stsCerts);
            } finally {
               if (lsConnection != null) {
                  lsConnection.close();
               }

            }
         } catch (Throwable var35) {
            if (var3 == null) {
               var3 = var35;
            } else if (var3 != var35) {
               var3.addSuppressed(var35);
            }

            throw var3;
         }
      } catch (InvalidTokenException var36) {
         throw new IllegalStateException("Failed to deserialize token!", var36);
      }

      return new TokenInfo(this.privateKey, hokToken);
   }

   public void shutdown() {
   }
}
