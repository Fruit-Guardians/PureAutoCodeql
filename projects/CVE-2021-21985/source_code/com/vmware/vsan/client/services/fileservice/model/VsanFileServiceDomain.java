package com.vmware.vsan.client.services.fileservice.model;

import com.vmware.vise.core.model.data;

@data
public class VsanFileServiceDomain {
   public VsanFileServiceSecurityMode securityMode;
   public String name;
   public String username;
   public String password;
   public String gatewayAddress;
   public String mask;
   public VsanFileServiceIpType ipType;
   public VsanFileServiceHostIpSettings[] ipSettings;

   public VsanFileServiceDomain() {
      this.ipType = VsanFileServiceIpType.V4;
   }
}
