package com.vmware.vsphere.client.vsan.base.impl;

import com.vmware.proxygen.ts.TsService;
import com.vmware.vim.binding.pbm.capability.CapabilityInstance;
import com.vmware.vim.binding.pbm.placement.CompatibilityResult;
import com.vmware.vim.binding.pbm.placement.PlacementHub;
import com.vmware.vim.binding.pbm.placement.PlacementSolver;
import com.vmware.vim.binding.pbm.profile.CapabilityBasedProfile;
import com.vmware.vim.binding.pbm.profile.Profile;
import com.vmware.vim.binding.pbm.profile.ProfileId;
import com.vmware.vim.binding.pbm.profile.ProfileManager;
import com.vmware.vim.binding.pbm.profile.ResourceType;
import com.vmware.vim.binding.pbm.profile.ResourceTypeEnum;
import com.vmware.vim.binding.pbm.profile.SubProfileCapabilityConstraints;
import com.vmware.vim.binding.pbm.profile.CapabilityBasedProfile.ProfileCategoryEnum;
import com.vmware.vim.binding.pbm.profile.SubProfileCapabilityConstraints.SubProfile;
import com.vmware.vim.binding.vim.Datastore;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vim.vmomi.core.impl.BlockingFuture;
import com.vmware.vim.vmomi.core.types.VmodlType;
import com.vmware.vim.vmomi.core.types.VmodlTypeMap;
import com.vmware.vim.vmomi.core.types.VmodlTypeMap.Factory;
import com.vmware.vsan.client.services.common.PermissionService;
import com.vmware.vsphere.client.vsan.base.data.StoragePolicyData;
import com.vmware.vsphere.client.vsan.base.util.BaseUtils;
import com.vmware.vsphere.client.vsan.util.MessageBundle;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.PbmClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.pbm.PbmConnection;
import com.vmware.vsphere.client.vsandp.helper.VsanDpInventoryHelper;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.apache.commons.lang.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class PbmDataProvider {
   @Autowired
   private PbmClient pbmClient;
   @Autowired
   private PermissionService permissionService;
   @Autowired
   private VsanDpInventoryHelper inventoryHelper;
   @Autowired
   private MessageBundle messages;
   private static final Logger logger = LoggerFactory.getLogger(PbmDataProvider.class);
   private static final String VMCRYPT_POLICY_NAMESPACE = "vmwarevmcrypt";
   private static final String ENCRYPTION_LINE_OF_SERVICE = "ENCRYPTION";
   private static final String DATASERVICE_POLICY_NAMESPACE = "com.vmware.storageprofile.dataservice";
   private static final String DATASTORE_WSDL_NAME;

   static {
      VmodlTypeMap vmodlTypes = Factory.getTypeMap();
      VmodlType dsVmodlType = vmodlTypes.getVmodlType(Datastore.class);
      DATASTORE_WSDL_NAME = dsVmodlType.getWsdlName();
   }

   @TsService
   public Map<String, String> getStoragePolicyIdNameMap(ManagedObjectReference clusterRef) {
      boolean hasReadPolicyPermission = true;

      try {
         hasReadPolicyPermission = this.permissionService.hasVcPermissions(clusterRef, new String[]{"StorageProfile.View"});
      } catch (Exception var10) {
         logger.error("Unable to query user permissions for read policies");
      }

      if (!hasReadPolicyPermission) {
         logger.info("User doesn't have permissions to read policies, returning empty result.");
         return new HashMap();
      } else {
         try {
            ProfileId[] profileIds = this.getProfileIds(clusterRef);
            Profile[] storageProfiles = this.getProfiles(clusterRef, profileIds);
            Map<String, String> result = new HashMap(profileIds.length);
            Profile[] var9 = storageProfiles;
            int var8 = storageProfiles.length;

            for(int var7 = 0; var7 < var8; ++var7) {
               Profile profile = var9[var7];
               result.put(profile.profileId.uniqueId, profile.name);
            }

            return result;
         } catch (Exception var11) {
            logger.error("Unable to get l ist of storage policies!", var11);
            return new HashMap();
         }
      }
   }

   @TsService
   public List<StoragePolicyData> getStoragePolicies(ManagedObjectReference clusterRef) throws Exception {
      return this.getStoragePolicies(clusterRef, false);
   }

   @TsService
   public List<StoragePolicyData> getObjectCompatibleStoragePolicies(ManagedObjectReference objectRef) throws Exception {
      ManagedObjectReference clusterRef = BaseUtils.getCluster(objectRef);
      return this.getStoragePolicies(clusterRef, true);
   }

   private List<StoragePolicyData> getStoragePolicies(ManagedObjectReference clusterRef, boolean compatibleOnly) throws Exception {
      boolean hasReadPolicyPermission = this.permissionService.hasVcPermissions(clusterRef, new String[]{"StorageProfile.View"});
      if (!hasReadPolicyPermission) {
         return Collections.EMPTY_LIST;
      } else {
         ManagedObjectReference vsanDatastore = this.inventoryHelper.getVsanDatastore(clusterRef);
         ProfileId[] requirementProfileIds = this.getProfileIds(clusterRef);
         Map<String, ProfileId> compatibleProfiles = this.getCompatibleProfiles(vsanDatastore, requirementProfileIds);
         if (vsanDatastore != null && compatibleOnly) {
            requirementProfileIds = (ProfileId[])compatibleProfiles.values().toArray(new ProfileId[compatibleProfiles.size()]);
         }

         if (ArrayUtils.isEmpty(requirementProfileIds)) {
            return Collections.EMPTY_LIST;
         } else {
            Profile[] requirementProfiles = this.getProfiles(clusterRef, requirementProfileIds);
            Set<String> encryptionProfiles = this.getEncryptionProfiles(clusterRef);
            String defaultProfileId = vsanDatastore == null ? null : this.getDefaultStorageProfileId(clusterRef, vsanDatastore);
            List<StoragePolicyData> result = new ArrayList();
            Profile[] var14 = requirementProfiles;
            int var13 = requirementProfiles.length;

            for(int var12 = 0; var12 < var13; ++var12) {
               Profile profile = var14[var12];
               StoragePolicyData policy = new StoragePolicyData(profile);
               if (policy.id.equals(defaultProfileId)) {
                  policy.isDefault = true;
               }

               ProfileId compatibleProfile = (ProfileId)compatibleProfiles.get(profile.getProfileId().getUniqueId());
               if (compatibleProfile != null && policy.hasVsanNamespace) {
                  policy.isCompatible = true;
               }

               policy.isVmCrypt = this.isVmCryptProfile(profile, encryptionProfiles);
               result.add(policy);
            }

            Collections.sort(result, new Comparator<StoragePolicyData>() {
               public int compare(StoragePolicyData lhs, StoragePolicyData rhs) {
                  return lhs.name.compareToIgnoreCase(rhs.name);
               }
            });
            return result;
         }
      }
   }

   private ProfileId[] getProfileIds(ManagedObjectReference param1) {
      // $FF: Couldn't be decompiled
   }

   private Profile[] getProfiles(ManagedObjectReference param1, ProfileId[] param2) {
      // $FF: Couldn't be decompiled
   }

   private Map<String, ProfileId> getCompatibleProfiles(ManagedObjectReference vsanDatastore, ProfileId[] profileIds) throws ExecutionException, InterruptedException {
      Map<String, ProfileId> compatibleProfiles = new HashMap();
      if (vsanDatastore != null && !ArrayUtils.isEmpty(profileIds)) {
         Throwable var4 = null;
         Object var5 = null;

         try {
            PbmConnection pbmConn = this.pbmClient.getConnection(vsanDatastore.getServerGuid());

            Throwable var10000;
            label289: {
               boolean var10001;
               HashMap var31;
               try {
                  PlacementSolver placementSolver = pbmConn.getPlacementSolver();
                  Map<Future<CompatibilityResult[]>, ProfileId> compatibilityFutures = new HashMap();
                  PlacementHub pbmHub = new PlacementHub(DATASTORE_WSDL_NAME, vsanDatastore.getValue());
                  ProfileId[] var13 = profileIds;
                  int var12 = profileIds.length;
                  int var11 = 0;

                  while(true) {
                     if (var11 >= var12) {
                        Iterator var28 = compatibilityFutures.keySet().iterator();

                        while(var28.hasNext()) {
                           Future<CompatibilityResult[]> requirementFuture = (Future)var28.next();
                           CompatibilityResult[] results = (CompatibilityResult[])requirementFuture.get();
                           if (results != null && results.length > 0 && results[0].error == null) {
                              ProfileId profileId = (ProfileId)compatibilityFutures.get(requirementFuture);
                              compatibleProfiles.put(profileId.getUniqueId(), profileId);
                           }
                        }

                        var31 = compatibleProfiles;
                        break;
                     }

                     ProfileId profileId = var13[var11];
                     Future<CompatibilityResult[]> compatibilityResult = new BlockingFuture();
                     placementSolver.checkCompatibility(new PlacementHub[]{pbmHub}, profileId, compatibilityResult);
                     compatibilityFutures.put(compatibilityResult, profileId);
                     ++var11;
                  }
               } catch (Throwable var25) {
                  var10000 = var25;
                  var10001 = false;
                  break label289;
               }

               if (pbmConn != null) {
                  pbmConn.close();
               }

               label275:
               try {
                  return var31;
               } catch (Throwable var24) {
                  var10000 = var24;
                  var10001 = false;
                  break label275;
               }
            }

            var4 = var10000;
            if (pbmConn != null) {
               pbmConn.close();
            }

            throw var4;
         } catch (Throwable var26) {
            if (var4 == null) {
               var4 = var26;
            } else if (var4 != var26) {
               var4.addSuppressed(var26);
            }

            throw var4;
         }
      } else {
         return compatibleProfiles;
      }
   }

   private Set<String> getEncryptionProfiles(ManagedObjectReference clusterRef) {
      Set<String> encryptionProfiles = new HashSet();
      Throwable var3 = null;
      Object var4 = null;

      try {
         PbmConnection pbmConn = this.pbmClient.getConnection(clusterRef.getServerGuid());

         Throwable var10000;
         label235: {
            boolean var10001;
            HashSet var27;
            try {
               ProfileManager profileManager = pbmConn.getProfileManager();
               ResourceType resource = new ResourceType(ResourceTypeEnum.STORAGE.name());
               ProfileId[] dataServiceProfileIds = profileManager.queryProfile(resource, ProfileCategoryEnum.DATA_SERVICE_POLICY.name());
               Profile[] dsProfiles = profileManager.retrieveContent(dataServiceProfileIds);
               Profile[] var13 = dsProfiles;
               int var12 = dsProfiles.length;
               int var11 = 0;

               while(true) {
                  if (var11 >= var12) {
                     var27 = encryptionProfiles;
                     break;
                  }

                  Profile profile = var13[var11];
                  if (profile instanceof CapabilityBasedProfile) {
                     CapabilityBasedProfile capabilityBasedProfile = (CapabilityBasedProfile)profile;
                     if ("ENCRYPTION".equals(capabilityBasedProfile.lineOfService)) {
                        encryptionProfiles.add(profile.profileId.getUniqueId());
                     }
                  }

                  ++var11;
               }
            } catch (Throwable var25) {
               var10000 = var25;
               var10001 = false;
               break label235;
            }

            if (pbmConn != null) {
               pbmConn.close();
            }

            label223:
            try {
               return var27;
            } catch (Throwable var24) {
               var10000 = var24;
               var10001 = false;
               break label223;
            }
         }

         var3 = var10000;
         if (pbmConn != null) {
            pbmConn.close();
         }

         throw var3;
      } catch (Throwable var26) {
         if (var3 == null) {
            var3 = var26;
         } else if (var3 != var26) {
            var3.addSuppressed(var26);
         }

         throw var3;
      }
   }

   private String getDefaultStorageProfileId(ManagedObjectReference clusterRef, ManagedObjectReference vsanDatastore) {
      String defaultProfileId = null;

      try {
         Throwable var4 = null;
         Object var5 = null;

         try {
            PbmConnection pbmConn = this.pbmClient.getConnection(clusterRef.getServerGuid());

            try {
               PlacementHub pbmHub = new PlacementHub(DATASTORE_WSDL_NAME, vsanDatastore.getValue());
               ProfileManager profileManager = pbmConn.getProfileManager();
               ProfileId profileId = profileManager.queryDefaultRequirementProfile(pbmHub);
               if (profileId != null) {
                  defaultProfileId = profileId.getUniqueId();
               } else {
                  logger.warn("There is no default storage policy for datastore " + vsanDatastore + ".");
               }
            } finally {
               if (pbmConn != null) {
                  pbmConn.close();
               }

            }
         } catch (Throwable var17) {
            if (var4 == null) {
               var4 = var17;
            } else if (var4 != var17) {
               var4.addSuppressed(var17);
            }

            throw var4;
         }
      } catch (Exception var18) {
         logger.error("Unable to find the default storage policy.", var18);
      }

      return defaultProfileId;
   }

   private boolean isVmCryptProfile(Profile profile, Set<String> encryptionProfiles) {
      if (!(profile instanceof CapabilityBasedProfile)) {
         return false;
      } else {
         CapabilityBasedProfile capabilityBasedProfile = (CapabilityBasedProfile)profile;
         if (!(capabilityBasedProfile.constraints instanceof SubProfileCapabilityConstraints)) {
            return false;
         } else {
            SubProfileCapabilityConstraints constraints = (SubProfileCapabilityConstraints)capabilityBasedProfile.constraints;
            if (ArrayUtils.isEmpty(constraints.subProfiles)) {
               return false;
            } else {
               SubProfile[] var8;
               int var7 = (var8 = constraints.subProfiles).length;

               for(int var6 = 0; var6 < var7; ++var6) {
                  SubProfile subProfile = var8[var6];
                  CapabilityInstance[] var12;
                  int var11 = (var12 = subProfile.capability).length;

                  for(int var10 = 0; var10 < var11; ++var10) {
                     CapabilityInstance capabilityInstance = var12[var10];
                     String capabilityNamespace = capabilityInstance.id.namespace;
                     if ("vmwarevmcrypt".equals(capabilityNamespace)) {
                        return true;
                     }

                     if ("com.vmware.storageprofile.dataservice".equals(capabilityNamespace) && encryptionProfiles.contains(capabilityInstance.id.getId())) {
                        return true;
                     }
                  }
               }

               return false;
            }
         }
      }
   }
}
