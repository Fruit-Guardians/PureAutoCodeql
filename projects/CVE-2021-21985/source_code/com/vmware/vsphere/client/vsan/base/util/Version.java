package com.vmware.vsphere.client.vsan.base.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class Version {
   private static final Log _logger = LogFactory.getLog(Version.class);
   public final int major;
   public final int minor;
   public final int revision;
   public final int buildNumber;

   public Version(String version) {
      if (version != null && version.length() != 0) {
         String[] parts = version.split("\\.");
         if (parts.length > 4) {
            throw new IllegalArgumentException("Invalid version: " + version);
         } else {
            int major = Integer.parseInt(parts[0]);
            if (major < 0) {
               major = 0;
            }

            this.major = major;
            int minorValue = 0;
            int revisionValue = 0;
            int buildNumberValue = 0;
            if (parts.length > 1) {
               minorValue = Integer.parseInt(parts[1]);
               if (minorValue < 0) {
                  minorValue = 0;
               }

               if (parts.length > 2) {
                  revisionValue = Integer.parseInt(parts[2]);
                  if (revisionValue < 0) {
                     revisionValue = 0;
                  }

                  if (parts.length > 3) {
                     buildNumberValue = Integer.parseInt(parts[3]);
                     if (buildNumberValue < 0) {
                        buildNumberValue = 0;
                     }
                  }
               }
            }

            this.minor = minorValue;
            this.revision = revisionValue;
            this.buildNumber = buildNumberValue;
         }
      } else {
         this.major = 0;
         this.minor = 0;
         this.revision = 0;
         this.buildNumber = 0;
      }
   }

   public int compareTo(Version value) {
      if (value == null) {
         return 1;
      } else if (this.major != value.major) {
         return this.major > value.major ? 1 : -1;
      } else if (this.minor != value.minor) {
         return this.minor > value.minor ? 1 : -1;
      } else if (this.revision != value.revision) {
         return this.revision > value.revision ? 1 : -1;
      } else if (this.buildNumber == value.buildNumber) {
         return 0;
      } else {
         return this.buildNumber > value.buildNumber ? 1 : -1;
      }
   }

   public static int compare(Version v1, Version v2) {
      if (v1 == null && v2 == null) {
         return 0;
      } else if (v1 == null) {
         return -1;
      } else {
         return v2 == null ? 1 : v1.compareTo(v2);
      }
   }

   public static boolean isValidVersion(String versionStr) {
      Version version = null;

      try {
         version = new Version(versionStr);
      } catch (Exception var2) {
      }

      return version != null;
   }

   public static boolean isSupportedVersion(String[] supportedVersions, String version) {
      if (supportedVersions == null) {
         return false;
      } else {
         Version v = new Version(version);
         String[] var6 = supportedVersions;
         int var5 = supportedVersions.length;

         for(int var4 = 0; var4 < var5; ++var4) {
            String supportedVersion = var6[var4];
            if (matchMajorMinorVersions(supportedVersion, v)) {
               return true;
            }
         }

         return false;
      }
   }

   private static boolean matchMajorMinorVersions(String supportedVersion, Version v) {
      try {
         Version sv = new Version(supportedVersion);
         return sv.major == v.major && sv.minor == v.minor;
      } catch (Exception var3) {
         _logger.error("Error when comparing versions", var3);
         return false;
      }
   }

   public String toString() {
      return String.format("%s.%s.%s.%s", this.major, this.minor, this.revision, this.buildNumber);
   }
}
