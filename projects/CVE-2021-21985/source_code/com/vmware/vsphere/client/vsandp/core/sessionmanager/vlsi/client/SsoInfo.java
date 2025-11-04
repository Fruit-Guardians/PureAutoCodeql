package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client;

import com.vmware.vim.binding.sso.version.version2;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.ClientCertificate;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.HttpSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.SingleThumbprintVerifier;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.LookupSvcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.LookupSvcFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.ServiceEndpoint;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.SsoAdminConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.SsoAdminFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.SsoEndpoints;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.Map;

public class SsoInfo {
   protected final ResourceFactory<LookupSvcConnection, VlsiSettings> lsFactory;
   protected final ResourceFactory<SsoAdminConnection, VlsiSettings> ssoFactory;
   protected final VlsiSettings lsSettings;
   protected final VlsiSettings ssoSettings;
   protected final ServiceEndpoint stsEndpoint;
   protected final ServiceEndpoint ssoAdminEndpoint;
   protected final X509Certificate[] stsCerts;

   public SsoInfo(VlsiSettings lsSettings) {
      this(new LookupSvcFactory(), new SsoAdminFactory(), lsSettings);
   }

   public SsoInfo(ResourceFactory<LookupSvcConnection, VlsiSettings> lsFactory, ResourceFactory<SsoAdminConnection, VlsiSettings> ssoFactory, VlsiSettings lsSettings) {
      this.lsFactory = lsFactory;
      this.ssoFactory = ssoFactory;
      this.lsSettings = lsSettings;
      Throwable var4 = null;
      Object var5 = null;

      try {
         LookupSvcConnection conn = (LookupSvcConnection)lsFactory.acquire(lsSettings);

         try {
            SsoEndpoints endpoints = conn.getSsoEndpoints();
            this.stsEndpoint = endpoints.getSts();
            this.ssoAdminEndpoint = endpoints.getAdmin();
         } finally {
            if (conn != null) {
               conn.close();
            }

         }
      } catch (Throwable var25) {
         if (var4 == null) {
            var4 = var25;
         } else if (var4 != var25) {
            var4.addSuppressed(var25);
         }

         throw var4;
      }

      this.ssoSettings = this.mkAdminSettings(this.ssoAdminEndpoint, lsSettings);
      var4 = null;
      var5 = null;

      try {
         SsoAdminConnection ssoAdmin = (SsoAdminConnection)ssoFactory.acquire(this.ssoSettings);

         try {
            this.stsCerts = ssoAdmin.getSigningCerts();
         } finally {
            if (ssoAdmin != null) {
               ssoAdmin.close();
            }

         }

      } catch (Throwable var27) {
         if (var4 == null) {
            var4 = var27;
         } else if (var4 != var27) {
            var4.addSuppressed(var27);
         }

         throw var4;
      }
   }

   public ResourceFactory<LookupSvcConnection, VlsiSettings> getLsFactory() {
      return this.lsFactory;
   }

   public ResourceFactory<SsoAdminConnection, VlsiSettings> getSsoFactory() {
      return this.ssoFactory;
   }

   public VlsiSettings getLsSettings() {
      return this.lsSettings;
   }

   public VlsiSettings getSsoSettings() {
      return this.ssoSettings;
   }

   public ServiceEndpoint getStsEndpoint() {
      return this.stsEndpoint;
   }

   public ServiceEndpoint getSsoAdminEndpoint() {
      return this.ssoAdminEndpoint;
   }

   public X509Certificate[] getStsCerts() {
      return this.stsCerts;
   }

   public SsoInfo refresh() {
      return new SsoInfo(this.lsFactory, this.ssoFactory, this.lsSettings);
   }

   protected VlsiSettings mkAdminSettings(ServiceEndpoint ssoAdminEndpoint, VlsiSettings lsSettings) {
      URI uri = ssoAdminEndpoint.getUri();
      HttpSettings lsHttpSettings = lsSettings.getHttpSettings();
      HttpSettings httpSettings = new HttpSettings(uri.getScheme(), uri.getHost(), uri.getPort(), uri.getPath(), (String)null, (String)null, -1, lsHttpSettings.getMaxConn(), lsHttpSettings.getTimeout(), (ClientCertificate)null, (ClientCertificate)null, new SingleThumbprintVerifier(ssoAdminEndpoint.getThumbprint()), lsHttpSettings.getExecutorFactory(), lsHttpSettings.getExecutorSettings(), version2.class, lsSettings.getHttpSettings().getVmodlContext(), (Map)null);
      return new VlsiSettings(lsSettings.getHttpFactory(), httpSettings, new Authenticator(), (String)null);
   }
}
