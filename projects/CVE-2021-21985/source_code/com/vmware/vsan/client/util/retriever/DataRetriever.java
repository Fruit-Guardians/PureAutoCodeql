package com.vmware.vsan.client.util.retriever;

import java.util.concurrent.ExecutionException;

interface DataRetriever<T> {
   void start();

   T getResult() throws ExecutionException, InterruptedException;
}
