package com.vmware.vsphere.client.vsan.health;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;

@data
public class VsanTestCell extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public ColumnType cellType;
   public Object cellValue;

   public VsanTestCell() {
   }

   public VsanTestCell(ColumnType cellType, Object cellValue) {
      this.cellType = cellType;
      this.cellValue = cellValue;
   }
}
