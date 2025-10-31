package com.vmware.vsan.client.services;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.data.query.ObjectReferenceService;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vsphere.client.vsan.util.QueryUtil;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
@RequestMapping(
   value = {"/data"},
   method = {RequestMethod.GET}
)
public class DataAccessController extends RestControllerBase {
   private static final String OBJECT_ID = "id";
   private final ObjectReferenceService _objectReferenceService;

   @Autowired
   public DataAccessController(@Qualifier("objectReferenceService") ObjectReferenceService objectReferenceService) {
      this._objectReferenceService = objectReferenceService;
      QueryUtil.setObjectReferenceService(objectReferenceService);
   }

   public DataAccessController() {
      this._objectReferenceService = null;
   }

   @RequestMapping({"/properties/{objectId}"})
   @ResponseBody
   public Map<String, Object> getProperties(@PathVariable("objectId") String encodedObjectId, @RequestParam("properties") String properties) throws Exception {
      ManagedObjectReference ref = this.getDecodedReference(encodedObjectId);
      String objectId = this._objectReferenceService.getUid(ref);
      String[] props = properties.split(",");
      PropertyValue[] pvs = QueryUtil.getProperties(ref, props).getPropertyValues();
      Map<String, Object> propsMap = new HashMap();
      propsMap.put("id", objectId);
      PropertyValue[] var11 = pvs;
      int var10 = pvs.length;

      for(int var9 = 0; var9 < var10; ++var9) {
         PropertyValue pv = var11[var9];
         propsMap.put(pv.propertyName, pv.value);
      }

      return propsMap;
   }

   @RequestMapping({"/multiObjectProperties/{objectIds}"})
   @ResponseBody
   public Object getMultiObjectProperties(@PathVariable("objectIds") String[] objectIds, @RequestParam("properties") String props) throws Exception {
      List<Object> objects = new ArrayList();
      String[] var7 = objectIds;
      int var6 = objectIds.length;

      for(int var5 = 0; var5 < var6; ++var5) {
         String objectId = var7[var5];
         objects.add(this.getDecodedReference(objectId));
      }

      String[] properties = props.split(",");
      PropertyValue[] pvs = QueryUtil.getProperties((ManagedObjectReference[])objects.toArray(new ManagedObjectReference[0]), properties).getPropertyValues();
      return pvs;
   }

   @RequestMapping({"/propertiesByRelation/{objectId}"})
   @ResponseBody
   public PropertyValue[] getPropertiesForRelatedObject(@PathVariable("objectId") String encodedObjectId, @RequestParam(value = "relation",required = true) String relation, @RequestParam(value = "targetType",required = true) String targetType, @RequestParam(value = "properties",required = true) String properties) throws Exception {
      ManagedObjectReference ref = this.getDecodedReference(encodedObjectId);
      String[] props = properties.split(",");
      PropertyValue[] result = QueryUtil.getPropertiesForRelatedObjects(ref, relation, targetType, props).getPropertyValues();
      return result;
   }

   private ManagedObjectReference getDecodedReference(String encodedObjectId) throws Exception {
      Object ref = this._objectReferenceService.getReference(encodedObjectId, true);
      if (ref == null) {
         throw new Exception("Object not found with id: " + encodedObjectId);
      } else if (!(ref instanceof ManagedObjectReference)) {
         throw new Exception("The only supported object references are of type ManagedObjectReference: " + ref);
      } else {
         return (ManagedObjectReference)ref;
      }
   }
}
