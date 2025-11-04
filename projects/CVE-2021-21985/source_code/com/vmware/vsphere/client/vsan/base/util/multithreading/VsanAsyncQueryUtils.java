package com.vmware.vsphere.client.vsan.base.util.multithreading;

import com.google.common.base.Function;
import com.google.common.collect.Maps;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.core.Future;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import org.apache.commons.lang.Validate;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanAsyncQueryUtils {
   private static final Log _logger = LogFactory.getLog(VsanAsyncQueryUtils.class);

   public static ResultSet getProperties(List<Callable<VsanAsyncQueryUtils.RequestResult>> requestTasks) {
      Validate.notNull(requestTasks);
      ResultSet result = new ResultSet();

      try {
         List<VsanAsyncQueryUtils.RequestResult> requestResults = getResultsAsync(requestTasks);
         result = createResultSet(requestResults);
      } catch (InterruptedException var3) {
         _logger.error("Interrupted while executing query.", var3);
         result.error = var3;
      } catch (TimeoutException var4) {
         _logger.error("Task executor unexpectedly timed out.", var4);
         result.error = var4;
      } catch (Exception var5) {
         _logger.error("Executing query failed", var5);
         result.error = var5;
      }

      return result;
   }

   private static <T> List<VsanAsyncQueryUtils.TaskResult<T>> executeTasks(List<Callable<T>> requestTasks) {
      Validate.notNull(requestTasks);
      List<VsanAsyncQueryUtils.TaskResult<T>> results = new ArrayList();
      Iterator var3 = requestTasks.iterator();

      while(var3.hasNext()) {
         Callable task = (Callable)var3.next();

         try {
            results.add(new VsanAsyncQueryUtils.TaskResult(task.call(), (Exception)null, (VsanAsyncQueryUtils.TaskResult)null));
         } catch (Exception var5) {
            results.add(new VsanAsyncQueryUtils.TaskResult((Object)null, var5, (VsanAsyncQueryUtils.TaskResult)null));
         }
      }

      return results;
   }

   public static <K, T> Map<K, T> awaitAll(Map<K, Future<T>> tasks) {
      return awaitAll(tasks, new Function<Entry<K, Future<T>>, T>() {
         public T apply(Entry<K, Future<T>> future) {
            try {
               return ((Future)future.getValue()).get();
            } catch (ExecutionException var3) {
               throw new IllegalStateException("Failed to get result of task.", var3.getCause());
            } catch (Exception var4) {
               throw new IllegalStateException("Failed to get result of task.", var4);
            }
         }
      });
   }

   public static <K, T> Map<K, T> awaitAll(Map<K, Future<T>> tasks, Function<Entry<K, Future<T>>, T> awaitOne) {
      Map<K, T> result = Maps.newHashMap();
      Iterator var4 = tasks.entrySet().iterator();

      while(var4.hasNext()) {
         Entry<K, Future<T>> entry = (Entry)var4.next();
         T taskResult = awaitOne.apply(entry);
         if (taskResult != null) {
            result.put(entry.getKey(), taskResult);
         }
      }

      return result;
   }

   private static List<VsanAsyncQueryUtils.RequestResult> getResultsAsync(List<Callable<VsanAsyncQueryUtils.RequestResult>> requestTasks) throws InterruptedException, TimeoutException {
      List<VsanAsyncQueryUtils.TaskResult<VsanAsyncQueryUtils.RequestResult>> taskResults = executeTasks(requestTasks);
      List<VsanAsyncQueryUtils.RequestResult> requestResults = new ArrayList();

      VsanAsyncQueryUtils.RequestResult result;
      for(Iterator var4 = taskResults.iterator(); var4.hasNext(); requestResults.add(result)) {
         VsanAsyncQueryUtils.TaskResult<VsanAsyncQueryUtils.RequestResult> taskResult = (VsanAsyncQueryUtils.TaskResult)var4.next();
         result = (VsanAsyncQueryUtils.RequestResult)taskResult.getResult();
         if (taskResult.getException() != null && result.error == null) {
            result = new VsanAsyncQueryUtils.RequestResult(result.result, taskResult.getException(), result.target, result.property);
         }
      }

      return requestResults;
   }

   private static ResultSet createResultSet(List<VsanAsyncQueryUtils.RequestResult> requestResults) {
      assert requestResults != null : "requestResults is null";

      List<Exception> errors = new ArrayList();
      Map<ManagedObjectReference, ArrayList<PropertyValue>> items = new HashMap();
      ResultSet result = new ResultSet();
      Iterator var5 = requestResults.iterator();

      while(var5.hasNext()) {
         VsanAsyncQueryUtils.RequestResult requestResult = (VsanAsyncQueryUtils.RequestResult)var5.next();
         if (requestResult.error != null) {
            errors.add(requestResult.error);
         }

         if (!items.containsKey(requestResult.target)) {
            items.put(requestResult.target, new ArrayList());
         }

         ArrayList<PropertyValue> propertyResults = (ArrayList)items.get(requestResult.target);
         propertyResults.add(requestResult.toPropertyValue());
      }

      if (errors.size() > 0) {
         result.error = (Exception)errors.get(0);
      }

      result.items = new ResultItem[items.size()];
      int resultItemIndex = 0;

      for(Iterator var11 = items.keySet().iterator(); var11.hasNext(); ++resultItemIndex) {
         ManagedObjectReference moref = (ManagedObjectReference)var11.next();
         ResultItem resultItem = new ResultItem();
         resultItem.resourceObject = moref;
         ArrayList<PropertyValue> propValues = (ArrayList)items.get(moref);
         resultItem.properties = (PropertyValue[])propValues.toArray(new PropertyValue[propValues.size()]);
         result.items[resultItemIndex] = resultItem;
      }

      return result;
   }

   public static class RequestResult {
      public final Object result;
      public final Exception error;
      public final ManagedObjectReference target;
      public final String property;

      public RequestResult(Object result, Exception error, ManagedObjectReference target, String property) {
         Validate.notNull(target);
         this.result = result;
         this.error = error;
         this.target = target;
         this.property = property;
      }

      public PropertyValue toPropertyValue() {
         PropertyValue propValue = new PropertyValue();
         propValue.resourceObject = this.target;
         propValue.propertyName = this.property;
         propValue.value = this.result;
         return propValue;
      }
   }

   public static final class TaskResult<T> {
      private final T _result;
      private final Exception _exception;

      private TaskResult(T result, Exception exception) {
         this._result = result;
         this._exception = exception;
      }

      public T getResult() {
         return this._result;
      }

      public Exception getException() {
         return this._exception;
      }

      public String toString() {
         return "[result: " + this._result + ", exception: " + this._exception + "]";
      }

      // $FF: synthetic method
      TaskResult(Object var1, Exception var2, VsanAsyncQueryUtils.TaskResult var3) {
         this(var1, var2);
      }
   }
}
