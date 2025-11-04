package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer;

import com.vmware.vim.binding.lookup.ServiceRegistration.Info;
import java.util.UUID;

public class VcRegistration extends AbstractLsRegistration {
   public VcRegistration(Info info) {
      super(info);
   }

   public UUID getVpxdUuid() {
      return UUID.fromString(this.findAttribute("com.vmware.cis.cm.HostId").getValue());
   }

   public String getVcName() {
      return this.findAttribute("com.vmware.vim.vcenter.instanceName").getValue();
   }

   public String getSiteId() {
      return this.info == null ? null : this.info.siteId;
   }

   public String getNodeId() {
      return this.info == null ? null : this.info.nodeId;
   }
}
