package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso;

import com.vmware.vim.binding.sso.admin.ConfigurationManagementService;
import com.vmware.vim.binding.sso.admin.ServiceContent;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiConnection;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

public class SsoAdminConnection extends VlsiConnection {
   protected volatile ServiceContent content;

   public ServiceContent getContent() {
      return this.content;
   }

   public X509Certificate[] getSigningCerts() {
      ConfigurationManagementService cms = (ConfigurationManagementService)this.createStub(ConfigurationManagementService.class, this.content.getConfigurationManagementService());

      try {
         return CertificateHelper.getCerts(cms.getTrustedCertificates());
      } catch (CertificateException var3) {
         throw new SsoException("Unable to retrieve trusted certificates from SSO Admin Service", var3);
      }
   }
}
