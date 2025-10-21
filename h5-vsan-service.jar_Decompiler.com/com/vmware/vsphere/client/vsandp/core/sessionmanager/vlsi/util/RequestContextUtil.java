package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.util;

import com.vmware.vim.binding.vmodl.ManagedObject;
import com.vmware.vim.sso.client.SamlToken;
import com.vmware.vim.vmomi.core.RequestContext;
import com.vmware.vim.vmomi.core.Stub;
import com.vmware.vim.vmomi.core.impl.RequestContextImpl;
import com.vmware.vim.vmomi.core.security.impl.SignInfoImpl;
import java.security.PrivateKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class RequestContextUtil {
   private static Logger log = LoggerFactory.getLogger(RequestContextUtil.class);

   public static <T extends ManagedObject> T withOperationId(T t) {
      String opId = MDC.get("operationID");
      if (opId == null) {
         return t;
      } else {
         RequestContext requestContext = ((Stub)t)._getRequestContext();
         if (requestContext == null) {
            requestContext = new RequestContextImpl();
         }

         ((RequestContext)requestContext).put("operationID", opId);
         ((Stub)t)._setRequestContext((RequestContext)requestContext);
         return t;
      }
   }

   public static <T extends ManagedObject> T withSignInfo(T t, PrivateKey privateKey, SamlToken token) {
      RequestContext requestContext = ((Stub)t)._getRequestContext();
      if (requestContext == null) {
         requestContext = new RequestContextImpl();
      }

      ((RequestContextImpl)requestContext).setSignInfo(new SignInfoImpl(privateKey, token));
      ((Stub)t)._setRequestContext((RequestContext)requestContext);
      return t;
   }
}
