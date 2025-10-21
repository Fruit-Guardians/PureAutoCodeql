package com.vmware.vsan.client.services.hci.model;

import com.vmware.vise.core.model.data;

@data
public enum VlanType {
   NONE,
   PVLAN,
   VLAN_ID,
   VLAN_TRUNK;
}
