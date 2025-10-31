package com.vmware.vsphere.client.vsan.health;

import com.vmware.vim.binding.impl.vmodl.DataObjectImpl;
import com.vmware.vise.core.model.data;
import java.util.List;

@data
public class VsanTestRow extends DataObjectImpl {
   private static final long serialVersionUID = 1L;
   public VsanTestCell[] rowValues;
   public List<VsanTestRow> nestedRows;

   public VsanTestRow() {
   }

   public VsanTestRow(VsanTestCell[] rowValues, List<VsanTestRow> nestedRows) {
      this.rowValues = rowValues;
      this.nestedRows = nestedRows;
   }
}
