package com.vmware.vsan.client.services.cns.model;

import com.vmware.vise.core.model.data;

@data
public class VolumeComplianceFailure {
   public String propertyName;
   public String currentValue;
   public String expectedValue;
}
