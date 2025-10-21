package com.vmware.vsan.client.services.virtualobjects.data;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.vmware.vim.binding.vim.vm.ConfigInfo;
import com.vmware.vim.binding.vim.vm.device.VirtualDevice;
import com.vmware.vim.binding.vim.vm.device.VirtualDisk;
import com.vmware.vim.binding.vim.vm.device.VirtualDevice.FileBackingInfo;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiLUN;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTarget;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentity;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentityAndHealth;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectInformation;
import com.vmware.vim.vsan.binding.vim.vsan.FileShare;
import com.vmware.vsan.client.services.virtualobjects.VirtualObjectsUtil;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectDataProtectionHealthState;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectHealthState;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectType;
import com.vmware.vsphere.client.vsan.util.Utils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Component;

@Component
public class VirtualObjectModelFactory {
   private static final String DISK_ICON = "disk-icon";
   private static final String FOLDER_ICON = "folder";
   private static final String VSPHERE_FOLDER_ICON = "vsphere-icon-folder";
   private static final String ISCSI_TARGET_ICON = "iscsi-target-icon";
   private static final String ISCSI_LUN_ICON = "iscsi-lun-icon";
   private static final String CNS_VOLUME_ICON = "cns-volume";
   // $FF: synthetic field
   private static int[] $SWITCH_TABLE$com$vmware$vsphere$client$vsan$base$data$VsanObjectType;

   public List<VirtualObjectModel> buildVms(VsanObjectIdentityAndHealth objectIdentityAndHealth, Set<ManagedObjectReference> vmRefs, VsanObjectInformation[] vsanObjectInformations, Map<Object, Map<String, Object>> dsProperties, Multimap<ManagedObjectReference, ConfigInfo> snapshots, Map<String, String> policies) {
      Multimap<String, VirtualObjectModel> objInfosByUuid = this.getVirtualObjectModelByUuid(objectIdentityAndHealth, vsanObjectInformations, policies);
      Multimap<String, VsanObjectIdentity> allIdentitiesByUuid = mapAllObjIdentByUuid(objectIdentityAndHealth.identities);
      Multimap<ManagedObjectReference, VirtualObjectModel> vmObjectsByVmRef = buildVmObjects(objInfosByUuid, allIdentitiesByUuid, dsProperties, snapshots);
      List<VirtualObjectModel> vmModels = this.buildVmModels(vmRefs, vmObjectsByVmRef, dsProperties);
      return vmModels;
   }

   private static Multimap<ManagedObjectReference, VirtualObjectModel> buildVmObjects(Multimap<String, VirtualObjectModel> objInfoMap, Multimap<String, VsanObjectIdentity> identitiesByUuid, Map<Object, Map<String, Object>> dsProperties, Multimap<ManagedObjectReference, ConfigInfo> snapshots) {
      Multimap<ManagedObjectReference, VirtualObjectModel> vmObjects = HashMultimap.create();
      Iterator var6 = objInfoMap.values().iterator();

      while(true) {
         while(true) {
            VirtualObjectModel vmObjectModel;
            ManagedObjectReference vmRef;
            HashSet fcdVMs;
            Iterator var10;
            do {
               label65:
               do {
                  if (!var6.hasNext()) {
                     return vmObjects;
                  }

                  vmObjectModel = (VirtualObjectModel)var6.next();
                  vmRef = vmObjectModel.vmRef;
                  fcdVMs = new HashSet();
                  switch($SWITCH_TABLE$com$vmware$vsphere$client$vsan$base$data$VsanObjectType()[vmObjectModel.objectType.ordinal()]) {
                  case 1:
                     vmObjectModel.name = Utils.getLocalizedString("vsan.virtualObjects.vmSwap");
                     break;
                  case 2:
                     VirtualDevice[] devices = dsProperties.containsKey(vmRef) ? (VirtualDevice[])((Map)dsProperties.get(vmRef)).get("config.hardware.device") : new VirtualDevice[0];
                     Collection<ConfigInfo> snapshotConfig = snapshots.containsKey(vmRef) ? snapshots.get(vmRef) : Collections.emptyList();
                     vmObjectModel.name = getDiskLabel(vmObjectModel.uid, vmObjectModel.name, devices, (Collection)snapshotConfig);
                     vmObjectModel.iconId = "disk-icon";
                     break;
                  case 3:
                     vmObjectModel.name = Utils.getLocalizedString("vsan.virtualObjects.vmHome");
                     vmObjectModel.iconId = "folder";
                     break;
                  case 4:
                     vmObjectModel.name = Utils.getLocalizedString("vsan.virtualObjects.vmMemory");
                     break;
                  case 13:
                     vmObjectModel.iconId = "disk-icon";
                     var10 = identitiesByUuid.get(vmObjectModel.uid).iterator();

                     while(true) {
                        VsanObjectIdentity vsanObjIdentity;
                        VsanObjectType identityType;
                        do {
                           do {
                              if (!var10.hasNext()) {
                                 continue label65;
                              }

                              vsanObjIdentity = (VsanObjectIdentity)var10.next();
                              identityType = VsanObjectType.parse(vsanObjIdentity.type);
                           } while(vsanObjIdentity.vm == null);
                        } while(VsanObjectType.vdisk != identityType && VsanObjectType.namespace != identityType);

                        fcdVMs.add(vsanObjIdentity.vm);
                     }
                  case 16:
                  case 18:
                     vmObjectModel.iconId = "cns-volume";
                  }
               } while(vmObjectModel.name == null);
            } while(vmRef == null);

            if (vmObjectModel.healthState == null) {
               vmObjectModel.healthState = VsanObjectHealthState.INACCESSIBLE;
            }

            if (!CollectionUtils.isEmpty(fcdVMs)) {
               var10 = fcdVMs.iterator();

               while(var10.hasNext()) {
                  ManagedObjectReference fcdVmRef = (ManagedObjectReference)var10.next();
                  vmObjects.put(fcdVmRef, vmObjectModel);
               }
            } else {
               vmObjects.put(vmObjectModel.vmRef, vmObjectModel);
            }
         }
      }
   }

   private List<VirtualObjectModel> buildVmModels(Set<ManagedObjectReference> vmRefs, Multimap<ManagedObjectReference, VirtualObjectModel> vmObjectsByVmRef, Map<Object, Map<String, Object>> dsProperties) {
      List<VirtualObjectModel> vmModels = new ArrayList();
      Iterator var6 = vmRefs.iterator();

      while(var6.hasNext()) {
         ManagedObjectReference vmRef = (ManagedObjectReference)var6.next();
         VirtualObjectModel vmModel = new VirtualObjectModel();
         vmModel.vmRef = vmRef;
         vmModel.applicableFilter = VirtualObjectsFilter.VMS;
         vmModel.iconId = "" + ((Map)dsProperties.get(vmRef)).get("primaryIconId");
         vmModel.name = "" + ((Map)dsProperties.get(vmRef)).get("name");
         vmModel.children = (VirtualObjectModel[])vmObjectsByVmRef.get(vmRef).toArray(new VirtualObjectModel[vmObjectsByVmRef.get(vmRef).size()]);
         Arrays.sort(vmModel.children, VirtualObjectModel.COMPARATOR);
         vmModel.healthState = VsanObjectHealthState.HEALTHY;
         VirtualObjectModel[] var11;
         int var10 = (var11 = vmModel.children).length;

         VirtualObjectModel child;
         int var9;
         for(var9 = 0; var9 < var10; ++var9) {
            child = var11[var9];
            if (child.healthState.ordinal() > vmModel.healthState.ordinal()) {
               vmModel.healthState = child.healthState;
            }
         }

         vmModel.dataProtectionHealthState = null;
         var10 = (var11 = vmModel.children).length;

         for(var9 = 0; var9 < var10; ++var9) {
            child = var11[var9];
            if (vmModel.dataProtectionHealthState == null) {
               vmModel.dataProtectionHealthState = child.dataProtectionHealthState;
            }

            if (child.dataProtectionHealthState != null && child.dataProtectionHealthState.ordinal() > vmModel.dataProtectionHealthState.ordinal()) {
               vmModel.dataProtectionHealthState = child.dataProtectionHealthState;
            }
         }

         vmModels.add(vmModel);
      }

      Collections.sort(vmModels, VirtualObjectModel.COMPARATOR);
      return vmModels;
   }

   public List<VirtualObjectModel> buildOthers(Set<String> allVsanUuids, VsanObjectIdentityAndHealth objectIdentityAndHealth, VsanObjectInformation[] vsanObjectInformations, Map<String, String> policies) {
      Multimap<String, VirtualObjectModel> objInfoByUuid = this.getVirtualObjectModelByUuid(objectIdentityAndHealth, vsanObjectInformations, policies);
      List<VirtualObjectModel> otherObjects = new ArrayList();
      List<VirtualObjectModel> cgObjects = new ArrayList();
      Iterator var9 = allVsanUuids.iterator();

      while(var9.hasNext()) {
         String vsanUuid = (String)var9.next();
         Iterator var11 = objInfoByUuid.get(vsanUuid).iterator();

         while(var11.hasNext()) {
            VirtualObjectModel model = (VirtualObjectModel)var11.next();
            if (model != null && model.isOtherType()) {
               if ("consistency group".equalsIgnoreCase(model.name)) {
                  model.objectType = VsanObjectType.dpConsistencyGroup;
                  cgObjects.add(model);
               } else {
                  otherObjects.add(model);
               }
            }
         }
      }

      if (cgObjects.size() > 0) {
         Collections.sort(cgObjects, VirtualObjectModel.COMPARATOR);
         VirtualObjectModel cgGroupModel = new VirtualObjectModel();
         cgGroupModel.name = Utils.getLocalizedString("vsan.virtualObjects.consistency.group.label");
         cgGroupModel.children = (VirtualObjectModel[])cgObjects.toArray(new VirtualObjectModel[cgObjects.size()]);
         otherObjects.add(cgGroupModel);
      }

      Collections.sort(otherObjects, VirtualObjectModel.COMPARATOR);
      return otherObjects;
   }

   public List<VirtualObjectModel> buildIscsiTargets(VsanIscsiTarget[] vsanIscsiTargets, VsanIscsiLUN[] vsanIscsiLUNs, Map<String, String> policies) {
      List<VirtualObjectModel> result = new ArrayList();
      VsanIscsiTarget[] var8 = vsanIscsiTargets;
      int var7 = vsanIscsiTargets.length;

      for(int var6 = 0; var6 < var7; ++var6) {
         VsanIscsiTarget iscsiTarget = var8[var6];
         VirtualObjectModel iscsiModel = this.fromVsanObjectInformation((VsanObjectIdentity)null, iscsiTarget.objectInformation, (VirtualObjectHealthModel)null, policies);
         iscsiModel.name = iscsiTarget.alias;
         iscsiModel.iconId = "iscsi-target-icon";
         iscsiModel.applicableFilter = VirtualObjectsFilter.ISCSI_TARGETS;
         iscsiModel.objectType = VsanObjectType.iscsiTarget;
         result.add(iscsiModel);
         if (vsanIscsiLUNs != null) {
            List<VirtualObjectModel> lunModels = new ArrayList();
            VsanIscsiLUN[] var14 = vsanIscsiLUNs;
            int var13 = vsanIscsiLUNs.length;

            for(int var12 = 0; var12 < var13; ++var12) {
               VsanIscsiLUN lun = var14[var12];
               if (lun.targetAlias.equals(iscsiTarget.alias)) {
                  VirtualObjectModel lunModel = this.fromVsanObjectInformation((VsanObjectIdentity)null, lun.objectInformation, (VirtualObjectHealthModel)null, policies);
                  lunModel.name = Utils.getLocalizedString("vsan.virtualObjects.iscsiLun", lun.alias != null ? lun.alias : "", Integer.toString(lun.lunId != null ? lun.lunId : 0)).trim();
                  lunModel.iconId = "iscsi-lun-icon";
                  lunModel.objectType = VsanObjectType.iscsiLun;
                  lunModels.add(lunModel);
               }
            }

            iscsiModel.children = (VirtualObjectModel[])lunModels.toArray(new VirtualObjectModel[lunModels.size()]);
            Arrays.sort(iscsiModel.children, VirtualObjectModel.COMPARATOR);
         }
      }

      Collections.sort(result, VirtualObjectModel.COMPARATOR);
      return result;
   }

   public List<VirtualObjectModel> buildFcds(VsanObjectIdentityAndHealth objectIdentityAndHealth, VsanObjectInformation[] vsanObjectInformations, Map<String, String> policies) {
      List<VirtualObjectModel> allFcds = new ArrayList();
      Multimap<String, VirtualObjectModel> vsanObjectInfosByUuid = this.getVirtualObjectModelByUuid(objectIdentityAndHealth, vsanObjectInformations, policies);
      Iterator var7 = vsanObjectInfosByUuid.values().iterator();

      while(true) {
         VirtualObjectModel model;
         label24:
         while(true) {
            do {
               if (!var7.hasNext()) {
                  Collections.sort(allFcds, VirtualObjectModel.COMPARATOR);
                  return allFcds;
               }

               model = (VirtualObjectModel)var7.next();
            } while(model.vmRef != null);

            switch($SWITCH_TABLE$com$vmware$vsphere$client$vsan$base$data$VsanObjectType()[model.objectType.ordinal()]) {
            case 13:
               model.iconId = "disk-icon";
               model.applicableFilter = VirtualObjectsFilter.FCD_OBJECTS;
               break label24;
            case 14:
            case 15:
            case 16:
            case 18:
            default:
               break;
            case 17:
            case 19:
               model.iconId = "cns-volume";
               model.applicableFilter = VirtualObjectsFilter.VOLUMES;
               break label24;
            }
         }

         allFcds.add(model);
      }
   }

   public List<VirtualObjectModel> buildFileShares(FileShare[] fileShares, VsanObjectIdentityAndHealth objectIdentityAndHealth, VsanObjectInformation[] vsanObjectInformations, Map<String, String> policies) {
      if (ArrayUtils.isEmpty(fileShares)) {
         return Collections.EMPTY_LIST;
      } else {
         List<VirtualObjectModel> result = new ArrayList(fileShares.length);
         Multimap<String, VirtualObjectModel> objInfoMap = this.getVirtualObjectModelByUuid(objectIdentityAndHealth, vsanObjectInformations, policies);
         FileShare[] var10 = fileShares;
         int var9 = fileShares.length;

         for(int var8 = 0; var8 < var9; ++var8) {
            FileShare fileShare = var10[var8];
            VirtualObjectModel share = new VirtualObjectModel();
            share.name = fileShare.config.name;
            share.iconId = "vsphere-icon-folder";
            share.objectType = VsanObjectType.fileShare;
            share.applicableFilter = VirtualObjectsFilter.FILE_SHARES;
            result.add(share);
            List<VirtualObjectModel> children = new ArrayList(fileShare.runtime.vsanObjectUuids.length);
            String[] var16;
            int var15 = (var16 = fileShare.runtime.vsanObjectUuids).length;

            for(int var14 = 0; var14 < var15; ++var14) {
               String childUuid = var16[var14];
               Iterator var18 = objInfoMap.get(childUuid).iterator();

               while(var18.hasNext()) {
                  VirtualObjectModel shareObject = (VirtualObjectModel)var18.next();
                  shareObject.objectType = VsanObjectType.fileShare;
                  shareObject.applicableFilter = VirtualObjectsFilter.FILE_SHARES;
                  children.add(shareObject);
               }
            }

            share.children = (VirtualObjectModel[])children.toArray(new VirtualObjectModel[0]);
         }

         return result;
      }
   }

   private static Multimap<String, VsanObjectIdentity> mapAllObjIdentByUuid(VsanObjectIdentity[] identities) {
      Multimap<String, VsanObjectIdentity> objIdentityByVsanUuid = ArrayListMultimap.create();
      if (ArrayUtils.isEmpty(identities)) {
         return objIdentityByVsanUuid;
      } else {
         VsanObjectIdentity[] var5 = identities;
         int var4 = identities.length;

         for(int var3 = 0; var3 < var4; ++var3) {
            VsanObjectIdentity identity = var5[var3];
            objIdentityByVsanUuid.put(identity.uuid, identity);
         }

         return objIdentityByVsanUuid;
      }
   }

   private Multimap<String, VirtualObjectModel> getVirtualObjectModelByUuid(VsanObjectIdentityAndHealth identitiesAndHealth, VsanObjectInformation[] vsanObjectInformations, Map<String, String> policies) {
      if (ArrayUtils.isEmpty(identitiesAndHealth.identities)) {
         return HashMultimap.create();
      } else {
         Map<String, ManagedObjectReference> vmByVsanUuid = getVmsByVsanUuid(identitiesAndHealth.identities);
         Map<String, VsanObjectInformation> objInfoByUuid = getObjectInfoByVsanUuid(vsanObjectInformations);
         Map<String, VirtualObjectHealthModel> objectHealthByUuid = VirtualObjectsUtil.getVsanObjectsHealthMap(identitiesAndHealth.health);
         Multimap<String, VirtualObjectModel> result = HashMultimap.create();
         VsanObjectIdentity[] var11;
         int var10 = (var11 = identitiesAndHealth.identities).length;

         for(int var9 = 0; var9 < var10; ++var9) {
            VsanObjectIdentity identity = var11[var9];
            VirtualObjectModel objData = this.fromVsanObjectInformation(identity, (VsanObjectInformation)objInfoByUuid.get(identity.uuid), (VirtualObjectHealthModel)objectHealthByUuid.get(identity.uuid), policies);
            if (identity.vm == null) {
               identity.vm = (ManagedObjectReference)vmByVsanUuid.get(identity.uuid);
            }

            result.put(identity.uuid, objData);
         }

         return result;
      }
   }

   private static Map<String, ManagedObjectReference> getVmsByVsanUuid(VsanObjectIdentity[] identities) {
      Map<String, ManagedObjectReference> vmByVsanUuid = new HashMap();
      VsanObjectIdentity[] var5 = identities;
      int var4 = identities.length;

      for(int var3 = 0; var3 < var4; ++var3) {
         VsanObjectIdentity identity = var5[var3];
         if (identity.vm != null) {
            vmByVsanUuid.put(identity.uuid, identity.vm);
         }
      }

      return vmByVsanUuid;
   }

   private static Map<String, VsanObjectInformation> getObjectInfoByVsanUuid(VsanObjectInformation[] vsanObjectInformations) {
      Map<String, VsanObjectInformation> objInfoByVsanUuid = new HashMap();
      VsanObjectInformation[] var5 = vsanObjectInformations;
      int var4 = vsanObjectInformations.length;

      for(int var3 = 0; var3 < var4; ++var3) {
         VsanObjectInformation info = var5[var3];
         objInfoByVsanUuid.put(info.vsanObjectUuid, info);
      }

      return objInfoByVsanUuid;
   }

   private VirtualObjectModel fromVsanObjectInformation(VsanObjectIdentity identity, VsanObjectInformation objectInformation, VirtualObjectHealthModel objectHealth, Map<String, String> policies) {
      VirtualObjectModel result = new VirtualObjectModel();
      if (identity != null) {
         result.uid = identity.uuid;
         result.name = identity.description;
         result.vmRef = identity.vm;
         result.objectType = VsanObjectType.parse(identity.type);
      } else {
         result.uid = result.name = objectInformation.vsanObjectUuid;
      }

      if (objectInformation != null) {
         result.storagePolicy = policies.containsKey(objectInformation.spbmProfileUuid) ? (String)policies.get(objectInformation.spbmProfileUuid) : objectInformation.spbmProfileUuid;
         result.healthState = VsanObjectHealthState.fromString(objectInformation.vsanHealth);
         if (objectInformation.vsanDataProtectionHealth != null) {
            result.dataProtectionHealthState = VsanObjectDataProtectionHealthState.fromString(objectInformation.vsanDataProtectionHealth);
         }
      } else {
         result.storagePolicy = policies.containsKey(identity.spbmProfileUuid) ? (String)policies.get(identity.spbmProfileUuid) : identity.spbmProfileUuid;
         if (objectHealth != null) {
            result.healthState = VsanObjectHealthState.fromString(objectHealth.health);
            if (StringUtils.isNotEmpty(objectHealth.dataProtectionHealth)) {
               result.dataProtectionHealthState = VsanObjectDataProtectionHealthState.fromString(objectHealth.dataProtectionHealth);
            }
         } else {
            result.healthState = VsanObjectHealthState.UNKNOWN;
            result.dataProtectionHealthState = VsanObjectDataProtectionHealthState.UNKNOWN;
         }
      }

      return result;
   }

   private static String getDiskLabel(String uuid, String objectName, VirtualDevice[] devices, Collection<ConfigInfo> configSnapshots) {
      VirtualDisk disk = VirtualObjectsUtil.findDisk(devices, uuid);
      if (disk != null) {
         return disk.deviceInfo.label;
      } else {
         Iterator var6 = configSnapshots.iterator();

         while(var6.hasNext()) {
            ConfigInfo configSnapshot = (ConfigInfo)var6.next();
            disk = VirtualObjectsUtil.findDisk(configSnapshot.hardware.device, uuid);
            if (disk != null) {
               String path = ((FileBackingInfo)disk.backing).fileName;
               int lastSeparator = path.lastIndexOf(47);
               if (lastSeparator != -1) {
                  path = path.substring(lastSeparator + 1);
               }

               return Utils.getLocalizedString("vsan.virtualObjects.vmSnapshot", disk.deviceInfo.label, path);
            }
         }

         return objectName;
      }
   }

   // $FF: synthetic method
   static int[] $SWITCH_TABLE$com$vmware$vsphere$client$vsan$base$data$VsanObjectType() {
      int[] var10000 = $SWITCH_TABLE$com$vmware$vsphere$client$vsan$base$data$VsanObjectType;
      if (var10000 != null) {
         return var10000;
      } else {
         int[] var0 = new int[VsanObjectType.values().length];

         try {
            var0[VsanObjectType.attachedCnsVolBlock.ordinal()] = 16;
         } catch (NoSuchFieldError var21) {
         }

         try {
            var0[VsanObjectType.attachedCnsVolFile.ordinal()] = 18;
         } catch (NoSuchFieldError var20) {
         }

         try {
            var0[VsanObjectType.checksumOverhead.ordinal()] = 12;
         } catch (NoSuchFieldError var19) {
         }

         try {
            var0[VsanObjectType.dedupOverhead.ordinal()] = 10;
         } catch (NoSuchFieldError var18) {
         }

         try {
            var0[VsanObjectType.detachedCnsVolBlock.ordinal()] = 17;
         } catch (NoSuchFieldError var17) {
         }

         try {
            var0[VsanObjectType.detachedCnsVolFile.ordinal()] = 19;
         } catch (NoSuchFieldError var16) {
         }

         try {
            var0[VsanObjectType.dpConsistencyGroup.ordinal()] = 21;
         } catch (NoSuchFieldError var15) {
         }

         try {
            var0[VsanObjectType.fileShare.ordinal()] = 15;
         } catch (NoSuchFieldError var14) {
         }

         try {
            var0[VsanObjectType.fileSystemOverhead.ordinal()] = 9;
         } catch (NoSuchFieldError var13) {
         }

         try {
            var0[VsanObjectType.improvedVirtualDisk.ordinal()] = 13;
         } catch (NoSuchFieldError var12) {
         }

         try {
            var0[VsanObjectType.iscsiLun.ordinal()] = 7;
         } catch (NoSuchFieldError var11) {
         }

         try {
            var0[VsanObjectType.iscsiTarget.ordinal()] = 6;
         } catch (NoSuchFieldError var10) {
         }

         try {
            var0[VsanObjectType.namespace.ordinal()] = 3;
         } catch (NoSuchFieldError var9) {
         }

         try {
            var0[VsanObjectType.other.ordinal()] = 8;
         } catch (NoSuchFieldError var8) {
         }

         try {
            var0[VsanObjectType.spaceUnderDedupConsideration.ordinal()] = 11;
         } catch (NoSuchFieldError var7) {
         }

         try {
            var0[VsanObjectType.statsdb.ordinal()] = 5;
         } catch (NoSuchFieldError var6) {
         }

         try {
            var0[VsanObjectType.transientSpace.ordinal()] = 14;
         } catch (NoSuchFieldError var5) {
         }

         try {
            var0[VsanObjectType.vdisk.ordinal()] = 2;
         } catch (NoSuchFieldError var4) {
         }

         try {
            var0[VsanObjectType.vdiskSnapshot.ordinal()] = 20;
         } catch (NoSuchFieldError var3) {
         }

         try {
            var0[VsanObjectType.vmem.ordinal()] = 4;
         } catch (NoSuchFieldError var2) {
         }

         try {
            var0[VsanObjectType.vmswap.ordinal()] = 1;
         } catch (NoSuchFieldError var1) {
         }

         $SWITCH_TABLE$com$vmware$vsphere$client$vsan$base$data$VsanObjectType = var0;
         return var0;
      }
   }
}
