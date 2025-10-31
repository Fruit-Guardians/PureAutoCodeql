package com.vmware.vsphere.client.vsan.iscsi.models.initiatorgroup;

import com.vmware.vise.core.model.data;

@data
public class InitiatorGroupAdditionSpec {
   public String initiatorGroupName;
   public String[] initiatorNames;
}
