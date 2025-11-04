package com.vmware.vsphere.client.vsan.health;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class VsanTestColumn extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public String columnLabel;
   public ColumnType columnType;

   public VsanTestColumn() {
   }

   public VsanTestColumn(String columnLabel, ColumnType columnType) {
      this.columnLabel = columnLabel;
      this.columnType = columnType;
   }
}
