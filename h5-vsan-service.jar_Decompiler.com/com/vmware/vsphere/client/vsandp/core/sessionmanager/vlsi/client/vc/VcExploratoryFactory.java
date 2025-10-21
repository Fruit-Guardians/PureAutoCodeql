package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc;

import com.vmware.vim.binding.lookup.ServiceRegistration;
import com.vmware.vim.vmomi.client.http.ThumbprintVerifier;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.ClientCertificate;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.VersionService;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiExploratorySettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.LookupSvcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcLsExplorer;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcRegistration;
import java.net.URI;
import org.springframework.beans.factory.annotation.Autowired;

public class VcExploratoryFactory implements ResourceFactory<VcConnection, VlsiExploratorySettings> {
   private final ResourceFactory<VcConnection, VlsiSettings> vcFactory;
   @Autowired
   private VersionService versionService;

   public VcExploratoryFactory(ResourceFactory<VcConnection, VlsiSettings> vcFactory) {
      this.vcFactory = vcFactory;
   }

   public VcConnection acquire(VlsiExploratorySettings settings) {
      Throwable var3 = null;
      Object var4 = null;

      VlsiSettings vlsiSettings;
      try {
         LookupSvcConnection lsConnection = (LookupSvcConnection)settings.getLookupSvcFactory().acquire(settings.getLookupSvcSettings());

         try {
            ServiceRegistration svcReg = lsConnection.getServiceRegistration();
            VcRegistration vcReg = (VcRegistration)(new VcLsExplorer(svcReg)).get(settings.getServiceUuid());
            ClientCertificate keyStore = new ClientCertificate(vcReg.getUuid().toString(), vcReg.getSslTrust(), "", "", vcReg.getUuid().toString());
            vlsiSettings = settings.getServiceSettingsTemplate().setServiceInfo(vcReg.getServiceUrl(), this.getVmodlVerion(vcReg.getServiceUrl())).setSslContext(keyStore, (ThumbprintVerifier)null);
         } finally {
            if (lsConnection != null) {
               lsConnection.close();
            }

         }
      } catch (Throwable var14) {
         if (var3 == null) {
            var3 = var14;
         } else if (var3 != var14) {
            var3.addSuppressed(var14);
         }

         throw var3;
      }

      return (VcConnection)this.vcFactory.acquire(vlsiSettings);
   }

   protected Class<?> getVmodlVerion(URI vcAddress) {
      return this.versionService.getVimVmodlVersion(vcAddress.toString());
   }
}
