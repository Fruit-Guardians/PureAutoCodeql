package com.vmware.vsan.client.services.encryption;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.encryption.CryptoManagerKmip;
import com.vmware.vim.binding.vim.encryption.KmipClusterInfo;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vsan.client.services.common.PermissionService;
import com.vmware.vsphere.client.vsan.data.KmipClusterData;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EncryptionPropertyProvider {
   @Autowired
   private VcClient _vcClient;
   @Autowired
   private PermissionService permissionService;

   @TsService
   public KmipClusterData getKmipClusterData(ManagedObjectReference clusterRef) throws Exception {
      KmipClusterData result = new KmipClusterData();
      result.hasManageKeyServersPermissions = this.permissionService.hasVcPermissions(clusterRef, new String[]{"Cryptographer.ManageKeyServers"});
      if (!result.hasManageKeyServersPermissions) {
         return result;
      } else {
         Throwable var3 = null;
         Object var4 = null;

         try {
            VcConnection vcConnection = this._vcClient.getConnection(clusterRef.getServerGuid());

            VcConnection var10000;
            try {
               ManagedObjectReference cryptoManagerMOR = vcConnection.getContent().cryptoManager;
               if (cryptoManagerMOR != null) {
                  CryptoManagerKmip cryptoManagerKmip = (CryptoManagerKmip)vcConnection.createStub(CryptoManagerKmip.class, cryptoManagerMOR);
                  KmipClusterInfo[] clusters = cryptoManagerKmip.listKmipServers((Integer)null);
                  if (clusters == null) {
                     return result;
                  }

                  KmipClusterInfo[] var12 = clusters;
                  int var11 = clusters.length;

                  for(int var10 = 0; var10 < var11; ++var10) {
                     KmipClusterInfo clusterInfo = var12[var10];
                     result.availableKmipClusters.add(clusterInfo.clusterId.id);
                     if (clusterInfo.useAsDefault) {
                        result.defaultKmipCluster = clusterInfo.clusterId.id;
                     }
                  }

                  return result;
               }
            } finally {
               var10000 = vcConnection;
               if (vcConnection != null) {
                  var10000 = vcConnection;
                  vcConnection.close();
               }

            }

            return var10000;
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

   @TsService
   public boolean getEncryptionPermissions(ManagedObjectReference clusterRef) throws Exception {
      return this.permissionService.hasPermissions(clusterRef, new String[]{"Cryptographer.ManageKeys", "Cryptographer.ManageEncryptionPolicy", "Cryptographer.ManageKeyServers"});
   }

   @TsService
   public boolean getReKeyPermissions(ManagedObjectReference clusterRef) throws Exception {
      return this.permissionService.hasPermissions(clusterRef, new String[]{"Cryptographer.ManageKeys"});
   }
}
