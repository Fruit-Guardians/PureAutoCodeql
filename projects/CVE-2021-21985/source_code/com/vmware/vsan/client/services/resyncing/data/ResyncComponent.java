package com.vmware.vsan.client.services.resyncing.data;

import com.vmware.vim.binding.vim.vm.device.VirtualDisk;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentity;
import com.vmware.vim.vsan.binding.vim.vsan.host.VsanComponentSyncState;
import com.vmware.vim.vsan.binding.vim.vsan.host.VsanObjectSyncState;
import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.common.data.VmData;
import com.vmware.vsan.client.services.virtualobjects.data.VsanObjectHealthData;
import com.vmware.vsphere.client.vsan.base.data.IscsiLun;
import com.vmware.vsphere.client.vsan.base.data.IscsiTarget;
import com.vmware.vsphere.client.vsan.base.data.VsanObject;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectType;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

@data
public class ResyncComponent {
   public String name;
   public String iconId;
   public ManagedObjectReference moRef;
   public String storageProfile;
   public String hostName;
   public long bytesToResync;
   public long etaToResync;
   public ResyncComponent.ResyncReasonCode reason;
   public SortedSet<ResyncComponent> children;
   public boolean isQueued;
   public String uuid;
   private ResyncComponent parent;
   private VmData vmData;
   private static final String DISK_ICON = "disk-icon";
   private static final String FOLDER_ICON = "vsphere-icon-folder";
   private static final String ISCSI_TARGET_ICON = "iscsi-target-icon";
   private static final String ISCSI_LUN_ICON = "iscsi-lun-icon";

   public ResyncComponent() {
      this.isQueued = false;
      this.bytesToResync = 0L;
      this.etaToResync = -1L;
      this.children = new TreeSet(new ResyncComponent.ResyncComponentComparator());
   }

   public ResyncComponent(VmData vmData) {
      this();
      if (vmData != null) {
         this.name = vmData.name;
         this.iconId = vmData.primaryIconId;
         this.moRef = vmData.vmRef;
         this.vmData = vmData;
      }

   }

   public ResyncComponent(VsanObject iscsiData, VsanObjectSyncState syncData, VsanObjectHealthData healthData, Map<String, String> hostUuidToHostNames) {
      this();
      if (iscsiData != null) {
         this.uuid = iscsiData.vsanObjectUuid;
         if (iscsiData instanceof IscsiTarget) {
            this.name = ((IscsiTarget)iscsiData).alias;
            this.iconId = "iscsi-target-icon";
         } else if (iscsiData instanceof IscsiLun) {
            IscsiLun lun = (IscsiLun)iscsiData;
            String alias;
            if (!StringUtils.isEmpty(lun.alias)) {
               alias = lun.alias;
            } else {
               alias = "-";
            }

            this.name = Utils.getLocalizedString("vsan.virtualObjects.iscsiLun", alias, Integer.toString(lun.lunId));
            this.iconId = "iscsi-lun-icon";
         }
      }

      this.updateHealthData(healthData);
      if (syncData != null) {
         this.addComponents(syncData, hostUuidToHostNames);
      }

   }

   public ResyncComponent(String name, String hostName, long bytesToResync, long etaToResync, ResyncComponent.ResyncReasonCode reason) {
      this();
      this.name = name;
      this.hostName = hostName;
      this.bytesToResync = bytesToResync;
      this.etaToResync = etaToResync;
      this.reason = reason;
   }

   public ResyncComponent addChildObject(String name, VsanObjectIdentity objectIdentity, VsanObjectSyncState syncData, VsanObjectHealthData healthData, Map<String, String> hostUuidToHostNames) {
      ResyncComponent child = new ResyncComponent();
      child.name = name;
      child.uuid = objectIdentity.uuid;
      if (this.vmData != null) {
         child.parent = this;
         child.processVmObjects(objectIdentity, healthData, this.vmData);
      }

      child.updateHealthData(healthData);
      if (syncData != null) {
         child.addComponents(syncData, hostUuidToHostNames);
      }

      this.children.add(child);
      return this;
   }

   private void processVmObjects(VsanObjectIdentity objectIdentity, VsanObjectHealthData healthData, VmData vmData) {
      if (objectIdentity.type.equals(VsanObjectType.vdisk.toString())) {
         if (vmData.uuidToVirtualDiskMap.containsKey(objectIdentity.uuid)) {
            VirtualDisk virtualDisk = (VirtualDisk)vmData.uuidToVirtualDiskMap.get(objectIdentity.uuid);
            if (virtualDisk.deviceInfo != null) {
               this.name = virtualDisk.deviceInfo.label;
               this.iconId = "disk-icon";
            }
         } else if (vmData.uuidToDiskSnapshotMap.containsKey(objectIdentity.uuid)) {
            this.name = vmData.getSnapshotName(objectIdentity.uuid);
            this.iconId = "disk-icon";
         }
      } else if (objectIdentity.type.equals(VsanObjectType.namespace.toString())) {
         this.name = Utils.getLocalizedString("vsan.resyncing.components.vm.home.label");
         this.iconId = "vsphere-icon-folder";
         this.parent.updateHealthData(healthData);
      }

   }

   private void addComponents(VsanObjectSyncState syncData, Map<String, String> hostUuidToHostNames) {
      VsanComponentSyncState[] var6;
      int var5 = (var6 = syncData.components).length;

      for(int var4 = 0; var4 < var5; ++var4) {
         VsanComponentSyncState vmResyncComponent = var6[var4];
         ResyncComponent component = new ResyncComponent(vmResyncComponent.uuid, (String)hostUuidToHostNames.get(vmResyncComponent.hostUuid), vmResyncComponent.bytesToSync, vmResyncComponent.recoveryETA != null ? vmResyncComponent.recoveryETA : -1L, this.getResyncReason(vmResyncComponent.reasons));
         this.children.add(component);
      }

   }

   private void updateHealthData(VsanObjectHealthData healthData) {
      if (healthData != null) {
         this.storageProfile = healthData.policyName;
      }

   }

   private ResyncComponent.ResyncReasonCode getResyncReason(String[] reasons) {
      if (ArrayUtils.isEmpty(reasons)) {
         return ResyncComponent.ResyncReasonCode.stale;
      } else {
         EnumSet<ResyncComponent.ResyncReasonCode> resonsSet = EnumSet.noneOf(ResyncComponent.ResyncReasonCode.class);
         String[] var6 = reasons;
         int var5 = reasons.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            String reason = var6[var4];
            resonsSet.add(ResyncComponent.ResyncReasonCode.valueOf(reason));
         }

         return (ResyncComponent.ResyncReasonCode)resonsSet.iterator().next();
      }
   }

   public static class ResyncComponentComparator implements Comparator<ResyncComponent> {
      public int compare(ResyncComponent o1, ResyncComponent o2) {
         if (Utils.getLocalizedString("vsan.resyncing.components.other").equals(o1.name)) {
            return 1;
         } else if (Utils.getLocalizedString("vsan.resyncing.components.iscsi").equals(o1.name)) {
            return !Utils.getLocalizedString("vsan.resyncing.components.other").equals(o2.name) ? 1 : -1;
         } else if (Utils.getLocalizedString("vsan.resyncing.components.other").equals(o2.name)) {
            return -1;
         } else if (Utils.getLocalizedString("vsan.resyncing.components.iscsi").equals(o2.name)) {
            return !Utils.getLocalizedString("vsan.resyncing.components.other").equals(o1.name) ? -1 : 1;
         } else {
            return o1.name.compareTo(o2.name);
         }
      }
   }

   @data
   public static enum ResyncReasonCode {
      evacuate,
      dying_evacuate,
      rebalance,
      repair,
      reconfigure,
      stale,
      merge_concat;
   }

   @data
   public static enum ResyncStatusCode {
      active,
      queued,
      suspended;
   }
}
