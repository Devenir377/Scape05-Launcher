package scape05;

import javax.swing.*;
import java.awt.*;

public class Progress {
    private JPanel rootPanel;
    private JLabel captionLabel;
    private JProgressBar progressBar;
    private JLabel actionLabel;

    public Progress() {
        initUI();
    }

    private void initUI() {
        // A 3-row, 3-column layout to place:
        //  (0,0): "Scape05" (top-left)
        //  (1,0..2): Progress bar (spans all 3 columns)
        //  (2,2): Status (bottom-right)
        rootPanel = new JPanel(new GridBagLayout());
        rootPanel.setBackground(Color.BLACK);

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 6, 4, 6); // minimal margin around components
        gbc.weightx = 0;
        gbc.weighty = 0;

        // ========== Row 0, Col 0: "Scape05" in the top-left ==========
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.NORTHWEST;

        captionLabel = new JLabel("Scape05");
        captionLabel.setForeground(new Color(0xD4D4D4));
        rootPanel.add(captionLabel, gbc);

        // ========== Row 1, Col 0..2: Progress bar (middle, spanning entire width) ==========
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 3; // span all 3 columns
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.CENTER;

        progressBar = new JProgressBar();
        progressBar.setBorderPainted(false);
        progressBar.setPreferredSize(new Dimension(220, 24));
        rootPanel.add(progressBar, gbc);

        // ========== Row 2, Col 2: Status label (bottom-right) ==========
        gbc.gridx = 2;
        gbc.gridy = 2;
        gbc.gridwidth = 1;
        gbc.fill = GridBagConstraints.NONE;
        gbc.anchor = GridBagConstraints.SOUTHEAST;

        actionLabel = new JLabel("Connecting to server...");
        actionLabel.setForeground(new Color(0xD4D4D4));
        rootPanel.add(actionLabel, gbc);
    }

    // --- Public getters for the Updater class to access these components ---
    public JPanel getRootPanel() {
        return rootPanel;
    }

    public JLabel getCaptionLabel() {
        return captionLabel;
    }

    public JProgressBar getProgressBar() {
        return progressBar;
    }

    public JLabel getActionLabel() {
        return actionLabel;
    }
}
