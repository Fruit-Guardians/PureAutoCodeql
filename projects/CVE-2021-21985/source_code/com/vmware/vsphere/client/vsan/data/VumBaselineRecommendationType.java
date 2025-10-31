package com.vmware.vsphere.client.vsan.data;

import com.vmware.vise.core.model.data;

@data
public enum VumBaselineRecommendationType {
   latestPatch,
   latestRelease,
   noRecommendation;
}
