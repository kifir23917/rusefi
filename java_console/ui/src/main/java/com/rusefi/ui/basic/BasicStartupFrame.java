package com.rusefi.ui.basic;

import com.rusefi.Launcher;
import com.rusefi.SerialPortScanner;
import com.rusefi.StartupFrame;
import com.rusefi.autodetect.PortDetector;
import com.rusefi.core.FindFileHelper;
import com.rusefi.core.net.ConnectionAndMeta;
import com.rusefi.core.ui.FrameHelper;
import com.rusefi.maintenance.DfuFlasher;
import com.rusefi.maintenance.ProgramSelector;
import com.rusefi.maintenance.StatusAnimation;
import com.rusefi.maintenance.UpdateStatusWindow;
import com.rusefi.ui.LogoHelper;
import com.rusefi.ui.util.DefaultExceptionHandler;
import com.rusefi.ui.util.HorizontalLine;
import com.rusefi.ui.util.UiUtils;
import com.rusefi.ui.widgets.ToolButtons;
import org.putgemin.VerticalFlowLayout;

import javax.swing.*;

import java.awt.Color;
import java.util.List;
import java.util.stream.Collectors;

import static com.rusefi.FileLog.isWindows;

/**
 * Focuses on firmware updater
 * Much simpler than {@link com.rusefi.StartupFrame}
 */
public class BasicStartupFrame {
    private final FrameHelper frame;

    private final JLabel noPortsMessage = new JLabel();
    private final StatusAnimation status = new StatusAnimation(noPortsMessage::setText, StartupFrame.SCANNING_PORTS);
    private final JButton update = ProgramSelector.createUpdateFirmwareButton();

    public static void main(String[] args) {
        runTool(null);
    }

    public static void runTool(String[] args) {
        DefaultExceptionHandler.install();
        new BasicStartupFrame().runTool();
    }

    public BasicStartupFrame() {
        String whiteLabel = ConnectionAndMeta.getWhiteLabel(ConnectionAndMeta.getProperties());
        String title = whiteLabel + " basic console " + Launcher.CONSOLE_VERSION;
        frame = FrameHelper.createFrame(title);
        JPanel panel = new JPanel(new VerticalFlowLayout());
        if (isWindows()) {
            panel.add(ToolButtons.createShowDeviceManagerButton());
            panel.add(StartupFrame.binaryModificationControl());

            boolean requireBlt = FindFileHelper.isObfuscated();
            if (requireBlt) {
                update.setEnabled(false);

                noPortsMessage.setForeground(Color.red);
                panel.add(noPortsMessage);

                SerialPortScanner.INSTANCE.addListener(currentHardware -> SwingUtilities.invokeLater(() -> {
                    onHardwareUpdated(currentHardware);
                }));
            } else {
                update.addActionListener(e -> DfuFlasher.doAutoDfu(update, PortDetector.AUTO, new UpdateStatusWindow("Update")));
            }
            panel.add(update);
        } else {
            panel.add(new JLabel("Sorry only works on Windows"));
        }

        panel.add(new HorizontalLine());
        JLabel logoLabel = LogoHelper.createLogoLabel();
        if (logoLabel != null)
            panel.add(logoLabel);
        if (ConnectionAndMeta.isDefaultWhitelabel(whiteLabel))
            panel.add(LogoHelper.createUrlLabel());

        frame.showFrame(panel, false);
        UiUtils.centerWindow(frame.getFrame());
    }

    private void onHardwareUpdated(final SerialPortScanner.AvailableHardware currentHardware) {
        status.stop();
        frame.getFrame().pack();

        List<SerialPortScanner.PortResult> ecuPorts =  currentHardware.getKnownPorts().stream().filter(portResult -> portResult.type == SerialPortScanner.SerialPortType.EcuWithOpenblt).collect(Collectors.toList());

        List<SerialPortScanner.PortResult> bootloaderPorts =  currentHardware.getKnownPorts().stream().filter(portResult -> portResult.type == SerialPortScanner.SerialPortType.OpenBlt).collect(Collectors.toList());


        if (!ecuPorts.isEmpty()) {
            noPortsMessage.setVisible(false);
            update.setEnabled(true);
            update.setText("Auto Update Firmware");
            update.addActionListener(e -> ProgramSelector.executeJob(update, ProgramSelector.OPENBLT_AUTO, ecuPorts.get(0)));
        } else if (!bootloaderPorts.isEmpty()) {
            noPortsMessage.setVisible(false);
            update.setEnabled(true);
            update.setText("Blt Update Firmware");
            update.addActionListener(e -> ProgramSelector.executeJob(update, ProgramSelector.OPENBLT_MANUAL, bootloaderPorts.get(0)));
        } else {
            noPortsMessage.setText("ECU not found");
        }
    }

    private void runTool() {
    }
}
