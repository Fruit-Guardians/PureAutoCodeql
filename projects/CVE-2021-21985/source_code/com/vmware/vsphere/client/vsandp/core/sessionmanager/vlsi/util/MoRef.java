package com.vmware.vsphere.client.vsandp.core.sessionmanager.vlsi.util;

import com.vmware.vim.binding.vmodl.ManagedObjectReference;
import com.vmware.vim.binding.vmodl.wsdlName;
import com.vmware.vim.binding.vmodl.fault.InvalidType;

public class MoRef extends ManagedObjectReference {
   private static final long serialVersionUID = 1L;

   public MoRef(String type, String moid) {
      this.setType(type);
      this.setValue(moid);
   }

   public MoRef(Class<?> clasz, String moid) {
      this.setType(getVmodlTypeName(clasz));
      this.setValue(moid);
   }

   public static String getVmodlTypeName(Class<?> clasz) {
      wsdlName vmodlTypeName = (wsdlName)clasz.getAnnotation(wsdlName.class);
      if (vmodlTypeName == null) {
         InvalidType fault = new InvalidType();
         fault.setArgument(clasz.getName());
         throw fault;
      } else {
         return vmodlTypeName.value();
      }
   }
}
