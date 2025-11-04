package com.vmware.vsan.client.services.resyncing.data;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentity;
import com.vmware.vim.vsan.binding.vim.vsan.host.VsanObjectSyncState;
import com.vmware.vim.vsan.binding.vim.vsan.host.VsanSyncingObjectQueryResult;
import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.common.data.VmData;
import com.vmware.vsan.client.services.fileservice.model.VsanFileServiceShare;
import com.vmware.vsan.client.services.virtualobjects.data.VsanObjectHealthData;
import com.vmware.vsphere.client.vsan.base.data.IscsiLun;
import com.vmware.vsphere.client.vsan.base.data.IscsiTarget;
import com.vmware.vsphere.client.vsan.base.data.VsanObject;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectType;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import org.apache.commons.collections4.CollectionUtils;

@data
public class ResyncMonitorData {
   private static final String FILE_SHARE_ICON = "vsphere-icon-folder";
   public long etaToResync;
   public long activeETA;
   public long queuedETA;
   public long suspendedETA;
   public long bytesToResync;
   public long activeBytesToResync;
   public long queuedBytesToResync;
   public long suspendedBytesToResync;
   public long componentsToSync;
   public long activeComponentsToResync;
   public long queuedComponentsToResync;
   public long suspendedComponentsToResync;
   public DelayTimerData delayTimerData;
   public RepairTimerData repairTimerData;
   public boolean isResyncThrottlingSupported;
   public boolean isVsanClusterPartitioned;
   public boolean isResyncFilterApiSupported;
   public int resyncThrottlingValue;
   public SortedSet<ResyncComponent> components;
   private Map<String, String> hostUuidToHostNames;
   private Map<String, VsanObjectSyncState> componentsSyncData;

   public ResyncMonitorData() {
      this.componentsSyncData = new HashMap();
   }

   public ResyncMonitorData(VsanSyncingObjectQueryResult syncingObjectsData, Map<String, String> hostUuidToHostNames) {
      this();
      this.etaToResync = syncingObjectsData.totalRecoveryETA;
      if (syncingObjectsData.syncingObjectRecoveryDetails != null) {
         if (syncingObjectsData.syncingObjectRecoveryDetails.getBytesToSyncForActiveObjects() != null) {
            this.activeComponentsToResync = syncingObjectsData.syncingObjectRecoveryDetails.getActiveObjectsToSync();
            this.activeETA = syncingObjectsData.syncingObjectRecoveryDetails.getActivelySyncingObjectRecoveryETA();
            this.activeBytesToResync = syncingObjectsData.syncingObjectRecoveryDetails.getBytesToSyncForActiveObjects();
         }

         if (syncingObjectsData.syncingObjectRecoveryDetails.getBytesToSyncForQueuedObjects() != null) {
            this.queuedComponentsToResync = syncingObjectsData.syncingObjectRecoveryDetails.getQueuedObjectsToSync();
            this.queuedETA = syncingObjectsData.syncingObjectRecoveryDetails.getQueuedForSyncObjectRecoveryETA();
            this.queuedBytesToResync = syncingObjectsData.syncingObjectRecoveryDetails.getBytesToSyncForQueuedObjects();
         }

         if (syncingObjectsData.syncingObjectRecoveryDetails.getBytesToSyncForSuspendedObjects() != null) {
            this.suspendedComponentsToResync = syncingObjectsData.syncingObjectRecoveryDetails.getSuspendedObjectsToSync();
            this.suspendedETA = syncingObjectsData.syncingObjectRecoveryDetails.getSuspendedObjectRecoveryETA();
            this.suspendedBytesToResync = syncingObjectsData.syncingObjectRecoveryDetails.getBytesToSyncForSuspendedObjects();
         }
      }

      this.componentsToSync = syncingObjectsData.totalObjectsToSync;
      this.bytesToResync = syncingObjectsData.totalBytesToSync;
      this.hostUuidToHostNames = hostUuidToHostNames;
      if (syncingObjectsData.objects != null) {
         VsanObjectSyncState[] var6;
         int var5 = (var6 = syncingObjectsData.objects).length;

         for(int var4 = 0; var4 < var5; ++var4) {
            VsanObjectSyncState vsanObject = var6[var4];
            this.componentsSyncData.put(vsanObject.uuid, vsanObject);
         }
      }

      if (this.componentsSyncData.size() > 0) {
         this.components = new TreeSet(new ResyncComponent.ResyncComponentComparator());
      }

   }

   public Set<String> getVsanObjectUuids() {
      return this.componentsSyncData.keySet();
   }

   public ResyncMonitorData uniteResyncingObjects(ResyncMonitorData resyncData) {
      this.etaToResync = Math.max(this.etaToResync, resyncData.etaToResync);
      this.bytesToResync += resyncData.bytesToResync;
      this.componentsToSync += resyncData.componentsToSync;
      this.componentsSyncData.putAll(resyncData.componentsSyncData);
      if (this.components == null && this.componentsSyncData.size() > 0) {
         this.components = new TreeSet(new ResyncComponent.ResyncComponentComparator());
      }

      return this;
   }

   public ResyncMonitorData processVmObjects(List<VsanObjectIdentity> vmObjectIdentities, Map<ManagedObjectReference, VmData> vmDataMap, Map<String, VsanObjectHealthData> vsanHealthData) {
      Map<ManagedObjectReference, ResyncComponent> vmResyncDataMap = new HashMap();

      VsanObjectIdentity objectIdentity;
      ResyncComponent vmResyncData;
      for(Iterator var6 = vmObjectIdentities.iterator(); var6.hasNext(); vmResyncData.addChildObject(objectIdentity.description, objectIdentity, (VsanObjectSyncState)this.componentsSyncData.get(objectIdentity.uuid), (VsanObjectHealthData)vsanHealthData.get(objectIdentity.uuid), this.hostUuidToHostNames)) {
         objectIdentity = (VsanObjectIdentity)var6.next();
         VmData vmData = (VmData)vmDataMap.get(objectIdentity.vm);
         vmResyncData = (ResyncComponent)vmResyncDataMap.get(objectIdentity.vm);
         if (vmResyncData == null) {
            vmResyncData = new ResyncComponent(vmData);
            vmResyncDataMap.put(objectIdentity.vm, vmResyncData);
         }
      }

      Collection<ResyncComponent> resyncComponents = vmResyncDataMap.values();
      Iterator var11 = resyncComponents.iterator();

      while(var11.hasNext()) {
         ResyncComponent vmResyncData = (ResyncComponent)var11.next();
         this.updateTotalBytesAndEtaToSync(vmResyncData);
      }

      this.components.addAll(resyncComponents);
      return this;
   }

   public ResyncMonitorData processOtherObjects(List<VsanObjectIdentity> vmObjectIdentities, List<String> orphanedSyncObjects, Map<String, VsanObjectHealthData> vsanHealthData) {
      if (CollectionUtils.isEmpty(vmObjectIdentities) && CollectionUtils.isEmpty(orphanedSyncObjects)) {
         return this;
      } else {
         ResyncComponent othersComponent = new ResyncComponent();
         othersComponent.name = Utils.getLocalizedString("vsan.resyncing.components.other");
         Iterator var6;
         if (vmObjectIdentities != null) {
            var6 = vmObjectIdentities.iterator();

            while(var6.hasNext()) {
               VsanObjectIdentity objectIdentity = (VsanObjectIdentity)var6.next();
               othersComponent.addChildObject(objectIdentity.description, objectIdentity, (VsanObjectSyncState)this.componentsSyncData.get(objectIdentity.uuid), (VsanObjectHealthData)vsanHealthData.get(objectIdentity.uuid), this.hostUuidToHostNames);
            }
         }

         if (orphanedSyncObjects != null) {
            var6 = orphanedSyncObjects.iterator();

            while(var6.hasNext()) {
               String orphanedSyncObject = (String)var6.next();
               VsanObjectIdentity objIdentity = new VsanObjectIdentity();
               objIdentity.setUuid(orphanedSyncObject);
               objIdentity.setType(VsanObjectType.other.toString());
               othersComponent.addChildObject(orphanedSyncObject, objIdentity, (VsanObjectSyncState)this.componentsSyncData.get(orphanedSyncObject), (VsanObjectHealthData)vsanHealthData.get(orphanedSyncObject), this.hostUuidToHostNames);
            }
         }

         this.updateTotalBytesAndEtaToSync(othersComponent);
         this.components.add(othersComponent);
         return this;
      }
   }

   public ResyncMonitorData processFileShares(List<VsanObjectIdentity> identitiesList, Map<String, VsanObjectHealthData> healthData, List<VsanFileServiceShare> shares) {
      Map<String, VsanObjectIdentity> identities = identitiesToMap(identitiesList);
      Iterator var6 = shares.iterator();

      while(var6.hasNext()) {
         VsanFileServiceShare share = (VsanFileServiceShare)var6.next();
         ResyncComponent shareComponent = new ResyncComponent();
         shareComponent.name = share.config.name;
         this.components.add(shareComponent);
         shareComponent.iconId = "vsphere-icon-folder";
         Iterator var9 = share.objectUuids.iterator();

         while(var9.hasNext()) {
            String objectUuid = (String)var9.next();
            VsanObjectIdentity identity = (VsanObjectIdentity)identities.get(objectUuid);
            shareComponent.addChildObject(identity.description, identity, (VsanObjectSyncState)this.componentsSyncData.get(objectUuid), (VsanObjectHealthData)healthData.get(objectUuid), this.hostUuidToHostNames);
         }

         this.updateTotalBytesAndEtaToSync(shareComponent);
      }

      return this;
   }

   public ResyncMonitorData processIscsiObjects(List<VsanObjectIdentity> vmObjectIdentities, Map<String, VsanObjectHealthData> vsanHealthData, Map<String, VsanObject> iscsiObjects) {
      ResyncComponent iscsiComponent = new ResyncComponent();
      iscsiComponent.name = Utils.getLocalizedString("vsan.resyncing.components.iscsi");
      List<IscsiLun> iscsiLuns = new ArrayList();
      Iterator var7 = vmObjectIdentities.iterator();

      while(true) {
         while(var7.hasNext()) {
            VsanObjectIdentity objectIdentity = (VsanObjectIdentity)var7.next();
            if (iscsiObjects != null && iscsiObjects.containsKey(objectIdentity.uuid)) {
               VsanObject iscsiTargetObject = (VsanObject)iscsiObjects.get(objectIdentity.uuid);
               if (iscsiTargetObject instanceof IscsiTarget) {
                  ResyncComponent iscsiTargetComponent = new ResyncComponent(iscsiTargetObject, (VsanObjectSyncState)this.componentsSyncData.get(objectIdentity.uuid), (VsanObjectHealthData)vsanHealthData.get(objectIdentity.uuid), this.hostUuidToHostNames);
                  iscsiTargetComponent.uuid = objectIdentity.uuid;
                  iscsiComponent.children.add(iscsiTargetComponent);
               } else if (iscsiTargetObject instanceof IscsiLun) {
                  iscsiLuns.add((IscsiLun)iscsiTargetObject);
               }
            } else {
               iscsiComponent.addChildObject(objectIdentity.description, objectIdentity, (VsanObjectSyncState)this.componentsSyncData.get(objectIdentity.uuid), (VsanObjectHealthData)vsanHealthData.get(objectIdentity.uuid), this.hostUuidToHostNames);
            }
         }

         var7 = iscsiLuns.iterator();

         while(true) {
            Iterator var11;
            ResyncComponent iscsiTargetComponent;
            IscsiLun iscsiLun;
            String targetAlias;
            boolean parentTargetFound;
            do {
               if (!var7.hasNext()) {
                  this.updateTotalBytesAndEtaToSync(iscsiComponent);
                  this.components.add(iscsiComponent);
                  return this;
               }

               iscsiLun = (IscsiLun)var7.next();
               targetAlias = iscsiLun.targetAlias;
               parentTargetFound = false;
               var11 = iscsiComponent.children.iterator();

               while(var11.hasNext()) {
                  ResyncComponent iscsiTargetComponent = (ResyncComponent)var11.next();
                  if (iscsiTargetComponent.name.equals(targetAlias)) {
                     parentTargetFound = true;
                     iscsiTargetComponent = new ResyncComponent(iscsiLun, (VsanObjectSyncState)this.componentsSyncData.get(iscsiLun.vsanObjectUuid), (VsanObjectHealthData)vsanHealthData.get(iscsiLun.vsanObjectUuid), this.hostUuidToHostNames);
                     iscsiTargetComponent.children.add(iscsiTargetComponent);
                  }
               }
            } while(parentTargetFound);

            var11 = iscsiObjects.values().iterator();

            while(var11.hasNext()) {
               VsanObject vsanObject = (VsanObject)var11.next();
               if (vsanObject instanceof IscsiTarget && ((IscsiTarget)vsanObject).alias.equals(targetAlias)) {
                  iscsiTargetComponent = new ResyncComponent(vsanObject, (VsanObjectSyncState)this.componentsSyncData.get(vsanObject.vsanObjectUuid), (VsanObjectHealthData)vsanHealthData.get(vsanObject.vsanObjectUuid), this.hostUuidToHostNames);
                  iscsiComponent.children.add(iscsiTargetComponent);
                  ResyncComponent iscsiLunComponent = new ResyncComponent(iscsiLun, (VsanObjectSyncState)this.componentsSyncData.get(iscsiLun.vsanObjectUuid), (VsanObjectHealthData)vsanHealthData.get(iscsiLun.vsanObjectUuid), this.hostUuidToHostNames);
                  iscsiTargetComponent.children.add(iscsiLunComponent);
               }
            }
         }
      }
   }

   private void updateTotalBytesAndEtaToSync(ResyncComponent resyncComponent) {
      ResyncComponent childComponent;
      for(Iterator var3 = resyncComponent.children.iterator(); var3.hasNext(); resyncComponent.etaToResync = Math.max(childComponent.etaToResync, resyncComponent.etaToResync)) {
         childComponent = (ResyncComponent)var3.next();
         if (childComponent.children.size() > 0) {
            this.updateTotalBytesAndEtaToSync(childComponent);
         }

         resyncComponent.bytesToResync += childComponent.bytesToResync;
      }

   }

   private static Map<String, VsanObjectIdentity> identitiesToMap(Collection<VsanObjectIdentity> identities) {
      Map<String, VsanObjectIdentity> identityMap = new HashMap();
      Iterator var3 = identities.iterator();

      while(var3.hasNext()) {
         VsanObjectIdentity identity = (VsanObjectIdentity)var3.next();
         identityMap.put(identity.uuid, identity);
      }

      return identityMap;
   }
}
