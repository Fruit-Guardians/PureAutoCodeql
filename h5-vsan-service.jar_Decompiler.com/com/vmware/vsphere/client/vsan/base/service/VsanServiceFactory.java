package com.vmware.vsphere.client.vsan.base.service;

public interface VsanServiceFactory {
   VsanService getService(String var1);

   String getSessionKey(String var1);
}
