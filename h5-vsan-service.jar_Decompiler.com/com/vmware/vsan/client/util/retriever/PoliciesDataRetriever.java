package com.vmware.vsan.client.util.retriever;

import com.vmware.vim.binding.pbm.profile.Profile;
import com.vmware.vim.binding.pbm.profile.ProfileId;
import com.vmware.vim.binding.pbm.profile.ProfileManager;
import com.vmware.vim.binding.pbm.profile.ResourceType;
import com.vmware.vim.binding.pbm.profile.ResourceTypeEnum;
import com.vmware.vim.binding.pbm.profile.CapabilityBasedProfile.ProfileCategoryEnum;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vsan.client.services.common.PermissionService;
import com.vmware.vsan.client.services.virtualobjects.VirtualObjectsService;
import com.vmware.vsan.client.util.Measure;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.common.PbmClient;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.pbm.PbmConnection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class PoliciesDataRetriever extends AbstractAsyncDataRetriever<Map<String, String>> {
   private final PbmClient pbmClient;
   private final PermissionService permissionService;
   private Future<Profile[]> profilesFuture;
   private static final Log logger = LogFactory.getLog(VirtualObjectsService.class);

   public PoliciesDataRetriever(ManagedObjectReference clusterRef, Measure measure, PbmClient pbmClient, PermissionService permissionService) {
      super(clusterRef, measure);
      this.pbmClient = pbmClient;
      this.permissionService = permissionService;
   }

   public void start() {
      boolean hasReadPolicyPermission = this.hasPoliciesPermissions();
      if (!hasReadPolicyPermission) {
         logger.info("User doesn't have permissions to read policies, returning empty result.");
      } else {
         try {
            Throwable var2 = null;
            Object var3 = null;

            try {
               PbmConnection pbmConn = this.pbmClient.getConnection(this.clusterRef.getServerGuid());

               try {
                  this.profilesFuture = this.measure.newFuture("ProfileManager.retrieveContent");
                  ProfileManager profileManager = pbmConn.getProfileManager();
                  ProfileId[] profileIds = profileManager.queryProfile(new ResourceType(ResourceTypeEnum.STORAGE.name()), ProfileCategoryEnum.REQUIREMENT.name());
                  profileManager.retrieveContent(profileIds, this.profilesFuture);
               } finally {
                  if (pbmConn != null) {
                     pbmConn.close();
                  }

               }
            } catch (Throwable var14) {
               if (var2 == null) {
                  var2 = var14;
               } else if (var2 != var14) {
                  var2.addSuppressed(var14);
               }

               throw var2;
            }
         } catch (Exception var15) {
            this.profilesFuture.setException(var15);
         }

      }
   }

   public Map<String, String> prepareResult() throws ExecutionException, InterruptedException {
      if (this.profilesFuture == null) {
         return new HashMap();
      } else {
         Profile[] profiles = (Profile[])this.profilesFuture.get();
         Map<String, String> result = new HashMap(profiles.length);
         Profile[] var6 = profiles;
         int var5 = profiles.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            Profile profile = var6[var4];
            result.put(profile.profileId.uniqueId, profile.name);
         }

         return result;
      }
   }

   private boolean hasPoliciesPermissions() {
      try {
         return this.permissionService.hasVcPermissions(this.clusterRef, new String[]{"StorageProfile.View"});
      } catch (Exception var1) {
         logger.error("Unable to query user permissions for read policies");
         return true;
      }
   }
}
