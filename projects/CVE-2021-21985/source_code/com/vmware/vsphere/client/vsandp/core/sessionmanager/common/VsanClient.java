package com.vmware.vsphere.client.vsandp.core.sessionmanager.common;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiExploratorySettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vsan.VsanConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vsan.VsanSessionCookieAuth;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class VsanClient {
   @Autowired
   private VcClient vcClient;
   @Autowired
   private LookupSvcClient lsClient;
   @Autowired
   @Qualifier("vsanFactory")
   private ResourceFactory<VsanConnection, VlsiExploratorySettings> vsanFactory;
   @Autowired
   @Qualifier("vlsiSettingsTemplate")
   private VlsiSettings vlsiSettingsTemplate;

   public VsanConnection getConnection(String vcUuid) {
      return this.getConnection(vcUuid, (LookupSvcInfo)null);
   }

   public VsanConnection getConnection(String vcUuid, LookupSvcInfo lsInfo) {
      Throwable var3 = null;
      Object var4 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(vcUuid, lsInfo);

         Throwable var10000;
         label173: {
            boolean var10001;
            VsanConnection var19;
            try {
               VlsiExploratorySettings exploratorySettings = new VlsiExploratorySettings(this.vlsiSettingsTemplate.setHttpSettings(vcConnection.getSettings().getHttpSettings()).setAuthenticator(new VsanSessionCookieAuth()).setSessionCookie(vcConnection.getSessionCookie()), this.lsClient.getProducerFactory(), this.lsClient.getSettings(lsInfo), UUID.fromString(vcUuid));
               var19 = (VsanConnection)this.vsanFactory.acquire(exploratorySettings);
            } catch (Throwable var17) {
               var10000 = var17;
               var10001 = false;
               break label173;
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label162:
            try {
               return var19;
            } catch (Throwable var16) {
               var10000 = var16;
               var10001 = false;
               break label162;
            }
         }

         var3 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var3;
      } catch (Throwable var18) {
         if (var3 == null) {
            var3 = var18;
         } else if (var3 != var18) {
            var3.addSuppressed(var18);
         }

         throw var3;
      }
   }
}
