package com.vmware.vsphere.client.vsan.health;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class ExternalProxySettingsConfig extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public boolean isAutoDiscovered;
   public String hostName;
   public Integer port;
   public String userName;
   public String password;
   public Boolean enableInternetAccess = false;
}
