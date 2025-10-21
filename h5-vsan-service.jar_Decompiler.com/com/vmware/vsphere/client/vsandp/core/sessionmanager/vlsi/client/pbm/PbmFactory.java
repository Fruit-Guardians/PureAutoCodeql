package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.pbm;

import com.vmware.vim.binding.pbm.ServiceInstance;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.AbstractConnectionFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;

public class PbmFactory extends AbstractConnectionFactory<PbmConnection, VlsiSettings> {
   protected PbmConnection buildConnection(VlsiSettings settings) {
      return new PbmConnection();
   }

   public void onConnect(VlsiSettings id, PbmConnection connection) {
      super.onConnect(id, connection);
      ServiceInstance vcSi = (ServiceInstance)connection.createStub(ServiceInstance.class, "ServiceInstance");
      connection.content = vcSi.getContent();
   }
}
