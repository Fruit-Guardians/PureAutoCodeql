package com.vmware.vsphere.client.vsan.iscsi.models.initiatorgroup;

import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiInitiatorGroup;
import com.vmware.vim.vsan.binding.vim.cluster.VsanIscsiTargetBasicInfo;
import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.iscsi.models.initiatorgroup.initiator.InitiatorGroupInitiator;
import com.vmware.vsphere.client.vsan.iscsi.models.initiatorgroup.target.InitiatorGroupTarget;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.ArrayUtils;

@data
public class InitiatorGroup {
   public String name;
   public List<InitiatorGroupInitiator> initiators = new ArrayList();
   public List<InitiatorGroupTarget> targets = new ArrayList();

   public InitiatorGroup(VsanIscsiInitiatorGroup initiatorGroup) {
      String[] groupInitiators = initiatorGroup.getInitiators();
      VsanIscsiTargetBasicInfo[] targetBasicInfos = initiatorGroup.getTargets();
      this.name = initiatorGroup.getName();
      int var5;
      int var6;
      if (ArrayUtils.isNotEmpty(groupInitiators)) {
         String[] var7 = groupInitiators;
         var6 = groupInitiators.length;

         for(var5 = 0; var5 < var6; ++var5) {
            String initiatorName = var7[var5];
            InitiatorGroupInitiator initiator = new InitiatorGroupInitiator();
            initiator.name = initiatorName;
            this.initiators.add(initiator);
         }
      }

      if (ArrayUtils.isNotEmpty(targetBasicInfos)) {
         VsanIscsiTargetBasicInfo[] var10 = targetBasicInfos;
         var6 = targetBasicInfos.length;

         for(var5 = 0; var5 < var6; ++var5) {
            VsanIscsiTargetBasicInfo target = var10[var5];
            this.targets.add(new InitiatorGroupTarget(target.getAlias(), target.getIqn()));
         }
      }

   }
}
