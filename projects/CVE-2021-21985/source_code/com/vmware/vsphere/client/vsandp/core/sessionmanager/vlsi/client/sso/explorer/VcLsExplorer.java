package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso.explorer;

import com.vmware.vim.binding.lookup.ServiceRegistration;
import com.vmware.vim.binding.lookup.ServiceRegistration.EndpointType;
import com.vmware.vim.binding.lookup.ServiceRegistration.Filter;
import com.vmware.vim.binding.lookup.ServiceRegistration.Info;
import com.vmware.vim.binding.lookup.ServiceRegistration.ServiceType;
import java.util.Map;
import java.util.UUID;

public class VcLsExplorer extends AbstractLsExplorer<VcRegistration> {
   public static final ServiceType VC_SERVICE_TYPE = new ServiceType("com.vmware.cis", "vcenterserver");
   public static final EndpointType VC_ENDPOINT_TYPE = new EndpointType("vmomi", "com.vmware.vim");
   public static final Filter ALL_VCS;

   static {
      ALL_VCS = new Filter((String)null, (String)null, VC_SERVICE_TYPE, VC_ENDPOINT_TYPE);
   }

   public VcLsExplorer(ServiceRegistration lookupService) {
      super(lookupService);
   }

   protected VcRegistration createRegistration(Info registrationInfo) {
      return new VcRegistration(registrationInfo);
   }

   protected void mapRegistration(VcRegistration registration, Map<UUID, VcRegistration> map) {
      map.put(registration.getUuid(), registration);
   }

   protected Filter getFilter() {
      return ALL_VCS;
   }
}
