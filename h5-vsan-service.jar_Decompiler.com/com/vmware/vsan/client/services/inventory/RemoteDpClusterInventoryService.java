package com.vmware.vsan.client.services.inventory;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.Datacenter;
import com.vmware.vim.binding.vim.Folder;
import com.vmware.vim.binding.vim.cluster.ConfigInfoEx;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanVcClusterConfigSystem;
import com.vmware.vsan.client.services.config.VsanConfigService;
import com.vmware.vsan.client.services.dataprotection.model.PscConnectionDetails;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VsanClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vsan.VsanConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RemoteDpClusterInventoryService extends InventoryBrowserService {
   private static final Log logger = LogFactory.getLog(RemoteDpClusterInventoryService.class);
   @Autowired
   private VcClient vcClient;
   @Autowired
   private VsanClient vsanClient;
   @Autowired
   private VmodlHelper vmodlHelper;
   @Autowired
   private VsanConfigService vsanConfigService;

   @TsService
   public InventoryEntryData[] getNodeInfo(ManagedObjectReference[] nodeRefs, PscConnectionDetails pscDetails) throws Exception {
      return super.getNodeInfo(nodeRefs, pscDetails);
   }

   @TsService
   public InventoryEntryData[] getNodeChildren(ManagedObjectReference parentRef, PscConnectionDetails pscDetails, ManagedObjectReference contextRef) throws Exception {
      return super.getNodeChildren(parentRef, pscDetails, contextRef);
   }

   protected boolean isLeafNode(ManagedObjectReference item) {
      return this.vmodlHelper.getTypeClass(item) == ClusterComputeResource.class;
   }

   protected List<ManagedObjectReference> listChildrenRefs(ManagedObjectReference parentRef, PscConnectionDetails pscDetails, ManagedObjectReference contextRef) {
      PscConnectionDetails remotePscDetails = pscDetails == null ? new PscConnectionDetails() : pscDetails;
      Throwable var5 = null;
      Object var6 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(parentRef.getServerGuid(), remotePscDetails.toLsInfo());

         Throwable var10000;
         label256: {
            boolean var10001;
            List var22;
            label263: {
               try {
                  if (Datacenter.class.isAssignableFrom(this.vmodlHelper.getTypeClass(parentRef))) {
                     ManagedObjectReference hostFolderRef = ((Datacenter)vcConnection.createStub(Datacenter.class, parentRef)).getHostFolder();
                     Folder hostFolder = (Folder)vcConnection.createStub(Folder.class, hostFolderRef);
                     var22 = this.filterChildren(VmodlHelper.assignServerGuid(hostFolder.getChildEntity(), parentRef.getServerGuid()), remotePscDetails, contextRef);
                     break label263;
                  }
               } catch (Throwable var20) {
                  var10000 = var20;
                  var10001 = false;
                  break label256;
               }

               if (vcConnection != null) {
                  vcConnection.close();
               }

               return Collections.emptyList();
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label233:
            try {
               return var22;
            } catch (Throwable var19) {
               var10000 = var19;
               var10001 = false;
               break label233;
            }
         }

         var5 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var5;
      } catch (Throwable var21) {
         if (var5 == null) {
            var5 = var21;
         } else if (var5 != var21) {
            var5.addSuppressed(var21);
         }

         throw var5;
      }
   }

   private List<ManagedObjectReference> filterChildren(ManagedObjectReference[] allChildren, PscConnectionDetails pscDetails, ManagedObjectReference contextCluster) {
      List<ManagedObjectReference> result = new ArrayList();
      ManagedObjectReference[] var8 = allChildren;
      int var7 = allChildren.length;

      for(int var6 = 0; var6 < var7; ++var6) {
         ManagedObjectReference clusterRef = var8[var6];
         if (this.vmodlHelper.isOfType(clusterRef, ClusterComputeResource.class) && !clusterRef.equals(contextCluster)) {
            Throwable var9 = null;
            Object var10 = null;

            try {
               VcConnection vcConnection = this.vcClient.getConnection(clusterRef.getServerGuid(), pscDetails.toLsInfo());

               try {
                  ClusterComputeResource cluster = (ClusterComputeResource)vcConnection.createStub(ClusterComputeResource.class, clusterRef);
                  ConfigInfoEx configInfoEx = (ConfigInfoEx)cluster.getConfigurationEx();
                  if (configInfoEx != null && configInfoEx.vsanConfigInfo != null && configInfoEx.vsanConfigInfo.enabled) {
                     try {
                        Throwable var14 = null;
                        Object var15 = null;

                        try {
                           VsanConnection vsanConnection = this.vsanClient.getConnection(clusterRef.getServerGuid(), pscDetails.toLsInfo());

                           try {
                              VsanVcClusterConfigSystem vsanConfigSystem = vsanConnection.getVsanConfigSystem();
                              com.vmware.vim.vsan.binding.vim.vsan.ConfigInfoEx vsanConfig = vsanConfigSystem.getConfigInfoEx(clusterRef);
                              if (vsanConfig.dataProtectionConfig == null || ArrayUtils.isEmpty(vsanConfig.dataProtectionConfig.pairingInfo) || vsanConfig.dataProtectionConfig.pairingInfo[0].peerClusterUuid == null) {
                                 result.add(clusterRef);
                              }
                           } finally {
                              if (vsanConnection != null) {
                                 vsanConnection.close();
                              }

                           }
                        } catch (Throwable var40) {
                           if (var14 == null) {
                              var14 = var40;
                           } else if (var14 != var40) {
                              var14.addSuppressed(var40);
                           }

                           throw var14;
                        }
                     } catch (Exception var41) {
                        logger.error("Unable to query vSAN data protection cluster configuration for cluster: " + clusterRef, var41);
                     }
                  }
               } finally {
                  if (vcConnection != null) {
                     vcConnection.close();
                  }

               }
            } catch (Throwable var43) {
               if (var9 == null) {
                  var9 = var43;
               } else if (var9 != var43) {
                  var9.addSuppressed(var43);
               }

               throw var9;
            }
         }
      }

      return result;
   }
}
