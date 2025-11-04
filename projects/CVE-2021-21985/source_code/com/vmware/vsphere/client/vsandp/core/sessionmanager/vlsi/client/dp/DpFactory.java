package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.dp;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.AbstractConnectionFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;

public class DpFactory extends AbstractConnectionFactory<DpConnection, VlsiSettings> {
   protected DpConnection buildConnection(VlsiSettings id) {
      return new DpConnection();
   }
}
