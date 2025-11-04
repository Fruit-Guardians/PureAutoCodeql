package com.vmware.vsphere.client.vsan.whatif;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.services.virtualobjects.data.VirtualObjectModel;
import java.util.ArrayList;
import java.util.List;

@data
public class WhatIfData extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public String summary;
   public boolean success;
   public long bytesToSync;
   public long extraSpaceNeeded;
   public boolean failedDueToInaccessibleObjects;
   public boolean successWithInaccessibleOrNonCompliantObjects;
   public boolean successWithoutDataLoss;
   public List<VirtualObjectModel> objects = new ArrayList();
   public long repairTime;
}
