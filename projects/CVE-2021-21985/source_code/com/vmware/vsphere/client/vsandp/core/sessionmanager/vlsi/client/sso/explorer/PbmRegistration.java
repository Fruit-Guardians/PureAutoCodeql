package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer;

import com.vmware.vim.binding.lookup.ServiceRegistration.Info;
import java.util.UUID;

public class PbmRegistration extends AbstractLsRegistration {
   public PbmRegistration(Info info) {
      super(info);
   }

   public UUID getUuid() {
      return UUID.fromString(this.findAttribute("com.vmware.cis.cm.HostId").getValue());
   }

   public String getSolutionUser() {
      String result = this.getOwnerId();
      int separator = result.lastIndexOf(64);
      if (separator != -1) {
         result = result.substring(0, separator);
      }

      return result;
   }
}
