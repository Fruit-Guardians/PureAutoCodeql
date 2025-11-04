package com.vmware.vsphere.client.vsan.base.data;

import com.vmware.vise.core.model.data;
import java.util.ArrayList;
import java.util.List;

@data
public class VsanRaidConfig extends VsanComponent {
   private static final long serialVersionUID = 1L;
   public List<VsanComponent> children = new ArrayList();
}
