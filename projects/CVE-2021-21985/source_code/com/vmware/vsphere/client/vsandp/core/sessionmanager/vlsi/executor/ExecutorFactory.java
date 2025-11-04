package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.executor;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.ResourceFactory;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;

public class ExecutorFactory implements ResourceFactory<CloseableExecutorService, ExecutorSettings> {
   public CloseableExecutorService acquire(ExecutorSettings config) {
      ThreadPoolExecutor threadPoolExecutor = new ThreadPoolExecutor(config.getInitialThreads(), config.getMaxThreads(), config.getKeepAliveTime(), config.getKeepAliveUnit(), new LinkedBlockingQueue());
      final CloseableExecutorService result = new CloseableExecutorService(threadPoolExecutor);
      result.setCloseHandler(new Runnable() {
         public void run() {
            result.shutdown();
         }
      });
      return result;
   }
}
