package com.vmware.vsan.client.services.common.data;

import com.vmware.vise.core.model.data;

@data
public class IpAddressesRequestSpec {
   public String ipAddress;
   public String subnetMask;
   public int hostsNumber;
}
