
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreePath;
import javax.swing.tree.DefaultTreeCellRenderer;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

/**
 * Main UI class for FileSortify application.
 * Handles the main window, toolbar, menu, category tree, downloads table, and file operations.
 */
public class FileSortifyUI extends JFrame {

    // --- Constants for file paths and storage ---
    private static final String BASE_FOLDER = System.getProperty("user.home") + File.separator + "FileSortifyDemo";
    private static final String CATEGORIES_FILE = BASE_FOLDER + File.separator + "custom_categories.txt";
    private static final String DOWNLOADS_FILE = BASE_FOLDER + File.separator + "downloads.dat";

    //UI Components
    private DefaultTableModel tableModel;
    private JTable downloadTable;
    private JTree categoryTree;
    private DefaultTreeModel treeModel;
    private JLabel statusLabel;
    private JProgressBar progressBar;

    //Constructor: Initializes the main window and all UI components.
    public FileSortifyUI() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            e.printStackTrace();
        }

        // Ensure base folder exists
        new File(BASE_FOLDER).mkdirs();

        // Load persisted downloads data
        loadDownloads();

        setTitle("FileSortify");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setLayout(new BorderLayout());

        // Add menu bar, toolbar, main panel, and status bar
        setJMenuBar(createMenuBar());
        add(createToolBar(), BorderLayout.NORTH);
        add(createMainPanel(), BorderLayout.CENTER);
        add(createStatusBar(), BorderLayout.SOUTH);

        setSize(1100, 650);
        setLocationRelativeTo(null);

        // Handle window closing: save state and exit
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                saveCategories();
                saveDownloads();
                dispose();
                System.exit(0);
            }
        });
    }

    /**
     * Saves custom categories to disk.
     * Only user-created categories (not predefined) are persisted.
     */
    private void saveCategories() {
        DefaultMutableTreeNode allDownloadsNode = findNode("All Downloads");
        if (allDownloadsNode == null) return;
        List<String> customCategories = new ArrayList<>();
        for (int i = 0; i < allDownloadsNode.getChildCount(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) allDownloadsNode.getChildAt(i);
            String categoryName = node.getUserObject().toString();
            if (!isPredefinedCategory(categoryName) && !"Add Folder".equals(categoryName)) {
                customCategories.add(categoryName);
            }
        }
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CATEGORIES_FILE))) {
            for (String category : customCategories) {
                writer.write(category);
                writer.newLine();
            }
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving custom categories: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Loads custom categories from disk.
     * Returns a list of user-created category names.
     */
    private List<String> loadCategories() {
        List<String> customCategories = new ArrayList<>();
        File categoriesFile = new File(CATEGORIES_FILE);
        if (categoriesFile.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(categoriesFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    customCategories.add(line.trim());
                }
            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error loading custom categories: " + ex.getMessage(), "Load Error", JOptionPane.ERROR_MESSAGE);
            }
        }
        return customCategories;
    }

    //Saves the current downloads table to disk for persistence
    private void saveDownloads() {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(DOWNLOADS_FILE))) {
            oos.writeObject(tableModel.getDataVector());
        } catch (IOException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Error saving downloads data: " + ex.getMessage(), "Save Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Loads downloads data from disk into the table model.
     * If no data exists, initializes an empty table.
     */
    private void loadDownloads() {
        File downloadsFile = new File(DOWNLOADS_FILE);
        if (downloadsFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(downloadsFile))) {
                Vector<Vector> data = (Vector<Vector>) ois.readObject();
                String[] columnNames = {"File Name", "Size", "Status", "Time Left",
                        "Transfer Rate", "Last Try Date", "Description", "Path"};
                tableModel = new DefaultTableModel(data, new Vector<>(java.util.Arrays.asList(columnNames))) {
                    public boolean isCellEditable(int row, int column) {
                        return false;
                    }
                };
                if (downloadTable != null) {
                    downloadTable.setModel(tableModel);
                    TableColumn pathColumn = downloadTable.getColumnModel().getColumn(7);
                    pathColumn.setMinWidth(0);
                    pathColumn.setMaxWidth(0);
                    pathColumn.setPreferredWidth(0);
                }
            } catch (IOException | ClassNotFoundException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error loading downloads data: " + ex.getMessage() + "\nStarting with an empty table.", "Load Error", JOptionPane.ERROR_MESSAGE);
                tableModel = new DefaultTableModel(new Object[][]{}, new String[]{"File Name", "Size", "Status", "Time Left", "Transfer Rate", "Last Try Date", "Description", "Path"}) {
                    public boolean isCellEditable(int row, int column) {
                        return false;
                    }
                };
            }
        } else {
            tableModel = new DefaultTableModel(new Object[][]{}, new String[]{"File Name", "Size", "Status", "Time Left", "Transfer Rate", "Last Try Date", "Description", "Path"}) {
                public boolean isCellEditable(int row, int column) {
                    return false;
                }
            };
        }
    }

    //checks if a category is one of the predefined categories
    private boolean isPredefinedCategory(String categoryName) {
        return categoryName.equals("Compressed") ||
                categoryName.equals("Documents") ||
                categoryName.equals("Music") ||
                categoryName.equals("Programs") ||
                categoryName.equals("Video") ||
                categoryName.equals("Images") ||
                categoryName.equals("Other");
    }

    //creates the main menu bar with placeholder items.
    private JMenuBar createMenuBar() {
        JMenuBar menuBar = new JMenuBar();
        String[] menuTitles = {"Tasks", "File", "Downloads", "View", "Help", "Registration"};
        for (String title : menuTitles) {
            JMenu menu = new JMenu(title);
            JMenuItem item = new JMenuItem(title + " (Not yet implemented)");
            item.addActionListener(e -> JOptionPane.showMessageDialog(this, title + " functionality not yet implemented.", title, JOptionPane.INFORMATION_MESSAGE));
            menu.add(item);
            menuBar.add(menu);
        }
        return menuBar;
    }

    /**
     * Creates the toolbar with buttons for common actions.
     * Some buttons are placeholders for future features.
     */
    private JToolBar createToolBar() {
        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        String[][] buttons = {
                {"Add URL", "Add a new download from a URL"},
                {"Import File", "Import a file from your computer"},
                {"Resume", "Resume selected download"},
                {"Stop", "Stop selected download"},
                {"Stop All", "Stop all downloads"},
                {"Delete", "Delete selected entries or folder"},
                {"Delete Completed", "Delete all completed downloads"},
                {"Options", "Open options/settings"},
                {"Scheduler", "Open scheduler"},
                {"Start Queue", "Start the download queue"},
                {"Stop Queue", "Stop the download queue"},
                {"Tell a Friend", "Share FileSortify with a friend"}
        };

        for (int i = 0; i < buttons.length; i++) {
            final int idx = i;
            JButton button = new JButton(buttons[idx][0]);
            button.setToolTipText(buttons[idx][1]);
            button.setFocusPainted(false);
            button.setMargin(new Insets(2, 8, 2, 8));

            //assign action listeners for each button
            switch (buttons[idx][0]) {
                case "Add URL":
                    button.addActionListener(e -> JOptionPane.showMessageDialog(this, "Add URL functionality not yet implemented.", "Add URL", JOptionPane.INFORMATION_MESSAGE));
                    break;
                case "Import File":
                    button.addActionListener(e -> showImportFileDialog());
                    break;
                case "Delete":
                    button.addActionListener(e -> {
                        if (downloadTable.getSelectedRowCount() > 0) {
                            deleteSelectedEntries();
                        } else if (categoryTree.getSelectionCount() > 0) {
                            deleteSelectedFolder();
                        } else {
                            JOptionPane.showMessageDialog(this, "Select an item in the downloads table or a folder in the category tree to delete.", "Delete", JOptionPane.INFORMATION_MESSAGE);
                        }
                    });
                    break;
                case "Delete Completed":
                    button.addActionListener(e -> deleteCompletedEntries());
                    break;
                case "Resume":
                case "Stop":
                case "Stop All":
                case "Options":
                case "Scheduler":
                case "Start Queue":
                case "Stop Queue":
                case "Tell a Friend":
                    button.addActionListener(e -> JOptionPane.showMessageDialog(this, buttons[idx][0] + " functionality not yet implemented.", buttons[idx][0], JOptionPane.INFORMATION_MESSAGE));
                    break;
                default:
                    button.addActionListener(e -> JOptionPane.showMessageDialog(this, "'" + buttons[idx][0] + "' functionality not explicitly handled.", buttons[idx][0], JOptionPane.INFORMATION_MESSAGE));
                    break;
            }
            toolBar.add(button);

            //Add separators for grouping
            if (i == 1 || i == 4 || i == 6 || i == 8) {
                toolBar.addSeparator(new Dimension(12, 0));
            }
        }
        return toolBar;
    }

    //creates the main split panel: left is the category tree, right is the downloads table
    private JSplitPane createMainPanel() {
        categoryTree = createCategoryTree();
        JScrollPane leftScroll = new JScrollPane(categoryTree);
        leftScroll.setPreferredSize(new Dimension(260, 0));
        leftScroll.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 4));

        //downloads Table Setup
        downloadTable = new JTable(tableModel) {
            // Show tooltips for table cells
            public String getToolTipText(MouseEvent e) {
                java.awt.Point p = e.getPoint();
                int rowIndex = rowAtPoint(p);
                int colIndex = columnAtPoint(p);
                if (rowIndex >= 0 && colIndex >= 0) {
                    Object value = getValueAt(rowIndex, colIndex);
                    return value != null ? value.toString() : null;
                }
                return null;
            }
        };
        downloadTable.setRowHeight(26);
        downloadTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        downloadTable.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 15));
        downloadTable.setSelectionBackground(new Color(220, 240, 255));
        downloadTable.setSelectionForeground(Color.BLACK);
        downloadTable.setShowGrid(false);
        downloadTable.setIntercellSpacing(new Dimension(0, 0));
        downloadTable.setAutoCreateRowSorter(true);

        //alternate row coloring for readability
        downloadTable.setDefaultRenderer(Object.class, new DefaultTableCellRenderer() {
            private final Color evenColor = new Color(245, 250, 255);
            @Override
            public Component getTableCellRendererComponent(JTable table, Object value,
                                                          boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
                if (!isSelected) {
                    c.setBackground(row % 2 == 0 ? evenColor : Color.WHITE);
                }
                return c;
            }
        });

        //hide the file path column (internal use only)
        TableColumn pathColumn = downloadTable.getColumnModel().getColumn(7);
        pathColumn.setMinWidth(0);
        pathColumn.setMaxWidth(0);
        pathColumn.setPreferredWidth(0);

        JScrollPane rightScroll = new JScrollPane(downloadTable);
        rightScroll.setBorder(BorderFactory.createTitledBorder("Downloads"));

        //Double-click to open containing folder
        downloadTable.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = downloadTable.getSelectedRow();
                    if (row != -1) {
                        String path = (String) tableModel.getValueAt(row, 7);
                        File file = new File(path);
                        if (!file.exists()) {
                            JOptionPane.showMessageDialog(FileSortifyUI.this,
                                    "Downloaded file not found:\n" + path, "File Not Found", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        File parentDir = file.getParentFile();
                        if (parentDir == null) {
                            JOptionPane.showMessageDialog(FileSortifyUI.this,
                                    "Cannot determine containing folder for:\n" + path, "Error", JOptionPane.ERROR_MESSAGE);
                            return;
                        }
                        if (parentDir.exists()) {
                            openElement(parentDir);
                        } else {
                            JOptionPane.showMessageDialog(FileSortifyUI.this,
                                    "Containing folder not found for:\n" + path + "\nAttempting to open base folder.", "Error", JOptionPane.WARNING_MESSAGE);
                            openElement(new File(BASE_FOLDER));
                        }
                    }
                }
            }
        });

        //Clear selection when clicking on empty area in JTable
        downloadTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int row = downloadTable.rowAtPoint(e.getPoint());
                int col = downloadTable.columnAtPoint(e.getPoint());
                if (row == -1 || col == -1) {
                    downloadTable.clearSelection();
                }
            }
        });

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, leftScroll, rightScroll);
        splitPane.setDividerLocation(260);
        splitPane.setResizeWeight(0.0);
        return splitPane;
    }

    /**
     * Creates the category tree (left panel) with predefined and custom categories.
     * Handles double-click for opening folders and adding new folders.
     */
    private JTree createCategoryTree() {
        DefaultMutableTreeNode root = new DefaultMutableTreeNode("ROOT");

        //Predefined categories under "All Downloads"
        DefaultMutableTreeNode allDownloads = new DefaultMutableTreeNode("All Downloads");
        allDownloads.add(new DefaultMutableTreeNode("Compressed"));
        allDownloads.add(new DefaultMutableTreeNode("Documents"));
        allDownloads.add(new DefaultMutableTreeNode("Images"));
        allDownloads.add(new DefaultMutableTreeNode("Music"));
        allDownloads.add(new DefaultMutableTreeNode("Programs"));
        allDownloads.add(new DefaultMutableTreeNode("Video"));

        //Load custom categories from disk
        List<String> customCategories = loadCategories();
        for (String category : customCategories) {
            File categoryDir = new File(BASE_FOLDER + File.separator + "All Downloads" + File.separator + category);
            if (categoryDir.exists() && categoryDir.isDirectory()) {
                allDownloads.add(new DefaultMutableTreeNode(category));
            }
        }
        allDownloads.add(new DefaultMutableTreeNode("Add Folder"));
        root.add(allDownloads);

        //Other top-level folders
        root.add(new DefaultMutableTreeNode("Unfinished"));
        root.add(new DefaultMutableTreeNode("Finished"));
        root.add(new DefaultMutableTreeNode("Grabber Projects"));
        root.add(new DefaultMutableTreeNode("Queues"));
        root.add(new DefaultMutableTreeNode("Add Folder"));

        treeModel = new DefaultTreeModel(root);
        JTree tree = new JTree(treeModel);
        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);
        tree.setRowHeight(26);
        tree.setFont(new Font("Segoe UI", Font.PLAIN, 14));

        //Set icons for tree nodes
        DefaultTreeCellRenderer renderer = new DefaultTreeCellRenderer();
        renderer.setLeafIcon(UIManager.getIcon("FileView.fileIcon"));
        renderer.setClosedIcon(UIManager.getIcon("FileView.directoryIcon"));
        renderer.setOpenIcon(UIManager.getIcon("FileView.directoryIcon"));
        tree.setCellRenderer(renderer);

        //show tooltips for tree nodes
        ToolTipManager.sharedInstance().registerComponent(tree);
        tree.addMouseMotionListener(new MouseMotionAdapter() {
            public void mouseMoved(MouseEvent e) {
                TreePath path = tree.getPathForLocation(e.getX(), e.getY());
                if (path != null) {
                    tree.setToolTipText(path.getLastPathComponent().toString());
                } else {
                    tree.setToolTipText(null);
                }
            }
        });

        //expand all nodes for visibility
        expandAllNodes(tree, 0, tree.getRowCount());

        //double-click logic for folders: open or add folder 
        tree.addMouseListener(new MouseAdapter() {
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    TreePath path = tree.getSelectionPath();
                    if (path == null)
                        return;
                    DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) path.getLastPathComponent();
                    String nodeName = selectedNode.getUserObject().toString();
                    DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();

                    if ("Add Folder".equals(nodeName)) {
                        //prompt user to create a new folder/category
                        String folderName = JOptionPane.showInputDialog(FileSortifyUI.this, "Enter new folder name:");
                        if (folderName != null && !folderName.trim().isEmpty()) {
                            String newFolderName = folderName.trim();
                            if (parentNode != null && "All Downloads".equals(parentNode.getUserObject().toString())) {
                                if (!categoryExists(parentNode, newFolderName)) {
                                    int insertionIndex = parentNode.getChildCount() - 1;
                                    treeModel.insertNodeInto(new DefaultMutableTreeNode(newFolderName), parentNode, insertionIndex);
                                    tree.expandPath(path.getParentPath());
                                    new File(BASE_FOLDER + File.separator + "All Downloads" + File.separator + newFolderName).mkdirs();
                                    saveCategories();
                                } else {
                                    JOptionPane.showMessageDialog(FileSortifyUI.this, "A category with that name already exists.", "Duplicate Category", JOptionPane.WARNING_MESSAGE);
                                }
                            } else if (parentNode != null && parentNode.getUserObject().equals("ROOT")) {
                                if (!categoryExists(parentNode, newFolderName)) {
                                    int insertionIndex = parentNode.getChildCount() - 1;
                                    treeModel.insertNodeInto(new DefaultMutableTreeNode(newFolderName), parentNode, insertionIndex);
                                    tree.expandPath(path.getParentPath());
                                    new File(BASE_FOLDER + File.separator + newFolderName).mkdirs();
                                } else {
                                    JOptionPane.showMessageDialog(FileSortifyUI.this, "A top-level folder with that name already exists.", "Duplicate Folder", JOptionPane.WARNING_MESSAGE);
                                }
                            }
                        }
                    } else {
                        // Open the folder in the system file explorer
                        String folderPath = BASE_FOLDER;
                        Object[] nodes = path.getPath();
                        for (int i = 1; i < nodes.length; i++) {
                            folderPath += File.separator + nodes[i].toString();
                        }
                        openElement(new File(folderPath));
                    }
                }
            }
        });

        //clear selection when clicking on empty area in JTree
        tree.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                int selRow = tree.getRowForLocation(e.getX(), e.getY());
                if (selRow == -1) {
                    tree.clearSelection();
                }
            }
        });

        return tree;
    }

    //Checks if a category with the given name exists under the specified parent node
    private boolean categoryExists(DefaultMutableTreeNode parentNode, String categoryName) {
        if (parentNode == null) return false;
        for (int i = 0; i < parentNode.getChildCount(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) parentNode.getChildAt(i);
            if (node.getUserObject().toString().equalsIgnoreCase(categoryName)) {
                return true;
            }
        }
        return false;
    }

    //xpands all nodes in the given JTree for full visibility
    private void expandAllNodes(JTree tree, int startingIndex, int rowCount) {
        for (int i = startingIndex; i < rowCount; i++) {
            tree.expandRow(i);
        }
        if (tree.getRowCount() != rowCount) {
            expandAllNodes(tree, rowCount, tree.getRowCount());
        }
    }

    /**
     * creates the status bar at the bottom of the window
     * shows status messages and a progress bar
     */
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new BorderLayout());
        statusBar.setBorder(new EmptyBorder(4, 10, 4, 10));
        statusLabel = new JLabel("Ready");
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        progressBar = new JProgressBar();
        progressBar.setPreferredSize(new Dimension(120, 18));
        progressBar.setVisible(false);
        statusBar.add(statusLabel, BorderLayout.WEST);
        statusBar.add(progressBar, BorderLayout.EAST);
        return statusBar;
    }

    //placeholder for future Add URL dialog
    private void showAddUrlDialog() {
        JOptionPane.showMessageDialog(this, "Add URL functionality not yet implemented.", "Add URL", JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * Placeholder for download logic from a URL.
     * (Implementation omitted for brevity.)
     */
    private void startDownload(String urlString, final File[] destWrapper, int tableRowIndex) {
        // (Implementation unchanged, see previous artifacts if you want to restore actual download logic)
    }

    /**
     * shows a file chooser dialog to import files
     * allows user to sort files by extension or choose a single category
     */
    private void showImportFileDialog() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Select Files to Import");
        fileChooser.setMultiSelectionEnabled(true);
        int result = fileChooser.showOpenDialog(this);

        if (result == JFileChooser.APPROVE_OPTION) {
            File[] selectedFiles = fileChooser.getSelectedFiles();
            if (selectedFiles.length == 0) {
                JOptionPane.showMessageDialog(this, "No files were selected for import.", "Import", JOptionPane.INFORMATION_MESSAGE);
                return;
            }

            String[] options = {"Sort by Extension", "Choose One Folder for All"};
            int choice = JOptionPane.showOptionDialog(
                    this,
                    "How do you want to organize the imported files?",
                    "Import Options",
                    JOptionPane.DEFAULT_OPTION,
                    JOptionPane.QUESTION_MESSAGE,
                    null,
                    options,
                    options[0]
            );

            if (choice == 1) { // "Choose One Folder for All"
                String selectedCategory = selectOrCreateCategory();
                if (selectedCategory == null) {
                    return;
                }
                String fileFolder = BASE_FOLDER + File.separator + "All Downloads" + File.separator + selectedCategory;
                new File(fileFolder).mkdirs();

                for (File selectedFile : selectedFiles) {
                    String fileName = selectedFile.getName();
                    String destPath = fileFolder + File.separator + fileName;
                    try {
                        File destinationFile = new File(destPath);
                        if (destinationFile.exists()) {
                            int overwriteResult = JOptionPane.showConfirmDialog(this,
                                    "File '" + fileName + "' already exists in '" + selectedCategory + "'. Overwrite?",
                                    "File Exists", JOptionPane.YES_NO_OPTION);
                            if (overwriteResult == JOptionPane.NO_OPTION) {
                                continue;
                            }
                        }
                        Files.copy(selectedFile.toPath(), destinationFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        addDownloadEntry(fileName, selectedFile.length() + " bytes", "Imported", "N/A", "N/A", "N/A", "Imported from local file | Category: " + selectedCategory, destPath);
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(this, "Error importing file '" + fileName + "': " + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
                saveDownloads();
                JOptionPane.showMessageDialog(this, selectedFiles.length + " file(s) imported to category: " + selectedCategory, "Import Complete", JOptionPane.INFORMATION_MESSAGE);
            } else { // "Sort by Extension"
                int importedCount = 0;
                for (File selectedFile : selectedFiles) {
                    String fileName = selectedFile.getName();
                    String category = determineCategory(fileName);
                    String fileFolder = BASE_FOLDER + File.separator + "All Downloads" + File.separator + category;
                    new File(fileFolder).mkdirs();

                    String destPath = fileFolder + File.separator + fileName;
                    try {
                        File destinationFile = new File(destPath);
                        if (destinationFile.exists()) {
                            int overwriteResult = JOptionPane.showConfirmDialog(this,
                                    "File '" + fileName + "' already exists in '" + category + "'. Overwrite?",
                                    "File Exists", JOptionPane.YES_NO_OPTION);
                            if (overwriteResult == JOptionPane.NO_OPTION) {
                                continue;
                            }
                        }
                        Files.copy(selectedFile.toPath(), destinationFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        addDownloadEntry(fileName, selectedFile.length() + " bytes", "Imported", "N/A", "N/A", "N/A", "Imported from local file | Category: " + category, destPath);
                        importedCount++;
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        JOptionPane.showMessageDialog(this, "Error importing file '" + fileName + "': " + ex.getMessage(), "Import Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
                saveDownloads();
                JOptionPane.showMessageDialog(this, importedCount + " file(s) imported to their respective extension folders.", "Import Complete", JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    /**
     * determines the category for a file based on its extension
     * used for sorting imported files
     */
    private String determineCategory(String fileName) {
        String extension = "";
        int index = fileName.lastIndexOf('.');
        if (index > 0 && index < fileName.length() - 1) {
            extension = fileName.substring(index + 1).toLowerCase();
        }
        switch (extension) {
            case "zip":
            case "rar":
            case "7z":
            case "tar":
            case "gz":
                return "Compressed";
            case "pdf":
            case "doc":
            case "docx":
            case "txt":
            case "xls":
            case "xlsx":
            case "ppt":
            case "pptx":
            case "odt":
            case "ods":
            case "odp":
                return "Documents";
            case "mp3":
            case "wav":
            case "flac":
            case "m4a":
            case "aac":
            case "ogg":
                return "Music";
            case "exe":
            case "msi":
            case "dmg":
            case "deb":
            case "rpm":
                return "Programs";
            case "mp4":
            case "avi":
            case "mkv":
            case "mov":
            case "wmv":
            case "flv":
            case "webm":
                return "Video";
            case "jpg":
            case "jpeg":
            case "png":
            case "gif":
            case "bmp":
            case "tiff":
            case "svg":
                return "Images";
            default:
                return "Other";
        }
    }

    /**
     * prompts the user to select or create a category for imported files
     * returns the selected or newly created category name
     */
    private String selectOrCreateCategory() {
        DefaultMutableTreeNode allDownloadsNode = findNode("All Downloads");
        if (allDownloadsNode == null) {
            return "Other";
        }
        List<String> categories = new ArrayList<>();
        categories.add("Compressed");
        categories.add("Documents");
        categories.add("Images");
        categories.add("Music");
        categories.add("Programs");
        categories.add("Video");
        categories.add("Other");

        for (int i = 0; i < allDownloadsNode.getChildCount(); i++) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) allDownloadsNode.getChildAt(i);
            String categoryName = node.getUserObject().toString();
            if (!isPredefinedCategory(categoryName) && !"Add Folder".equals(categoryName) && !categoryName.equals("Other")) {
                if (!categories.contains(categoryName)) {
                    categories.add(categoryName);
                }
            }
        }
        categories.add("Create New Folder...");

        String[] categoryOptions = categories.toArray(new String[0]);
        String selectedOption = (String) JOptionPane.showInputDialog(
                this,
                "Select or create a category for the imported files:",
                "Choose Category",
                JOptionPane.QUESTION_MESSAGE,
                null,
                categoryOptions,
                categories.contains("Other") ? "Other" : (categoryOptions.length > 0 ? categoryOptions[0] : null)
        );

        if (selectedOption == null) {
            return null;
        }
        if ("Create New Folder...".equals(selectedOption)) {
            String newFolderName = JOptionPane.showInputDialog(this, "Enter the name for the new category folder:");
            if (newFolderName != null && !newFolderName.trim().isEmpty()) {
                String categoryName = newFolderName.trim();
                if (!categoryExists(allDownloadsNode, categoryName)) {
                    DefaultMutableTreeNode newNode = new DefaultMutableTreeNode(categoryName);
                    int addFolderIndex = -1;
                    for (int i = 0; i < allDownloadsNode.getChildCount(); i++) {
                        if ("Add Folder".equals(allDownloadsNode.getChildAt(i).toString())) {
                            addFolderIndex = i;
                            break;
                        }
                    }
                    if (addFolderIndex != -1) {
                        treeModel.insertNodeInto(newNode, allDownloadsNode, addFolderIndex);
                    } else {
                        treeModel.insertNodeInto(newNode, allDownloadsNode, allDownloadsNode.getChildCount());
                    }
                    categoryTree.expandPath(new TreePath(allDownloadsNode.getPath()));
                    new File(BASE_FOLDER + File.separator + "All Downloads" + File.separator + categoryName).mkdirs();
                    saveCategories();
                    return categoryName;
                } else {
                    JOptionPane.showMessageDialog(this, "A category with that name already exists.", "Duplicate Category", JOptionPane.WARNING_MESSAGE);
                    return selectOrCreateCategory();
                }
            } else {
                return selectOrCreateCategory();
            }
        } else {
            return selectedOption;
        }
    }

    /**
     * Finds a tree node by its user object string
     * Returns the node if found, or null
     */
    private DefaultMutableTreeNode findNode(String userObject) {
        DefaultMutableTreeNode root = (DefaultMutableTreeNode) treeModel.getRoot();
        java.util.Enumeration<?> e = root.breadthFirstEnumeration();
        while (e.hasMoreElements()) {
            DefaultMutableTreeNode node = (DefaultMutableTreeNode) e.nextElement();
            if (userObject.equals(node.getUserObject().toString())) {
                return node;
            }
        }
        return null;
    }

    //adds a new entry to the downloads table
    public void addDownloadEntry(String fileName, String size, String status, String timeLeft, String transferRate, String lastTryDate, String description, String filePath) {
        Object[] rowData = {fileName, size, status, timeLeft, transferRate, lastTryDate, description, filePath};
        tableModel.addRow(rowData);
    }

    /**
     * Deletes selected entries from the downloads table
     * Prompts the user for confirmation
     */
    private void deleteSelectedEntries() {
        int[] selectedRows = downloadTable.getSelectedRows();
        if (selectedRows.length == 0) {
            JOptionPane.showMessageDialog(this, "No entries selected to delete.", "Delete", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete the selected entries?", "Confirm Deletion", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            for (int i = selectedRows.length - 1; i >= 0; i--) {
                tableModel.removeRow(selectedRows[i]);
            }
            saveDownloads();
        }
    }

    //deletes all completed entries from the downloads table
    private void deleteCompletedEntries() {
        int rowCount = tableModel.getRowCount();
        int deletedCount = 0;
        for (int i = rowCount - 1; i >= 0; i--) {
            String status = (String) tableModel.getValueAt(i, 2);
            if ("Completed".equals(status)) {
                tableModel.removeRow(i);
                deletedCount++;
            }
        }
        if (deletedCount > 0) {
            saveDownloads();
            JOptionPane.showMessageDialog(this, deletedCount + " completed entries deleted.", "Delete Completed", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "No completed entries found.", "Delete Completed", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    /**
     * Deletes the selected folder from the category tree and its files from disk
     * Prompts the user for confirmation
     */
    private void deleteSelectedFolder() {
        TreePath selectedPath = categoryTree.getSelectionPath();
        if (selectedPath == null) {
            JOptionPane.showMessageDialog(this, "No folder selected to delete.", "Delete Folder", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        DefaultMutableTreeNode selectedNode = (DefaultMutableTreeNode) selectedPath.getLastPathComponent();
        DefaultMutableTreeNode parentNode = (DefaultMutableTreeNode) selectedNode.getParent();

        //prevents deletion of predefined folders
        if (selectedNode.getUserObject().equals("ROOT") ||
                selectedNode.getUserObject().equals("All Downloads") ||
                selectedNode.getUserObject().equals("Unfinished") ||
                selectedNode.getUserObject().equals("Finished") ||
                selectedNode.getUserObject().equals("Grabber Projects") ||
                selectedNode.getUserObject().equals("Queues")) {
            JOptionPane.showMessageDialog(this, "Cannot delete this predefined folder.", "Delete Folder Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (selectedNode.getUserObject().equals("Add Folder")) {
            JOptionPane.showMessageDialog(this, "Cannot delete the 'Add Folder' placeholder.", "Delete Folder Error", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String folderName = selectedNode.getUserObject().toString();
        int confirm = JOptionPane.showConfirmDialog(this, "Are you sure you want to delete the folder '" + folderName + "' and its contents?\nThis action cannot be undone.", "Confirm Folder Deletion", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            try {
                String folderPath = BASE_FOLDER;
                Object[] nodes = selectedPath.getPath();
                for (int i = 1; i < nodes.length; i++) {
                    folderPath += File.separator + nodes[i].toString();
                }
                File folderToDelete = new File(folderPath);
                deleteFolder(folderToDelete);

                if (parentNode != null) {
                    treeModel.removeNodeFromParent(selectedNode);
                    if (parentNode.getUserObject().equals("All Downloads")) {
                        saveCategories();
                    }
                }
                removeTableEntriesForFolder(folderPath);
                saveDownloads();
                JOptionPane.showMessageDialog(this, "Folder '" + folderName + "' deleted successfully.", "Delete Folder", JOptionPane.INFORMATION_MESSAGE);

            } catch (IOException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(this, "Error deleting folder '" + folderName + "': " + ex.getMessage(), "Delete Folder Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    //Recursively deletes a folder and all its contents from disk
    private void deleteFolder(File folder) throws IOException {
        if (folder.isDirectory()) {
            File[] files = folder.listFiles();
            if (files != null) {
                for (File file : files) {
                    deleteFolder(file);
                }
            }
        }
        try {
            Files.delete(folder.toPath());
        } catch (IOException e) {
            throw new IOException("Failed to delete: " + folder.getAbsolutePath(), e);
        }
    }

    /**
     * Removes all table entries whose file path is under the specified folder.
     */
    private void removeTableEntriesForFolder(String folderPath) {
        if (!folderPath.endsWith(File.separator)) {
            folderPath += File.separator;
        }
        for (int i = tableModel.getRowCount() - 1; i >= 0; i--) {
            String entryFilePath = (String) tableModel.getValueAt(i, 7);
            if (entryFilePath != null && (entryFilePath.equals(folderPath.substring(0, folderPath.length() - 1)) || entryFilePath.startsWith(folderPath))) {
                tableModel.removeRow(i);
            }
        }
    }

    /**
     * Opens a file or folder in the system's file explorer
     * If doesnt exist, creates it or open its parent
     */
    private static void openElement(File element) {
        if (!element.exists()) {
            if (element.isDirectory() || element.getName().indexOf('.') == -1) {
                boolean dirsCreated = element.mkdirs();
                if (!dirsCreated) {
                    JOptionPane.showMessageDialog(null, "Could not create directory: " + element.getAbsolutePath(), "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            } else {
                File parentDir = element.getParentFile();
                if (parentDir != null && parentDir.exists()) {
                    openElement(parentDir);
                    return;
                } else {
                    JOptionPane.showMessageDialog(null, "File not found and cannot determine parent folder: " + element.getAbsolutePath(), "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
            }
        }
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().open(element);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, "Error opening '" + element.getAbsolutePath() + "':\n" + e.getMessage(), "Error Opening Element", JOptionPane.ERROR_MESSAGE);
                e.printStackTrace();
            }
        } else {
            JOptionPane.showMessageDialog(null, "Desktop is not supported on this system. Cannot open files or folders.", "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    //launches FileSortify
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FileSortifyUI frame = new FileSortifyUI();
            frame.setVisible(true);
        });
    }
}
