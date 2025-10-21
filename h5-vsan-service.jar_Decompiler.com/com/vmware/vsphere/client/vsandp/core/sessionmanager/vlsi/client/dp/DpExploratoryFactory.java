package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.dp;

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
import java.net.URISyntaxException;
import org.springframework.beans.factory.annotation.Autowired;

public class DpExploratoryFactory implements ResourceFactory<DpConnection, VlsiExploratorySettings> {
   private static final String VSAN_DP_SERVICE_SUBDIR = "/vsandp";
   private final ResourceFactory<DpConnection, VlsiSettings> dpFactory;
   @Autowired
   VersionService versionService;

   public DpExploratoryFactory(ResourceFactory<DpConnection, VlsiSettings> dpFactory) {
      this.dpFactory = dpFactory;
   }

   public DpConnection acquire(VlsiExploratorySettings settings) {
      Throwable var2 = null;
      Object var3 = null;

      try {
         LookupSvcConnection lsConnection = (LookupSvcConnection)settings.getLookupSvcFactory().acquire(settings.getLookupSvcSettings());

         Throwable var10000;
         label173: {
            boolean var10001;
            DpConnection var23;
            try {
               ServiceRegistration svcReg = lsConnection.getServiceRegistration();
               VcRegistration vcReg = (VcRegistration)(new VcLsExplorer(svcReg)).get(settings.getServiceUuid());
               ClientCertificate keyStore = new ClientCertificate(vcReg.getUuid().toString(), vcReg.getSslTrust(), "", "", vcReg.getUuid().toString());
               URI dpEndpoint = this.getDpEndpoint(vcReg.getServiceUrl());
               Class version = this.versionService.getVsanDpVmodlVersion(dpEndpoint.toString());
               VlsiSettings dpSettings = settings.getServiceSettingsTemplate().setServiceInfo(dpEndpoint, version).setSslContext(keyStore, (ThumbprintVerifier)null);
               var23 = (DpConnection)this.dpFactory.acquire(dpSettings);
            } catch (Throwable var21) {
               var10000 = var21;
               var10001 = false;
               break label173;
            }

            if (lsConnection != null) {
               lsConnection.close();
            }

            label162:
            try {
               return var23;
            } catch (Throwable var20) {
               var10000 = var20;
               var10001 = false;
               break label162;
            }
         }

         var2 = var10000;
         if (lsConnection != null) {
            lsConnection.close();
         }

         throw var2;
      } catch (Throwable var22) {
         if (var2 == null) {
            var2 = var22;
         } else if (var2 != var22) {
            var2.addSuppressed(var22);
         }

         throw var2;
      }
   }

   private URI getDpEndpoint(URI serviceUri) {
      try {
         return new URI(serviceUri.getScheme(), (String)null, serviceUri.getHost(), serviceUri.getPort(), "/vsandp", (String)null, (String)null);
      } catch (URISyntaxException var3) {
         throw new RuntimeException("Unable to acquire DP connection.", var3);
      }
   }
}
