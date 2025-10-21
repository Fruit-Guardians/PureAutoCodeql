package com.vmware.vsan.client.services.hci.model;

import com.vmware.vise.core.model.data;

@data
public enum VsanClusterType {
   SINGLE_SITE_CLUSTER,
   TWO_HOST_VSAN_CLUSTER,
   STRETCHED_CLUSTER,
   NO_VSAN,
   NOT_IN_HCI_WORKFLOW;
}
