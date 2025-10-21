package com.vmware.vsan.client.services.dataprotection.remote;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.vsan.DataProtectionInfo;
import com.vmware.vim.vsan.binding.vim.vsan.DataProtectionPairingInfo;
import com.vmware.vim.vsan.binding.vim.vsan.ReconfigSpec;
import com.vmware.vise.usersession.ServerInfo;
import com.vmware.vise.usersession.UserSessionService;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.dataprotection.model.PscConnectionDetails;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.LookupSvcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.LookupSvcInfo;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.LookupSvcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcLsExplorer;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer.VcRegistration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RemoteDpPairingWorkflowBacking {
   @Autowired
   private UserSessionService sessionService;
   @Autowired
   protected LookupSvcClient lsClient;
   @Autowired
   protected DpPairingReconfigureSpecBuilder specBuilder;
   @Autowired
   private RemoteAuthenticationService authenticationService;
   @Autowired
   private RemoteDpConfigService dpConfigService;

   @TsService
   public List<ManagedObjectReference> getRootInventory(PscConnectionDetails pscDetails) {
      List<ManagedObjectReference> result = new ArrayList();
      if (pscDetails == null) {
         ServerInfo[] var6;
         int var5 = (var6 = this.sessionService.getUserSession().serversInfo).length;

         for(int var4 = 0; var4 < var5; ++var4) {
            ServerInfo vcServer = var6[var4];
            result.add(VmodlHelper.getRootFolder(vcServer.serviceGuid));
         }
      } else {
         Throwable var14 = null;
         Object var15 = null;

         try {
            LookupSvcConnection lsConn = this.lsClient.getConnection(LookupSvcInfo.from(pscDetails));

            try {
               Iterator var7 = (new VcLsExplorer(lsConn.getServiceRegistration())).list().iterator();

               while(var7.hasNext()) {
                  VcRegistration vcRegistration = (VcRegistration)var7.next();
                  result.add(VmodlHelper.getRootFolder(vcRegistration.getUuid().toString()));
               }
            } finally {
               if (lsConn != null) {
                  lsConn.close();
               }

            }
         } catch (Throwable var13) {
            if (var14 == null) {
               var14 = var13;
            } else if (var14 != var13) {
               var14.addSuppressed(var13);
            }

            throw var14;
         }
      }

      return result;
   }

   @TsService
   public String validateConnectionDetails(String host, Integer port, String username, String password) throws VsanUiLocalizableException {
      return this.authenticationService.validateConnectionDetails(host, port, username, password);
   }

   @TsService
   public void authenticate(PscConnectionDetails pscDetails, String username, String password) throws VsanUiLocalizableException {
      this.authenticationService.authenticate(pscDetails, username, password);
   }

   @TsService
   public ManagedObjectReference getConfigure(ManagedObjectReference sourceCluster, ManagedObjectReference targetCluster, PscConnectionDetails pscDetails) throws VsanUiLocalizableException {
      this.specBuilder.init(sourceCluster, targetCluster, pscDetails);
      ReconfigSpec sourceReconfigureSpec = this.specBuilder.buildSourceReconfigureSpec();
      ReconfigSpec targetReconfigureSpec = this.specBuilder.buildTargetReconfigureSpec();
      ManagedObjectReference sourceTask = null;
      if (!this.isPaired(sourceCluster, (PscConnectionDetails)null) && !this.isPaired(targetCluster, pscDetails)) {
         sourceTask = this.dpConfigService.reconfigureCluster(sourceCluster, (PscConnectionDetails)null, sourceReconfigureSpec);
         VmodlHelper.assignServerGuid(sourceTask, sourceCluster.getServerGuid());
         this.dpConfigService.reconfigureCluster(targetCluster, pscDetails, targetReconfigureSpec);
         return sourceTask;
      } else {
         return null;
      }
   }

   private boolean isPaired(ManagedObjectReference clusterRef, PscConnectionDetails pscDetails) throws VsanUiLocalizableException {
      DataProtectionInfo dpConfig = this.dpConfigService.getDpConfig(clusterRef, pscDetails);
      DataProtectionPairingInfo pairingInfo = this.dpConfigService.getPairingInfo(dpConfig);
      return pairingInfo != null;
   }
}
