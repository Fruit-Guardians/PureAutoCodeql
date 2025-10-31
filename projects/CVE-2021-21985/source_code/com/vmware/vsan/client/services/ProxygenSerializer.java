package com.vmware.vsan.client.services;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vise.core.model.data;
import java.lang.annotation.Annotation;
import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

public class ProxygenSerializer {
   private static Logger logger = LoggerFactory.getLogger(ProxygenSerializer.class);

   public Object deserialize(Object data, Class<?> type, ProxygenSerializer.ElementType metadata) throws Exception {
      try {
         if (data == null) {
            return type.isPrimitive() ? this.getPrimitiveDefaultValue(type) : null;
         } else if (Map.class.isAssignableFrom(type)) {
            return this.deserializeAsMap(data, metadata);
         } else if (List.class.isAssignableFrom(type)) {
            return this.deserializeAsList(data, metadata);
         } else if (Set.class.isAssignableFrom(type)) {
            return this.deserializeAsSet(data, metadata);
         } else if (Date.class.isAssignableFrom(type)) {
            return new Date(Long.parseLong(data.toString()));
         } else if (Calendar.class.isAssignableFrom(type)) {
            Calendar result = Calendar.getInstance();
            result.setTimeInMillis(Long.parseLong(data.toString()));
            return result;
         } else if (!type.isArray()) {
            if (Enum.class.isAssignableFrom(type)) {
               return type.getMethod("valueOf", String.class).invoke((Object)null, data.toString());
            } else {
               return data instanceof Map ? this.deserializeAsModel((Map)data, type) : this.deserializeAsPrimitive(data, type);
            }
         } else {
            if (type.getComponentType() == Byte.TYPE || type.getComponentType() == Byte.class) {
               if (data instanceof String) {
                  return ((String)data).getBytes();
               }

               if (data instanceof List) {
                  List<Number> list = (List)data;
                  byte[] result = new byte[list.size()];

                  for(int i = 0; i < list.size(); ++i) {
                     result[i] = ((Number)list.get(i)).byteValue();
                  }

                  return result;
               }
            }

            return this.deserializeAsArray((List)data, type);
         }
      } catch (Exception var7) {
         throw new IllegalStateException("Cannot deserialize as " + type + ": " + data, var7);
      }
   }

   private Object getPrimitiveDefaultValue(Class<?> type) {
      if (type == Boolean.TYPE) {
         return false;
      } else if (type == Byte.TYPE) {
         return 0;
      } else if (type == Short.TYPE) {
         return Short.valueOf((short)0);
      } else if (type == Integer.TYPE) {
         return 0;
      } else if (type == Long.TYPE) {
         return 0L;
      } else if (type == Float.TYPE) {
         return 0.0F;
      } else {
         return type == Double.TYPE ? 0.0D : type.cast((Object)null);
      }
   }

   private Object deserializeAsPrimitive(Object data, Class<?> type) {
      try {
         if (type != Boolean.TYPE && type != Boolean.class) {
            if (type != Byte.TYPE && type != Byte.class) {
               if (type != Short.TYPE && type != Short.class) {
                  if (type != Integer.TYPE && type != Integer.class) {
                     if (type != Long.TYPE && type != Long.class) {
                        if (type != Float.TYPE && type != Float.class) {
                           return type != Double.TYPE && type != Double.class ? type.cast(data) : ((Number)data).doubleValue();
                        } else {
                           return ((Number)data).floatValue();
                        }
                     } else {
                        return ((Number)data).longValue();
                     }
                  } else {
                     return ((Number)data).intValue();
                  }
               } else {
                  return ((Number)data).shortValue();
               }
            } else {
               return ((Number)data).byteValue();
            }
         } else {
            return Boolean.valueOf("" + data);
         }
      } catch (Exception var4) {
         throw new IllegalArgumentException(String.format("Cannot deserialize primitive %s(%s) as %s", data.getClass(), data, type), var4);
      }
   }

   private Object deserializeAsModel(Map<String, Object> data, Class<?> type) throws Exception {
      Map<String, Object> kvPairs = data;
      Object instance = type.getConstructor().newInstance();
      Iterator var6 = data.keySet().iterator();

      while(true) {
         while(true) {
            label51:
            while(var6.hasNext()) {
               String key = (String)var6.next();
               Method[] var10;
               int var9 = (var10 = type.getMethods()).length;

               int var8;
               for(var8 = 0; var8 < var9; ++var8) {
                  Method setter = var10[var8];
                  if (setter.getName().equals("set" + Character.toUpperCase(key.charAt(0)) + key.substring(1))) {
                     Annotation[][] annotations = setter.getParameterAnnotations();
                     if (annotations.length == 1) {
                        ProxygenSerializer.ElementType innerMeta = null;
                        Annotation[] var16;
                        int var15 = (var16 = annotations[0]).length;

                        for(int var14 = 0; var14 < var15; ++var14) {
                           Annotation a = var16[var14];
                           if (a instanceof ProxygenSerializer.ElementType) {
                              innerMeta = (ProxygenSerializer.ElementType)a;
                              break;
                           }
                        }

                        try {
                           setter.invoke(instance, this.deserialize(kvPairs.get(key), setter.getParameterTypes()[0], innerMeta));
                           continue label51;
                        } catch (Exception var18) {
                           throw new IllegalArgumentException(String.format("Cannot deserialize property '%s' in %s", key, type), var18);
                        }
                     }
                  }
               }

               Field[] var20;
               var9 = (var20 = type.getFields()).length;

               for(var8 = 0; var8 < var9; ++var8) {
                  Field field = var20[var8];
                  if (field.getName().equals(key)) {
                     try {
                        field.set(instance, this.deserialize(kvPairs.get(key), field.getType(), (ProxygenSerializer.ElementType)field.getAnnotation(ProxygenSerializer.ElementType.class)));
                        continue label51;
                     } catch (Exception var17) {
                        throw new IllegalArgumentException(String.format("Cannot deserialize property '%s' in %s", key, type), var17);
                     }
                  }
               }

               logger.warn("No field/setter found for property '" + key + "' in " + type);
            }

            return instance;
         }
      }
   }

   private Object deserializeAsArray(List<Object> data, Class<?> type) throws Exception {
      List<Object> source = data;
      Object[] result = (Object[])Array.newInstance(type.getComponentType(), data.size());

      for(int i = 0; i < result.length; ++i) {
         result[i] = this.deserialize(source.get(i), type.getComponentType(), (ProxygenSerializer.ElementType)null);
      }

      return result;
   }

   private Object deserializeAsSet(Object data, ProxygenSerializer.ElementType metadata) throws Exception {
      if (metadata == null) {
         logger.debug("Deserializing set without metadata. This may be due to forgotten annotation on a field or parameter or a return value may consist of nested collections. Returning raw set instance as best effort: " + data);
         return new HashSet((List)data);
      } else {
         List<Object> source = (List)data;
         Set<Object> result = new HashSet(source.size());
         Iterator var6 = source.iterator();

         while(var6.hasNext()) {
            Object val = var6.next();
            result.add(this.deserialize(val, metadata.value(), (ProxygenSerializer.ElementType)null));
         }

         return result;
      }
   }

   private Object deserializeAsList(Object data, ProxygenSerializer.ElementType metadata) throws Exception {
      if (metadata == null) {
         logger.debug("Deserializing list without metadata. This may be due to forgotten annotation on a field or parameter or a return value may consist of nested collections. Returning raw list instance as best effort: " + data);
         return data;
      } else {
         List<Object> source = (List)data;
         List<Object> result = new ArrayList(source.size());
         Iterator var6 = source.iterator();

         while(var6.hasNext()) {
            Object val = var6.next();
            result.add(this.deserialize(val, metadata.value(), (ProxygenSerializer.ElementType)null));
         }

         return result;
      }
   }

   private Object deserializeAsMap(Object data, ProxygenSerializer.ElementType metadata) throws Exception {
      if (metadata == null) {
         logger.debug("Deserializing map without metadata. This may be due to forgotten annotation on a field or parameter or a return value may consist of nested collections. Returning raw map instance as best effort: " + data);
         return data;
      } else {
         boolean deserializeKey = metadata.key() != Void.TYPE;
         Map<Object, Object> source = (Map)data;
         Map<Object, Object> result = new HashMap();
         Iterator var7 = source.keySet().iterator();

         while(var7.hasNext()) {
            Object key = var7.next();
            if (deserializeKey) {
               result.put(this.deserialize(key, metadata.key(), (ProxygenSerializer.ElementType)null), this.deserialize(source.get(key), metadata.value(), (ProxygenSerializer.ElementType)null));
            } else {
               result.put(key, this.deserialize(source.get(key), metadata.value(), (ProxygenSerializer.ElementType)null));
            }
         }

         return result;
      }
   }

   public Object[] deserializeMethodInput(List<Object> data, MultipartFile[] files, Method method) throws Exception {
      List<Object> result = new ArrayList();
      Class[] parameterTypes = method.getParameterTypes();
      ProxygenSerializer.ElementType[] metadata = getElementMetadata(method);
      Queue dataQueue = new LinkedList(data);
      Queue filesQueue = files != null ? new LinkedList(Arrays.asList(files)) : new LinkedList();

      for(int i = 0; i < parameterTypes.length; ++i) {
         Class<?> type = parameterTypes[i];
         ProxygenSerializer.ElementType metadataEntry = metadata[i];
         if (MultipartFile.class.isAssignableFrom(type)) {
            if (filesQueue.isEmpty()) {
               throw new IllegalStateException("Not enough files uploaded");
            }

            result.add(filesQueue.poll());
         } else if (type.isArray() && MultipartFile.class.isAssignableFrom(type.getComponentType())) {
            result.add(filesQueue.toArray(new MultipartFile[filesQueue.size()]));
            filesQueue.clear();
         } else {
            if (dataQueue.isEmpty()) {
               throw new IllegalStateException("Not enough arguments");
            }

            Object dataEntry = dataQueue.poll();
            result.add(this.deserialize(dataEntry, type, metadataEntry));
         }
      }

      if (result.size() != parameterTypes.length) {
         throw new IllegalStateException("Service method parameters count (" + parameterTypes.length + ") do not match provided input length (" + result.size() + ")");
      } else if (!filesQueue.isEmpty()) {
         throw new IllegalStateException("Not all MultipartFiles are handled");
      } else {
         return result.toArray();
      }
   }

   public Object serialize(Object data) throws Exception {
      if (data == null) {
         return data;
      } else {
         HashMap serialized;
         if (data instanceof ManagedObjectReference) {
            ManagedObjectReference mor = (ManagedObjectReference)data;
            serialized = new HashMap();
            serialized.put("type", mor.getType());
            serialized.put("serverGuid", mor.getServerGuid());
            serialized.put("value", mor.getValue());
            return Collections.unmodifiableMap(serialized);
         } else if (data instanceof Enum) {
            return ((Enum)data).name();
         } else if (data instanceof Date) {
            return ((Date)data).getTime();
         } else if (data instanceof Calendar) {
            return ((Calendar)data).getTimeInMillis();
         } else {
            Object[] sourceArray = null;
            int el;
            if (data.getClass().isArray()) {
               Class<?> componentType = data.getClass().getComponentType();
               if (componentType.isPrimitive()) {
                  List<Object> dataList = null;
                  int var7;
                  int var8;
                  if (Boolean.TYPE.isAssignableFrom(componentType)) {
                     boolean[] dataArr = (boolean[])data;
                     dataList = new ArrayList(dataArr.length);
                     boolean[] var43 = dataArr;
                     var8 = dataArr.length;

                     for(var7 = 0; var7 < var8; ++var7) {
                        boolean el = var43[var7];
                        dataList.add(el);
                     }
                  } else if (Byte.TYPE.isAssignableFrom(componentType)) {
                     byte[] dataArr = (byte[])data;
                     dataList = new ArrayList(dataArr.length);
                     byte[] var41 = dataArr;
                     var8 = dataArr.length;

                     for(var7 = 0; var7 < var8; ++var7) {
                        byte el = var41[var7];
                        dataList.add(el);
                     }
                  } else if (Character.TYPE.isAssignableFrom(componentType)) {
                     char[] dataArr = (char[])data;
                     dataList = new ArrayList(dataArr.length);
                     char[] var40 = dataArr;
                     var8 = dataArr.length;

                     for(var7 = 0; var7 < var8; ++var7) {
                        char el = var40[var7];
                        dataList.add(el);
                     }
                  } else {
                     int var36;
                     if (Double.TYPE.isAssignableFrom(componentType)) {
                        double[] dataArr = (double[])data;
                        dataList = new ArrayList(dataArr.length);
                        double[] var39 = dataArr;
                        var36 = dataArr.length;

                        for(var8 = 0; var8 < var36; ++var8) {
                           double el = var39[var8];
                           dataList.add(el);
                        }
                     } else if (Float.TYPE.isAssignableFrom(componentType)) {
                        float[] dataArr = (float[])data;
                        dataList = new ArrayList(dataArr.length);
                        float[] var38 = dataArr;
                        var8 = dataArr.length;

                        for(var7 = 0; var7 < var8; ++var7) {
                           float el = var38[var7];
                           dataList.add(el);
                        }
                     } else if (Integer.TYPE.isAssignableFrom(componentType)) {
                        int[] dataArr = (int[])data;
                        dataList = new ArrayList(dataArr.length);
                        int[] var37 = dataArr;
                        var8 = dataArr.length;

                        for(var7 = 0; var7 < var8; ++var7) {
                           el = var37[var7];
                           dataList.add(el);
                        }
                     } else if (Long.TYPE.isAssignableFrom(componentType)) {
                        long[] dataArr = (long[])data;
                        dataList = new ArrayList(dataArr.length);
                        long[] var10 = dataArr;
                        var36 = dataArr.length;

                        for(var8 = 0; var8 < var36; ++var8) {
                           long el = var10[var8];
                           dataList.add(el);
                        }
                     } else {
                        if (!Short.TYPE.isAssignableFrom(componentType)) {
                           throw new IllegalArgumentException("Unknown primitive type?!?!?");
                        }

                        short[] dataArr = (short[])data;
                        dataList = new ArrayList(dataArr.length);
                        short[] var9 = dataArr;
                        var8 = dataArr.length;

                        for(var7 = 0; var7 < var8; ++var7) {
                           short el = var9[var7];
                           dataList.add(el);
                        }
                     }
                  }

                  sourceArray = dataList.toArray();
               } else {
                  sourceArray = (Object[])data;
               }
            } else if (data instanceof Collection) {
               sourceArray = ((Collection)data).toArray();
            }

            int var30;
            if (sourceArray != null) {
               List<Object> list = new ArrayList();
               Object[] var46 = sourceArray;
               el = sourceArray.length;

               for(var30 = 0; var30 < el; ++var30) {
                  Object o = var46[var30];
                  list.add(this.serialize(o));
               }

               return list;
            } else if (!(data instanceof Map)) {
               if (data.getClass().getAnnotation(data.class) == null && data.getClass().getAnnotation(com.vmware.vim.binding.vmodl.data.class) == null) {
                  return data;
               } else {
                  serialized = new HashMap();
                  Method[] var42;
                  el = (var42 = data.getClass().getMethods()).length;

                  String propertyName;
                  for(var30 = 0; var30 < el; ++var30) {
                     Method method = var42[var30];
                     propertyName = getPropertyName(method);
                     if (propertyName != null) {
                        serialized.put(propertyName, this.serialize(method.invoke(data)));
                     }
                  }

                  Field[] var44;
                  el = (var44 = data.getClass().getFields()).length;

                  for(var30 = 0; var30 < el; ++var30) {
                     Field field = var44[var30];
                     propertyName = getPropertyName(field);
                     if (propertyName != null) {
                        serialized.put(propertyName, this.serialize(field.get(data)));
                     }
                  }

                  return serialized;
               }
            } else {
               Map<Object, Object> sourceMap = (Map)data;
               Map<Object, Object> resultMap = new HashMap();
               Iterator var35 = sourceMap.keySet().iterator();

               while(var35.hasNext()) {
                  Object key = var35.next();
                  resultMap.put(this.serialize(key), this.serialize(sourceMap.get(key)));
               }

               return resultMap;
            }
         }
      }
   }

   private static String getPropertyName(Method method) throws NoSuchMethodException {
      String getterPrefix = null;
      if (method.getName().startsWith("get") && method.getName().length() > "get".length()) {
         getterPrefix = "get";
      } else if (Boolean.class.isAssignableFrom(method.getReturnType()) && method.getName().startsWith("is") && method.getName().length() > "is".length()) {
         getterPrefix = "is";
      }

      if (getterPrefix == null) {
         return null;
      } else if (method.getParameterTypes().length != 0) {
         return null;
      } else {
         return (method.getModifiers() & 8) != 0 ? null : Character.toLowerCase(method.getName().charAt(getterPrefix.length())) + method.getName().substring(getterPrefix.length() + 1);
      }
   }

   private static String getPropertyName(Field field) {
      return (field.getModifiers() & 8) != 0 ? null : field.getName();
   }

   private static ProxygenSerializer.ElementType[] getElementMetadata(Method method) {
      Annotation[][] parameterAnnotations = method.getParameterAnnotations();
      ProxygenSerializer.ElementType[] typedAnnotations = new ProxygenSerializer.ElementType[parameterAnnotations.length];

      for(int i = 0; i < typedAnnotations.length; ++i) {
         Annotation[] var7;
         int var6 = (var7 = parameterAnnotations[i]).length;

         for(int var5 = 0; var5 < var6; ++var5) {
            Annotation a = var7[var5];
            if (a instanceof ProxygenSerializer.ElementType) {
               typedAnnotations[i] = (ProxygenSerializer.ElementType)a;
               break;
            }
         }
      }

      return typedAnnotations;
   }

   @Documented
   @Retention(RetentionPolicy.RUNTIME)
   @Target({java.lang.annotation.ElementType.PARAMETER, java.lang.annotation.ElementType.FIELD})
   public @interface ElementType {
      Class<?> value();

      Class<?> key() default void.class;
   }

   private static class MethodPrefix {
      static final String GET = "get";
      static final String IS = "is";
   }
}
