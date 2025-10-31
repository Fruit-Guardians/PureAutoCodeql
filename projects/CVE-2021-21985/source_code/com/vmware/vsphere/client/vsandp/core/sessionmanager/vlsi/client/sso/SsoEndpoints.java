package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.sso;

public class SsoEndpoints {
   protected final ServiceEndpoint sts;
   protected final ServiceEndpoint admin;
   protected final ServiceEndpoint groupCheck;

   public SsoEndpoints(ServiceEndpoint sts, ServiceEndpoint admin, ServiceEndpoint groupCheck) {
      this.sts = sts;
      this.admin = admin;
      this.groupCheck = groupCheck;
   }

   public ServiceEndpoint getSts() {
      return this.sts;
   }

   public ServiceEndpoint getAdmin() {
      return this.admin;
   }

   public ServiceEndpoint getGroupCheck() {
      return this.groupCheck;
   }
}
