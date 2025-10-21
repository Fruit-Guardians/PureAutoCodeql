package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.client.http;

import com.vmware.vim.vmomi.client.ext.InvocationContext;
import com.vmware.vim.vmomi.client.ext.RequestContextProvider;
import com.vmware.vim.vmomi.core.RequestContext;
import com.vmware.vim.vmomi.core.impl.RequestContextImpl;
import java.util.Map;

public class HttpRequestContextProvider implements RequestContextProvider {
   private final Map<String, Object> requestProperties;

   public HttpRequestContextProvider(Map<String, Object> requestProperties) {
      this.requestProperties = requestProperties;
   }

   public RequestContext getRequestContext(InvocationContext invocationContext) {
      RequestContextImpl result = new RequestContextImpl();
      result.putAll(this.requestProperties);
      return result;
   }
}
