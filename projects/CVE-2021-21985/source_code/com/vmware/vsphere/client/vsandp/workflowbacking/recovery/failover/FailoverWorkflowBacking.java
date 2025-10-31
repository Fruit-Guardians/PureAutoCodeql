package com.vmware.vsphere.client.vsandp.workflowbacking.recovery.failover;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.Network;
import com.vmware.vim.binding.vim.ResourcePool;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsandp.binding.vim.vsandp.CgInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.InstanceLocation;
import com.vmware.vim.vsandp.binding.vim.vsandp.TargetGroupInstanceData;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.common.PermissionService;
import com.vmware.vsan.client.services.dataprotection.ClusterDpConfigService;
import com.vmware.vsan.client.services.inventory.InventoryEntryData;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsan.client.util.retriever.VsanDataRetrieverFactory;
import com.vmware.vsphere.client.vsan.base.data.StoragePolicyData;
import com.vmware.vsphere.client.vsan.base.impl.PbmDataProvider;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.DpClient;
import com.vmware.vsphere.client.vsandp.dataproviders.vm.VmConsistencyGroupPropertyProvider;
import com.vmware.vsphere.client.vsandp.helper.VsanDpInventoryHelper;
import com.vmware.vsphere.client.vsandp.workflowbacking.recovery.failover.model.RemoteReplicationData;
import com.vmware.vsphere.client.vsandp.workflowbacking.recovery.failover.model.RemoteReplicationPointData;
import java.util.List;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class FailoverWorkflowBacking {
   private static final Log logger = LogFactory.getLog(ClusterDpConfigService.class);
   @Autowired
   private DpClient dpClient;
   @Autowired
   private PbmDataProvider pbmDataProvider;
   @Autowired
   private VsanDataRetrieverFactory dataRetrieverFactory;
   @Autowired
   private VsanDpInventoryHelper inventoryHelper;
   @Autowired
   private VmodlHelper vmodlHelper;
   @Autowired
   private PermissionService permissionService;
   @Autowired
   private VmConsistencyGroupPropertyProvider cgPropertyProvider;

   @TsService
   public ManagedObjectReference getTestFailover(ManagedObjectReference clusterRef, String cgUid, String seriesKey, String instanceKey, String remoteClusterUuid, ManagedObjectReference folderRef, ManagedObjectReference computeRef, ManagedObjectReference networkRef, String recoverVmName, String selectedPolicyId, boolean powerOnVm) {
      return null;
   }

   @TsService
   public RemoteReplicationData getRemoteReplicationData(ManagedObjectReference clusterRef, String cgUid) {
      CgInfo cgInfo = this.cgPropertyProvider.getCgInfo(clusterRef, cgUid);
      if (cgInfo.getTarget() == null) {
         logger.error("CG with uid " + cgUid + " is not a remote dataprotection object");
         throw new VsanUiLocalizableException("vsan.failover.validation.replicas.not.found");
      } else {
         RemoteReplicationData result = new RemoteReplicationData(cgInfo.getDisplayName(), cgInfo.getTarget().getSeries().getKey(), cgInfo.getTarget().getLocation().getCluster());
         if (ArrayUtils.isEmpty(cgInfo.getTarget().getInstance())) {
            return result;
         } else {
            TargetGroupInstanceData[] var8;
            int var7 = (var8 = cgInfo.getTarget().getInstance()).length;

            for(int var6 = 0; var6 < var7; ++var6) {
               TargetGroupInstanceData instanceData = var8[var6];
               result.points.add(new RemoteReplicationPointData(instanceData.getKey(), instanceData.getSnapshotTimestamp().getTime()));
            }

            return result;
         }
      }
   }

   @TsService
   public List<StoragePolicyData> getAvailablePolicies(ManagedObjectReference clusterRef) {
      try {
         return this.pbmDataProvider.getObjectCompatibleStoragePolicies(clusterRef);
      } catch (Exception var3) {
         logger.error("Unable to retrieve policies for cluster " + clusterRef, var3);
         throw new VsanUiLocalizableException("vsan.failover.validation.retrieve.policies.error");
      }
   }

   @TsService
   public String getObjectPolicyId(ManagedObjectReference param1, String param2) {
      // $FF: Couldn't be decompiled
   }

   @TsService
   public ManagedObjectReference getParentDc(ManagedObjectReference clusterRef) {
      try {
         return (ManagedObjectReference)QueryUtil.getProperty(clusterRef, "dc", (Object)null);
      } catch (Exception var3) {
         logger.error("Unable to retrieve parent DC for cluster " + clusterRef, var3);
         throw new VsanUiLocalizableException("vsan.failover.dc.not.found");
      }
   }

   @TsService
   public String getValidateFolder(InventoryEntryData folder, String recoverVmName) {
      String privilegeCheckError = this.checkForDataProtectionPrivileges(this.inventoryHelper.getVmFolderOfDataCenter(folder.nodeRef));
      if (privilegeCheckError != null) {
         return privilegeCheckError;
      } else {
         try {
            if (!this.inventoryHelper.isVmNameUniqueForFolder(folder.nodeRef, recoverVmName)) {
               return Utils.getLocalizedString("vsan.failover.validation.folder.vm.name.exists");
            }
         } catch (Exception var5) {
            logger.error("Unable to validate the VM name " + recoverVmName, var5);
         }

         return null;
      }
   }

   @TsService
   public String getValidateCompute(InventoryEntryData compute) {
      return this.checkForDataProtectionPrivileges(this.inventoryHelper.getResourcePoolForCompute(compute.nodeRef));
   }

   @TsService
   public String getValidateNetwork(InventoryEntryData network) {
      return network == null ? null : this.checkForDataProtectionPrivileges(network.nodeRef);
   }

   private String checkForDataProtectionPrivileges(ManagedObjectReference moRef) {
      if (moRef == null) {
         return null;
      } else {
         try {
            boolean hasPermission = this.permissionService.hasPermissions(moRef, new String[]{"Vsan.DataProtection.Management.Remote"});
            return !hasPermission ? Utils.getLocalizedString(this.getPrivilegeErrorKey(moRef)) : null;
         } catch (Exception var3) {
            logger.error(var3);
            return Utils.getLocalizedString("vsan.common.permission.retrieve.error");
         }
      }
   }

   private String getPrivilegeErrorKey(ManagedObjectReference mor) {
      if (this.vmodlHelper.isVmFolder(mor)) {
         return "vsan.failover.validation.permission.folder";
      } else if (this.vmodlHelper.isOfType(mor, ResourcePool.class)) {
         return "vsan.failover.validation.permission.compute";
      } else if (this.vmodlHelper.isOfType(mor, Network.class)) {
         return "vsan.failover.validation.permission.network";
      } else {
         throw new RuntimeException("Missing implementation for type " + this.vmodlHelper.getTypeClass(mor).getName());
      }
   }

   private InstanceLocation buildInstanceLocation(String cgUid, String seriesKey, String instanceKey, String remoteClusterUuid) {
      return new InstanceLocation();
   }
}
