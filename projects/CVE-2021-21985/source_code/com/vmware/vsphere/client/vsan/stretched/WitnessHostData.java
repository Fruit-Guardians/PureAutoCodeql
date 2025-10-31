package com.vmware.vsphere.client.vsan.stretched;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VSANWitnessHostInfo;
import com.vmware.vise.core.model.data;

@data
public class WitnessHostData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public ManagedObjectReference witnessHost;
   public String witnessHostName;
   public String witnessHostIcon;
   public String preferredFaultDomainName;
   public String unicastAgentAddress;

   public WitnessHostData() {
   }

   public WitnessHostData(VSANWitnessHostInfo witnessInfo, String serverGuid) {
      this.witnessHost = witnessInfo.host;
      this.witnessHost.setServerGuid(serverGuid);
      this.preferredFaultDomainName = witnessInfo.preferredFdName;
      this.unicastAgentAddress = witnessInfo.unicastAgentAddr;
   }
}
