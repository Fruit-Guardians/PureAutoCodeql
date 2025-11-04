package com.vmware.vsphere.client.vsan.perf.model;

import com.vmware.vise.core.model.data;

@data
public class FeedbackSpec {
   public String transactionId;
   public String exceptionId;
   public String entityRefId;

   public FeedbackSpec(String transactionId, String exceptionId, String entityRefId) {
      this.transactionId = transactionId;
      this.exceptionId = exceptionId;
      this.entityRefId = entityRefId;
   }
}
