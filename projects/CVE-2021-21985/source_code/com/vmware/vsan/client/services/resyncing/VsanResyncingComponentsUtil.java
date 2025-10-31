package com.vmware.vsan.client.services.resyncing;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentity;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectIdentityAndHealth;
import com.vmware.vim.vsan.binding.vim.cluster.VsanObjectInformation;
import com.vmware.vsan.client.services.common.data.VmData;
import com.vmware.vsan.client.services.virtualobjects.VirtualObjectsUtil;
import com.vmware.vsan.client.services.virtualobjects.data.VirtualObjectHealthModel;
import com.vmware.vsan.client.services.virtualobjects.data.VirtualObjectsFilter;
import com.vmware.vsan.client.services.virtualobjects.data.VsanObjectHealthData;
import com.vmware.vsan.client.util.VmodlHelper;
import com.vmware.vsphere.client.vsan.base.util.VsanProfiler;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanResyncingComponentsUtil {
   private static final String IDENTITY_TYPE_FILE_SHARE = "fileShare";
   private static final String IDENTITY_TYPE_ISCSI_PREFFIX = "iscsi";
   private static final Log _logger = LogFactory.getLog(VsanResyncingComponentsUtil.class);
   private static final VsanProfiler _profiler = new VsanProfiler(VsanResyncingComponentsUtil.class);
   private static final String[] DISK_MAPPINGS_VM_PROPERTIES = new String[]{"name", "primaryIconId", "config.hardware.device", "summary.config.vmPathName"};

   public static Map<VirtualObjectsFilter, List<VsanObjectIdentity>> getVirtualObjectsFilterToObjectIdentities(VsanObjectIdentityAndHealth vsanObjectIdentityAndHealth) {
      Map<VirtualObjectsFilter, List<VsanObjectIdentity>> result = initVsanObjectIdentitiesMap();
      if (vsanObjectIdentityAndHealth != null && !ArrayUtils.isEmpty(vsanObjectIdentityAndHealth.identities)) {
         VsanObjectIdentity[] var5;
         int var4 = (var5 = vsanObjectIdentityAndHealth.identities).length;

         for(int var3 = 0; var3 < var4; ++var3) {
            VsanObjectIdentity objectIdentity = var5[var3];
            if (isVmObject(objectIdentity)) {
               ((List)result.get(VirtualObjectsFilter.VMS)).add(objectIdentity);
            } else if (isIsciObject(objectIdentity)) {
               ((List)result.get(VirtualObjectsFilter.ISCSI_TARGETS)).add(objectIdentity);
            } else if (isFileShare(objectIdentity)) {
               ((List)result.get(VirtualObjectsFilter.FILE_SHARES)).add(objectIdentity);
            } else {
               ((List)result.get(VirtualObjectsFilter.OTHERS)).add(objectIdentity);
            }
         }

         return result;
      } else {
         return result;
      }
   }

   private static Map<VirtualObjectsFilter, List<VsanObjectIdentity>> initVsanObjectIdentitiesMap() {
      Map<VirtualObjectsFilter, List<VsanObjectIdentity>> result = new HashMap();
      result.put(VirtualObjectsFilter.VMS, new ArrayList());
      result.put(VirtualObjectsFilter.ISCSI_TARGETS, new ArrayList());
      result.put(VirtualObjectsFilter.FILE_SHARES, new ArrayList());
      result.put(VirtualObjectsFilter.OTHERS, new ArrayList());
      return result;
   }

   private static boolean isVmObject(VsanObjectIdentity vsanObjectIdentity) {
      return vsanObjectIdentity.vm != null;
   }

   private static boolean isIsciObject(VsanObjectIdentity vsanObjectIdentity) {
      return vsanObjectIdentity.type.startsWith("iscsi");
   }

   private static boolean isFileShare(VsanObjectIdentity identity) {
      return "fileShare".equals(identity.type);
   }

   public static Map<ManagedObjectReference, VmData> getVmData(ManagedObjectReference param0, List<VsanObjectIdentity> param1) {
      // $FF: Couldn't be decompiled
   }

   private static Set<ManagedObjectReference> getVmsFromVsanObjects(ManagedObjectReference clusterRef, List<VsanObjectIdentity> vsanObjectIdentities) {
      Set<ManagedObjectReference> vmRefs = new HashSet();
      if (vsanObjectIdentities != null) {
         Iterator var4 = vsanObjectIdentities.iterator();

         while(var4.hasNext()) {
            VsanObjectIdentity identity = (VsanObjectIdentity)var4.next();
            if (identity.vm != null) {
               ManagedObjectReference vmRef = identity.vm;
               VmodlHelper.assignServerGuid(vmRef, clusterRef.getServerGuid());
               vmRefs.add(vmRef);
            }
         }
      }

      return vmRefs;
   }

   public static Map<String, VsanObjectHealthData> getVsanUuidToObjectHealthData(VsanObjectIdentityAndHealth vsanObjectIdentityAndHealth, VsanObjectInformation[] vsanObjectInformations, Map<String, String> storagePolicies) {
      Map<String, VsanObjectHealthData> result = new HashMap();
      if (vsanObjectIdentityAndHealth != null && !ArrayUtils.isEmpty(vsanObjectIdentityAndHealth.identities)) {
         Map<String, VirtualObjectHealthModel> objectHealthByUuid = VirtualObjectsUtil.getVsanObjectsHealthMap(vsanObjectIdentityAndHealth.health);
         Map<String, VsanObjectInformation> objInfoByUuid = getObjectInfoByVsanUuid(vsanObjectInformations);
         VsanObjectIdentity[] var9;
         int var8 = (var9 = vsanObjectIdentityAndHealth.identities).length;

         for(int var7 = 0; var7 < var8; ++var7) {
            VsanObjectIdentity identity = var9[var7];
            if (StringUtils.isEmpty(identity.uuid)) {
               _logger.warn(String.format("Invalid UUID returned for type %s, additional description: %s.", identity.type, identity.description));
            } else {
               VirtualObjectHealthModel objectHealthData = (VirtualObjectHealthModel)objectHealthByUuid.get(identity.uuid);
               VsanObjectInformation objectInformation = (VsanObjectInformation)objInfoByUuid.get(identity.uuid);
               String storagePolicy;
               if (objectInformation != null) {
                  storagePolicy = storagePolicies.containsKey(objectInformation.spbmProfileUuid) ? (String)storagePolicies.get(objectInformation.spbmProfileUuid) : objectInformation.spbmProfileUuid;
               } else {
                  storagePolicy = storagePolicies.containsKey(identity.spbmProfileUuid) ? (String)storagePolicies.get(identity.spbmProfileUuid) : identity.spbmProfileUuid;
               }

               if (objectHealthData != null) {
                  result.put(identity.uuid, new VsanObjectHealthData(objectHealthData.health, objectHealthData.dataProtectionHealth, storagePolicy));
               } else {
                  _logger.error("VsanObjectOverallHealth's health data is not populated for UUID: " + identity.uuid);
                  result.put(identity.uuid, new VsanObjectHealthData((String)null, (String)null, storagePolicy));
               }
            }
         }

         return result;
      } else {
         _logger.error("No data returned for object health and identities.");
         return result;
      }
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

   private static String getVmHomeVsanUuid(String vmFilePath) {
      if (vmFilePath == null) {
         return null;
      } else {
         int startIndex = vmFilePath.indexOf(93);
         int endIndex = vmFilePath.indexOf(47);
         return startIndex >= 0 && endIndex > startIndex ? vmFilePath.substring(startIndex + 1, endIndex).trim() : null;
      }
   }
}
