package com.vmware.vsphere.client.vsandp.workflowbacking.recovery.restore;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.binding.vmodl.RuntimeFault;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vim.vsandp.binding.vim.vsandp.CgInfo;
import com.vmware.vim.vsandp.binding.vim.vsandp.GroupInstanceData;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.CgInfoQuery;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.InstanceQuery;
import com.vmware.vim.vsandp.binding.vim.vsandp.cluster.ProtectionService.InstanceQuery.Result.SeriesEntry;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsphere.client.vsan.util.MessageBundle;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsan.util.Utils;
import com.vmware.vsphere.client.vsandp.data.DataProtectionInstance;
import com.vmware.vsphere.client.vsandp.data.ProtectionType;
import com.vmware.vsphere.client.vsandp.dataproviders.vm.VmConsistencyGroupPropertyProvider;
import com.vmware.vsphere.client.vsandp.helper.VsanDpInventoryHelper;
import com.vmware.vsphere.client.vsandp.workflowbacking.recovery.restore.model.GetClosestSyncPointsSpec;
import com.vmware.vsphere.client.vsandp.workflowbacking.recovery.restore.model.MultiRestoreVmSpec;
import com.vmware.vsphere.client.vsandp.workflowbacking.recovery.restore.model.VmInventoryModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MultiVmRestoreBacking {
   private static final Logger logger = LoggerFactory.getLogger(MultiVmRestoreBacking.class);
   @Autowired
   private RestoreWorkflowBacking commonBacking;
   @Autowired
   private VmConsistencyGroupPropertyProvider cgProvider;
   @Autowired
   private VsanDpInventoryHelper inventoryHelper;
   @Autowired
   private MessageBundle messages;

   @TsService
   public String validateTargetVms(ManagedObjectReference[] vmRefs) throws Exception {
      if (vmRefs.length > 1 && !this.validateMultipleVirtualMachines(vmRefs)) {
         return Utils.getLocalizedString("vsan.restore.validation.vmsFromDifferentClusters");
      } else {
         return vmRefs.length > 30 ? Utils.getLocalizedString("vsan.restore.validation.tooManyVmsToRestore") : null;
      }
   }

   private boolean validateMultipleVirtualMachines(ManagedObjectReference[] targetObjects) throws Exception {
      boolean result = true;
      PropertyValue[] values = QueryUtil.getProperties(targetObjects, new String[]{"cluster"}).getPropertyValues();
      ManagedObjectReference vmCluster = null;
      PropertyValue[] var8 = values;
      int var7 = values.length;

      for(int var6 = 0; var6 < var7; ++var6) {
         PropertyValue item = var8[var6];
         if (vmCluster == null) {
            vmCluster = (ManagedObjectReference)item.value;
         } else if (!vmCluster.equals(item.value)) {
            result = false;
            break;
         }
      }

      return result;
   }

   @TsService
   public List<DataProtectionInstance> getClosestSyncPointsToDate(ManagedObjectReference firstVmRef, GetClosestSyncPointsSpec spec) throws Exception {
      List<DataProtectionInstance> result = new ArrayList();
      ManagedObjectReference sourceCluster = this.inventoryHelper.getVmCluster(firstVmRef);
      if (!VsanCapabilityUtils.isArchiveDataProtectionSupported(sourceCluster)) {
         spec.restoreOnlyFromLocal = true;
      }

      Map<ManagedObjectReference, MultiVmRestoreBacking.VmProtectionData> vmDataMap = new HashMap();
      this.collectLocalData(spec.vmRefs, sourceCluster, vmDataMap);
      if (!spec.restoreOnlyFromLocal) {
         this.collectArchiveData(spec.vmRefs, sourceCluster, vmDataMap);
      }

      ManagedObjectReference[] var9;
      int var8 = (var9 = spec.vmRefs).length;

      for(int var7 = 0; var7 < var8; ++var7) {
         ManagedObjectReference vmRef = var9[var7];
         DataProtectionInstance pit = this.findClosestPit(spec.restoreOnlyFromQuiesced, spec.targetTime, (MultiVmRestoreBacking.VmProtectionData)vmDataMap.get(vmRef), vmRef);
         result.add(pit);
      }

      return result;
   }

   @TsService
   public String getValidateTargetInventory(ManagedObjectReference[] vmRefs, VmInventoryModel targetInventory) throws Exception {
      ManagedObjectReference[] var6 = vmRefs;
      int var5 = vmRefs.length;

      for(int var4 = 0; var4 < var5; ++var4) {
         ManagedObjectReference vmRef = var6[var4];
         String result = this.commonBacking.getValidatePermissions(vmRef, targetInventory);
         if (result != null) {
            return result;
         }
      }

      if (targetInventory.computeSameAsSource) {
         List<ManagedObjectReference> sourceComputeRefs = new ArrayList();
         ManagedObjectReference[] var11 = vmRefs;
         int var10 = vmRefs.length;

         for(var5 = 0; var5 < var10; ++var5) {
            ManagedObjectReference vmRef = var11[var5];
            sourceComputeRefs.add(this.inventoryHelper.getVmResourcePool(vmRef));
         }

         if (!this.commonBacking.checkHostConnectionState((ManagedObjectReference[])sourceComputeRefs.toArray(new ManagedObjectReference[0]))) {
            return Utils.getLocalizedString("vsan.restore.validation.compute.sameassource.error");
         }
      }

      return null;
   }

   @TsService
   public List<ManagedObjectReference> getMultiRestoreVm(MultiRestoreVmSpec spec) throws Exception {
      ManagedObjectReference clusterRef = this.inventoryHelper.getVmCluster(spec.selectedSyncPoints[0].vmRef);
      List<ManagedObjectReference> resultTasks = new ArrayList();

      for(int i = 0; i < spec.selectedSyncPoints.length; ++i) {
         DataProtectionInstance instance = spec.selectedSyncPoints[i];
         ManagedObjectReference vmReference = instance.vmRef;
         String vmName = spec.vmName[i];
         ManagedObjectReference selectedNetwork = spec.selectedNetwork;
         if (spec.keepNetworkAsSource) {
            selectedNetwork = this.inventoryHelper.getVmNetwork(vmReference);
         }

         ManagedObjectReference selectedVmFolder = spec.selectedVmFolder;
         if (spec.keepFolderAsSource) {
            selectedVmFolder = this.inventoryHelper.getVmFolder(vmReference);
         }

         ManagedObjectReference vmFolder = this.inventoryHelper.getVmFolderOfDataCenter(selectedVmFolder);
         ManagedObjectReference selectedCompute = spec.selectedResourcePool;
         if (spec.keepComputeAsSource) {
            selectedCompute = this.inventoryHelper.getVmResourcePool(vmReference);
         }

         ManagedObjectReference taskRef = (ManagedObjectReference)this.commonBacking.restore(vmReference, instance, spec.powerOn, spec.createIndependentVm, vmFolder, spec.storagePolicyId, vmName, selectedNetwork, selectedCompute, clusterRef).get();
         ManagedObjectReference resultTask = new ManagedObjectReference(taskRef.getType(), taskRef.getValue(), vmReference.getServerGuid());
         resultTasks.add(resultTask);
      }

      return resultTasks;
   }

   private void collectLocalData(ManagedObjectReference[] vmRefs, ManagedObjectReference sourceCluster, Map<ManagedObjectReference, MultiVmRestoreBacking.VmProtectionData> vmDataMap) throws Exception {
      Map<ManagedObjectReference, Future<CgInfoQuery>> cgInfoFuturesMap = new HashMap();
      ManagedObjectReference[] var8 = vmRefs;
      int var7 = vmRefs.length;

      ManagedObjectReference vmRef;
      int var6;
      for(var6 = 0; var6 < var7; ++var6) {
         vmRef = var8[var6];
         cgInfoFuturesMap.put(vmRef, this.cgProvider.getCgInfoAsync(vmRef, sourceCluster));
      }

      var8 = vmRefs;
      var7 = vmRefs.length;

      for(var6 = 0; var6 < var7; ++var6) {
         vmRef = var8[var6];
         CgInfoQuery cgInfoQuery = (CgInfoQuery)((Future)cgInfoFuturesMap.get(vmRef)).get();
         if (ArrayUtils.isEmpty(cgInfoQuery.getResult())) {
            logger.error("No local protection data exists for VM: {}, error: {}", vmRef, cgInfoQuery.error);
            throw new RuntimeFault(this.messages.string("dataproviders.vm.cg.cgInfoQueryFault"));
         }

         CgInfo cgInfo = cgInfoQuery.getResult()[0];
         vmDataMap.put(vmRef, new MultiVmRestoreBacking.VmProtectionData(cgInfo));
      }

   }

   private void collectArchiveData(ManagedObjectReference[] vmRefs, ManagedObjectReference sourceCluster, Map<ManagedObjectReference, MultiVmRestoreBacking.VmProtectionData> vmDataMap) throws Exception {
      Map<ManagedObjectReference, Future<InstanceQuery>> archiveFutures = new HashMap();
      ManagedObjectReference[] var8 = vmRefs;
      int var7 = vmRefs.length;

      ManagedObjectReference vmRef;
      int var6;
      for(var6 = 0; var6 < var7; ++var6) {
         vmRef = var8[var6];
         MultiVmRestoreBacking.VmProtectionData vmData = (MultiVmRestoreBacking.VmProtectionData)vmDataMap.get(vmRef);
         archiveFutures.put(vmRef, this.cgProvider.getArchivalSeriesAsync(sourceCluster, vmData.cgId));
      }

      var8 = vmRefs;
      var7 = vmRefs.length;

      for(var6 = 0; var6 < var7; ++var6) {
         vmRef = var8[var6];
         InstanceQuery archiveInstances = (InstanceQuery)((Future)archiveFutures.get(vmRef)).get();
         if (ArrayUtils.isEmpty(archiveInstances.result)) {
            logger.error("No archival pits found for VM: {}, Error: {}", vmRef, archiveInstances.error);
            throw new Exception(this.messages.string("dataproviders.vm.queryArchivalInstancesFault"));
         }

         SeriesEntry[] archiveSeries = archiveInstances.getResult()[0].getSeries();
         if (archiveSeries != null) {
            MultiVmRestoreBacking.VmProtectionData vmData = (MultiVmRestoreBacking.VmProtectionData)vmDataMap.get(vmRef);
            SeriesEntry[] var15 = archiveSeries;
            int var14 = archiveSeries.length;

            for(int var13 = 0; var13 < var14; ++var13) {
               SeriesEntry series = var15[var13];
               vmData.archivePits.addAll(Arrays.asList(series.getInstance()));
            }
         }
      }

   }

   private DataProtectionInstance findClosestPit(boolean useOnlyQuiesced, Long targetTime, MultiVmRestoreBacking.VmProtectionData vmData, ManagedObjectReference vmRef) {
      List<GroupInstanceData> pitsData = vmData.getAllPits();
      GroupInstanceData closestInstance = null;
      GroupInstanceData instance;
      Iterator var8;
      long syncTime;
      if (targetTime == null) {
         var8 = pitsData.iterator();

         label64:
         while(true) {
            do {
               do {
                  if (!var8.hasNext()) {
                     break label64;
                  }

                  instance = (GroupInstanceData)var8.next();
               } while(useOnlyQuiesced && instance.getQuiescedType().equalsIgnoreCase(DataProtectionInstance.QuiescingType.NONE.toString()));

               syncTime = instance.getSnapshotTimestamp().getTime().getTime();
            } while(closestInstance != null && syncTime <= closestInstance.getSnapshotTimestamp().getTime().getTime());

            closestInstance = instance;
         }
      } else {
         var8 = pitsData.iterator();

         label47:
         while(true) {
            do {
               if (!var8.hasNext()) {
                  break label47;
               }

               instance = (GroupInstanceData)var8.next();
            } while(useOnlyQuiesced && instance.getQuiescedType().equalsIgnoreCase(DataProtectionInstance.QuiescingType.NONE.toString()));

            syncTime = instance.getSnapshotTimestamp().getTime().getTime();
            if (closestInstance != null) {
               if (syncTime > closestInstance.getSnapshotTimestamp().getTime().getTime() && syncTime <= targetTime) {
                  closestInstance = instance;
               }
            } else if (syncTime <= targetTime) {
               closestInstance = instance;
            }
         }
      }

      if (closestInstance == null) {
         return new DataProtectionInstance(vmRef);
      } else {
         return vmData.isLocalPit(closestInstance) ? DataProtectionInstance.createInstance(vmData.localSeriesId, ProtectionType.LOCAL, closestInstance, vmRef, vmData.cgId) : DataProtectionInstance.createInstance(vmData.archiveSeriesId, ProtectionType.ARCHIVE, closestInstance, vmRef, vmData.cgId);
      }
   }

   private class VmProtectionData {
      String cgId;
      List<GroupInstanceData> localPits;
      List<GroupInstanceData> archivePits;
      String localSeriesId;
      String archiveSeriesId;

      VmProtectionData(CgInfo cgInfo) {
         this.cgId = cgInfo.key;
         if (cgInfo.local != null && cgInfo.local.series != null) {
            this.localSeriesId = cgInfo.local.series.key;
            GroupInstanceData[] localInstances = cgInfo.local.getInstance();
            if (localInstances == null) {
               this.localPits = new ArrayList();
            } else {
               this.localPits = Arrays.asList(cgInfo.getLocal().getInstance());
            }
         }

         if (ArrayUtils.isNotEmpty(cgInfo.archive) && cgInfo.archive[0].series != null) {
            this.archiveSeriesId = cgInfo.archive[0].series.key;
            this.archivePits = new ArrayList();
         }

      }

      List<GroupInstanceData> getAllPits() {
         List<GroupInstanceData> allPits = new ArrayList();
         if (this.localPits != null) {
            allPits.addAll(this.localPits);
         }

         if (this.archivePits != null) {
            allPits.addAll(this.archivePits);
         }

         return allPits;
      }

      boolean isLocalPit(GroupInstanceData pit) {
         return pit != null && this.localPits != null && this.localPits.contains(pit);
      }

      public boolean isArchivePit(GroupInstanceData pit) {
         return pit != null && this.archivePits != null && this.archivePits.contains(pit);
      }
   }
}
