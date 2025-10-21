package com.vmware.vsphere.client.vsan.health;

import com.vmware.vise.core.model.data;
import java.util.List;

@data
public class VsanSpaceUsageDataModel {
   public long totalCapacityB;
   public long totalUsedB;
   public CapacityOverviewData overview;
   public List<VsanObjectSpaceSummaryDataModel> spaceDetail;
}
