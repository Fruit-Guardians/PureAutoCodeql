package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.pbm;

import com.vmware.vim.binding.lookup.ServiceRegistration;
import com.vmware.vim.binding.pbm.version.version11;
import com.vmware.vim.vmomi.client.http.ThumbprintVerifier;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.util.ClientCertificate;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiExploratorySettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.LookupSvcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.PbmLsExplorer;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.PbmRegistration;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcLsExplorer;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcRegistration;

public class PbmExploratoryFactory implements ResourceFactory<PbmConnection, VlsiExploratorySettings> {
   private final ResourceFactory<PbmConnection, VlsiSettings> pbmFactory;

   public PbmExploratoryFactory(ResourceFactory<PbmConnection, VlsiSettings> pbmFactory) {
      this.pbmFactory = pbmFactory;
   }

   public PbmConnection acquire(VlsiExploratorySettings settings) {
      Throwable var3 = null;
      Object var4 = null;

      VlsiSettings vlsiSettings;
      try {
         LookupSvcConnection lsConnection = (LookupSvcConnection)settings.getLookupSvcFactory().acquire(settings.getLookupSvcSettings());

         try {
            ServiceRegistration svcReg = lsConnection.getServiceRegistration();
            VcRegistration vcReg = (VcRegistration)(new VcLsExplorer(svcReg)).get(settings.getServiceUuid());
            PbmRegistration pbmReg = (PbmRegistration)(new PbmLsExplorer(svcReg)).get(vcReg.getVpxdUuid());
            ClientCertificate keyStore = new ClientCertificate(pbmReg.getUuid().toString(), pbmReg.getSslTrust(), "", "", pbmReg.getUuid().toString());
            vlsiSettings = settings.getServiceSettingsTemplate().setServiceInfo(pbmReg.getServiceUrl(), version11.class).setSslContext(keyStore, (ThumbprintVerifier)null);
         } finally {
            if (lsConnection != null) {
               lsConnection.close();
            }

         }
      } catch (Throwable var15) {
         if (var3 == null) {
            var3 = var15;
         } else if (var3 != var15) {
            var3.addSuppressed(var15);
         }

         throw var3;
      }

      return (PbmConnection)this.pbmFactory.acquire(vlsiSettings);
   }
}
