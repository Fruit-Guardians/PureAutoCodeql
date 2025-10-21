package com.vmware.vsan.client.util.retriever;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vsan.client.util.Measure;
import java.util.concurrent.ExecutionException;

abstract class AbstractAsyncDataRetriever<T> implements DataRetriever<T> {
   protected ManagedObjectReference clusterRef;
   protected Measure measure;
   protected Future<T> future;
   protected T result;

   public AbstractAsyncDataRetriever(ManagedObjectReference clusterRef, Measure measure) {
      this.clusterRef = clusterRef;
      this.measure = measure;
   }

   public final T getResult() throws ExecutionException, InterruptedException {
      if (this.result == null) {
         this.result = this.prepareResult();
      }

      return this.result;
   }

   protected T prepareResult() throws ExecutionException, InterruptedException {
      return this.future.get();
   }
}
