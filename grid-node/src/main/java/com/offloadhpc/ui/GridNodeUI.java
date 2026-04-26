package com.offloadhpc.ui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Swing-based monitoring UI for the OFFLOAD-HPC GridNode.
 * Shows node status, connected workers, mobile clients, recent jobs,
 * worker sub-tasks (for worker role), and event log.
 *
 * v2.1 -- added mobile clients panel, worker job tracking,
 * and better role-dependent visibility.
 */
public class GridNodeUI extends JFrame implements GridNodeEventListener {

    // -- Theme colors --
    private static final Color BG_DARK = new Color(30, 30, 36);
    private static final Color BG_PANEL = new Color(40, 42, 54);
    private static final Color BG_TABLE = new Color(44, 46, 58);
    private static final Color TEXT_PRIMARY = new Color(230, 230, 240);
    private static final Color TEXT_MUTED = new Color(150, 155, 170);
    private static final Color ACCENT_BROKER = new Color(80, 250, 123);
    private static final Color ACCENT_WORKER = new Color(139, 233, 253);
    private static final Color ACCENT_WARN = new Color(255, 121, 98);
    private static final Color ACCENT_MOBILE = new Color(255, 184, 108);
    private static final Color BORDER_COLOR = new Color(60, 63, 80);

    // -- Header --
    private JLabel lblNodeId;
    private JLabel lblRole;
    private JLabel lblLocalIp;
    private JLabel lblPriority;
    private JLabel lblTcpPort;
    private JLabel lblRmiPort;
    private JLabel lblBrokerInfo;

    // -- Worker table (broker view) --
    private DefaultTableModel workerTableModel;
    private JTable workerTable;
    private JLabel lblWorkerCount;
    private JPanel workerPanel;

    // -- Mobile clients table (broker view) --
    private DefaultTableModel mobileTableModel;
    private JTable mobileTable;
    private JLabel lblMobileCount;
    private JPanel mobilePanel;

    // -- Job table (broker view: received jobs) --
    private DefaultTableModel jobTableModel;
    private JTable jobTable;
    private JLabel lblJobCount;
    private int jobCounter = 0;
    private JPanel jobPanel;

    // -- Worker sub-task table (worker view: assigned sub-tasks) --
    private DefaultTableModel subTaskTableModel;
    private JTable subTaskTable;
    private JLabel lblSubTaskCount;
    private int subTaskCounter = 0;
    private JPanel subTaskPanel;

    // -- Event log --
    private JTextArea taEventLog;
    private final SimpleDateFormat timeFmt = new SimpleDateFormat("HH:mm:ss");

    // -- Controls --
    private Runnable onForceElection;
    private Runnable onStopNode;

    public GridNodeUI(String nodeId, int priority, int tcpPort, int rmiPort, String localIp) {
        super("OFFLOAD-HPC Grid Node Monitor");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int res = JOptionPane.showConfirmDialog(GridNodeUI.this,
                        "Stop this grid node and exit?", "Confirm Exit",
                        JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                if (res == JOptionPane.YES_OPTION) {
                    if (onStopNode != null) onStopNode.run();
                    dispose();
                    System.exit(0);
                }
            }
        });

        setSize(720, 850);
        setLocationRelativeTo(null);
        getContentPane().setBackground(BG_DARK);
        setLayout(new BorderLayout(0, 0));

        JPanel mainPanel = new JPanel();
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBackground(BG_DARK);
        mainPanel.setBorder(new EmptyBorder(12, 12, 12, 12));

        mainPanel.add(buildHeaderPanel(nodeId, priority, tcpPort, rmiPort, localIp));
        mainPanel.add(Box.createVerticalStrut(8));

        workerPanel = buildWorkerPanel();
        mainPanel.add(workerPanel);
        mainPanel.add(Box.createVerticalStrut(8));

        mobilePanel = buildMobilePanel();
        mainPanel.add(mobilePanel);
        mainPanel.add(Box.createVerticalStrut(8));

        jobPanel = buildJobPanel();
        mainPanel.add(jobPanel);
        mainPanel.add(Box.createVerticalStrut(8));

        subTaskPanel = buildSubTaskPanel();
        mainPanel.add(subTaskPanel);
        mainPanel.add(Box.createVerticalStrut(8));

        mainPanel.add(buildLogPanel());
        mainPanel.add(Box.createVerticalStrut(8));
        mainPanel.add(buildControlPanel());

        JScrollPane scrollPane = new JScrollPane(mainPanel);
        scrollPane.setBorder(null);
        scrollPane.getViewport().setBackground(BG_DARK);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        add(scrollPane, BorderLayout.CENTER);

        // Initially hide role-specific panels
        mobilePanel.setVisible(false);
        subTaskPanel.setVisible(false);
    }

    // -- Panel builders --

    private JPanel buildHeaderPanel(String nodeId, int priority, int tcpPort, int rmiPort, String localIp) {
        JPanel panel = createStyledPanel("Node Information");

        JPanel grid = new JPanel(new GridLayout(4, 2, 10, 6));
        grid.setBackground(BG_PANEL);

        lblNodeId = createValueLabel(nodeId);
        lblRole = createValueLabel("INITIALIZING...");
        lblRole.setForeground(TEXT_MUTED);
        lblLocalIp = createValueLabel(localIp);
        lblPriority = createValueLabel(String.valueOf(priority));
        lblTcpPort = createValueLabel(String.valueOf(tcpPort));
        lblRmiPort = createValueLabel(String.valueOf(rmiPort));
        lblBrokerInfo = createValueLabel("--");

        grid.add(createKeyLabel("Node ID:"));  grid.add(lblNodeId);
        grid.add(createKeyLabel("Role:"));     grid.add(lblRole);
        grid.add(createKeyLabel("Local IP:")); grid.add(lblLocalIp);
        grid.add(createKeyLabel("Priority:")); grid.add(lblPriority);

        JPanel portsRow = new JPanel(new GridLayout(1, 4, 10, 0));
        portsRow.setBackground(BG_PANEL);
        portsRow.add(createKeyLabel("TCP Port:"));  portsRow.add(lblTcpPort);
        portsRow.add(createKeyLabel("RMI Port:"));  portsRow.add(lblRmiPort);

        JPanel brokerRow = new JPanel(new GridLayout(1, 2, 10, 0));
        brokerRow.setBackground(BG_PANEL);
        brokerRow.add(createKeyLabel("Broker:"));  brokerRow.add(lblBrokerInfo);

        JPanel wrapper = new JPanel();
        wrapper.setLayout(new BoxLayout(wrapper, BoxLayout.Y_AXIS));
        wrapper.setBackground(BG_PANEL);
        wrapper.add(grid);
        wrapper.add(Box.createVerticalStrut(4));
        wrapper.add(portsRow);
        wrapper.add(Box.createVerticalStrut(4));
        wrapper.add(brokerRow);

        panel.add(wrapper, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildWorkerPanel() {
        JPanel panel = createStyledPanel("Connected Workers");

        lblWorkerCount = createKeyLabel("Workers: 0");
        lblWorkerCount.setForeground(ACCENT_WORKER);
        panel.add(lblWorkerCount, BorderLayout.NORTH);

        String[] cols = {"Worker ID", "IP Address", "CPU Cores", "Memory (MB)", "Status"};
        workerTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        workerTable = createStyledTable(workerTableModel);
        JScrollPane sp = new JScrollPane(workerTable);
        sp.setPreferredSize(new Dimension(0, 100));
        sp.getViewport().setBackground(BG_TABLE);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        panel.add(sp, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildMobilePanel() {
        JPanel panel = createStyledPanel("Connected Mobile Devices");

        lblMobileCount = createKeyLabel("Devices: 0");
        lblMobileCount.setForeground(ACCENT_MOBILE);
        panel.add(lblMobileCount, BorderLayout.NORTH);

        String[] cols = {"IP Address", "Connected At", "Status"};
        mobileTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        mobileTable = createStyledTable(mobileTableModel);
        JScrollPane sp = new JScrollPane(mobileTable);
        sp.setPreferredSize(new Dimension(0, 70));
        sp.getViewport().setBackground(BG_TABLE);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        panel.add(sp, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildJobPanel() {
        JPanel panel = createStyledPanel("Jobs (Broker View)");

        lblJobCount = createKeyLabel("Jobs: 0");
        lblJobCount.setForeground(ACCENT_WORKER);
        panel.add(lblJobCount, BorderLayout.NORTH);

        String[] cols = {"#", "Job ID", "Type", "Status", "Time"};
        jobTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        jobTable = createStyledTable(jobTableModel);
        jobTable.getColumnModel().getColumn(0).setMaxWidth(40);
        JScrollPane sp = new JScrollPane(jobTable);
        sp.setPreferredSize(new Dimension(0, 100));
        sp.getViewport().setBackground(BG_TABLE);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        panel.add(sp, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildSubTaskPanel() {
        JPanel panel = createStyledPanel("Sub-Tasks Processed (Worker View)");

        lblSubTaskCount = createKeyLabel("Sub-tasks: 0");
        lblSubTaskCount.setForeground(ACCENT_WORKER);
        panel.add(lblSubTaskCount, BorderLayout.NORTH);

        String[] cols = {"#", "Sub-Task ID", "Type", "Duration (ms)", "Time"};
        subTaskTableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };
        subTaskTable = createStyledTable(subTaskTableModel);
        subTaskTable.getColumnModel().getColumn(0).setMaxWidth(40);
        JScrollPane sp = new JScrollPane(subTaskTable);
        sp.setPreferredSize(new Dimension(0, 100));
        sp.getViewport().setBackground(BG_TABLE);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        panel.add(sp, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildLogPanel() {
        JPanel panel = createStyledPanel("Event Log");

        taEventLog = new JTextArea(6, 60);
        taEventLog.setEditable(false);
        taEventLog.setFont(new Font("Consolas", Font.PLAIN, 12));
        taEventLog.setBackground(BG_TABLE);
        taEventLog.setForeground(TEXT_PRIMARY);
        taEventLog.setCaretColor(TEXT_PRIMARY);
        taEventLog.setBorder(new EmptyBorder(6, 6, 6, 6));

        JScrollPane sp = new JScrollPane(taEventLog);
        sp.setBorder(BorderFactory.createLineBorder(BORDER_COLOR));
        panel.add(sp, BorderLayout.CENTER);

        return panel;
    }

    private JPanel buildControlPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 16, 8));
        panel.setBackground(BG_DARK);

        JButton btnElection = createStyledButton(">> Trigger Re-Election", ACCENT_WARN);
        btnElection.addActionListener(e -> {
            if (onForceElection != null) {
                appendLog("Manual re-election triggered by user");
                onForceElection.run();
            }
        });

        JButton btnStop = createStyledButton("[X] Stop Node", new Color(255, 85, 85));
        btnStop.addActionListener(e -> {
            int res = JOptionPane.showConfirmDialog(this,
                    "Stop this grid node?", "Confirm Stop",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (res == JOptionPane.YES_OPTION) {
                if (onStopNode != null) onStopNode.run();
                dispose();
                System.exit(0);
            }
        });

        panel.add(btnElection);
        panel.add(btnStop);
        return panel;
    }

    // -- Styling helpers --

    private JPanel createStyledPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout(8, 8));
        panel.setBackground(BG_PANEL);
        TitledBorder tb = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(BORDER_COLOR), " " + title + " ");
        tb.setTitleColor(TEXT_PRIMARY);
        tb.setTitleFont(new Font("Segoe UI", Font.BOLD, 13));
        panel.setBorder(BorderFactory.createCompoundBorder(
                tb, new EmptyBorder(8, 10, 8, 10)));
        return panel;
    }

    private JLabel createKeyLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(TEXT_MUTED);
        lbl.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        return lbl;
    }

    private JLabel createValueLabel(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setForeground(TEXT_PRIMARY);
        lbl.setFont(new Font("Segoe UI Semibold", Font.BOLD, 13));
        return lbl;
    }

    private JTable createStyledTable(DefaultTableModel model) {
        JTable table = new JTable(model);
        table.setBackground(BG_TABLE);
        table.setForeground(TEXT_PRIMARY);
        table.setSelectionBackground(new Color(68, 71, 90));
        table.setSelectionForeground(TEXT_PRIMARY);
        table.setGridColor(BORDER_COLOR);
        table.setRowHeight(24);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.getTableHeader().setBackground(BG_PANEL);
        table.getTableHeader().setForeground(TEXT_MUTED);
        table.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 12));
        table.setFillsViewportHeight(true);

        DefaultTableCellRenderer cellRenderer = new DefaultTableCellRenderer();
        cellRenderer.setBackground(BG_TABLE);
        cellRenderer.setForeground(TEXT_PRIMARY);
        for (int i = 0; i < model.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setCellRenderer(cellRenderer);
        }

        return table;
    }

    private JButton createStyledButton(String text, Color accent) {
        JButton btn = new JButton(text);
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setBackground(accent.darker());
        btn.setOpaque(true);
        btn.setContentAreaFilled(true);
        btn.setFocusPainted(false);
        btn.setBorderPainted(true);
        btn.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(accent, 1),
                new EmptyBorder(8, 20, 8, 20)));
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return btn;
    }

    // -- Public setters for control callbacks --

    public void setOnForceElection(Runnable callback) {
        this.onForceElection = callback;
    }

    public void setOnStopNode(Runnable callback) {
        this.onStopNode = callback;
    }

    // -- GridNodeEventListener implementation --

    @Override
    public void onRoleChanged(String role, String brokerId, String brokerIp, int brokerTcpPort) {
        SwingUtilities.invokeLater(() -> {
            if ("BROKER".equals(role)) {
                lblRole.setText("* BROKER *");
                lblRole.setForeground(ACCENT_BROKER);
                lblBrokerInfo.setText("This node (self)");
                lblBrokerInfo.setForeground(ACCENT_BROKER);
                // Show broker-specific panels
                workerPanel.setVisible(true);
                mobilePanel.setVisible(true);
                jobPanel.setVisible(true);
                subTaskPanel.setVisible(true); // also shows embedded worker sub-tasks
            } else {
                lblRole.setText("WORKER");
                lblRole.setForeground(ACCENT_WORKER);
                String brokerStr = brokerId;
                if (brokerIp != null) {
                    brokerStr += " @ " + brokerIp + ":" + brokerTcpPort;
                }
                lblBrokerInfo.setText(brokerStr);
                lblBrokerInfo.setForeground(TEXT_PRIMARY);
                // Show worker-specific panels only
                workerPanel.setVisible(false);
                mobilePanel.setVisible(false);
                jobPanel.setVisible(false);
                subTaskPanel.setVisible(true);
            }
            appendLog("Role changed: " + role +
                    (brokerId != null ? " (broker=" + brokerId + ")" : ""));
        });
    }

    @Override
    public void onWorkerRegistered(String workerId, String ip, int cores, long memMB) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < workerTableModel.getRowCount(); i++) {
                if (workerId.equals(workerTableModel.getValueAt(i, 0))) {
                    workerTableModel.setValueAt(ip, i, 1);
                    workerTableModel.setValueAt(cores, i, 2);
                    workerTableModel.setValueAt(memMB, i, 3);
                    workerTableModel.setValueAt("ACTIVE", i, 4);
                    return;
                }
            }
            workerTableModel.addRow(new Object[]{workerId, ip, cores, memMB, "ACTIVE"});
            lblWorkerCount.setText("Workers: " + workerTableModel.getRowCount());
            appendLog("Worker registered: " + workerId + " (" + ip + ", " + cores + " cores, " + memMB + " MB)");
        });
    }

    @Override
    public void onWorkerLost(String workerId) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < workerTableModel.getRowCount(); i++) {
                if (workerId.equals(workerTableModel.getValueAt(i, 0))) {
                    workerTableModel.removeRow(i);
                    break;
                }
            }
            lblWorkerCount.setText("Workers: " + workerTableModel.getRowCount());
            appendLog("[!] Worker lost: " + workerId);
        });
    }

    @Override
    public void onJobReceived(String jobId, String jobType) {
        SwingUtilities.invokeLater(() -> {
            jobCounter++;
            String shortId = jobId.length() > 8 ? jobId.substring(0, 8) + ".." : jobId;
            jobTableModel.insertRow(0, new Object[]{
                    jobCounter, shortId, jobType, "PROCESSING", timeFmt.format(new Date())
            });
            lblJobCount.setText("Jobs: " + jobCounter);
            while (jobTableModel.getRowCount() > 50) {
                jobTableModel.removeRow(jobTableModel.getRowCount() - 1);
            }
            appendLog("Job received: " + jobType + " (" + shortId + ")");
        });
    }

    @Override
    public void onJobCompleted(String jobId, String jobType, String status) {
        SwingUtilities.invokeLater(() -> {
            String shortId = jobId.length() > 8 ? jobId.substring(0, 8) + ".." : jobId;
            for (int i = 0; i < jobTableModel.getRowCount(); i++) {
                String rowId = jobTableModel.getValueAt(i, 1).toString();
                if (rowId.equals(shortId)) {
                    jobTableModel.setValueAt(status, i, 3);
                    break;
                }
            }
            appendLog("Job completed: " + jobType + " (" + shortId + ") - " + status);
        });
    }

    @Override
    public void onMobileClientConnected(String clientIp) {
        SwingUtilities.invokeLater(() -> {
            // Check for duplicate
            for (int i = 0; i < mobileTableModel.getRowCount(); i++) {
                if (clientIp.equals(mobileTableModel.getValueAt(i, 0))) {
                    mobileTableModel.setValueAt("CONNECTED", i, 2);
                    return;
                }
            }
            mobileTableModel.addRow(new Object[]{clientIp, timeFmt.format(new Date()), "CONNECTED"});
            lblMobileCount.setText("Devices: " + mobileTableModel.getRowCount());
            appendLog("Mobile device connected: " + clientIp);
        });
    }

    @Override
    public void onMobileClientDisconnected(String clientIp) {
        SwingUtilities.invokeLater(() -> {
            for (int i = 0; i < mobileTableModel.getRowCount(); i++) {
                if (clientIp.equals(mobileTableModel.getValueAt(i, 0))) {
                    mobileTableModel.setValueAt("DISCONNECTED", i, 2);
                    break;
                }
            }
            appendLog("Mobile device disconnected: " + clientIp);
        });
    }

    @Override
    public void onWorkerJobAssigned(String subTaskId, String jobType, long durationMs) {
        SwingUtilities.invokeLater(() -> {
            subTaskCounter++;
            String shortId = subTaskId.length() > 12 ? subTaskId.substring(0, 12) + ".." : subTaskId;
            subTaskTableModel.insertRow(0, new Object[]{
                    subTaskCounter, shortId, jobType, durationMs, timeFmt.format(new Date())
            });
            lblSubTaskCount.setText("Sub-tasks: " + subTaskCounter);
            while (subTaskTableModel.getRowCount() > 100) {
                subTaskTableModel.removeRow(subTaskTableModel.getRowCount() - 1);
            }
        });
    }

    @Override
    public void onLogMessage(String message) {
        SwingUtilities.invokeLater(() -> appendLog(message));
    }

    private void appendLog(String message) {
        String timestamp = timeFmt.format(new Date());
        taEventLog.append("[" + timestamp + "] " + message + "\n");
        taEventLog.setCaretPosition(taEventLog.getDocument().getLength());
    }
}
