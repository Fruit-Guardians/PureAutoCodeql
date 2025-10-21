package com.vmware.vsphere.client.vsan.iscsi.models.config;

import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetAuthSpec;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetAuthSpec.VsanIscsiTargetAuthType;
import com.vmware.vise.core.model.data;

@data
public class VsanIscsiAuthSpec {
   public String authType;
   public String initiatorUsername;
   public String initiatorSecret;
   public String targetUsername;
   public String targetSecret;

   public static VsanIscsiTargetAuthSpec getVsanIscsiTargetAuthSpec(VsanIscsiAuthSpec spec) {
      if (spec == null) {
         return null;
      } else {
         VsanIscsiTargetAuthSpec authSpec = new VsanIscsiTargetAuthSpec();
         authSpec.authType = spec.authType;
         if (VsanIscsiTargetAuthType.CHAP.toString().equals(spec.authType)) {
            authSpec.userNameAttachToTarget = spec.targetUsername;
            authSpec.userSecretAttachToTarget = spec.targetSecret;
         } else if (VsanIscsiTargetAuthType.CHAP_Mutual.toString().equals(spec.authType)) {
            authSpec.userNameAttachToTarget = spec.targetUsername;
            authSpec.userSecretAttachToTarget = spec.targetSecret;
            authSpec.userNameAttachToInitiator = spec.initiatorUsername;
            authSpec.userSecretAttachToInitiator = spec.initiatorSecret;
         }

         return authSpec;
      }
   }
}
