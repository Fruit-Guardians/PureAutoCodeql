package com.vmware.vsphere.client.vsandp.workflowbacking.recovery.restore;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.ClusterComputeResource;
import com.vmware.vim.binding.vim.ComputeResource;
import com.vmware.vim.binding.vim.Datacenter;
import com.vmware.vim.binding.vim.HostSystem;
import com.vmware.vim.binding.vim.Network;
import com.vmware.vim.binding.vim.ResourcePool;
import com.vmware.vim.binding.vim.VirtualMachine;
import com.vmware.vim.binding.vim.HostSystem.ConnectionState;
import com.vmware.vim.binding.vim.dvs.DistributedVirtualPortgroup;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vim.vmomi.core.impl.BlockingFuture;
import com.vmware.vim.vsandp.binding.vim.vsandp.ArchivalStorageLocation;
import com.vmware.vim.vsandp.binding.vim.vsandp.CgInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.InstanceLocation;
import com.vmware.vim.vsandp.binding.vim.vsandp.LocalVsanLocation;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.VsanDataProtectionRecoverySystem;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.VsanDataProtectionRecoverySystem.RestoreSpec;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vsan.client.services.common.PermissionService;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.data.StoragePolicyData;
import com.vmware.vsphere.client.vsan.base.impl.PbmDataProvider;
import com.vmware.vsphere.client.vsan.util.DataServiceResponse;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.controllers.vm.monitor.vsan.provider.pits.PitProvider;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.DpClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.dp.DpConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import com.vmware.vsphere.client.vsandp.data.DataProtectionInstance;
import com.vmware.vsphere.client.vsandp.data.ProtectionType;
import com.vmware.vsphere.client.vsandp.dataproviders.vm.VmConsistencyGroupPropertyProvider;
import com.vmware.vsphere.client.vsandp.helper.VsanDpInventoryHelper;
import com.vmware.vsphere.client.vsandp.workflowbacking.recovery.restore.model.VmInventoryModel;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import org.apache.commons.lang.ArrayUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RestoreWorkflowBacking {
   private static final String VM_CRYPT_PROPERTY = "config.keyId";
   @Autowired
   private PitProvider pitProvider;
   @Autowired
   private VsanDpInventoryHelper inventoryHelper;
   @Autowired
   private VmConsistencyGroupPropertyProvider cgProvider;
   @Autowired
   private VmodlHelper vmodlHelper;
   @Autowired
   private DpClient dpClient;
   @Autowired
   private VcClient vcClient;
   @Autowired
   private PbmDataProvider pbmDataProvider;
   @Autowired
   private PermissionService permissionService;

   @TsService
   public String getSourceVmName(ManagedObjectReference vmRef) {
      Throwable var2 = null;
      Object var3 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(vmRef.getServerGuid());

         Throwable var10000;
         label173: {
            String var17;
            boolean var10001;
            try {
               var17 = ((VirtualMachine)vcConnection.createStub(VirtualMachine.class, vmRef)).getName();
            } catch (Throwable var15) {
               var10000 = var15;
               var10001 = false;
               break label173;
            }

            if (vcConnection != null) {
               vcConnection.close();
            }

            label162:
            try {
               return var17;
            } catch (Throwable var14) {
               var10000 = var14;
               var10001 = false;
               break label162;
            }
         }

         var2 = var10000;
         if (vcConnection != null) {
            vcConnection.close();
         }

         throw var2;
      } catch (Throwable var16) {
         if (var2 == null) {
            var2 = var16;
         } else if (var2 != var16) {
            var2.addSuppressed(var16);
         }

         throw var2;
      }
   }

   @TsService
   public ArrayList<DataProtectionInstance> getSyncPoints(ManagedObjectReference vmRef) throws Exception {
      ArrayList<DataProtectionInstance> result = new ArrayList();
      ManagedObjectReference clusterRef = this.inventoryHelper.getVmCluster(vmRef);
      CgInfo vmCgInfo = this.cgProvider.getCgInfo(vmRef, clusterRef);
      TreeSet<DataProtectionInstance> localPits = this.pitProvider.getLocalPits(vmRef, vmCgInfo);
      if (localPits != null) {
         result.addAll(localPits);
      }

      TreeSet<DataProtectionInstance> archivePits = this.pitProvider.getArchivePits(vmRef, vmCgInfo);
      if (archivePits != null) {
         result.addAll(archivePits);
      }

      Collections.sort(result, Collections.reverseOrder());
      return result;
   }

   @TsService
   public VmInventoryModel getSourceVmInventory(ManagedObjectReference vmRef) throws Exception {
      VmInventoryModel inventoryModel = new VmInventoryModel();
      List<ManagedObjectReference> inventories = new ArrayList();
      ManagedObjectReference sourceVmFolder = this.inventoryHelper.getVmFolder(vmRef);
      if (sourceVmFolder != null) {
         inventories.add(sourceVmFolder);
      }

      ManagedObjectReference sourceVmResourcePool = this.inventoryHelper.getVmResourcePool(vmRef);
      if (sourceVmResourcePool != null) {
         inventories.add(sourceVmResourcePool);
      }

      ManagedObjectReference sourceVmNetwork = this.inventoryHelper.getVmNetwork(vmRef);
      if (sourceVmNetwork != null) {
         inventories.add(sourceVmNetwork);
      } else {
         inventoryModel.network = null;
      }

      ManagedObjectReference[] objects = VmodlHelper.assignServerGuid((ManagedObjectReference[])inventories.toArray(new ManagedObjectReference[0]), vmRef.getServerGuid());
      PropertyValue[] values = QueryUtil.getProperties(objects, new String[]{"name", "primaryIconId"}).getPropertyValues();
      PropertyValue[] var12 = values;
      int var11 = values.length;

      for(int var10 = 0; var10 < var11; ++var10) {
         PropertyValue property = var12[var10];
         ManagedObjectReference nodeRef;
         if (property.propertyName.equals("name")) {
            nodeRef = (ManagedObjectReference)property.resourceObject;
            if (this.isFolder(nodeRef)) {
               inventoryModel.folder.name = (String)property.value;
               inventoryModel.folder.ref = nodeRef;
            } else if (this.isCompute(nodeRef)) {
               inventoryModel.compute.name = (String)property.value;
               inventoryModel.compute.ref = nodeRef;
            } else if (this.isNetwork(nodeRef)) {
               inventoryModel.network.name = (String)property.value;
               inventoryModel.network.ref = nodeRef;
            }
         } else if (property.propertyName.equals("primaryIconId")) {
            nodeRef = (ManagedObjectReference)property.resourceObject;
            if (this.isFolder(nodeRef)) {
               inventoryModel.folder.iconId = (String)property.value;
            } else if (this.isCompute(nodeRef)) {
               inventoryModel.compute.iconId = (String)property.value;
            } else if (this.isNetwork(nodeRef)) {
               inventoryModel.network.iconId = (String)property.value;
            }
         }
      }

      inventoryModel.rootDc = this.inventoryHelper.getVmDc(vmRef);
      inventoryModel.rootVsanCluster = this.inventoryHelper.getVmCluster(vmRef);
      return inventoryModel;
   }

   private boolean isFolder(ManagedObjectReference moRef) {
      return this.vmodlHelper.isOfType(moRef, Datacenter.class) || this.vmodlHelper.isVmFolder(moRef);
   }

   private boolean isCompute(ManagedObjectReference moRef) {
      return this.vmodlHelper.isOfType(moRef, ClusterComputeResource.class) || this.vmodlHelper.isOfType(moRef, HostSystem.class) || this.vmodlHelper.isOfType(moRef, ResourcePool.class) || this.vmodlHelper.isHostFolder(moRef);
   }

   private boolean isNetwork(ManagedObjectReference moRef) {
      return this.vmodlHelper.isOfType(moRef, Network.class) || this.vmodlHelper.isOfType(moRef, DistributedVirtualPortgroup.class);
   }

   @TsService
   public List<StoragePolicyData> getProtectedVmStoragePolicies(ManagedObjectReference vmRef) throws Exception {
      ClassLoader classLoader = RestoreWorkflowBacking.class.getClassLoader();
      ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();

      List var5;
      try {
         Thread.currentThread().setContextClassLoader(classLoader);
         var5 = this.pbmDataProvider.getObjectCompatibleStoragePolicies(this.inventoryHelper.getVmCluster(vmRef));
      } finally {
         Thread.currentThread().setContextClassLoader(currentClassLoader);
      }

      return var5;
   }

   @TsService
   public Map<ManagedObjectReference, Boolean> getVmsEncryptedState(ManagedObjectReference[] vmRefs) throws Exception {
      if (ArrayUtils.isEmpty(vmRefs)) {
         return new HashMap();
      } else {
         Map<ManagedObjectReference, Boolean> result = new HashMap(vmRefs.length);
         DataServiceResponse response = QueryUtil.getProperties(vmRefs, new String[]{"config.keyId"});
         ManagedObjectReference[] var7 = vmRefs;
         int var6 = vmRefs.length;

         for(int var5 = 0; var5 < var6; ++var5) {
            ManagedObjectReference vmRef = var7[var5];
            Object vmCrypt = response.getProperty(vmRef, "config.keyId");
            result.put(vmRef, vmCrypt != null);
         }

         return result;
      }
   }

   @TsService
   public String getValidatePermissions(ManagedObjectReference vmRef, VmInventoryModel targetInventory) throws Exception {
      if (!this.isFolderPermissionValid(vmRef, targetInventory)) {
         return Utils.getLocalizedString("vsan.restore.validation.permission.folder");
      } else if (!this.isComputePermissionValid(vmRef, targetInventory)) {
         return Utils.getLocalizedString("vsan.restore.validation.permission.compute");
      } else {
         return !this.isNetworkPermissionValid(vmRef, targetInventory) ? Utils.getLocalizedString("vsan.restore.validation.permission.network") : null;
      }
   }

   private boolean isFolderPermissionValid(ManagedObjectReference vmRef, VmInventoryModel targetInventory) throws Exception {
      ManagedObjectReference folderToCheck;
      if (targetInventory.folderSameAsSource) {
         folderToCheck = this.inventoryHelper.getVmFolder(vmRef);
      } else {
         if (targetInventory.folder == null) {
            return true;
         }

         folderToCheck = targetInventory.folder.ref;
      }

      return this.permissionService.hasPermissions(folderToCheck, new String[]{"Vsan.DataProtection.Management"});
   }

   private boolean isComputePermissionValid(ManagedObjectReference vmRef, VmInventoryModel targetInventory) throws Exception {
      ManagedObjectReference computeToCheck;
      if (targetInventory.computeSameAsSource) {
         computeToCheck = this.inventoryHelper.getVmResourcePool(vmRef);
      } else {
         if (targetInventory.compute == null) {
            return true;
         }

         computeToCheck = targetInventory.compute.ref;
      }

      return this.permissionService.hasPermissions(computeToCheck, new String[]{"Vsan.DataProtection.Management"});
   }

   private boolean isNetworkPermissionValid(ManagedObjectReference vmRef, VmInventoryModel targetInventory) throws Exception {
      ManagedObjectReference networkToCheck;
      if (targetInventory.networkSameAsSource) {
         networkToCheck = this.inventoryHelper.getVmNetwork(vmRef);
      } else {
         if (targetInventory.network == null) {
            return true;
         }

         networkToCheck = targetInventory.network.ref;
      }

      return this.permissionService.hasPermissions(networkToCheck, new String[]{"Vsan.DataProtection.Management"});
   }

   protected Future<ManagedObjectReference> restore(ManagedObjectReference vmRef, DataProtectionInstance selectedSyncPoint, Boolean powerOn, Boolean createIndependentVm, ManagedObjectReference selectedVmFolder, String storagePolicyId, String vmName, ManagedObjectReference selectedNetwork, ManagedObjectReference selectedResourcePool, ManagedObjectReference sourceCluster) throws Exception {
      Throwable var11 = null;
      Object var12 = null;

      try {
         DpConnection dpConnection = this.dpClient.getConnection(sourceCluster);

         Throwable var10000;
         label960: {
            BlockingFuture var77;
            boolean var10001;
            try {
               VsanDataProtectionRecoverySystem recoveryService = dpConnection.getRecoveryService();
               RestoreSpec spec = new RestoreSpec();
               spec.setFolder(selectedVmFolder);
               spec.setProfileId(storagePolicyId);
               spec.setName(vmName);
               spec.setPowerOn(powerOn);
               spec.setFullClone(createIndependentVm);
               spec.setDefaultNetwork(selectedNetwork);
               spec.setCluster(sourceCluster);
               ManagedObjectReference datastore = this.inventoryHelper.getVsanDatastore(vmRef);
               if (datastore == null) {
                  throw new IllegalArgumentException("vSAN datastore not found for VM " + vmRef);
               }

               spec.setDatastore(datastore);
               Throwable var17;
               BlockingFuture result;
               VcConnection vc;
               if (HostSystem.class.isAssignableFrom(this.vmodlHelper.getTypeClass(selectedResourcePool))) {
                  spec.setHost(selectedResourcePool);
                  var17 = null;
                  result = null;

                  try {
                     vc = this.vcClient.getConnection(vmRef.getServerGuid());

                     try {
                        selectedResourcePool = ((HostSystem)vc.createStub(HostSystem.class, selectedResourcePool)).getParent();
                     } finally {
                        if (vc != null) {
                           vc.close();
                        }

                     }
                  } catch (Throwable var73) {
                     if (var17 == null) {
                        var17 = var73;
                     } else if (var17 != var73) {
                        var17.addSuppressed(var73);
                     }

                     throw var17;
                  }
               }

               if (ResourcePool.class.isAssignableFrom(this.vmodlHelper.getTypeClass(selectedResourcePool))) {
                  spec.setResourcePool(selectedResourcePool);
               } else if (ComputeResource.class.isAssignableFrom(this.vmodlHelper.getTypeClass(selectedResourcePool))) {
                  var17 = null;
                  result = null;

                  try {
                     vc = this.vcClient.getConnection(vmRef.getServerGuid());

                     try {
                        ManagedObjectReference resource = ((ComputeResource)vc.createStub(ComputeResource.class, selectedResourcePool)).getResourcePool();
                        spec.setResourcePool(resource);
                     } finally {
                        if (vc != null) {
                           vc.close();
                        }

                     }
                  } catch (Throwable var71) {
                     if (var17 == null) {
                        var17 = var71;
                     } else if (var17 != var71) {
                        var17.addSuppressed(var71);
                     }

                     throw var17;
                  }
               }

               InstanceLocation location = new InstanceLocation();
               location.setSeries(selectedSyncPoint.seriesKey);
               location.setGroupInstanceKey(selectedSyncPoint.id);
               if (selectedSyncPoint.type.equals(ProtectionType.LOCAL)) {
                  location.setLocation(new LocalVsanLocation());
               } else if (selectedSyncPoint.type.equals(ProtectionType.ARCHIVE)) {
                  location.setLocation(new ArchivalStorageLocation());
               }

               spec.setLocation(location);
               result = new BlockingFuture();
               recoveryService.restoreVm(spec, result);
               var77 = result;
            } catch (Throwable var75) {
               var10000 = var75;
               var10001 = false;
               break label960;
            }

            if (dpConnection != null) {
               dpConnection.close();
            }

            label948:
            try {
               return var77;
            } catch (Throwable var74) {
               var10000 = var74;
               var10001 = false;
               break label948;
            }
         }

         var11 = var10000;
         if (dpConnection != null) {
            dpConnection.close();
         }

         throw var11;
      } catch (Throwable var76) {
         if (var11 == null) {
            var11 = var76;
         } else if (var11 != var76) {
            var11.addSuppressed(var76);
         }

         throw var11;
      }
   }

   public boolean checkHostConnectionState(ManagedObjectReference[] sourceComputeRefs) throws Exception {
      List<ManagedObjectReference> hostRefs = new ArrayList();
      ManagedObjectReference[] var6 = sourceComputeRefs;
      int var5 = sourceComputeRefs.length;

      for(int var4 = 0; var4 < var5; ++var4) {
         ManagedObjectReference computeRef = var6[var4];
         if (computeRef.getType().equals(HostSystem.class.getSimpleName())) {
            hostRefs.add(computeRef);
         }
      }

      if (hostRefs.size() == 0) {
         return true;
      } else {
         DataServiceResponse result = QueryUtil.getProperties((ManagedObjectReference[])hostRefs.toArray(new ManagedObjectReference[0]), new String[]{"runtime.connectionState", "runtime.inMaintenanceMode", "runtime.inQuarantineMode"});
         Map<Object, Map<String, Object>> mappedProperties = result.getMap();
         Iterator var13 = mappedProperties.values().iterator();

         boolean isQuarantineMode;
         do {
            if (!var13.hasNext()) {
               return true;
            }

            Map<String, Object> properties = (Map)var13.next();
            ConnectionState connectionState = (ConnectionState)properties.get("runtime.connectionState");
            if (!com.vmware.vsan.client.services.common.data.ConnectionState.fromHostState(connectionState).equals(com.vmware.vsan.client.services.common.data.ConnectionState.connected)) {
               return false;
            }

            boolean isInMaintenanceMode = (Boolean)properties.get("runtime.inMaintenanceMode");
            if (isInMaintenanceMode) {
               return false;
            }

            isQuarantineMode = (Boolean)properties.get("runtime.inQuarantineMode");
         } while(!isQuarantineMode);

         return false;
      }
   }
}
