package com.rusefi.autoupdate;

import com.rusefi.core.FindFileHelper;
import com.rusefi.core.io.BundleUtil;
import com.rusefi.core.net.ConnectionAndMeta;
import com.rusefi.core.FileUtil;
import com.rusefi.core.preferences.storage.PersistentConfiguration;
import com.rusefi.core.ui.AutoupdateUtil;
import com.rusefi.core.ui.FrameHelper;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.Date;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.rusefi.core.FindFileHelper.findSrecFile;

public class Autoupdate {
    private static final int VERSION = 20240612;

    private static final String LOGO_PATH = "/com/rusefi/";
    private static final String LOGO = LOGO_PATH + "logo.png";
    private static final String TITLE = "rusEFI Bundle Updater " + VERSION;
    private static final String AUTOUPDATE_MODE = "autoupdate";
    private static final String RUSEFI_CONSOLE_JAR = "rusefi_console.jar";
    private static final String COM_RUSEFI_LAUNCHER = "com.rusefi.Launcher";

    public static void main(String[] args) {
        try {
            autoupdate(args);
        } catch (Throwable e) {
            String stackTrace = extracted(e);
            JOptionPane.showMessageDialog(null, stackTrace, "Autoupdate Error " + TITLE, JOptionPane.ERROR_MESSAGE);
        }
    }

    private static String extracted(Throwable e) {
        StringBuilder sb = new StringBuilder(e.toString());
        for (StackTraceElement ste : e.getStackTrace()) {
            sb.append("\n\tat ");
            sb.append(ste);
        }
        return sb.toString();
    }

    private static void autoupdate(String[] args) {
        String bundleFullName = BundleUtil.readBundleFullName();
        if (bundleFullName == null) {
            System.err.println("ERROR: Autoupdate: unable to perform without bundleFullName (parent folder name)");
            System.exit(-1);
        }
        System.out.println("Handling parent folder name [" + bundleFullName + "]");

        BundleUtil.BundleInfo bundleInfo = BundleUtil.parse(bundleFullName);
        String branchName = bundleInfo.getBranchName();

        @NotNull String firstArgument = args.length > 0 ? args[0] : "";

        if (firstArgument.equalsIgnoreCase("basic-ui")) {
            doDownload(bundleInfo, UpdateMode.ALWAYS);
        } else if (args.length > 0 && args[0].equalsIgnoreCase("release")) {
            // this branch needs progress for custom boards!
            System.out.println("Release update requested");
            downloadAndUnzipAutoupdate(
                bundleInfo,
                UpdateMode.ALWAYS,
                ConnectionAndMeta.BASE_URL_RELEASE,
                ConnectionAndMeta.DEFAULT_WHITELABEL_PREFIX
            );
        } else {
            UpdateMode mode = getMode();
            if (mode != UpdateMode.NEVER) {
                doDownload(bundleInfo, mode);
            } else {
                System.out.println("Update mode: NEVER");
            }
        }
        startConsole(args);
    }

    private static void doDownload(BundleUtil.BundleInfo bundleInfo, UpdateMode mode) {
        if (bundleInfo.getBranchName().equals("snapshot")) {
            System.out.println("Snapshot requested");
            downloadAndUnzipAutoupdate(
                bundleInfo,
                mode,
                ConnectionAndMeta.getBaseUrl() + ConnectionAndMeta.AUTOUPDATE,
                ConnectionAndMeta.getWhitelabelPrefix()
            );
        } else {
            downloadAndUnzipAutoupdate(
                bundleInfo,
                mode,
                ConnectionAndMeta.getBaseUrl() + "/lts/" + bundleInfo.getBranchName() + ConnectionAndMeta.AUTOUPDATE,
                ConnectionAndMeta.getWhitelabelPrefix()
            );
        }
    }

    private static void startConsole(String[] args) {
        try {
            // we want to make sure that files are available to write so we use reflection to get lazy class initialization
            System.out.println("Running rusEFI console with " + Arrays.toString(args));
            // since we are overriding file we cannot just use static java classpath while launching
            URLClassLoader jarClassLoader = AutoupdateUtil.getClassLoaderByJar(RUSEFI_CONSOLE_JAR);

            Class mainClass = Class.forName(COM_RUSEFI_LAUNCHER, true, jarClassLoader);
            Method mainMethod = mainClass.getMethod("main", args.getClass());
            mainMethod.invoke(null, new Object[]{args});
        } catch (ClassNotFoundException | IllegalAccessException | InvocationTargetException | NoSuchMethodException |
                 MalformedURLException e) {
            System.out.println(e);
        }
    }

    private static UpdateMode getMode() {
        String value = PersistentConfiguration.getConfig().getRoot().getProperty(AUTOUPDATE_MODE);
        try {
            return UpdateMode.valueOf(value);
        } catch (Throwable e) {
            return UpdateMode.ASK;
        }
    }

    private static void downloadAndUnzipAutoupdate(
        BundleUtil.BundleInfo info,
        UpdateMode mode,
        String baseUrl,
        String whitelabelPrefix
    ) {
        try {
            String suffix = FindFileHelper.isObfuscated() ? "_obfuscated_public" : "";
            String zipFileName = whitelabelPrefix + info.getTarget() + suffix + "_autoupdate" + ".zip";
            ConnectionAndMeta connectionAndMeta = new ConnectionAndMeta(zipFileName).invoke(baseUrl);
            System.out.println("Remote file " + zipFileName);
            System.out.println("Server has " + connectionAndMeta.getCompleteFileSize() + " from " + new Date(connectionAndMeta.getLastModified()));

            if (AutoupdateUtil.hasExistingFile(zipFileName, connectionAndMeta.getCompleteFileSize(), connectionAndMeta.getLastModified())) {
                System.out.println("We already have latest update " + new Date(connectionAndMeta.getLastModified()));
                return;
            }

            if (mode != UpdateMode.ALWAYS) {
                boolean doUpdate = askUserIfUpdateIsDesired();
                if (!doUpdate)
                    return;
            }

            // todo: user could have waited hours to respond to question above, we probably need to re-establish connection
            long completeFileSize = connectionAndMeta.getCompleteFileSize();
            long lastModified = connectionAndMeta.getLastModified();

            System.out.println(info + " " + completeFileSize + " bytes, last modified " + new Date(lastModified));

            AutoupdateUtil.downloadAutoupdateFile(zipFileName, connectionAndMeta, TITLE);

            File file = new File(zipFileName);
            file.setLastModified(lastModified);
            System.out.println("Downloaded " + file.length() + " bytes, lastModified=" + lastModified);

            FileUtil.unzip(zipFileName, new File(".."));
            String srecFile = findSrecFile();
            new File(srecFile == null ? FindFileHelper.FIRMWARE_BIN_FILE : srecFile).setLastModified(lastModified);
        } catch (ReportedIOException e) {
            // we had already reported error with a UI dialog when we had parent frame
            System.err.println("Error downloading bundle: " + e);
        } catch (IOException e) {
            // we are here if error happened while we did not have UI frame
            // todo: open frame prior to network connection and keep frame opened while uncompressing?
            System.err.println("Error downloading bundle: " + e);
            if (!AutoupdateUtil.runHeadless) {
                JOptionPane.showMessageDialog(null, "Error downloading " + e, "Error",
                    JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private static boolean askUserIfUpdateIsDesired() {
        CountDownLatch frameClosed = new CountDownLatch(1);

        if (AutoupdateUtil.runHeadless) {
            // todo: command line ask for options
            return true;
        }

        return askUserIfUpdateIsDesiredWithGUI(frameClosed);
    }

    private static boolean askUserIfUpdateIsDesiredWithGUI(CountDownLatch frameClosed) {
        AtomicBoolean doUpdate = new AtomicBoolean();

        FrameHelper frameHelper = new FrameHelper() {
            @Override
            protected void onWindowClosed() {
                frameClosed.countDown();
            }
        };
        JFrame frame = frameHelper.getFrame();
        frame.setTitle(TITLE);
        ImageIcon icon = AutoupdateUtil.loadIcon(LOGO);
        if (icon != null)
            frame.setIconImage(icon.getImage());
        JPanel choice = new JPanel(new BorderLayout());

        choice.add(new JLabel("Do you want to update bundle to latest version?"), BorderLayout.NORTH);

        JPanel middle = new JPanel(new FlowLayout());

        JButton never = new JButton("Never");
        never.setBackground(Color.red);
        never.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PersistentConfiguration.getConfig().getRoot().setProperty(AUTOUPDATE_MODE, UpdateMode.NEVER.toString());
                frame.dispose();
            }
        });
        middle.add(never);

        JButton no = new JButton("No");
        no.setBackground(Color.red);
        no.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
            }
        });
        middle.add(no);

        JButton once = new JButton("Once");
        once.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                doUpdate.set(true);
                frame.dispose();
            }
        });
        middle.add(once);

        JButton always = new JButton("Always");
        always.setBackground(Color.green);
        always.addActionListener(new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                PersistentConfiguration.getConfig().getRoot().setProperty(AUTOUPDATE_MODE, UpdateMode.ALWAYS.toString());
                doUpdate.set(true);
                frame.dispose();
            }
        });
        middle.add(always);

        choice.add(middle, BorderLayout.CENTER);

        frameHelper.showFrame(choice, true);
        try {
            frameClosed.await();
        } catch (InterruptedException e) {
            // ignore
        }
        return doUpdate.get();
    }

    enum UpdateMode {
        ALWAYS,
        NEVER,
        ASK
    }

}
