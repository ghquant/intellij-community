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
package org.jetbrains.idea.svn;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vcs.rollback.RollbackProgressListener;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.idea.svn.ignore.FileGroupInfo;
import org.jetbrains.idea.svn.ignore.SvnPropertyService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.wc.ISVNPropertyValueProvider;
import org.tmatesoft.svn.core.wc.SVNPropertyData;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNWCClient;

import java.io.File;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 12/3/12
 * Time: 1:28 PM
 */
public class SvnRollbackTest extends Svn17TestCase {

  private VcsDirtyScopeManager myDirtyScopeManager;
  private ChangeListManager myChangeListManager;
  private SvnVcs myVcs;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    myDirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    myChangeListManager = ChangeListManager.getInstance(myProject);

    myVcs = SvnVcs.getInstance(myProject);

    enableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    enableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
  }

  @Test
  public void testSimpleRollback() throws Exception {
    final VirtualFile a = createFileInCommand("a.txt", "test");
    checkin();

    editFileInCommand(myProject, a, "tset");
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change change = myChangeListManager.getChange(a);
    Assert.assertNotNull(change);

    rollbackIMpl(Collections.singletonList(change), Collections.<Change>emptyList());
  }

  private void rollbackIMpl(List<Change> changes, final List<Change> allowedAfter) throws VcsException {
    final List<VcsException> exceptions = new ArrayList<VcsException>();
    myVcs.createRollbackEnvironment().rollbackChanges(changes, exceptions, RollbackProgressListener.EMPTY);
    if (! exceptions.isEmpty()) {
      throw exceptions.get(0);
    }

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    List<LocalChangeList> lists = myChangeListManager.getChangeLists();
    final HashSet<Change> afterCopy = new HashSet<Change>(allowedAfter);
    for (LocalChangeList list : lists) {
      final Collection<Change> listChanges = list.getChanges();
      if (! listChanges.isEmpty()) {
        for (Change change : listChanges) {
          final boolean removed = afterCopy.remove(change);
          Assert.assertTrue(removed);
        }
      }
    }
    Assert.assertTrue(afterCopy.isEmpty());
  }

  @Test
  public void testRollbackMoveDir() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    moveFileInCommand(myProject, tree.mySourceDir, tree.myTargetDir);

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change change = assertMovedChange(tree.mySourceDir);
    final Change s1Change = assertMovedChange(tree.myS1File);
    final Change s2Change = assertMovedChange(tree.myS2File);

    rollbackIMpl(Collections.singletonList(change), Collections.<Change>emptyList());
  }

  @Test
  public void testRollbackMOveDirVariant() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile unv = createFileInCommand(tree.mySourceDir, "unv.txt", "***");
    final File wasUnversioned = new File(unv.getPath());

    moveFileInCommand(myProject, tree.mySourceDir, tree.myTargetDir);

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change change = assertMovedChange(tree.mySourceDir);
    final Change s1Change = assertMovedChange(tree.myS1File);
    final Change s2Change = assertMovedChange(tree.myS2File);

    Assert.assertTrue(unv != null);
    Assert.assertTrue(unv.isValid());
    Assert.assertTrue(!FileUtil.filesEqual(new File(unv.getPath()), wasUnversioned));
    Assert.assertTrue(! wasUnversioned.exists());

    rollbackIMpl(Arrays.asList(change, s2Change), Collections.<Change>emptyList());
    Assert.assertTrue(wasUnversioned.exists());
  }

  //IDEA-39943
  @Test
  public void testRollbackWithDeepUnversioned() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    final VirtualFile inner = createDirInCommand(tree.mySourceDir, "inner");
    final VirtualFile innerFile = createFileInCommand(inner, "inInner.txt", "kdfjsdisdjiuewjfew wefn w");
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile deepUnverioned = createFileInCommand(inner, "deepUnverioned.txt", "deepUnverioned");
    final File was = new File(deepUnverioned.getPath());

    checkin();
    verify(runSvn("status"), "? root" + File.separator + "source" + File.separator + "inner" + File.separator + deepUnverioned.getName());

    renameFileInCommand(myProject, tree.mySourceDir, "newName");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change change = assertRenamedChange(tree.mySourceDir);
    final Change s1Change = assertMovedChange(tree.myS1File);
    final Change s2Change = assertMovedChange(tree.myS2File);
    assertMovedChange(inner);
    assertMovedChange(innerFile);

    Assert.assertTrue(!FileUtil.filesEqual(new File(deepUnverioned.getPath()), was));
    Assert.assertTrue(! was.exists());

    rollbackIMpl(Arrays.asList(change), Collections.<Change>emptyList());
    Assert.assertTrue(was.exists());
  }

  @Test
  public void testRollbackDeepEdit() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    final VirtualFile inner = createDirInCommand(tree.mySourceDir, "inner");
    final VirtualFile innerFile = createFileInCommand(inner, "inInner.txt", "kdfjsdisdjiuewjfew wefn w");

    checkin();
    verify(runSvn("status"));

    editFileInCommand(myProject, innerFile, "some content");
    renameFileInCommand(myProject, tree.mySourceDir, "newName");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change change = assertRenamedChange(tree.mySourceDir);
    final Change s1Change = assertMovedChange(tree.myS1File);
    final Change s2Change = assertMovedChange(tree.myS2File);
    assertMovedChange(inner);
    final Change innerChange = assertMovedChange(innerFile);

    rollbackIMpl(Arrays.asList(change),
                 Arrays.asList(new Change(innerChange.getBeforeRevision(), innerChange.getBeforeRevision(), FileStatus.MODIFIED)));
  }

  @Test
  public void testRollbackDirRenameWithDeepRenamesAndUnverioned() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    final VirtualFile inner = createDirInCommand(tree.mySourceDir, "inner");
    final VirtualFile inner1 = createDirInCommand(inner, "inner1");
    final VirtualFile inner2 = createDirInCommand(inner1, "inner2");
    final VirtualFile innerFile_ = createFileInCommand(inner1, "inInner38432.txt", "kdfjsdisdjiuewjfew wefn w");
    final VirtualFile inner3 = createDirInCommand(inner2, "inner3");
    final VirtualFile innerFile = createFileInCommand(inner3, "inInner.txt", "kdfjsdisdjiuewjfew wefn w");
    final VirtualFile innerFile1 = createFileInCommand(inner3, "inInner1.txt", "kdfjsdisdjiuewjfew wefn w");
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile deepUNversioned = createFileInCommand(inner3, "deep.txt", "deep");
    final File wasU = new File(deepUNversioned.getPath());
    final File wasLowestDir = new File(inner3.getPath());
    final File wasInnerFile1 = new File(innerFile1.getPath());
    final File wasInnerFile = new File(innerFile.getPath());

    checkin();
    verify(runSvn("status"));

    editFileInCommand(myProject, innerFile, "some content");
    final File inner2Before = new File(inner2.getPath());
    renameFileInCommand(myProject, inner2, "newName2");
    final File wasU2 = new File(deepUNversioned.getPath());
    final File inner2After = new File(inner2.getPath());
    final File wasInnerFileAfter = new File(innerFile.getPath());
    final File wasInnerFile1After = new File(innerFile1.getPath());
    final File wasLowestDirAfter = new File(inner3.getPath());

    renameFileInCommand(myProject, tree.mySourceDir, "newNameSource");

    Assert.assertTrue(! wasU.exists());
    Assert.assertTrue(! wasU2.exists());

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change change = assertRenamedChange(tree.mySourceDir);
    final Change s1Change = assertMovedChange(tree.myS1File);
    final Change s2Change = assertMovedChange(tree.myS2File);
    final Change inner2Change = assertMovedChange(inner2);
    assertMovedChange(inner);
    final Change innerChange = assertMovedChange(innerFile);

    final Change fantomDelete1 = new Change(new SimpleContentRevision("1", new FilePathImpl(wasLowestDir, true), "2"),
                                            new SimpleContentRevision("1", new FilePathImpl(wasLowestDirAfter, true), "2"));
    final Change fantomDelete2 = new Change(new SimpleContentRevision("1", new FilePathImpl(wasInnerFile1, false), "2"),
                                            new SimpleContentRevision("1", new FilePathImpl(wasInnerFile1After, false), SVNRevision.WORKING.getName()));

    rollbackIMpl(Arrays.asList(change),
                 Arrays.asList(new Change(new SimpleContentRevision("1", new FilePathImpl(wasInnerFile, false), "2"),
                                          new SimpleContentRevision("1", new FilePathImpl(wasInnerFileAfter, false), SVNRevision.WORKING.getName())),
                               new Change(new SimpleContentRevision("1", new FilePathImpl(inner2Before, true), "2"),
                                          new SimpleContentRevision("1", new FilePathImpl(inner2After, true), SVNRevision.WORKING.getName())),
                               fantomDelete1, fantomDelete2));
    Assert.assertTrue(wasU2.exists());
  }

  @Test
  public void testKeepDeepProperty() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    final VirtualFile inner = createDirInCommand(tree.mySourceDir, "inner");
    final VirtualFile innerFile = createFileInCommand(inner, "inInner.txt", "kdfjsdisdjiuewjfew wefn w");

    checkin();
    verify(runSvn("status"));

    final File fileBefore = new File(innerFile.getPath());
    setProperty(fileBefore, "abc", "cde");
    Assert.assertEquals("cde", getProperty(new File(innerFile.getPath()), "abc"));
    final File innerBefore = new File(inner.getPath());
    renameFileInCommand(myProject, inner, "innerNew");
    final File innerAfter = new File(inner.getPath());
    final File fileAfter = new File(innerFile.getPath());
    renameFileInCommand(myProject, tree.mySourceDir, "newName");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change change = assertRenamedChange(tree.mySourceDir);
    final Change s1Change = assertMovedChange(tree.myS1File);
    final Change s2Change = assertMovedChange(tree.myS2File);
    assertMovedChange(inner);
    final Change innerChange = assertMovedChange(innerFile);
    Assert.assertEquals("cde", getProperty(new File(innerFile.getPath()), "abc"));

    rollbackIMpl(Arrays.asList(change),
                 Arrays.asList(new Change(new SimpleContentRevision("1", new FilePathImpl(innerBefore, true), "2"),
                                          new SimpleContentRevision("1", new FilePathImpl(innerAfter, true), SVNRevision.WORKING.getName())),
                               new Change(new SimpleContentRevision("1", new FilePathImpl(fileBefore, false), "2"),
                                          new SimpleContentRevision("1", new FilePathImpl(fileAfter, false), SVNRevision.WORKING.getName()))));
    Assert.assertEquals("cde", getProperty(fileAfter, "abc"));
  }

  private String getProperty(File file, String name) throws SVNException {
    final SVNWCClient client = myVcs.createWCClient();
    final SVNPropertyData data = client.doGetProperty(file, name, SVNRevision.UNDEFINED, SVNRevision.WORKING);
    return data == null ? null : new String(data.getValue().getBytes());
  }

  private void setProperty(final File file, final String name, final String value) throws SVNException {
    final SVNWCClient client = myVcs.createWCClient();
    client.doSetProperty(file, new ISVNPropertyValueProvider() {
      @Override
      public SVNProperties providePropertyValues(File path, SVNProperties properties) throws SVNException {
        final SVNProperties result = new SVNProperties();
        result.put(name, SVNPropertyValue.create(value));
        return result;
      }
    }, true, SVNDepth.EMPTY, null, null);
  }

  @Test
  public void testRollbackDelete() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final FilePath fpSource = new FilePathImpl(new File(tree.mySourceDir.getPath()), true);
    final FilePath fpT11 = new FilePathImpl(new File(tree.myTargetFiles.get(0).getPath()), false);
    deleteFileInCommand(myProject, tree.mySourceDir);
    deleteFileInCommand(myProject, tree.myTargetFiles.get(0));

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change change = assertDeletedChange(fpSource);
    final Change t11Change = assertDeletedChange(fpT11);

    rollbackIMpl(Arrays.asList(change, t11Change), Collections.<Change>emptyList());
  }

  private Change assertDeletedChange(FilePath fpSource) {
    final Change change = myChangeListManager.getChange(fpSource);
    Assert.assertNotNull(change);
    Assert.assertNull(change.getAfterRevision());
    return change;
  }

  @Test
  public void testRollbackAdd() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final VirtualFile newDir = createDirInCommand(tree.mySourceDir, "newDir");
    final VirtualFile inNewDir = createFileInCommand(newDir, "f.txt", "12345");
    final VirtualFile inSource = createFileInCommand(tree.myTargetDir, "newF.txt", "54321");

    Assert.assertTrue(newDir != null && inNewDir != null && inSource != null);
    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change change = assertCreatedChange(newDir);
    final Change inNewDirChange = assertCreatedChange(inNewDir);
    final Change inSourceChange = assertCreatedChange(inSource);

    rollbackIMpl(Arrays.asList(change, inSourceChange), Collections.<Change>emptyList());
  }

  private Change assertCreatedChange(VirtualFile newDir) {
    final Change change = myChangeListManager.getChange(newDir);
    Assert.assertNotNull(change);
    Assert.assertNull(change.getBeforeRevision());
    return change;
  }

  // move directory with unversioned dir + check edit
  @Test
  public void testRollbackRenameDirWithUnversionedDir() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final String editedText = "s1 edited";
    editFileInCommand(myProject, tree.myS1File, editedText);
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    final VirtualFile unverionedDir = createDirInCommand(tree.mySourceDir, "unverionedDir");
    final String unvText = "unv content";
    final VirtualFile unvFile = createFileInCommand(unverionedDir, "childFile", unvText);
    final File wasUnvDir = new File(unverionedDir.getPath());
    final File wasUnvFile = new File(unvFile.getPath());

    renameFileInCommand(myProject, tree.mySourceDir, "renamed");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change dirChange = assertRenamedChange(tree.mySourceDir);
    final Change s1Change = assertMovedChange(tree.myS1File);

    FileStatus status = myChangeListManager.getStatus(unverionedDir);
    Assert.assertNotNull(FileStatus.UNKNOWN.equals(status));
    Assert.assertTrue(! wasUnvDir.exists());

    FileStatus fileStatus = myChangeListManager.getStatus(unvFile);
    Assert.assertNotNull(FileStatus.UNKNOWN.equals(fileStatus));
    Assert.assertTrue(! wasUnvFile.exists());

    rollbackIMpl(Collections.singletonList(dirChange), Collections.singletonList(new Change(s1Change.getBeforeRevision(),
                                                                                            s1Change.getBeforeRevision(), FileStatus.MODIFIED)));
    Assert.assertTrue(wasUnvDir.exists());
    Assert.assertTrue(wasUnvFile.exists());
  }

  @Test
  public void testRollbackDirWithIgnored() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    VirtualFile ignored = createFileInCommand(tree.mySourceDir, "ign.txt", "ignored");
    final File wasIgnored = new File(ignored.getPath());
    final FileGroupInfo groupInfo = new FileGroupInfo();
    groupInfo.onFileEnabled(ignored);
    SvnPropertyService.doAddToIgnoreProperty(myVcs, myProject, false, new VirtualFile[]{ignored}, groupInfo);

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    Assert.assertTrue(FileStatus.IGNORED.equals(myChangeListManager.getStatus(ignored)));

    renameFileInCommand(myProject, tree.mySourceDir, "renamed");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change dirChange = assertRenamedChange(tree.mySourceDir);
    final Change s1Change = assertMovedChange(tree.myS1File);
    final Change s2Change = assertMovedChange(tree.myS2File);
    Assert.assertTrue(! wasIgnored.exists());
    Assert.assertTrue(FileStatus.IGNORED.equals(myChangeListManager.getStatus(ignored)));

    rollbackIMpl(Collections.singletonList(dirChange), Collections.<Change>emptyList());
    ignored = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(wasIgnored);
    // ignored property was not committed
    Assert.assertTrue(FileStatus.UNKNOWN.equals(myChangeListManager.getStatus(ignored)));
    Assert.assertTrue(wasIgnored.exists());
  }

  @Test
  public void testRollbackDirWithCommittedIgnored() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.ADD);
    VirtualFile ignored = createFileInCommand(tree.mySourceDir, "ign.txt", "ignored");
    final File wasIgnored = new File(ignored.getPath());
    final FileGroupInfo groupInfo = new FileGroupInfo();
    groupInfo.onFileEnabled(ignored);
    SvnPropertyService.doAddToIgnoreProperty(myVcs, myProject, false, new VirtualFile[]{ignored}, groupInfo);
    checkin();

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);
    Assert.assertTrue(FileStatus.IGNORED.equals(myChangeListManager.getStatus(ignored)));

    renameFileInCommand(myProject, tree.mySourceDir, "renamed");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change dirChange = assertRenamedChange(tree.mySourceDir);
    final Change s1Change = assertMovedChange(tree.myS1File);
    final Change s2Change = assertMovedChange(tree.myS2File);
    Assert.assertTrue(! wasIgnored.exists());
    Assert.assertTrue(FileStatus.IGNORED.equals(myChangeListManager.getStatus(ignored)));

    rollbackIMpl(Collections.singletonList(dirChange), Collections.<Change>emptyList());
    ignored = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(wasIgnored);
    // ignored property was not committed
    Assert.assertTrue(FileStatus.IGNORED.equals(myChangeListManager.getStatus(ignored)));
    Assert.assertTrue(wasIgnored.exists());
  }

  @Test
  public void testListAllChangesForRevert() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();

    final String editedText = "s1 edited";
    editFileInCommand(myProject, tree.myS1File, editedText);

    renameFileInCommand(myProject, tree.mySourceDir, "renamed");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change dirChange = assertRenamedChange(tree.mySourceDir);
    final Change s1Change = assertMovedChange(tree.myS1File);
    final Change s2Change = assertMovedChange(tree.myS2File);

    rollbackIMpl(Arrays.asList(dirChange, s1Change, s2Change), Collections.<Change>emptyList());
  }

  @Test
  public void testKeepOneUnderRenamed() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    final File was2 = new File(tree.myS2File.getPath());

    final String editedText = "s1 edited";
    editFileInCommand(myProject, tree.myS1File, editedText);
    editFileInCommand(myProject, tree.myS2File, "s2 edited");

    renameFileInCommand(myProject, tree.mySourceDir, "renamed");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change dirChange = assertRenamedChange(tree.mySourceDir);
    final Change s1Change = assertMovedChange(tree.myS1File);
    final Change s2Change = assertMovedChange(tree.myS2File);

    final FilePathImpl fp = new FilePathImpl(was2, false);
    rollbackIMpl(Arrays.asList(dirChange, s1Change), Arrays.asList(new Change(
      new SimpleContentRevision("1", fp, "1"), new SimpleContentRevision("1", fp, SVNRevision.WORKING.getName()))));
  }

  @Test
  public void testRollbackLocallyDeletedSimple() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final File wasFile = new File(tree.myS1File.getPath());
    deleteFileInCommand(myProject, tree.myS1File);

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final List<LocallyDeletedChange> deletedFiles = ((ChangeListManagerImpl)myChangeListManager).getDeletedFiles();
    Assert.assertNotNull(deletedFiles);
    Assert.assertTrue(deletedFiles.size() == 1);
    Assert.assertEquals(wasFile, deletedFiles.get(0).getPath().getIOFile());

    rollbackLocallyDeleted(Collections.singletonList(deletedFiles.get(0).getPath()), Collections.<FilePath>emptyList());
  }

  @Test
  public void testRollbackLocallyDeletedSimpleDir() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final File wasFile = new File(tree.mySourceDir.getPath());
    final File wasFileS1 = new File(tree.myS1File.getPath());
    final File wasFileS2 = new File(tree.myS2File.getPath());
    deleteFileInCommand(myProject, tree.mySourceDir);

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final List<LocallyDeletedChange> deletedFiles = ((ChangeListManagerImpl)myChangeListManager).getDeletedFiles();
    Assert.assertNotNull(deletedFiles);
    Assert.assertTrue(deletedFiles.size() == 3);

    final Set<File> files = new HashSet<File>();
    files.add(wasFile);
    files.add(wasFileS1);
    files.add(wasFileS2);

    for (LocallyDeletedChange file : deletedFiles) {
      files.remove(file.getPath().getIOFile());
    }
    Assert.assertTrue(files.isEmpty());

    rollbackLocallyDeleted(Collections.<FilePath>singletonList(new FilePathImpl(wasFile, true)), Collections.<FilePath>emptyList());
  }

  @Test
  public void testRollbackAddedLocallyDeleted() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    VirtualFile f1 = createFileInCommand(tree.mySourceDir, "f1", "4");
    VirtualFile dir = createDirInCommand(tree.mySourceDir, "dirrr");
    VirtualFile f2 = createFileInCommand(dir, "f2", "411");

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    assertCreatedChange(f1);
    assertCreatedChange(dir);
    assertCreatedChange(f2);

    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    final File wasFile1 = new File(f1.getPath());
    final File wasFile2 = new File(dir.getPath());
    final File wasFile3 = new File(f2.getPath());

    deleteFileInCommand(myProject, f1);
    deleteFileInCommand(myProject, dir);

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final List<LocallyDeletedChange> deletedFiles = ((ChangeListManagerImpl)myChangeListManager).getDeletedFiles();
    Assert.assertNotNull(deletedFiles);
    Assert.assertTrue(deletedFiles.size() == 3);
    final Set<File> files = new HashSet<File>();
    files.add(wasFile1);
    files.add(wasFile2);
    files.add(wasFile3);

    Assert.assertTrue(files.contains(deletedFiles.get(0).getPath().getIOFile()));
    Assert.assertTrue(files.contains(deletedFiles.get(1).getPath().getIOFile()));
    Assert.assertTrue(files.contains(deletedFiles.get(2).getPath().getIOFile()));

    rollbackLocallyDeleted(Arrays.<FilePath>asList(new FilePathImpl(wasFile2, true), new FilePathImpl(wasFile1, false)), Collections.<FilePath>emptyList());
  }

  @Test
  public void testRollbackMovedDirectoryLocallyDeleted() throws Exception {
    final SubTree tree = new SubTree(myWorkingCopyDir);
    checkin();
    final File wasInitially = new File(tree.mySourceDir.getPath());
    Assert.assertTrue(wasInitially.exists());

    moveFileInCommand(myProject, tree.mySourceDir, tree.myTargetDir);
    Assert.assertTrue(!wasInitially.exists());

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final Change movedChange = assertMovedChange(tree.mySourceDir);
    final File was = new File(tree.mySourceDir.getPath());
    Assert.assertNotSame(wasInitially, was);
    disableSilentOperation(VcsConfiguration.StandardConfirmation.REMOVE);
    deleteFileInCommand(myProject, tree.mySourceDir);

    verify(runSvn("status"), "D root" + File.separator + "source",
           "D root" + File.separator + "source" + File.separator + "s1.txt",
           "D root" + File.separator + "source" + File.separator + "s2.txt",
           "! root" + File.separator + "target" + File.separator + "source",
           "! root" + File.separator + "target" + File.separator + "source" + File.separator + "s1.txt",
           "! root" + File.separator + "target" + File.separator + "source" + File.separator + "s2.txt");

    rollbackLocallyDeleted(Collections.<FilePath>singletonList(new FilePathImpl(was, true)), Collections.<FilePath>emptyList());
    verify(runSvn("status"), "D root" + File.separator + "source",
               "D root" + File.separator + "source" + File.separator + "s1.txt",
               "D root" + File.separator + "source" + File.separator + "s2.txt");
  }

  private void rollbackLocallyDeleted(final List<FilePath> locally, final List<FilePath> allowed) {
    final List<VcsException> exceptions = new ArrayList<VcsException>();
    myVcs.createRollbackEnvironment().rollbackMissingFileDeletion(locally, exceptions, RollbackProgressListener.EMPTY);
    Assert.assertTrue(exceptions.isEmpty());

    myDirtyScopeManager.markEverythingDirty();
    myChangeListManager.ensureUpToDate(false);

    final List<LocallyDeletedChange> deletedFiles = ((ChangeListManagerImpl)myChangeListManager).getDeletedFiles();
    if (allowed == null || allowed.isEmpty()) {
      Assert.assertTrue(deletedFiles == null || deletedFiles.isEmpty());
    }
    final ArrayList<FilePath> copy = new ArrayList<FilePath>(allowed);
    for (LocallyDeletedChange file : deletedFiles) {
      copy.remove(file.getPath());
    }
    Assert.assertTrue(copy.isEmpty());
  }

  private Change assertMovedChange(final VirtualFile file) {
    final Change change = myChangeListManager.getChange(file);
    Assert.assertNotNull(change);
    Assert.assertTrue(change.isMoved());
    return change;
  }

  private Change assertRenamedChange(final VirtualFile file) {
    final Change change = myChangeListManager.getChange(file);
    Assert.assertNotNull(change);
    Assert.assertTrue(change.isRenamed());
    return change;
  }
}
