package com.vmware.vsan.client.services.dataprotection.model;

import com.vmware.vise.core.model.data;
import com.vmware.vsan.client.util.StringUtil;
import com.vmware.vsphere.client.vsan.base.data.VsanObjectDataProtectionHealthState;
import java.util.Date;

@data
public class ProtectionData {
   public VsanObjectDataProtectionHealthState healthState;
   public Integer frequency;
   public Integer rpo;
   public Date lastPointTimestamp;

   public String toString() {
      return String.format("%s [healthState = %s, frequency = %d, rpo = %d, lastPointTimestamp = %s]", this.getClass().getName(), this.healthState, this.frequency, this.rpo, StringUtil.parseTimestamp(this.lastPointTimestamp));
   }
}
