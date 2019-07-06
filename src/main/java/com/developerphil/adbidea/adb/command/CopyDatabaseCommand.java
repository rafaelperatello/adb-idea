package com.developerphil.adbidea.adb.command;

import com.android.ddmlib.FileListingService;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.SyncService;
import com.developerphil.adbidea.adb.command.receiver.GenericReceiver;
import com.developerphil.adbidea.ui.NotificationHelper;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.android.facet.AndroidFacet;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.developerphil.adbidea.adb.AdbUtil.isAppInstalled;
import static com.developerphil.adbidea.ui.NotificationHelper.error;
import static com.developerphil.adbidea.ui.NotificationHelper.info;

public class CopyDatabaseCommand implements Command, SyncService.ISyncProgressMonitor, FileChooser.FileChooserConsumer {

    private String destination = null;
    private CountDownLatch pullLatch = new CountDownLatch(1);

    @Override
    public boolean run(Project project, IDevice device, AndroidFacet facet, String packageName) {
        try {
            if (isAppInstalled(device, packageName)) {
                device.executeShellCommand("run-as " + packageName + " cp -R databases /sdcard/", new GenericReceiver(), 15L, TimeUnit.SECONDS);
                info(String.format("<b>%s</b> database copied to sdcard", packageName));

                String selectedDestination = getDestination(project);
                if (selectedDestination == null) {
                    return true;
                }

                SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String stamp = format.format(new Date());
                String destination = selectedDestination + "/" + stamp;
                boolean result = new File(destination).mkdir();

                FileListingService.FileEntry[] entry = {getEntry("sdcard/databases", device)};
                device.getSyncService().pull(entry, destination, this);
                boolean flag = pullLatch.await(10, TimeUnit.SECONDS);

                if (!flag) {
                    throw new RuntimeException("Failed to download database");
                }

                NotificationHelper.info("Database copied to: " + destination);
                return true;
            } else {
                error(String.format("<b>%s</b> is not installed on %s", packageName, device.getName()));
            }
        } catch (Exception e1) {
            NotificationHelper.error("Copy database failed with: " + e1.getMessage());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
        }

        return false;
    }

    @Override
    public void start(int i) {

    }

    @Override
    public void stop() {
        pullLatch.countDown();
    }

    @Override
    public boolean isCanceled() {
        return false;
    }

    @Override
    public void startSubTask(String s) {

    }

    @Override
    public void advance(int i) {

    }

    @Override
    public void cancelled() {

    }

    @Override
    public void consume(List<VirtualFile> virtualFiles) {

    }

    private String getDestination(Project project) throws InterruptedException {
        CountDownLatch chooserLatch = new CountDownLatch(1);

        FileChooserDescriptor fileDescriptor = new FileChooserDescriptor(
                false,
                true,
                false,
                false,
                false,
                false);


        ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
                FileChooser.chooseFiles(fileDescriptor, project, project.getWorkspaceFile(), new FileChooser.FileChooserConsumer() {
                    @Override
                    public void cancelled() {
                        chooserLatch.countDown();
                    }

                    @Override
                    public void consume(List<VirtualFile> virtualFiles) {
                        destination = virtualFiles.get(0).getPath();
                        chooserLatch.countDown();
                    }
                });
            }
        });

        chooserLatch.await();
        return destination;
    }

    private FileListingService.FileEntry getEntry(String remotePath, IDevice device) throws Exception {

        FileListingService listingService = device.getFileListingService();

        FileListingService.FileEntry entry = listingService.getRoot();
        String[] segments = remotePath.split("/");

        for (String segment : segments) {
            listingService.getChildren(entry, false, null);
            entry = entry.findChild(segment);
            if (entry == null) {
                throw new Exception("File not found");
            }
        }

        return entry;
    }

}