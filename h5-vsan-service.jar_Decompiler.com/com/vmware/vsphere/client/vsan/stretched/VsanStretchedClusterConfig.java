package com.vmware.vsphere.client.vsan.stretched;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vim.binding.vim.vsan.host.DiskMapping;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.ProxygenSerializer;
import java.util.List;

@data
public class VsanStretchedClusterConfig extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public ManagedObjectReference witnessHost;
   public DiskMapping witnessHostDiskMapping;
   public String preferredSiteName;
   @ProxygenSerializer.ElementType(ManagedObjectReference.class)
   public List<ManagedObjectReference> preferredSiteHosts;
   public String secondarySiteName;
   @ProxygenSerializer.ElementType(ManagedObjectReference.class)
   public List<ManagedObjectReference> secondarySiteHosts;
   public boolean isFaultDomainConfigurationChanged;
}
