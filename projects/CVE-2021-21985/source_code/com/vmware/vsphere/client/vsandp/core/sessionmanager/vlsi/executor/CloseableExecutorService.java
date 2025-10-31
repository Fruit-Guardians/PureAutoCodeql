package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.executor;

import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.Resource;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CloseableExecutorService extends Resource implements ExecutorService {
   protected final ExecutorService delegatedExecutor;

   public CloseableExecutorService(ExecutorService delegatedExecutor) {
      this.delegatedExecutor = delegatedExecutor;
   }

   public void execute(Runnable arg0) {
      this.delegatedExecutor.execute(arg0);
   }

   public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
      return this.delegatedExecutor.awaitTermination(timeout, unit);
   }

   public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
      return this.delegatedExecutor.invokeAll(tasks);
   }

   public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
      return this.delegatedExecutor.invokeAll(tasks, timeout, unit);
   }

   public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
      return this.delegatedExecutor.invokeAny(tasks);
   }

   public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
      return this.delegatedExecutor.invokeAny(tasks, timeout, unit);
   }

   public boolean isShutdown() {
      return this.delegatedExecutor.isShutdown();
   }

   public boolean isTerminated() {
      return this.isTerminated();
   }

   public void shutdown() {
      this.delegatedExecutor.shutdown();
   }

   public List<Runnable> shutdownNow() {
      return this.delegatedExecutor.shutdownNow();
   }

   public <T> Future<T> submit(Callable<T> task) {
      return this.delegatedExecutor.submit(task);
   }

   public Future<?> submit(Runnable task) {
      return this.delegatedExecutor.submit(task);
   }

   public <T> Future<T> submit(Runnable task, T result) {
      return this.delegatedExecutor.submit(task, result);
   }

   public String toString() {
      return this.getClass().getSimpleName() + "@" + this.hashCode();
   }
}
