package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client;

import com.vmware.vim.binding.vmodl.ManagedObject;
import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.vmomi.client.Client;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.Resource;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http.ClientCfg;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.util.MoRef;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.util.RequestContextUtil;

public class VlsiConnection extends Resource {
   protected Client client;
   protected ClientCfg clientCfg;
   protected VlsiSettings settings;

   public Client getClient() {
      return this.client;
   }

   protected void setClient(Client client) {
      this.client = client;
   }

   public ClientCfg getClientConfig() {
      return this.clientCfg;
   }

   public void setClientConfig(ClientCfg clientCfg) {
      this.clientCfg = clientCfg;
   }

   public VlsiSettings getSettings() {
      return this.settings;
   }

   public <T extends ManagedObject> T createStub(Class<T> clazz, String moId) {
      ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();

      ManagedObject var5;
      try {
         Thread.currentThread().setContextClassLoader(VlsiConnection.class.getClassLoader());
         var5 = RequestContextUtil.withOperationId(this.client.createStub(clazz, new MoRef(clazz, moId)));
      } finally {
         Thread.currentThread().setContextClassLoader(oldClassLoader);
      }

      return var5;
   }

   public <T extends ManagedObject> T createStub(Class<T> clazz, ManagedObjectReference moRef) {
      ClassLoader oldClassLoader = Thread.currentThread().getContextClassLoader();

      ManagedObject var5;
      try {
         Thread.currentThread().setContextClassLoader(VlsiConnection.class.getClassLoader());
         var5 = RequestContextUtil.withOperationId(this.client.createStub(clazz, moRef));
      } finally {
         Thread.currentThread().setContextClassLoader(oldClassLoader);
      }

      return var5;
   }

   public String toString() {
      String connectionType = this.getClass().getSimpleName();
      String host = this.settings != null ? this.settings.getHttpSettings().getHost() : "initializing";
      return String.format("%s(%s)", connectionType, host);
   }
}
