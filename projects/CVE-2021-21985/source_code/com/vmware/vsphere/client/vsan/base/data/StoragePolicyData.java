package com.vmware.vsphere.client.vsan.base.data;

import com.vmware.vim.binding.pbm.capability.CapabilityInstance;
import com.vmware.vim.binding.pbm.capability.ConstraintInstance;
import com.vmware.vim.binding.pbm.capability.PropertyInstance;
import com.vmware.vim.binding.pbm.profile.CapabilityBasedProfile;
import com.vmware.vim.binding.pbm.profile.Profile;
import com.vmware.vim.binding.pbm.profile.SubProfileCapabilityConstraints;
import com.vmware.vim.binding.pbm.profile.SubProfileCapabilityConstraints.SubProfile;
import com.vmware.vise.core.model.data;
import org.apache.commons.lang.ArrayUtils;

@data
public class StoragePolicyData {
   private static final String LOCAL_PROTECTION = "localProtection";
   private static final String HOST_FAILURES_TO_TOLERATE = "hostFailuresToTolerate";
   public String id;
   public String name;
   public boolean isDefault;
   public boolean hasVsanNamespace;
   public boolean isDataProtection;
   public boolean isVmCrypt;
   public Integer ftt;
   public boolean isCompatible;

   public StoragePolicyData() {
   }

   public StoragePolicyData(Profile profile) {
      this.id = profile.getProfileId().getUniqueId();
      this.name = profile.getName();
      if (profile instanceof CapabilityBasedProfile) {
         CapabilityBasedProfile capabilityBasedProfile = (CapabilityBasedProfile)profile;
         if (capabilityBasedProfile.constraints instanceof SubProfileCapabilityConstraints) {
            SubProfileCapabilityConstraints constraints = (SubProfileCapabilityConstraints)capabilityBasedProfile.constraints;
            if (ArrayUtils.isEmpty(constraints.subProfiles)) {
               return;
            }

            SubProfile[] var7;
            int var6 = (var7 = constraints.subProfiles).length;

            for(int var5 = 0; var5 < var6; ++var5) {
               SubProfile subProfile = var7[var5];
               if (ArrayUtils.isEmpty(subProfile.capability)) {
                  return;
               }

               CapabilityInstance[] var11;
               int var10 = (var11 = subProfile.capability).length;

               for(int var9 = 0; var9 < var10; ++var9) {
                  CapabilityInstance capability = var11[var9];
                  if ("VSAN".equals(capability.id.namespace)) {
                     this.hasVsanNamespace = true;
                  }

                  if (capability.id.id.equals("localProtection")) {
                     this.isDataProtection = true;
                  }

                  ConstraintInstance[] var15;
                  int var14 = (var15 = capability.constraint).length;

                  for(int var13 = 0; var13 < var14; ++var13) {
                     ConstraintInstance constraintInstance = var15[var13];
                     if (ArrayUtils.isEmpty(constraintInstance.propertyInstance)) {
                        return;
                     }

                     PropertyInstance[] var19;
                     int var18 = (var19 = constraintInstance.propertyInstance).length;

                     for(int var17 = 0; var17 < var18; ++var17) {
                        PropertyInstance propertyInstance = var19[var17];
                        if (propertyInstance.id.equals("hostFailuresToTolerate")) {
                           this.ftt = (Integer)propertyInstance.value;
                        }
                     }
                  }
               }
            }
         }
      }

   }
}
