package com.vmware.vsphere.client.vsan.iscsi.models.initiatorgroup.initiator;

import com.vmware.vise.core.model.data;

@data
public class InitiatorGroupInitiatorAdditionSpec {
   public String initiatorGroupName;
   public String[] initiatorNames;
}
