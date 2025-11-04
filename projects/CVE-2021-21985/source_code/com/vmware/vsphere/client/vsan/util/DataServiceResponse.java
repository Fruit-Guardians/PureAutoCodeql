package com.vmware.vsphere.client.vsan.util;

import com.vmware.vise.data.query.PropertyValue;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DataServiceResponse {
   private static final Logger logger = LoggerFactory.getLogger(DataServiceResponse.class);
   public static final String RESOURCE_OBJECT = "__resourceObject";
   private final PropertyValue[] propertyValues;
   private Map<Object, Map<String, Object>> mappedProperties;
   private final String[] properties;

   DataServiceResponse(PropertyValue[] propertyValues, String[] properties) {
      this.properties = properties;
      this.propertyValues = propertyValues;
   }

   public String[] getRequestedProperties() {
      return this.properties;
   }

   public PropertyValue[] getPropertyValues() {
      return this.propertyValues;
   }

   public <T> Map<T, Map<String, Object>> getMap() {
      if (this.mappedProperties == null) {
         if (this.propertyValues.length % this.properties.length != 0) {
            logger.warn("The DataService didn't return data for all the requested properties!", this.propertyValues);
         }

         this.mappedProperties = new HashMap();
         PropertyValue[] var4;
         int var3 = (var4 = this.propertyValues).length;

         for(int var2 = 0; var2 < var3; ++var2) {
            PropertyValue propertyValue = var4[var2];
            if (!(propertyValue.resourceObject instanceof Object)) {
               throw new IllegalStateException("unknown resource object: " + propertyValue.resourceObject);
            }

            Object resourceObject = propertyValue.resourceObject;
            Map<String, Object> resourceProperties = (Map)this.mappedProperties.get(resourceObject);
            if (resourceProperties == null) {
               resourceProperties = new HashMap();
               ((Map)resourceProperties).put("__resourceObject", resourceObject);
               this.mappedProperties.put(resourceObject, resourceProperties);
            }

            ((Map)resourceProperties).put(propertyValue.propertyName, propertyValue.value);
         }
      }

      return Collections.unmodifiableMap(this.mappedProperties);
   }

   public Set<Object> getResourceObjects() {
      return this.getMap().keySet();
   }

   public <P> P getProperty(Object resourceObject, String property) {
      Map<String, Object> objectProperties = (Map)this.getMap().get(resourceObject);
      if (!objectProperties.containsKey(property)) {
         throw new IllegalStateException("property not found: " + property);
      } else {
         return objectProperties.get(property);
      }
   }
}
