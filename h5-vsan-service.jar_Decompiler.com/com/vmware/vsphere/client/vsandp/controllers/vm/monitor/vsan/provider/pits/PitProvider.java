package com.vmware.vsphere.client.vsandp.controllers.vm.monitor.vsan.provider.pits;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vsandp.binding.vim.vsandp.CgInfo;
import com.vmware.vsphere.client.vsandp.data.DataProtectionInstance;
import java.util.TreeSet;

public interface PitProvider {
   TreeSet<DataProtectionInstance> getLocalPits(ManagedObjectReference var1, CgInfo var2);

   TreeSet<DataProtectionInstance> getArchivePits(ManagedObjectReference var1, CgInfo var2);

   TreeSet<DataProtectionInstance> getRemotePits(ManagedObjectReference var1, CgInfo var2);

   TreeSet<DataProtectionInstance> getTargetPits(CgInfo var1);
}
