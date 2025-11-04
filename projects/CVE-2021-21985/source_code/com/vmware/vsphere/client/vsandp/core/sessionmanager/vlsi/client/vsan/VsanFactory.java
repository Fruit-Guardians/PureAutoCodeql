package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.vsan;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.AbstractConnectionFactory;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.VlsiSettings;

public class VsanFactory extends AbstractConnectionFactory<VsanConnection, VlsiSettings> {
   protected VsanConnection buildConnection(VlsiSettings id) {
      return new VsanConnection();
   }
}
