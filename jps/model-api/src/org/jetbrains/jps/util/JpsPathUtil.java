/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.jps.util;

import com.intellij.openapi.util.SystemInfoRt;
import com.intellij.openapi.util.io.FileUtilRt;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.Set;

/**
 * @author nik
 */
public class JpsPathUtil {

  public static boolean isUnder(Set<File> ancestors, File file) {
    if (ancestors.isEmpty()) {
      return false; // optimization
    }
    File current = file;
    while (current != null) {
      if (ancestors.contains(current)) {
        return true;
      }
      current = FileUtilRt.getParentFile(current);
    }
    return false;
  }

  public static File urlToFile(String url) {
    return new File(FileUtilRt.toSystemDependentName(urlToPath(url)));
  }

  public static String urlToPath(String url) {
    if (url == null) return null;
    if (url.startsWith("file://")) {
      return url.substring("file://".length());
    }
    else if (url.startsWith("jar://")) {
      url = url.substring("jar://".length());
      if (url.endsWith("!/")) {
        url = url.substring(0, url.length() - "!/".length());
      }
    }
    return url;
  }

  //todo[nik] copied from VfsUtil
  @NotNull
  public static String fixURLforIDEA(@NotNull String url ) {
    int idx = url.indexOf(":/");
    if( idx >= 0 && idx+2 < url.length() && url.charAt(idx+2) != '/' ) {
      String prefix = url.substring(0, idx);
      String suffix = url.substring(idx+2);

      if (SystemInfoRt.isWindows) {
        url = prefix+"://"+suffix;
      } else {
        url = prefix+":///"+suffix;
      }
    }
    return url;
  }

  public static String pathToUrl(String path) {
    return "file://" + path;
  }

  public static String getLibraryRootUrl(File file) {
    String path = FileUtilRt.toSystemIndependentName(file.getAbsolutePath());
    if (file.isDirectory()) {
      return "file://" + path;
    }
    return "jar://" + path + "!/";
  }
}
