package merger.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * A scrollable list of discovered replays, each rendered as a checkbox so the user can
 * choose exactly which replays to process. All replays start selected.
 * <p>
 * The component owns:
 * <ul>
 *     <li>a header showing "<i>x of y</i> selected" with <b>Select all</b> / <b>Deselect all</b> buttons,</li>
 *     <li>a {@link JList} of {@link ReplayItem}s rendered as checkboxes (toggled by mouse click or the space key),</li>
 *     <li>a per-row status label (e.g. an emoji) that processing updates as each replay completes.</li>
 * </ul>
 * Clicking a row toggles its checkbox; a registered {@link #setSelectionListener(Runnable)}
 * is notified whenever the selection changes so callers can re-validate (e.g. enable/disable
 * the Process button). While {@link #setInteractionEnabled(boolean) interaction is disabled}
 * (during processing) the checkboxes cannot be toggled.
 */
public class ReplayChecklist extends JPanel {

    /** A single replay row: its file name, whether it is selected for processing, and a status label. */
    private static final class ReplayItem {
        private final String replayName;
        private boolean selected = true;
        private String status = "";

        private ReplayItem(String replayName) {
            this.replayName = replayName;
        }

        private String displayText() {
            return status.isEmpty() ? replayName : status + " " + replayName;
        }
    }

    private final DefaultListModel<ReplayItem> model = new DefaultListModel<>();
    private final JList<ReplayItem> list = new JList<>(model);
    private final JLabel selectionCountLabel = new JLabel();
    private final JButton selectAllButton = new JButton("Select all");
    private final JButton deselectAllButton = new JButton("Deselect all");

    /** Notified whenever the set of selected replays changes. */
    private Runnable selectionListener = () -> {};

    public ReplayChecklist() {
        super(new BorderLayout());
        add(buildHeader(), BorderLayout.NORTH);
        add(new JScrollPane(list), BorderLayout.CENTER);
        configureList();
        updateSelectionCountLabel();
    }

    private JComponent buildHeader() {
        JPanel header = new JPanel(new BorderLayout());

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        selectAllButton.setFocusable(false);
        deselectAllButton.setFocusable(false);
        selectAllButton.addActionListener(e -> setAllSelected(true));
        deselectAllButton.addActionListener(e -> setAllSelected(false));
        buttons.add(selectAllButton);
        buttons.add(deselectAllButton);

        header.add(selectionCountLabel, BorderLayout.WEST);
        header.add(buttons, BorderLayout.EAST);
        return header;
    }

    private void configureList() {
        list.setCellRenderer(new CheckboxRenderer());
        // The list's own selection (highlight) is incidental; toggling happens via the checkbox.
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        list.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!list.isEnabled()) {
                    return;
                }
                int index = list.locationToIndex(e.getPoint());
                Rectangle cellBounds = index < 0 ? null : list.getCellBounds(index, index);
                if (cellBounds != null && cellBounds.contains(e.getPoint())) {
                    toggle(index);
                }
            }
        });

        // Allow toggling the highlighted row with the space bar.
        list.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggleSelected");
        list.getActionMap().put("toggleSelected", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                if (list.isEnabled()) {
                    toggle(list.getSelectedIndex());
                }
            }
        });
    }

    private void toggle(int index) {
        if (index < 0 || index >= model.getSize()) {
            return;
        }
        ReplayItem item = model.getElementAt(index);
        item.selected = !item.selected;
        repaintRow(index);
        updateSelectionCountLabel();
        selectionListener.run();
    }

    private void setAllSelected(boolean selected) {
        if (!list.isEnabled()) {
            return;
        }
        for (int i = 0; i < model.getSize(); i++) {
            model.getElementAt(i).selected = selected;
        }
        list.repaint();
        updateSelectionCountLabel();
        selectionListener.run();
    }

    private void repaintRow(int index) {
        Rectangle cellBounds = list.getCellBounds(index, index);
        if (cellBounds != null) {
            list.repaint(cellBounds);
        }
    }

    private void updateSelectionCountLabel() {
        selectionCountLabel.setText("Replays: " + getSelectedCount() + " of " + model.getSize() + " selected");
    }

    private int getSelectedCount() {
        int count = 0;
        for (int i = 0; i < model.getSize(); i++) {
            if (model.getElementAt(i).selected) {
                count++;
            }
        }
        return count;
    }

    // ---- Public API used by the UI / controller ----------------------------------------------

    /** Removes all replays from the list. */
    public void clear() {
        model.clear();
        updateSelectionCountLabel();
    }

    /** Adds a replay row (selected by default). */
    public void addReplay(String replayName) {
        model.addElement(new ReplayItem(replayName));
        updateSelectionCountLabel();
    }

    /** Returns the names of the replays the user has checked, in display order. */
    public List<String> getSelectedReplayNames() {
        List<String> selected = new ArrayList<>();
        for (int i = 0; i < model.getSize(); i++) {
            ReplayItem item = model.getElementAt(i);
            if (item.selected) {
                selected.add(item.replayName);
            }
        }
        return selected;
    }

    /** Updates the status label shown next to the given replay (e.g. a progress emoji). */
    public void setReplayStatus(String replayName, String status) {
        for (int i = 0; i < model.getSize(); i++) {
            if (model.getElementAt(i).replayName.equals(replayName)) {
                model.getElementAt(i).status = status;
                repaintRow(i);
                break;
            }
        }
    }

    /** Clears every row's status label (call before starting a fresh processing run). */
    public void clearStatuses() {
        for (int i = 0; i < model.getSize(); i++) {
            model.getElementAt(i).status = "";
        }
        list.repaint();
    }

    /** Enables or disables user interaction (toggling) — used to lock the list while processing. */
    public void setInteractionEnabled(boolean enabled) {
        list.setEnabled(enabled);
        selectAllButton.setEnabled(enabled);
        deselectAllButton.setEnabled(enabled);
    }

    /** Registers a callback invoked whenever the selection changes. */
    public void setSelectionListener(Runnable selectionListener) {
        this.selectionListener = selectionListener != null ? selectionListener : () -> {};
    }

    /** Renders each {@link ReplayItem} as a checkbox reflecting its selected state and status. */
    private final class CheckboxRenderer extends JCheckBox implements ListCellRenderer<ReplayItem> {
        @Override
        public Component getListCellRendererComponent(JList<? extends ReplayItem> list, ReplayItem item,
                                                      int index, boolean isSelected, boolean cellHasFocus) {
            setComponentOrientation(list.getComponentOrientation());
            setFont(list.getFont());
            setEnabled(list.isEnabled());
            setSelected(item.selected);
            setText(item.displayText());
            setBackground(isSelected ? list.getSelectionBackground() : list.getBackground());
            setForeground(isSelected ? list.getSelectionForeground() : list.getForeground());
            return this;
        }
    }
}
