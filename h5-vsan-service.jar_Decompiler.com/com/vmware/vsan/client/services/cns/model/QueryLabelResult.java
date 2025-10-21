package com.vmware.vsan.client.services.cns.model;

import com.vmware.vim.vsan.binding.vim.cns.SearchLabelResult;
import com.vmware.vise.core.model.data;
import java.util.List;

@data
public class QueryLabelResult {
   public List<CnsLabel> labels;
   public boolean hasMore;

   public static QueryLabelResult fromVmodl(SearchLabelResult vmodl) {
      if (vmodl == null) {
         return null;
      } else {
         QueryLabelResult result = new QueryLabelResult();
         result.hasMore = vmodl.hasMoreResults;
         result.labels = CnsLabel.fromKeyValue(vmodl.labels);
         return result;
      }
   }
}
