package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.tokenstore;

import com.vmware.vim.binding.sso.version.version1;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.ClientCertificate;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.Authenticator;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.HttpSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.SingleThumbprintVerifier;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.HokStsService;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.LookupSvcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.ServiceEndpoint;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.SsoAdminConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.StsService;
import java.net.URI;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;

public abstract class AbstractTokenRetriever implements TokenRetriever {
   protected final PrivateKey privateKey;
   protected final X509Certificate certificate;
   protected final VlsiSettings lsSettings;
   protected final ResourceFactory<LookupSvcConnection, VlsiSettings> lsFactory;
   protected final ResourceFactory<SsoAdminConnection, VlsiSettings> adminFactory;

   public AbstractTokenRetriever(PrivateKey privateKey, X509Certificate cert, VlsiSettings lsSettings, ResourceFactory<LookupSvcConnection, VlsiSettings> lsFactory, ResourceFactory<SsoAdminConnection, VlsiSettings> adminFactory) {
      this.privateKey = privateKey;
      this.certificate = cert;
      this.lsSettings = lsSettings;
      this.lsFactory = lsFactory;
      this.adminFactory = adminFactory;
   }

   public TokenInfo retrieveDelegatedToken(String param1) {
      // $FF: Couldn't be decompiled
   }

   protected static StsService getSts(PrivateKey privateKey, X509Certificate cert, LookupSvcConnection conn, ResourceFactory<SsoAdminConnection, VlsiSettings> adminFactory, VlsiSettings lsSettings) {
      ServiceEndpoint stsEndpoint = conn.getSts();
      ServiceEndpoint ssoAdminEndpoint = conn.getAdmin();
      VlsiSettings adminSettings = mkAdminSettings(ssoAdminEndpoint, lsSettings);
      Throwable var9 = null;
      Object var10 = null;

      X509Certificate[] stsCerts;
      try {
         SsoAdminConnection ssoAdmin = (SsoAdminConnection)adminFactory.acquire(adminSettings);

         try {
            stsCerts = ssoAdmin.getSigningCerts();
         } finally {
            if (ssoAdmin != null) {
               ssoAdmin.close();
            }

         }
      } catch (Throwable var17) {
         if (var9 == null) {
            var9 = var17;
         } else if (var9 != var17) {
            var9.addSuppressed(var17);
         }

         throw var9;
      }

      return (StsService)(privateKey == null ? new StsService(stsEndpoint, stsCerts) : new HokStsService(stsEndpoint, stsCerts, privateKey, cert));
   }

   protected static VlsiSettings mkAdminSettings(ServiceEndpoint ssoAdminEndpoint, VlsiSettings lsSettings) {
      URI uri = ssoAdminEndpoint.getUri();
      HttpSettings lsHttpSettings = lsSettings.getHttpSettings();
      HttpSettings httpSettings = new HttpSettings(uri.getScheme(), uri.getHost(), uri.getPort(), uri.getPath(), (String)null, (String)null, -1, lsHttpSettings.getMaxConn(), lsHttpSettings.getTimeout(), (ClientCertificate)null, (ClientCertificate)null, new SingleThumbprintVerifier(ssoAdminEndpoint.getThumbprint()), lsHttpSettings.getExecutorFactory(), lsHttpSettings.getExecutorSettings(), version1.class, lsSettings.getHttpSettings().getVmodlContext(), Collections.emptyMap());
      return new VlsiSettings(lsSettings.getHttpFactory(), httpSettings, new Authenticator(), (String)null);
   }
}
