/*
 * Copyright (c) 2012, the Dart project authors.
 * 
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.dart.tools.debug.ui.internal.view;

import com.google.dart.compiler.PackageLibraryManager;
import com.google.dart.tools.core.DartCore;
import com.google.dart.tools.core.internal.model.PackageLibraryManagerProvider;
import com.google.dart.tools.core.internal.util.ResourceUtil;
import com.google.dart.tools.ui.DartUI;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.Path;
import org.eclipse.debug.ui.console.FileLink;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.ui.console.IPatternMatchListener;
import org.eclipse.ui.console.PatternMatchEvent;
import org.eclipse.ui.console.TextConsole;

import java.net.URI;
import java.net.URISyntaxException;

// VM exceptions look like:
//
// Unhandled exception:
// foo
// #1      main (file:///Users/devoncarew/projects/dart/dart/editor/util/debuggertest/cmd_test.dart:30:11)

// Dartium exceptions look like:
//
// Exception: foo
// Stack Trace: #0      List.add (bootstrap_impl:787:7)
// #1      testAnimals (http://127.0.0.1:3030/Users/devoncarew/projects/dart/dart/editor/util/debuggertest/web_test.dart:55:11)
// #2      rotateText.rotateText (http://127.0.0.1:3030/Users/devoncarew/projects/dart/dart/editor/util/debuggertest/web_test.dart:33:14)

/**
 * A console pattern match listener that creates hyperlinks in the console for VM and Dartium
 * exceptions.
 */
public class DebuggerPatternMatchListener implements IPatternMatchListener {

  private static class Location {
    private String filePath;
    private int line;

    public Location(String filePath, int line) {
      this.filePath = filePath;
      this.line = line;
    }

    public boolean doesExist() {
      return getFile() != null;
    }

    public IFile getFile() {
      IResource resource = ResourcesPlugin.getWorkspace().getRoot().findMember(filePath);

      if (resource instanceof IFile) {
        return (IFile) resource;
      }

      IPath path = new Path(filePath);

      return ResourceUtil.getFile(path.toFile());
    }

    public int getLine() {
      return line;
    }

    @Override
    public String toString() {
      return "[" + filePath + ":" + line + "]";
    }
  }

  private TextConsole console;

  public DebuggerPatternMatchListener() {

  }

  @Override
  public void connect(TextConsole console) {
    this.console = console;
  }

  @Override
  public void disconnect() {
    this.console = null;
  }

  @Override
  public int getCompilerFlags() {
    return 0;
  }

  @Override
  public String getLineQualifier() {
    return null;
  }

  @Override
  public String getPattern() {
    // (http://127.0.0.1:3030/Users/util/debuggertest/web_test.dart:33:14)

    return "\\(\\S+\\.dart:\\d+:\\d+\\)";
  }

  @Override
  public void matchFound(PatternMatchEvent event) {
    try {
      if (console != null) {
        String match = console.getDocument().get(event.getOffset(), event.getLength());

        Location location = parseMatch(match);

        if (location != null && location.doesExist()) {
          console.addHyperlink(new FileLink(
              location.getFile(),
              DartUI.ID_CU_EDITOR,
              -1,
              -1,
              location.getLine()), event.getOffset(), event.getLength());
        }
      }
    } catch (BadLocationException e) {
      // don't create a hyperlink

    }
  }

  private IFile getIFileForAbsolutePath(String pathStr) {
    IPath path = new Path(pathStr);

    return ResourceUtil.getFile(path.toFile());
  }

  private Location parseMatch(String match) {
    // (http://127.0.0.1:3030/Users/util/debuggertest/web_test.dart:33:14)

    if (!(match.startsWith("(") && match.endsWith(")"))) {
      return null;
    }

    // remove the parans
    match = match.substring(1, match.length() - 1);

    int index = match.indexOf(".dart:");

    if (index == -1) {
      return null;
    }

    index += ".dart".length();

    String url = match.substring(0, index);

    int lineIndex = match.indexOf(':', index + 1);

    if (lineIndex == -1) {
      return null;
    }

    String lineStr = match.substring(index + 1, lineIndex);

    try {
      String filePath;
      int line = Integer.parseInt(lineStr);

      // /Users/foo/dart/serverapp/serverapp.dart
      // file:///Users/foo/dart/webapp2/webapp2.dart
      // http://0.0.0.0:3030/webapp/webapp.dart
      // package:abc/abc.dart

      // resolve package: urls to file: urls
      if (PackageLibraryManager.isPackageSpec(url)
          && DartCore.getPlugin().getPackageRootPref() != null) {
        url = PackageLibraryManagerProvider.getSystemLibraryManager().resolvePackageUri(url).toString();
      }

      // Handle both fully absolute path names and http: urls.
      if (url.startsWith("/")) {
        IFile file = getIFileForAbsolutePath(url);

        if (file != null) {
          filePath = file.getFullPath().toPortableString();
        } else {
          return null;
        }
      } else if (url.startsWith("file:")) {
        IFile file = getIFileForAbsolutePath(new URI(url).getPath());

        if (file != null) {
          filePath = file.getFullPath().toPortableString();
        } else {
          return null;
        }
      } else {
        filePath = new URI(url).getPath();
      }

      return new Location(filePath, line);
    } catch (NumberFormatException nfe) {
      return null;
    } catch (URISyntaxException e) {
      return null;
    }
  }

}
