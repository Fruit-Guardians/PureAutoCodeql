package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.executor;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;

public class NullExecutorFactory implements ResourceFactory<CloseableExecutorService, ExecutorSettings> {
   public CloseableExecutorService acquire(ExecutorSettings settings) {
      return null;
   }
}
