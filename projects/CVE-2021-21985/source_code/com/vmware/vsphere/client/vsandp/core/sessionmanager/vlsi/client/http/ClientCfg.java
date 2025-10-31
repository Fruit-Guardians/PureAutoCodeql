package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http;

import com.vmware.vim.vmomi.client.Client;
import com.vmware.vim.vmomi.client.http.HttpClientConfiguration;
import com.vmware.vsphere.client.vsandp.core.sessionmanager.resource.resource.Resource;

public class ClientCfg extends Resource {
   protected HttpClientConfiguration clientConfig;
   protected Client extraClient;

   public ClientCfg(HttpClientConfiguration clientConfig, Client extraClient) {
      this.clientConfig = clientConfig;
      this.extraClient = extraClient;
   }

   public HttpClientConfiguration getClientConfig() {
      return this.clientConfig;
   }

   public void setClientConfig(HttpClientConfiguration clientConfig) {
      this.clientConfig = clientConfig;
   }

   public Client getExtraClient() {
      return this.extraClient;
   }

   public void setExtraClient(Client extraClient) {
      this.extraClient = extraClient;
   }
}
