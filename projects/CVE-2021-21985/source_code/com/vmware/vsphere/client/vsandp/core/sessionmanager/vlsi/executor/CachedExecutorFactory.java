package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.executor;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.CachedResourceFactory;

public class CachedExecutorFactory extends CachedResourceFactory<CloseableExecutorService, ExecutorSettings> {
   public CachedExecutorFactory() {
      super(new ExecutorFactory());
   }

   public synchronized void shutdown() {
      super.shutdown();
   }
}
