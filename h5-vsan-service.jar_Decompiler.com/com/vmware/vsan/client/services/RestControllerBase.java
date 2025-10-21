package com.vmware.vsan.client.services;

import com.google.gson.Gson;
import com.vmware.vise.data.query.DataException;
import com.vmware.vsphere.client.vsan.util.MessageBundle;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.http.HttpServletResponse;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

public abstract class RestControllerBase {
   protected final Log _logger = LogFactory.getLog(this.getClass());
   protected static final String RETURN_TYPE_RESULT = "result";
   protected static final String RETURN_TYPE_ERROR = "error";
   private static final String CLIENT_ABORT_EXCEPTION = "org.apache.catalina.connector.ClientAbortException";
   @Autowired
   private MessageBundle messages;

   @ExceptionHandler({Exception.class})
   @ResponseBody
   public Map<String, String> handleException(Exception ex, HttpServletResponse response) {
      response.setStatus(HttpStatus.INTERNAL_SERVER_ERROR.value());
      Map<String, String> errorMap = new HashMap();
      errorMap.put("error", this.messages.string("vsan.common.generic.error"));
      String[] rootCauseStack = ExceptionUtils.getRootCauseStackTrace(ex);
      if (rootCauseStack.length > 0) {
         if (ex instanceof IOException && "org.apache.catalina.connector.ClientAbortException".equals(ex.getClass().getName())) {
            return null;
         }

         this._logger.error("Exception handled in a controller: ", ex);
      }

      if (ex instanceof DataException) {
         DataException de = (DataException)ex;
         Gson gson = new Gson();
         if (de.objects != null && de.objects.length > 0) {
            errorMap.put("de_objects", gson.toJson(de.objects));
         }

         if (de.properties != null && de.properties.length > 0) {
            errorMap.put("de_properties", gson.toJson(de.properties));
         }
      }

      return errorMap;
   }
}
