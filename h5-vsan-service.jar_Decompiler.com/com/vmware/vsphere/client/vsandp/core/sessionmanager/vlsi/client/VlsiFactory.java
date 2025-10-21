package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client;

public class VlsiFactory extends AbstractConnectionFactory<VlsiConnection, VlsiSettings> {
   protected VlsiConnection buildConnection(VlsiSettings id) {
      return new VlsiConnection();
   }
}
