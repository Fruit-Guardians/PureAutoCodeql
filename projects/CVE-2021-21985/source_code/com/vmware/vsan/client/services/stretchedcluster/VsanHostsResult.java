package com.vmware.vsan.client.services.stretchedcluster;

import com.vmware.vim.binding.vim.HostSystem.ConnectionState;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsan.binding.vim.cluster.VSANWitnessHostInfo;
import com.vmware.vise.core.model.data;
import com.vmware.vise.data.query.PropertyValue;
import java.util.HashSet;
import java.util.Set;

@data
public class VsanHostsResult {
   private final PropertyValue[] hostData;
   private final VSANWitnessHostInfo[] witnessHostInfos;
   public final Set<ManagedObjectReference> members;
   public final Set<ManagedObjectReference> connectedMembers;
   public final Set<ManagedObjectReference> witnesses;

   public VsanHostsResult() {
      this(new PropertyValue[0], new VSANWitnessHostInfo[0]);
   }

   public VsanHostsResult(PropertyValue[] hostData, VSANWitnessHostInfo[] witnessHostInfos) {
      this.hostData = hostData;
      this.witnessHostInfos = witnessHostInfos;
      Set<ManagedObjectReference> members = new HashSet();
      Set<ManagedObjectReference> connectedMembers = new HashSet();
      Set<ManagedObjectReference> witnesses = new HashSet();
      PropertyValue[] var9 = hostData;
      int var8 = hostData.length;

      for(int var7 = 0; var7 < var8; ++var7) {
         PropertyValue val = var9[var7];
         if (val.propertyName.equals("runtime.connectionState")) {
            ManagedObjectReference hostRef = (ManagedObjectReference)val.resourceObject;
            members.add(hostRef);
            if (ConnectionState.connected.equals(val.value)) {
               connectedMembers.add(hostRef);
            }
         }
      }

      if (witnessHostInfos != null) {
         for(int i = 0; i < witnessHostInfos.length; ++i) {
            witnesses.add(witnessHostInfos[i].host);
         }
      }

      this.members = members;
      this.connectedMembers = connectedMembers;
      this.witnesses = witnesses;
   }

   public VSANWitnessHostInfo[] getWitnessInfos() {
      return this.witnessHostInfos;
   }

   public Set<ManagedObjectReference> getAll() {
      Set<ManagedObjectReference> result = new HashSet();
      result.addAll(this.members);
      result.addAll(this.witnesses);
      return result;
   }
}
