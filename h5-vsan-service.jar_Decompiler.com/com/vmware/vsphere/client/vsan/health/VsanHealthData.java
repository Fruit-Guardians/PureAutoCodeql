package com.vmware.vsphere.client.vsan.health;

import com.vmware.vise.core.model.data;
import java.util.Calendar;
import java.util.List;

@data
public class VsanHealthData {
   public VsanHealthStatus status;
   public String description;
   public String helpId;
   public List<VsanTestData> testsData;
   public Calendar timeStamp;
}
