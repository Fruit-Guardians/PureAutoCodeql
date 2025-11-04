package com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.health;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.Resource;

public interface IHealthMonitor<R extends Resource, S> {
   void onCreated(R var1, S var2);

   void onDisposed(R var1, S var2);

   void check(R var1, S var2) throws Exception;

   void onError(R var1, S var2, Throwable var3);
}
