package com.vmware.vsan.client.util;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.vmware.vim.vmomi.core.Future;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Measure implements AutoCloseable {
   private static final Log log = LogFactory.getLog(Measure.class);
   private static final boolean loggingEnabled = true;
   private static final int REPRESENTATION_LENGTH = 180;
   protected final String task;
   protected final long startTime = System.currentTimeMillis();
   protected volatile long endTime = -1L;
   protected final Multimap<String, Measure> subtasks = Multimaps.synchronizedMultimap(ArrayListMultimap.create());
   protected final List<String> taskOrder = Collections.synchronizedList(new ArrayList());

   public Measure(String task) {
      this.task = task;
   }

   public Measure start(String task) {
      if (!this.taskOrder.contains(task)) {
         this.taskOrder.add(task);
      }

      Measure subtask = new Measure(task) {
         public void close() {
            this.markClosed();
         }

         public Measure start(String task) {
            throw new UnsupportedOperationException("Nested tasks unsupported");
         }
      };
      this.subtasks.put(task, subtask);
      return subtask;
   }

   protected void markClosed() {
      if (this.endTime != -1L) {
         throw new IllegalStateException("Measure already closed.");
      } else {
         this.endTime = System.currentTimeMillis();
      }
   }

   protected String format(long parentStartTime, long millisPerSymbol) {
      if (millisPerSymbol <= 0L) {
         return "!";
      } else {
         int offset = (int)((this.startTime - parentStartTime) / millisPerSymbol);
         int length = (int)(((this.endTime != -1L ? this.endTime : System.currentTimeMillis()) - this.startTime) / millisPerSymbol);
         String line = "";

         int regionStart;
         for(regionStart = 0; regionStart < offset && line.length() < 180; ++regionStart) {
            line = line + " ";
         }

         regionStart = line.length();
         line = line + "*";

         int regionEnd;
         for(regionEnd = 1; regionEnd < length && line.length() < 180; ++regionEnd) {
            line = line + "-";
         }

         regionEnd = line.length();
         if (this.endTime != -1L) {
            line = line.substring(0, line.length() - 1) + "*";
         }

         while(line.length() < 180) {
            line = line + " ";
         }

         if (regionEnd - regionStart >= this.task.length() + 4) {
            line = line.substring(0, regionStart) + "*" + " " + this.task + " " + line.substring(regionStart + this.task.length() + 3);
         }

         return line;
      }
   }

   public <T> Future<T> newFuture(String task) {
      return new MeasurableFuture(this, task);
   }

   public long getDuration() {
      return this.endTime == -1L ? -1L : this.endTime - this.startTime;
   }

   public void close() {
      this.markClosed();
      log.info(this.task + " (" + (new DecimalFormat("0.00")).format((double)this.getDuration() / 1000.0D) + " s):\n" + this.toString());
   }

   public String toString() {
      StringBuilder builder = new StringBuilder();
      long duration = (this.endTime != -1L ? this.endTime : System.currentTimeMillis()) - this.startTime;
      long millisPerSymbol = duration / 180L;
      List<String> subtaskIds = this.taskOrder;

      for(int st = 0; st < subtaskIds.size(); ++st) {
         Iterator var9 = this.subtasks.get((String)subtaskIds.get(st)).iterator();

         while(var9.hasNext()) {
            Measure subtask = (Measure)var9.next();
            String durationStr = subtask.getDuration() != -1L ? subtask.getDuration() + "ms" : "ongoing";
            String line = subtask.format(this.startTime, millisPerSymbol);
            builder.append("[" + line + "] ");
            builder.append((String)subtaskIds.get(st));
            builder.append(" (" + durationStr + ")");
            builder.append("\n");
         }
      }

      return builder.toString();
   }
}
