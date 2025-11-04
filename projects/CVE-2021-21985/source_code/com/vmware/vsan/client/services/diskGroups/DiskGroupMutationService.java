package com.vmware.vsan.client.services.diskGroups;

import com.google.common.collect.ImmutableMap;
import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.vim.host.MaintenanceSpec;
import com.vmware.vim.binding.vim.host.StorageSystem;
import com.vmware.vim.binding.vim.host.VsanSystem;
import com.vmware.vim.binding.vim.vsan.host.DecommissionMode;
import com.vmware.vim.binding.vim.vsan.host.DiskMapping;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.host.VsanSystemEx;
import com.vmware.vsan.client.services.VsanUiLocalizableException;
import com.vmware.vsan.client.services.capability.VsanCapabilityUtils;
import com.vmware.vsan.client.services.diskGroups.data.DiskMappingSpec;
import com.vmware.vsan.client.services.diskGroups.data.RemoveDiskGroupSpec;
import com.vmware.vsan.client.services.diskGroups.data.RemoveDiskSpec;
import com.vmware.vsan.client.services.diskGroups.data.UnmountDiskGroupSpec;
import com.vmware.vsan.client.services.diskGroups.data.VsanDiskMapping;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.impl.VsanMutationProvider;
import com.vmware.vsphere.client.vsan.spec.VsanDiskMappingSpec;
import com.vmware.vsphere.client.vsan.spec.VsanRemoveDataDiskSpec;
import com.vmware.vsphere.client.vsan.spec.VsanRemoveDiskGroupSpec;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VcClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.VsanClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc.VcConnection;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vsan.VsanConnection;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class DiskGroupMutationService {
   @Autowired
   VsanClient vsanClient;
   @Autowired
   private VcClient vcClient;
   @Autowired
   private VsanMutationProvider vsanMutationProvider;
   private static final Log logger = LogFactory.getLog(DiskGroupMutationService.class);

   @TsService
   public Object createDiskGroup(ManagedObjectReference hostRef, DiskMappingSpec spec) throws Exception {
      VsanDiskMappingSpec vsanSpec = new VsanDiskMappingSpec();
      vsanSpec.clusterRef = spec.clusterRef;
      ArrayList mappings = new ArrayList();
      VsanDiskMapping[] var8;
      int var7 = (var8 = spec.mappings).length;

      for(int var6 = 0; var6 < var7; ++var6) {
         VsanDiskMapping diskMapping = var8[var6];
         DiskMapping vsanDiskMapping = new DiskMapping();
         vsanDiskMapping.ssd = diskMapping.ssd;
         vsanDiskMapping.nonSsd = diskMapping.nonSsd;
         mappings.add(vsanDiskMapping);
      }

      vsanSpec.mappings = mappings.toArray();
      vsanSpec.isAllFlashSupported = spec.isAllFlashSupported;
      ManagedObjectReference task = this.vsanMutationProvider.claimDisks(hostRef, vsanSpec);
      return ImmutableMap.of("task", new ManagedObjectReference(task.getType(), task.getValue(), hostRef.getServerGuid()));
   }

   @TsService
   public Map<String, ManagedObjectReference> addDiskToDiskGroup(ManagedObjectReference clusterRef, ManagedObjectReference hostRef, VsanDiskMapping vsanDiskMappings) throws Exception {
      DiskMapping diskMapping = new DiskMapping();
      diskMapping.ssd = vsanDiskMappings.ssd;
      diskMapping.nonSsd = vsanDiskMappings.nonSsd;
      VsanDiskMappingSpec spec = new VsanDiskMappingSpec();
      spec.mappings = new Object[]{diskMapping};
      spec.clusterRef = clusterRef;
      spec.isAllFlashSupported = VsanCapabilityUtils.isAllFlashSupported(hostRef);
      ManagedObjectReference task = this.vsanMutationProvider.claimDisks(hostRef, spec);
      return ImmutableMap.of("task", new ManagedObjectReference(task.getType(), task.getValue(), hostRef.getServerGuid()));
   }

   @TsService
   public List<ManagedObjectReference> removeDisksAndMappings(ManagedObjectReference hostRef, RemoveDiskGroupSpec diskGroupSpec, RemoveDiskSpec diskSpec) throws Exception {
      List<ManagedObjectReference> tasks = new ArrayList();
      ManagedObjectReference task;
      if (diskGroupSpec != null && ArrayUtils.isNotEmpty(diskGroupSpec.mappings)) {
         task = this.removeDiskGroup(hostRef, diskGroupSpec);
         tasks.add(task);
      }

      if (diskSpec != null && ArrayUtils.isNotEmpty(diskSpec.disks)) {
         task = this.removeDisks(hostRef, diskSpec);
         tasks.add(task);
      }

      return tasks;
   }

   @TsService
   public ManagedObjectReference removeDiskGroup(ManagedObjectReference hostRef, RemoveDiskGroupSpec spec) throws Exception {
      VsanRemoveDiskGroupSpec vsanSpec = new VsanRemoveDiskGroupSpec();
      vsanSpec.evacuateData = new MaintenanceSpec();
      if (spec.decommissionMode != null) {
         DecommissionMode mode = new DecommissionMode(spec.decommissionMode.toString());
         vsanSpec.evacuateData.setVsanMode(mode);
      }

      ArrayList<DiskMapping> mappings = new ArrayList();
      VsanDiskMapping[] var8;
      int var7 = (var8 = spec.mappings).length;

      for(int var6 = 0; var6 < var7; ++var6) {
         VsanDiskMapping diskMapping = var8[var6];
         DiskMapping vsanDiskMapping = new DiskMapping();
         vsanDiskMapping.ssd = diskMapping.ssd;
         vsanDiskMapping.nonSsd = diskMapping.nonSsd;
         mappings.add(vsanDiskMapping);
      }

      vsanSpec.mappings = (DiskMapping[])mappings.toArray(new DiskMapping[mappings.size()]);
      ManagedObjectReference task = this.vsanMutationProvider.removeDiskGroup(hostRef, vsanSpec);
      return new ManagedObjectReference(task.getType(), task.getValue(), hostRef.getServerGuid());
   }

   @TsService
   public ManagedObjectReference removeDisks(ManagedObjectReference hostRef, RemoveDiskSpec spec) throws Exception {
      VsanRemoveDataDiskSpec vsanSpec = new VsanRemoveDataDiskSpec();
      vsanSpec.evacuateData = new MaintenanceSpec();
      if (spec.decommissionMode != null) {
         DecommissionMode mode = new DecommissionMode(spec.decommissionMode.toString());
         vsanSpec.evacuateData.setVsanMode(mode);
      }

      vsanSpec.disks = spec.disks;
      ManagedObjectReference task = this.vsanMutationProvider.removeDisk(hostRef, vsanSpec);
      return new ManagedObjectReference(task.getType(), task.getValue(), hostRef.getServerGuid());
   }

   @TsService
   public ManagedObjectReference mountDiskGroup(ManagedObjectReference param1, VsanDiskMapping param2) {
      // $FF: Couldn't be decompiled
   }

   @TsService
   public ManagedObjectReference unmountDiskGroup(ManagedObjectReference hostRef, UnmountDiskGroupSpec spec) {
      ManagedObjectReference unmountTask = null;
      DecommissionMode mode;
      VcConnection vcConnection;
      if (VsanCapabilityUtils.isUnmountWithMaintenanceModeSupported(hostRef)) {
         MaintenanceSpec maintenanceSpec = new MaintenanceSpec();
         if (spec.decommissionMode != null) {
            mode = new DecommissionMode(spec.decommissionMode.toString());
            maintenanceSpec.setVsanMode(mode);
         }

         try {
            Throwable var40 = null;
            vcConnection = null;

            try {
               VsanConnection vsanConnection = this.vsanClient.getConnection(hostRef.getServerGuid());

               try {
                  VsanSystemEx vsanSystemEx = vsanConnection.getVsanSystemEx(hostRef);
                  unmountTask = vsanSystemEx.unmountDiskMappingEx(new DiskMapping[]{spec.diskMapping.toVmodl()}, maintenanceSpec, 0);
               } finally {
                  if (vsanConnection != null) {
                     vsanConnection.close();
                  }

               }
            } catch (Throwable var37) {
               if (var40 == null) {
                  var40 = var37;
               } else if (var40 != var37) {
                  var40.addSuppressed(var37);
               }

               throw var40;
            }
         } catch (Exception var38) {
            logger.error("Failed to unmount disk group: ", var38);
            throw new VsanUiLocalizableException("vsan.manage.diskManagement.unmountDiskGroup.error");
         }
      } else {
         try {
            Throwable var39 = null;
            mode = null;

            try {
               vcConnection = this.vcClient.getConnection(hostRef.getServerGuid());

               try {
                  VsanSystem vsanSystem = vcConnection.getHostVsanSystem(hostRef);
                  unmountTask = vsanSystem.unmountDiskMapping(new DiskMapping[]{spec.diskMapping.toVmodl()});
               } finally {
                  if (vcConnection != null) {
                     vcConnection.close();
                  }

               }
            } catch (Throwable var34) {
               if (var39 == null) {
                  var39 = var34;
               } else if (var39 != var34) {
                  var39.addSuppressed(var34);
               }

               throw var39;
            }
         } catch (Exception var35) {
            logger.error("Failed to unmount disk group: ", var35);
            throw new VsanUiLocalizableException("vsan.manage.diskManagement.unmountDiskGroup.error");
         }
      }

      VmodlHelper.assignServerGuid(unmountTask, hostRef.getServerGuid());
      return unmountTask;
   }

   @TsService
   public ManagedObjectReference setDiskLedState(ManagedObjectReference hostRef, String[] diskUuids, boolean on) throws Exception {
      ManagedObjectReference storageSystemRef = (ManagedObjectReference)QueryUtil.getProperty(hostRef, "storageSystem", (Object)null);
      Throwable var6 = null;
      Object var7 = null;

      ManagedObjectReference taskRef;
      try {
         VcConnection vcConnection = this.vcClient.getConnection(hostRef.getServerGuid());

         try {
            StorageSystem storageSystem = (StorageSystem)vcConnection.createStub(StorageSystem.class, storageSystemRef);
            if (on) {
               taskRef = storageSystem.turnDiskLocatorLedOn(diskUuids);
            } else {
               taskRef = storageSystem.turnDiskLocatorLedOff(diskUuids);
            }
         } finally {
            if (vcConnection != null) {
               vcConnection.close();
            }

         }
      } catch (Throwable var15) {
         if (var6 == null) {
            var6 = var15;
         } else if (var6 != var15) {
            var6.addSuppressed(var15);
         }

         throw var6;
      }

      return new ManagedObjectReference(taskRef.getType(), taskRef.getValue(), hostRef.getServerGuid());
   }

   @TsService
   public List<ManagedObjectReference> setDiskLocality(ManagedObjectReference hostRef, String[] diskUuids, boolean local) throws Exception {
      ManagedObjectReference storageSystemRef = (ManagedObjectReference)QueryUtil.getProperty(hostRef, "storageSystem", (Object)null);
      List<ManagedObjectReference> tasks = new ArrayList(diskUuids.length);
      Throwable var6 = null;
      Object var7 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(hostRef.getServerGuid());

         try {
            StorageSystem storageSystem = (StorageSystem)vcConnection.createStub(StorageSystem.class, storageSystemRef);
            String[] var13 = diskUuids;
            int var12 = diskUuids.length;

            for(int var11 = 0; var11 < var12; ++var11) {
               String uuid = var13[var11];
               ManagedObjectReference task = local ? storageSystem.markAsLocal(uuid) : storageSystem.markAsNonLocal(uuid);
               tasks.add(new ManagedObjectReference(task.getType(), task.getValue(), hostRef.getServerGuid()));
            }
         } finally {
            if (vcConnection != null) {
               vcConnection.close();
            }

         }

         return tasks;
      } catch (Throwable var20) {
         if (var6 == null) {
            var6 = var20;
         } else if (var6 != var20) {
            var6.addSuppressed(var20);
         }

         throw var6;
      }
   }

   @TsService
   public List<ManagedObjectReference> setDiskType(ManagedObjectReference hostRef, String[] diskUuids, boolean ssd) throws Exception {
      ManagedObjectReference storageSystemRef = (ManagedObjectReference)QueryUtil.getProperty(hostRef, "storageSystem", (Object)null);
      List<ManagedObjectReference> tasks = new ArrayList(diskUuids.length);
      Throwable var6 = null;
      Object var7 = null;

      try {
         VcConnection vcConnection = this.vcClient.getConnection(hostRef.getServerGuid());

         try {
            StorageSystem storageSystem = (StorageSystem)vcConnection.createStub(StorageSystem.class, storageSystemRef);
            String[] var13 = diskUuids;
            int var12 = diskUuids.length;

            for(int var11 = 0; var11 < var12; ++var11) {
               String uuid = var13[var11];
               ManagedObjectReference task;
               if (ssd) {
                  task = storageSystem.markAsSsd(uuid);
               } else {
                  task = storageSystem.markAsNonSsd(uuid);
               }

               tasks.add(new ManagedObjectReference(task.getType(), task.getValue(), hostRef.getServerGuid()));
            }
         } finally {
            if (vcConnection != null) {
               vcConnection.close();
            }

         }

         return tasks;
      } catch (Throwable var20) {
         if (var6 == null) {
            var6 = var20;
         } else if (var6 != var20) {
            var6.addSuppressed(var20);
         }

         throw var6;
      }
   }
}
