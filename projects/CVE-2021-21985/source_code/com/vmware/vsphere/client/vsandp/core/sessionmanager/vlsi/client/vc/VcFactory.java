package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vc;

import com.vmware.vim.binding.vim.ServiceInstance;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.AbstractConnectionFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;

public class VcFactory extends AbstractConnectionFactory<VcConnection, VlsiSettings> {
   protected VcConnection buildConnection(VlsiSettings id) {
      return new VcConnection();
   }

   public void onConnect(VlsiSettings id, VcConnection connection) {
      super.onConnect(id, connection);
      ServiceInstance vcSi = (ServiceInstance)connection.createStub(ServiceInstance.class, "ServiceInstance");
      connection.content = vcSi.getContent();
   }
}
