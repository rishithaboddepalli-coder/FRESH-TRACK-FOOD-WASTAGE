import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

// FRESHTRACK - FOOD EXPIRY MANAGEMENT SYSTEM
public class FreshTrack extends JFrame {
    private static final String FILE_NAME = "freshtrack_data.dat";

    private ArrayList<FoodItem> foodItems = new ArrayList<>();
    private int wastedProducts = 0;
    private int wastedQuantity = 0;

    private DefaultTableModel tableModel;
    private JTable table;
    private JLabel totalLabel;
    private JLabel nearLabel;
    private JLabel expiredLabel;
    private JLabel wastedLabel;
    private Thread expiryThread;

    // CUSTOM EXCEPTION
    static class InvalidFoodException extends Exception {
        public InvalidFoodException(String message) {
            super(message);
        }
    }

    // FOOD ITEM CLASS
    static class FoodItem implements Serializable {
        private static final long serialVersionUID = 1L;

        private int id;
        private String name;
        private String category;
        private int quantity;
        private LocalDate purchaseDate;
        private LocalDate expiryDate;
        private String storage;

        public FoodItem(int id, String name, String category, int quantity,
                        LocalDate purchaseDate, LocalDate expiryDate, String storage) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.quantity = quantity;
            this.purchaseDate = purchaseDate;
            this.expiryDate = expiryDate;
            this.storage = storage;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getCategory() { return category; }
        public int getQuantity() { return quantity; }
        public LocalDate getPurchaseDate() { return purchaseDate; }
        public LocalDate getExpiryDate() { return expiryDate; }
        public String getStorage() { return storage; }

        public void update(String name, String category, int quantity,
                          LocalDate purchaseDate, LocalDate expiryDate, String storage) {
            this.name = name;
            this.category = category;
            this.quantity = quantity;
            this.purchaseDate = purchaseDate;
            this.expiryDate = expiryDate;
            this.storage = storage;
        }

        public long getDaysRemaining() {
            return ChronoUnit.DAYS.between(LocalDate.now(), expiryDate);
        }

        public String getStatus() {
            long days = getDaysRemaining();
            if (days < 0) {
                return "EXPIRED";
            } else if (days == 0) {
                return "EXPIRES TODAY";
            } else if (days <= 7) {
                return "NEAR EXPIRY";
            } else {
                return "FRESH";
            }
        }
    }

    // CONSTRUCTOR
    public FreshTrack() {
        setTitle("FreshTrack - Smart Food Management System");
        setSize(1150, 650);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        loadData();
        createInterface();
        refreshTable();
        startExpiryMonitor();

        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (expiryThread != null) {
                    expiryThread.interrupt();
                }
                saveData();
            }
        });
    }

    // USER INTERFACE
    private void createInterface() {
        setLayout(new BorderLayout(10, 10));

        JPanel header = new JPanel();
        header.setBackground(new Color(34, 139, 87));
        JLabel title = new JLabel("FRESHTRACK - SMART FOOD MANAGEMENT");
        title.setFont(new Font("Arial", Font.BOLD, 25));
        title.setForeground(Color.WHITE);
        header.add(title);
        add(header, BorderLayout.NORTH);

        JPanel dashboard = new JPanel(new GridLayout(1, 4, 10, 10));
        dashboard.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        totalLabel = createDashboardLabel("Total Items: 0");
        nearLabel = createDashboardLabel("Near Expiry: 0");
        expiredLabel = createDashboardLabel("Expired: 0");
        wastedLabel = createDashboardLabel("Wasted: 0");
        dashboard.add(totalLabel);
        dashboard.add(nearLabel);
        dashboard.add(expiredLabel);
        dashboard.add(wastedLabel);

        String[] columns = {
            "ID", "Food Name", "Category", "Quantity", "Purchase Date",
            "Expiry Date", "Days Left", "Storage", "Status"
        };

        tableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };

        table = new JTable(tableModel);
        table.setRowHeight(28);
        table.setFont(new Font("Arial", Font.PLAIN, 13));
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane scrollPane = new JScrollPane(table);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 12));
        JButton addButton = createButton("Add Food");
        JButton updateButton = createButton("Update Food");
        JButton deleteButton = createButton("Delete Food");
        JButton wasteButton = createButton("Mark as Wasted");
        JButton refreshButton = createButton("Refresh");
        JButton reportButton = createButton("Waste Report");

        buttons.add(addButton);
        buttons.add(updateButton);
        buttons.add(deleteButton);
        buttons.add(wasteButton);
        buttons.add(refreshButton);
        buttons.add(reportButton);

        addButton.addActionListener(e -> addFood());
        updateButton.addActionListener(e -> updateFood());
        deleteButton.addActionListener(e -> deleteFood());
        wasteButton.addActionListener(e -> markAsWasted());
        refreshButton.addActionListener(e -> refreshTable());
        reportButton.addActionListener(e -> showReport());

        JPanel center = new JPanel(new BorderLayout());
        center.add(dashboard, BorderLayout.NORTH);
        center.add(scrollPane, BorderLayout.CENTER);
        add(center, BorderLayout.CENTER);
        add(buttons, BorderLayout.SOUTH);
    }

    private JLabel createDashboardLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.CENTER);
        label.setFont(new Font("Arial", Font.BOLD, 15));
        label.setOpaque(true);
        label.setBackground(new Color(225, 242, 230));
        label.setBorder(BorderFactory.createLineBorder(new Color(34, 139, 87)));
        return label;
    }

    private JButton createButton(String text) {
        JButton button = new JButton(text);
        button.setFont(new Font("Arial", Font.BOLD, 12));
        return button;
    }

    // INPUT VALIDATION
    private void validateFood(String name, String category, int quantity,
                             LocalDate purchase, LocalDate expiry, String storage)
            throws InvalidFoodException {
        if (name.trim().isEmpty()) {
            throw new InvalidFoodException("Food name cannot be empty!");
        }
        if (category.trim().isEmpty()) {
            throw new InvalidFoodException("Category cannot be empty!");
        }
        if (storage.trim().isEmpty()) {
            throw new InvalidFoodException("Storage location cannot be empty!");
        }
        if (quantity <= 0) {
            throw new InvalidFoodException("Quantity must be greater than zero!");
        }
        if (purchase.isAfter(LocalDate.now())) {
            throw new InvalidFoodException("Purchase date cannot be in the future!");
        }
        if (expiry.isBefore(purchase)) {
            throw new InvalidFoodException("Expiry date cannot be before purchase date!");
        }
    }

    // ADD FOOD
    private void addFood() {
        JTextField nameField = new JTextField();
        JTextField categoryField = new JTextField();
        JTextField quantityField = new JTextField();
        JTextField purchaseField = new JTextField(LocalDate.now().toString());
        JTextField expiryField = new JTextField();
        JTextField storageField = new JTextField();

        Object[] fields = {
            "Food Name:", nameField,
            "Category:", categoryField,
            "Quantity:", quantityField,
            "Purchase Date (YYYY-MM-DD):", purchaseField,
            "Expiry Date (YYYY-MM-DD):", expiryField,
            "Storage Location:", storageField
        };

        int result = JOptionPane.showConfirmDialog(
            this, fields, "Add New Food", JOptionPane.OK_CANCEL_OPTION
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            String name = nameField.getText().trim();
            String category = categoryField.getText().trim();
            int quantity = Integer.parseInt(quantityField.getText().trim());
            LocalDate purchase = LocalDate.parse(purchaseField.getText().trim());
            LocalDate expiry = LocalDate.parse(expiryField.getText().trim());
            String storage = storageField.getText().trim();

            validateFood(name, category, quantity, purchase, expiry, storage);

            synchronized (this) {
                int id = getNextId();
                FoodItem item = new FoodItem(id, name, category, quantity,
                                             purchase, expiry, storage);
                foodItems.add(item);
                saveData();
            }

            refreshTable();
            JOptionPane.showMessageDialog(this, "Food added successfully!");
        } catch (NumberFormatException e) {
            showError("Quantity must be a valid number!");
        } catch (java.time.format.DateTimeParseException e) {
            showError("Enter dates in YYYY-MM-DD format!");
        } catch (InvalidFoodException e) {
            showError(e.getMessage());
        }
    }

    // Generate next unique ID
    private synchronized int getNextId() {
        int maxId = 0;
        for (FoodItem item : foodItems) {
            maxId = Math.max(maxId, item.getId());
        }
        return maxId + 1;
    }

    // UPDATE FOOD
    private void updateFood() {
        int row = table.getSelectedRow();
        if (row == -1) {
            showError("Please select a food item!");
            return;
        }

        int id = (int) tableModel.getValueAt(row, 0);
        FoodItem selected = findFood(id);
        if (selected == null) {
            showError("Food item not found!");
            return;
        }

        JTextField nameField = new JTextField(selected.getName());
        JTextField categoryField = new JTextField(selected.getCategory());
        JTextField quantityField = new JTextField(String.valueOf(selected.getQuantity()));
        JTextField purchaseField = new JTextField(selected.getPurchaseDate().toString());
        JTextField expiryField = new JTextField(selected.getExpiryDate().toString());
        JTextField storageField = new JTextField(selected.getStorage());

        Object[] fields = {
            "Food Name:", nameField,
            "Category:", categoryField,
            "Quantity:", quantityField,
            "Purchase Date:", purchaseField,
            "Expiry Date:", expiryField,
            "Storage:", storageField
        };

        int result = JOptionPane.showConfirmDialog(
            this, fields, "Update Food", JOptionPane.OK_CANCEL_OPTION
        );
        if (result != JOptionPane.OK_OPTION) {
            return;
        }

        try {
            String name = nameField.getText().trim();
            String category = categoryField.getText().trim();
            int quantity = Integer.parseInt(quantityField.getText().trim());
            LocalDate purchase = LocalDate.parse(purchaseField.getText().trim());
            LocalDate expiry = LocalDate.parse(expiryField.getText().trim());
            String storage = storageField.getText().trim();

            validateFood(name, category, quantity, purchase, expiry, storage);
            synchronized (this) {
                selected.update(name, category, quantity, purchase, expiry, storage);
                saveData();
            }

            refreshTable();
            JOptionPane.showMessageDialog(this, "Food updated successfully!");
        } catch (NumberFormatException e) {
            showError("Invalid quantity!");
        } catch (java.time.format.DateTimeParseException e) {
            showError("Enter valid dates in YYYY-MM-DD format!");
        } catch (InvalidFoodException e) {
            showError(e.getMessage());
        }
    }

    // DELETE FOOD
    private void deleteFood() {
        int row = table.getSelectedRow();
        if (row == -1) {
            showError("Please select a food item!");
            return;
        }

        int id = (int) tableModel.getValueAt(row, 0);
        int confirm = JOptionPane.showConfirmDialog(
            this, "Are you sure you want to delete this food item?",
            "Confirm Delete", JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {
            synchronized (this) {
                foodItems.removeIf(item -> item.getId() == id);
                saveData();
            }
            refreshTable();
            JOptionPane.showMessageDialog(this, "Food item deleted!");
        }
    }

    // MARK FOOD AS WASTED
    private void markAsWasted() {
        int row = table.getSelectedRow();
        if (row == -1) {
            showError("Please select a food item!");
            return;
        }

        int id = (int) tableModel.getValueAt(row, 0);
        FoodItem item = findFood(id);
        if (item == null) {
            showError("Food item not found!");
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(
            this, "Mark " + item.getName() + " as wasted?",
            "Waste Confirmation", JOptionPane.YES_NO_OPTION
        );

        if (confirm == JOptionPane.YES_OPTION) {
            synchronized (this) {
                wastedProducts++;
                wastedQuantity += item.getQuantity();
                foodItems.removeIf(food -> food.getId() == id);
                saveData();
            }
            refreshTable();
            JOptionPane.showMessageDialog(this, "Waste record updated!");
        }
    }

    // FIND FOOD
    private synchronized FoodItem findFood(int id) {
        for (FoodItem item : foodItems) {
            if (item.getId() == id) {
                return item;
            }
        }
        return null;
    }

    // REFRESH TABLE
    private void refreshTable() {
        synchronized (this) {
            tableModel.setRowCount(0);
            for (FoodItem item : foodItems) {
                tableModel.addRow(new Object[] {
                    item.getId(), item.getName(), item.getCategory(),
                    item.getQuantity(), item.getPurchaseDate(), item.getExpiryDate(),
                    item.getDaysRemaining(), item.getStorage(), item.getStatus()
                });
            }

            totalLabel.setText("Total Items: " + foodItems.size());
            nearLabel.setText("Near Expiry: " + getNearExpiryCount());
            expiredLabel.setText("Expired: " + getExpiredCount());
            wastedLabel.setText("Wasted: " + wastedProducts);
        }
    }

    private synchronized int getNearExpiryCount() {
        int count = 0;
        for (FoodItem item : foodItems) {
            long days = item.getDaysRemaining();
            if (days >= 0 && days <= 7) {
                count++;
            }
        }
        return count;
    }

    private synchronized int getExpiredCount() {
        int count = 0;
        for (FoodItem item : foodItems) {
            if (item.getDaysRemaining() < 0) {
                count++;
            }
        }
        return count;
    }

    // FOOD WASTE REPORT
    private void showReport() {
        String report =
            "========== FRESHTRACK WASTE REPORT ==========\n\n" +
            "Total Food Items: " + foodItems.size() + "\n\n" +
            "Near Expiry Items: " + getNearExpiryCount() + "\n\n" +
            "Expired Items: " + getExpiredCount() + "\n\n" +
            "Wasted Products: " + wastedProducts + "\n\n" +
            "Total Wasted Quantity: " + wastedQuantity + "\n\n";

        JOptionPane.showMessageDialog(
            this, report, "Food Waste Report", JOptionPane.INFORMATION_MESSAGE
        );
    }

    // BACKGROUND EXPIRY MONITOR
    private void startExpiryMonitor() {
        Runnable monitorTask = () -> {
            Set<String> notified = new HashSet<>();

            while (!Thread.currentThread().isInterrupted()) {
                ArrayList<FoodItem> snapshot;
                synchronized (this) {
                    snapshot = new ArrayList<>(foodItems);
                }

                for (FoodItem item : snapshot) {
                    String status = item.getStatus();
                    if (status.equals("NEAR EXPIRY")
                            || status.equals("EXPIRES TODAY")
                            || status.equals("EXPIRED")) {
                        String key = item.getId() + "-" + status;
                        if (notified.add(key)) {
                            SwingUtilities.invokeLater(() -> {
                                JOptionPane.showMessageDialog(
                                    this,
                                    item.getName() + "\nStatus: " + status
                                        + "\nDays Remaining: " + item.getDaysRemaining(),
                                    "FreshTrack Expiry Alert",
                                    status.equals("EXPIRED") || status.equals("EXPIRES TODAY")
                                        ? JOptionPane.WARNING_MESSAGE
                                        : JOptionPane.INFORMATION_MESSAGE
                                );
                            });
                        }
                    }
                }

                SwingUtilities.invokeLater(this::refreshTable);
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };

        expiryThread = new Thread(monitorTask, "Expiry-Monitor-Thread");
        expiryThread.setDaemon(true);
        expiryThread.start();
    }

    // SAVE DATA
    private synchronized void saveData() {
        try (ObjectOutputStream out = new ObjectOutputStream(
                new FileOutputStream(FILE_NAME))) {
            AppData data = new AppData(foodItems, wastedProducts, wastedQuantity);
            out.writeObject(data);
        } catch (IOException e) {
            System.out.println("Error saving data: " + e.getMessage());
        }
    }

    // LOAD DATA
    private void loadData() {
        File file = new File(FILE_NAME);
        if (!file.exists()) {
            return;
        }

        try (ObjectInputStream in = new ObjectInputStream(
                new FileInputStream(file))) {
            AppData data = (AppData) in.readObject();
            foodItems = data.items;
            wastedProducts = data.wastedProducts;
            wastedQuantity = data.wastedQuantity;
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            JOptionPane.showMessageDialog(
                null, "Could not load saved data: " + e.getMessage()
            );
        }
    }

    // DATA STORAGE CLASS
    static class AppData implements Serializable {
        private static final long serialVersionUID = 1L;

        ArrayList<FoodItem> items;
        int wastedProducts;
        int wastedQuantity;

        AppData(ArrayList<FoodItem> items, int wastedProducts, int wastedQuantity) {
            this.items = new ArrayList<>(items);
            this.wastedProducts = wastedProducts;
            this.wastedQuantity = wastedQuantity;
        }
    }

    // ERROR MESSAGE
    private void showError(String message) {
        JOptionPane.showMessageDialog(
            this, message, "FreshTrack Error", JOptionPane.ERROR_MESSAGE
        );
    }

    // MAIN METHOD
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            FreshTrack application = new FreshTrack();
            application.setVisible(true);
        });
    }
}
