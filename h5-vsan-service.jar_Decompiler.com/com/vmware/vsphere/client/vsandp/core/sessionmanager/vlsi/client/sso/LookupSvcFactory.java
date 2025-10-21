package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso;

import com.vmware.vim.binding.lookup.ServiceInstance;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.AbstractConnectionFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;

public class LookupSvcFactory extends AbstractConnectionFactory<LookupSvcConnection, VlsiSettings> {
   protected LookupSvcConnection buildConnection(VlsiSettings id) {
      return new LookupSvcConnection();
   }

   public void onConnect(VlsiSettings id, LookupSvcConnection connection) {
      super.onConnect(id, connection);
      ServiceInstance si = (ServiceInstance)connection.createStub(ServiceInstance.class, "ServiceInstance");
      connection.content = si.retrieveServiceContent();
   }
}
