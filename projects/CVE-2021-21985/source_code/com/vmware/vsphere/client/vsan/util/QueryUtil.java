package com.vmware.vsphere.client.vsan.util;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.binding.vmodl.fault.ManagedObjectNotFound;
import com.vmware.vise.core.model.data;
import com.vmware.vise.data.Constraint;
import com.vmware.vise.data.ParameterSpec;
import com.vmware.vise.data.PropertySpec;
import com.vmware.vise.data.ResourceSpec;
import com.vmware.vise.data.query.Comparator;
import com.vmware.vise.data.query.CompositeConstraint;
import com.vmware.vise.data.query.Conjoiner;
import com.vmware.vise.data.query.DataService;
import com.vmware.vise.data.query.ObjectIdentityConstraint;
import com.vmware.vise.data.query.ObjectReferenceService;
import com.vmware.vise.data.query.PropertyConstraint;
import com.vmware.vise.data.query.PropertyRequestSpec;
import com.vmware.vise.data.query.PropertyValue;
import com.vmware.vise.data.query.QuerySpec;
import com.vmware.vise.data.query.RelationalConstraint;
import com.vmware.vise.data.query.RequestSpec;
import com.vmware.vise.data.query.Response;
import com.vmware.vise.data.query.ResultItem;
import com.vmware.vise.data.query.ResultSet;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.commons.lang.ArrayUtils;

@data
public class QueryUtil {
   private static ObjectReferenceService _objectReferenceService;
   private static DataService _dataService;
   public static final String SERVER_GUID_PROPERTY = "serverGuid";
   public static final String NAME_PROPERTY = "name";
   public static final String CHILD_ENTITY_PROPERTY = "childEntity";
   public static final String PRIMARY_ICON_ID_PROPERTY = "primaryIconId";
   public static final String CLUSTER_PROPERTY = "cluster";
   public static final String CLUSTER_HOST_PROPERTY = "host";
   public static final String DATASTORE_PROPERTY = "datastore";
   public static final String CLUSTER_HOST_COUNT_PROPERTY = "host._length";
   public static final String HOST_VSAN_NODE_UUID_PROPERTY = "config.vsanHostConfig.clusterInfo.nodeUuid";
   public static final String HOST_CONNECTION_STATE_PROPERTY = "runtime.connectionState";
   public static final String HOST_MAINTENANCE_MODE_PROPERTY = "runtime.inMaintenanceMode";
   public static final String HOST_QUARANTINE_MODE_PROPERTY = "runtime.inQuarantineMode";
   public static final String WITNESS_HOST_RELATION = "witnessHost";
   public static final String ALL_VSAN_HOSTS_RELATION = "allVsanHosts";
   public static final String VSAN_DISK_GROUP_PROPERTY_NAME = "vsanDisksAndGroupsData";
   public static final String VSAN_DISK_MAP_DATA = "vsanDiskMapData";
   public static final String VSAN_PHYSICAL_DISK_VIRTUAL_MAPPING = "vsanPhysicalDiskVirtualMapping";
   public static final String VSAN_HOST_STORAGE_ADAPTER_DEVICES = "vsanStorageAdapterDevices";
   public static final String VM_DEVICES_PROPERTY = "config.hardware.device";
   public static final String VM_NAMESPACE_CAPABILITY_METADATA = "namespaceCapabilityMetadata";
   public static final String VM_PATH_NAME = "summary.config.vmPathName";
   public static final String VSAN_ENABLED_PROPERTY = "configurationEx[@type='ClusterConfigInfoEx'].vsanConfigInfo.enabled";
   public static final String HOST_VERSION_PROPERTY = "config.product.version";
   public static final String HOST_VSAN_CONFIG_PROPERTY = "config.vsanHostConfig";
   public static final String HOST_VSAN_ENABLED_PROPERTY = "config.vsanHostConfig.enabled";
   public static final String ISCSI_TARGETS_PROPERTY = "iscsiTargets";
   public static final String DISK_CLAIM_PROPERTY = "configurationEx[@type='ClusterConfigInfoEx'].vsanConfigInfo.defaultConfig.autoClaimStorage";
   public static final String CLUSTER_VSAN_CONFIG_UUID_PROPERTY = "configurationEx[@type='ClusterConfigInfoEx'].vsanConfigInfo.defaultConfig.uuid";
   public static final String CLUSTER_DRS_ENABLED = "configuration.drsConfig";
   public static final String VM_STORAGE_OBJECT_ID_PROPERTY = "config.vmStorageObjectId";
   public static final String IS_VM_DATA_PROTECTED_PROPERTY = "isVmDataProtected";
   public static final String DATASTORE_TYPE_PROPERTY = "summary.type";
   public static final String DATASTORE_URL = "summary.url";
   public static final String DATACENTER_RELATION = "dc";
   public static final String DATASTORE_HOST_MOUNTS = "host";
   public static final String HOST_FAULT_DOMAIN = "config.vsanHostConfig.faultDomainInfo.name";

   public static void setObjectReferenceService(ObjectReferenceService objectReferenceService) {
      _objectReferenceService = objectReferenceService;
   }

   public static void setDataService(DataService dataService) {
      _dataService = dataService;
   }

   public static <T> T getProperty(ManagedObjectReference target, String propertyName, Object parameter) throws Exception {
      ResourceSpec rs = new ResourceSpec();
      ObjectIdentityConstraint oic = new ObjectIdentityConstraint();
      oic.target = target;
      oic.targetType = target.getType();
      rs.constraint = oic;
      PropertySpec ps = new PropertySpec();
      ps.propertyNames = new String[]{propertyName};
      if (parameter != null) {
         ParameterSpec paramSpec = new ParameterSpec();
         paramSpec.parameter = parameter;
         paramSpec.propertyName = propertyName;
         ps.parameters = new ParameterSpec[]{paramSpec};
      } else {
         ps.parameters = new ParameterSpec[0];
      }

      rs.propertySpecs = new PropertySpec[]{ps};
      QuerySpec query = new QuerySpec();
      query.resourceSpec = rs;
      RequestSpec request = new RequestSpec();
      request.querySpec = new QuerySpec[]{query};
      Response response = _dataService.getData(request);
      if (response.resultSet.length == 1 && response.resultSet[0].items.length == 1) {
         if (response.resultSet[0].error != null) {
            throw response.resultSet[0].error;
         } else {
            PropertyValue[] var12;
            int var11 = (var12 = response.resultSet[0].items[0].properties).length;

            for(int var10 = 0; var10 < var11; ++var10) {
               PropertyValue pv = var12[var10];
               if (pv.propertyName.equals(propertyName)) {
                  return pv.value;
               }
            }

            throw new IllegalStateException("Property value not found: " + propertyName);
         }
      } else {
         throw new IllegalStateException("illegal resource, 1 item expected");
      }
   }

   public static <T> T getProperty(ManagedObjectReference target, String propertyName) throws Exception {
      return getProperty(target, propertyName, (Object)null);
   }

   public static DataServiceResponse getProperties(ManagedObjectReference obj, String[] properties) throws Exception {
      return getProperties(new ManagedObjectReference[]{obj}, properties);
   }

   public static DataServiceResponse getProperties(ManagedObjectReference[] objs, String[] properties) throws Exception {
      if (objs != null && objs.length != 0 && properties != null && properties.length != 0) {
         Object obj = objs[0];
         QuerySpec query = buildQuerySpec(objs, properties);
         query.name = _objectReferenceService.getUid(obj) + ".properties";
         ResultSet resultSet = getData(query);
         return getDataServiceResponse(resultSet, properties);
      } else {
         throw new Exception("Invalid parameters for getProperties");
      }
   }

   public static DataServiceResponse getPropertyForRelatedObjects(ManagedObjectReference object, String relationship, String targetType, String property) throws Exception {
      return getPropertiesForRelatedObjects(object, relationship, targetType, new String[]{property});
   }

   public static DataServiceResponse getPropertiesForRelatedObjects(ManagedObjectReference obj, String relationship, String targetType, String[] properties) throws Exception {
      if (obj != null && properties != null && properties.length != 0) {
         if (relationship != null && relationship.length() != 0) {
            ObjectIdentityConstraint objectConstraint = createObjectIdentityConstraint(obj);
            RelationalConstraint relationalConstraint = createRelationalConstraint(relationship, objectConstraint, true, targetType);
            QuerySpec query = buildQuerySpec((Constraint)relationalConstraint, properties);
            query.name = _objectReferenceService.getUid(obj) + "." + relationship + ".properties";
            ResultSet resultSet = getData(query);
            return getDataServiceResponse(resultSet, properties);
         } else {
            return getProperties(obj, properties);
         }
      } else {
         throw new Exception("invalid parameters in getPropertiesForRelatedObjects");
      }
   }

   public static DataServiceResponse getDataServiceResponse(ResultSet resultSet, String[] properties) throws Exception {
      if (resultSet.totalMatchedObjectCount == 0 && resultSet.error != null) {
         throw resultSet.error;
      } else {
         List<PropertyValue> result = new ArrayList();
         if (resultSet != null && resultSet.items != null) {
            ResultItem[] var6;
            int var5 = (var6 = resultSet.items).length;

            for(int var4 = 0; var4 < var5; ++var4) {
               ResultItem item = var6[var4];
               Map<String, PropertyValue> resultValues = new HashMap();
               if (item != null && item.properties != null) {
                  PropertyValue[] var11;
                  int var10 = (var11 = item.properties).length;

                  int var9;
                  for(var9 = 0; var9 < var10; ++var9) {
                     PropertyValue propValue = var11[var9];
                     if (propValue != null) {
                        if (propValue.resourceObject == null && item.resourceObject != null) {
                           propValue.resourceObject = item.resourceObject;
                        }

                        resultValues.put(propValue.propertyName, propValue);
                        result.add(propValue);
                     }
                  }

                  String[] var14 = properties;
                  var10 = properties.length;

                  for(var9 = 0; var9 < var10; ++var9) {
                     String property = var14[var9];
                     if (!resultValues.containsKey(property)) {
                        PropertyValue pv = new PropertyValue();
                        pv.propertyName = property;
                        pv.resourceObject = item.resourceObject;
                        pv.value = null;
                        result.add(pv);
                     }
                  }
               }
            }
         }

         return new DataServiceResponse((PropertyValue[])result.toArray(new PropertyValue[0]), properties);
      }
   }

   public static ResultSet getData(QuerySpec query) throws Exception {
      return getDataMultiSpec(new QuerySpec[]{query})[0];
   }

   public static ResultSet[] getDataMultiSpec(QuerySpec[] queries) throws Exception {
      RequestSpec requestSpec = new RequestSpec();
      requestSpec.querySpec = queries;
      Response response = _dataService.getData(requestSpec);
      ResultSet[] result = response.resultSet;
      if (result != null && result.length != 0 && result[0] != null) {
         if (response.resultSet[0].error != null) {
            throw response.resultSet[0].error;
         } else {
            return result;
         }
      } else {
         throw new Exception("Empty result");
      }
   }

   public static QuerySpec buildQuerySpec(ManagedObjectReference entity, String[] properties) {
      ObjectIdentityConstraint oc = new ObjectIdentityConstraint();
      oc.target = entity;
      String targetType = _objectReferenceService.getResourceObjectType(entity);
      Set<String> targetTypes = new HashSet();
      targetTypes.add(targetType);
      QuerySpec query = buildQuerySpec(oc, properties, targetTypes);
      return query;
   }

   public static QuerySpec buildQuerySpec(ManagedObjectReference[] entities, String[] properties) {
      if (entities.length == 1) {
         return buildQuerySpec(entities[0], properties);
      } else {
         CompositeConstraint cc = new CompositeConstraint();
         cc.conjoiner = Conjoiner.OR;
         Constraint[] nestedConstraints = new Constraint[entities.length];
         Set<String> targetTypes = new HashSet();
         String targetType = null;

         for(int index = 0; index < entities.length; ++index) {
            ObjectIdentityConstraint oc = new ObjectIdentityConstraint();
            oc.target = entities[index];
            nestedConstraints[index] = oc;
            targetType = _objectReferenceService.getResourceObjectType(oc.target);
            targetTypes.add(targetType);
         }

         cc.nestedConstraints = nestedConstraints;
         QuerySpec query = buildQuerySpec(cc, properties, targetTypes);
         return query;
      }
   }

   public static QuerySpec buildQuerySpec(Constraint constraint, String[] properties) {
      QuerySpec query = buildQuerySpec(constraint, properties, (Set)null);
      return query;
   }

   public static QuerySpec buildQuerySpec(Constraint constraint, String[] properties, Set<String> targetTypes) {
      QuerySpec query = new QuerySpec();
      ResourceSpec resourceSpec = new ResourceSpec();
      resourceSpec.constraint = constraint;
      List<PropertySpec> pSpecs = new ArrayList();
      if (targetTypes != null) {
         Iterator var7 = targetTypes.iterator();

         while(var7.hasNext()) {
            String targetType = (String)var7.next();
            PropertySpec propSpec = createPropertySpec(properties, targetType);
            pSpecs.add(propSpec);
         }
      } else {
         PropertySpec propSpec = createPropertySpec(properties, (String)null);
         pSpecs.add(propSpec);
      }

      resourceSpec.propertySpecs = (PropertySpec[])pSpecs.toArray(new PropertySpec[0]);
      query.resourceSpec = resourceSpec;
      return query;
   }

   public static RelationalConstraint createRelationalConstraint(String relationship, Constraint constraintOnRelatedObject, Boolean hasInverseRelation, String targetType) {
      RelationalConstraint rc = new RelationalConstraint();
      rc.relation = relationship;
      rc.hasInverseRelation = hasInverseRelation;
      rc.constraintOnRelatedObject = constraintOnRelatedObject;
      rc.targetType = targetType;
      return rc;
   }

   public static ObjectIdentityConstraint createObjectIdentityConstraint(Object entity) {
      ObjectIdentityConstraint oc = new ObjectIdentityConstraint();
      oc.target = entity;
      oc.targetType = _objectReferenceService.getResourceObjectType(entity);
      return oc;
   }

   public static PropertyConstraint createPropertyConstraint(String targetType, String propertyName, Comparator comparator, Object value) {
      PropertyConstraint propConstraint = new PropertyConstraint();
      propConstraint.targetType = targetType;
      propConstraint.propertyName = propertyName;
      propConstraint.comparableValue = value;
      propConstraint.comparator = comparator;
      return propConstraint;
   }

   public static CompositeConstraint createCompositeConstraint(Conjoiner conjoiner, String targetType, Constraint... nestedConstraints) {
      CompositeConstraint constraint = new CompositeConstraint();
      constraint.targetType = targetType;
      constraint.conjoiner = conjoiner;
      constraint.nestedConstraints = nestedConstraints;
      return constraint;
   }

   private static PropertySpec createPropertySpec(String[] properties, String targetType) {
      PropertySpec propSpec = new PropertySpec();
      propSpec.type = targetType;
      propSpec.propertyNames = properties;
      return propSpec;
   }

   public static Response newResponse(ResultSet... resultSet) {
      Response result = new Response();
      result.resultSet = resultSet;
      return result;
   }

   public static ResultSet newResultSet(ResultItem... items) {
      ResultSet result = new ResultSet();
      result.items = items;
      result.totalMatchedObjectCount = items != null ? items.length : null;
      return result;
   }

   public static ResultItem newResultItem(Object object, PropertyValue... props) {
      ResultItem result = new ResultItem();
      result.resourceObject = object;
      result.properties = props;
      return result;
   }

   public static PropertyValue newProperty(String name, Object value) {
      PropertyValue result = new PropertyValue();
      result.propertyName = name;
      result.value = value;
      return result;
   }

   public static boolean isAnyPropertyRequested(PropertySpec[] propertySpecs, String... properties) {
      if (!ArrayUtils.isEmpty(propertySpecs) && !ArrayUtils.isEmpty(properties)) {
         Set<String> propertiesSet = new HashSet(Arrays.asList(properties));
         boolean result = false;
         PropertySpec[] var7 = propertySpecs;
         int var6 = propertySpecs.length;

         for(int var5 = 0; var5 < var6; ++var5) {
            PropertySpec pSpec = var7[var5];
            String[] var11;
            int var10 = (var11 = pSpec.propertyNames).length;

            for(int var9 = 0; var9 < var10; ++var9) {
               String p = var11[var9];
               if (propertiesSet.contains(p)) {
                  result = true;
                  break;
               }
            }
         }

         return result;
      } else {
         return false;
      }
   }

   public static String[] getPropertyNames(PropertySpec[] props) {
      Set<String> allProperties = new HashSet();
      PropertySpec[] var5 = props;
      int var4 = props.length;

      for(int var3 = 0; var3 < var4; ++var3) {
         PropertySpec propSpec = var5[var3];
         if (!ArrayUtils.isEmpty(propSpec.propertyNames)) {
            String[] var9;
            int var8 = (var9 = propSpec.propertyNames).length;

            for(int var7 = 0; var7 < var8; ++var7) {
               String propertyName = var9[var7];
               allProperties.add(propertyName);
            }
         }
      }

      return (String[])allProperties.toArray(new String[allProperties.size()]);
   }

   public static Map<ManagedObjectReference, List<PropertyValue>> groupPropertiesByObject(PropertyValue[] properties) {
      Map<ManagedObjectReference, List<PropertyValue>> result = new HashMap();
      PropertyValue[] var5 = properties;
      int var4 = properties.length;

      for(int var3 = 0; var3 < var4; ++var3) {
         PropertyValue property = var5[var3];
         ManagedObjectReference objectMor = (ManagedObjectReference)property.resourceObject;
         if (!result.containsKey(objectMor)) {
            result.put(objectMor, new ArrayList());
         }

         ((List)result.get(objectMor)).add(property);
      }

      return result;
   }

   public static Constraint combineIntoSingleConstraint(Constraint[] constraints, Conjoiner conjoiner) {
      if (constraints != null && constraints.length != 0) {
         return (Constraint)(constraints.length == 1 ? constraints[0] : createCompositeConstraint(constraints, conjoiner));
      } else {
         return null;
      }
   }

   public static CompositeConstraint createCompositeConstraint(Constraint[] nestedConstraints, Conjoiner conjoiner) {
      CompositeConstraint compositeConstraint = new CompositeConstraint();
      compositeConstraint.nestedConstraints = nestedConstraints;
      compositeConstraint.conjoiner = conjoiner;
      return compositeConstraint;
   }

   public static Constraint createConstraintForRelationship(Object object, String relationship, String targetType) {
      ObjectIdentityConstraint objectConstraint = createObjectIdentityConstraint(object);
      RelationalConstraint relationalConstraint = createRelationalConstraint(relationship, objectConstraint, true, targetType);
      return relationalConstraint;
   }

   public static void throwIfObjectNotFound(Object[] targetEntities, ResultSet resultSet) throws ManagedObjectNotFound {
      Object[] deletedObjects = detectDeletedObjects(targetEntities, resultSet);
      if (deletedObjects.length != 0) {
         Object firstObject = deletedObjects[0];
         if (firstObject instanceof ManagedObjectReference) {
            throw new ManagedObjectNotFound((ManagedObjectReference)firstObject);
         } else {
            new ManagedObjectNotFound();
         }
      }
   }

   public static Object[] detectDeletedObjects(Object[] targetEntities, ResultSet resultSet) {
      if (targetEntities != null && targetEntities.length != 0) {
         if (resultSet != null && resultSet.error == null) {
            HashMap<String, Object> deletedObjects = new HashMap();
            Object[] var6 = targetEntities;
            int var5 = targetEntities.length;

            int var4;
            for(var4 = 0; var4 < var5; ++var4) {
               Object entity = var6[var4];
               deletedObjects.put(_objectReferenceService.getUid(entity), entity);
            }

            if (resultSet.items != null) {
               ResultItem[] var10;
               var5 = (var10 = resultSet.items).length;

               for(var4 = 0; var4 < var5; ++var4) {
                  ResultItem resultItem = var10[var4];
                  Object object = resultItem.resourceObject;
                  if (object != null) {
                     deletedObjects.remove(_objectReferenceService.getUid(object));
                  }
               }
            }

            Collection<Object> result = deletedObjects.values();
            return result.toArray();
         } else {
            return (Object[])Array.newInstance(targetEntities[0].getClass(), 0);
         }
      } else {
         return targetEntities;
      }
   }

   public static boolean isValidRequest(PropertyRequestSpec propertyRequest) {
      if (propertyRequest == null) {
         return false;
      } else {
         return !ArrayUtils.isEmpty(propertyRequest.objects) && !ArrayUtils.isEmpty(propertyRequest.properties);
      }
   }

   public static PropertyValue[] createPropValue(String name, Object value, Object provider) {
      PropertyValue propValue = new PropertyValue();
      propValue.propertyName = name;
      propValue.value = value;
      propValue.resourceObject = provider;
      return new PropertyValue[]{propValue};
   }

   public static ResultItem createResultItem(String property, Object value, Object provider) {
      ResultItem resultItem = new ResultItem();
      resultItem.resourceObject = provider;
      resultItem.properties = createPropValue(property, value, provider);
      return resultItem;
   }
}
