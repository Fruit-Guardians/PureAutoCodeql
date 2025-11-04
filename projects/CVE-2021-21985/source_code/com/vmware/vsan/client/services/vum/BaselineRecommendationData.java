package com.vmware.vsan.client.services.vum;

import com.vmware.vise.core.model.data;
import com.vmware.vsphere.client.vsan.data.VumBaselineRecommendationType;

@data
public class BaselineRecommendationData {
   public VumBaselineRecommendationType clusterRecommendation;
   public VumBaselineRecommendationType vcRecommendation;
}
