package com.vmware.vsphere.client.vsan.base.data;

import com.vmware.vise.core.model.data;

@data
public enum VsanComplianceStatus {
   COMPLIANT,
   NOT_COMPLIANT,
   NOT_APPLICABLE,
   OUT_OF_DATE,
   UNKNOWN;
}
