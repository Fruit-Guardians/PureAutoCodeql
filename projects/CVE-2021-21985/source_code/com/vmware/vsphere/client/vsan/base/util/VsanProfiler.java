package com.vmware.vsphere.client.vsan.base.util;

import com.vmware.vsan.client.util.Measure;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class VsanProfiler {
   private static final Log _logger = LogFactory.getLog(VsanProfiler.class);
   private final String _tag;
   private final Map<String, Long> _points;
   private static final long THRESHOLD = 10000L;
   private static final String MESSAGE_TIME = "%s[%s] - %d milliseconds";
   private static final String MESSAGE_NOT_STARTED = "%s[%s] - not started or already stopped";

   public VsanProfiler(String tag) {
      this._points = new ConcurrentHashMap();
      this._tag = tag;
   }

   public VsanProfiler(Class<?> clazz) {
      this(clazz.getSimpleName());
   }

   public VsanProfiler.Point point(String name) {
      return new VsanProfiler.Point(name);
   }

   public static class Point extends Measure {
      public Point(String task) {
         super(task);
      }

      public String toString() {
         return this.task + this.getDuration();
      }
   }
}
