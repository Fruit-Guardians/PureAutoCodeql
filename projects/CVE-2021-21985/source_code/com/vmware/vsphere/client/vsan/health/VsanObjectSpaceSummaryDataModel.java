package com.vmware.vsphere.client.vsan.health;

import com.vmware.vise.core.model.data;

@data
public class VsanObjectSpaceSummaryDataModel {
   public long physicalUsedSpace;
   public long reservedSpace;
   public String objectType;
   public long overheadSpace;
   public long tempOverheadSpace;
   public long primaryCapacitySpace;
   public long vsanDpOverheadSpace;
}
