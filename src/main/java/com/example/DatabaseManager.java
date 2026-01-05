package com.example;

import java.sql.*;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class DatabaseManager {
    private static final String DB_URL = "jdbc:sqlite:restaurant.db";
    private static Connection connection;

    // Initialize database and create tables
    public static void initialize() {
        try {
            connection = DriverManager.getConnection(DB_URL);
            createTables();

            // Load images for existing menu items that don't have them
            loadImagesForExistingMenuItems();

            System.out.println("Database initialized successfully!");
        } catch (SQLException e) {
            System.err.println("Error initializing database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Migrate database schema for existing databases
    private static void migrateDatabase() {
        try (Statement stmt = connection.createStatement()) {
            // Check if order_id column exists, if not add it
            ResultSet rs = stmt.executeQuery("PRAGMA table_info(orders)");
            boolean hasOrderId = false;
            boolean hasTableNumber = false;
            boolean hasStatus = false;

            while (rs.next()) {
                String columnName = rs.getString("name");
                if ("order_id".equals(columnName))
                    hasOrderId = true;
                if ("table_number".equals(columnName))
                    hasTableNumber = true;
                if ("status".equals(columnName))
                    hasStatus = true;
            }

            // Add missing columns to orders table
            if (!hasOrderId) {
                stmt.execute("ALTER TABLE orders ADD COLUMN order_id INTEGER DEFAULT 1");
                System.out.println("Added order_id column to orders table");
            }
            if (!hasTableNumber) {
                stmt.execute("ALTER TABLE orders ADD COLUMN table_number INTEGER DEFAULT 0");
                System.out.println("Added table_number column to orders table");
            }
            if (!hasStatus) {
                stmt.execute("ALTER TABLE orders ADD COLUMN status TEXT DEFAULT 'Pending'");
                System.out.println("Added status column to orders table");
            }

            // Check if image_data column exists in menu_items table
            ResultSet menuRs = stmt.executeQuery("PRAGMA table_info(menu_items)");
            boolean hasImageData = false;
            boolean hasImagePath = false;

            while (menuRs.next()) {
                String columnName = menuRs.getString("name");
                if ("image_data".equals(columnName)) {
                    hasImageData = true;
                }
                if ("image_path".equals(columnName)) {
                    hasImagePath = true;
                }
            }

            // Add image_data column if it doesn't exist
            if (!hasImageData) {
                stmt.execute("ALTER TABLE menu_items ADD COLUMN image_data BLOB");
                System.out.println("Added image_data column to menu_items table");
            }

            // Remove old image_path column if it exists (can't do in SQLite, just ignore
            // it)
            if (hasImagePath && !hasImageData) {
                System.out.println("Note: image_path column exists but will use image_data instead");
            }

            // Populate order_id for existing records if needed
            if (hasOrderId) {
                // Assign sequential order_ids to existing orders
                // Group by client_name, client_number, and order_date
                ResultSet existingOrders = stmt.executeQuery(
                        "SELECT id, client_name, client_number, order_date FROM orders ORDER BY client_name, client_number, order_date");

                int currentOrderId = 1;
                String lastClient = "";
                String lastDate = "";

                while (existingOrders.next()) {
                    String clientKey = existingOrders.getString("client_name") + "|" +
                            existingOrders.getString("client_number") + "|" +
                            existingOrders.getString("order_date");

                    if (!clientKey.equals(lastClient + "|" + lastDate)) {
                        currentOrderId++;
                    }

                    int orderDbId = existingOrders.getInt("id");
                    PreparedStatement updateStmt = connection.prepareStatement(
                            "UPDATE orders SET order_id = ? WHERE id = ?");
                    updateStmt.setInt(1, currentOrderId);
                    updateStmt.setInt(2, orderDbId);
                    updateStmt.executeUpdate();
                    updateStmt.close();

                    lastClient = existingOrders.getString("client_name") + "|" +
                            existingOrders.getString("client_number");
                    lastDate = existingOrders.getString("order_date");
                }
            }

        } catch (SQLException e) {
            System.err.println("Error migrating database: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Create tables if they don't exist
    private static void createTables() {
        String createClientTable = "CREATE TABLE IF NOT EXISTS client (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL, " +
                "number TEXT NOT NULL, " +
                "UNIQUE(name, number)" +
                ")";

        String createAdminTable = "CREATE TABLE IF NOT EXISTS admin (" +
                "admin_id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "admin_code TEXT NOT NULL UNIQUE, " +
                "admin_password TEXT NOT NULL" +
                ")";

        String createTablesTable = "CREATE TABLE IF NOT EXISTS tables (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "max_capacity INTEGER NOT NULL, " +
                "availability INTEGER DEFAULT 1" +
                ")";

        String createOrdersTable = "CREATE TABLE IF NOT EXISTS orders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "order_id INTEGER NOT NULL, " +
                "client_name TEXT NOT NULL, " +
                "client_number TEXT NOT NULL, " +
                "table_number INTEGER DEFAULT 0, " +
                "item_name TEXT NOT NULL, " +
                "quantity INTEGER NOT NULL, " +
                "price REAL NOT NULL, " +
                "order_date TEXT NOT NULL, " +
                "status TEXT DEFAULT 'Pending'" +
                ")";

        String createReservationsTable = "CREATE TABLE IF NOT EXISTS reservations (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "client_name TEXT NOT NULL, " +
                "client_number TEXT NOT NULL, " +
                "table_id INTEGER NOT NULL, " +
                "party_size INTEGER NOT NULL, " +
                "reservation_date TEXT NOT NULL, " +
                "UNIQUE(client_name, client_number), " +
                "FOREIGN KEY(table_id) REFERENCES tables(id)" +
                ")";

        String createMenuItemsTable = "CREATE TABLE IF NOT EXISTS menu_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT NOT NULL UNIQUE, " +
                "price REAL NOT NULL, " +
                "available INTEGER DEFAULT 1, " +
                "image_data BLOB" +
                ")";

        try (Statement stmt = connection.createStatement()) {
            stmt.execute(createClientTable);
            stmt.execute(createAdminTable);
            stmt.execute(createTablesTable);
            stmt.execute(createOrdersTable);
            stmt.execute(createReservationsTable);
            stmt.execute(createMenuItemsTable);

            // Migrate existing database if needed
            migrateDatabase();

            // Add default admin if table is empty
            addDefaultAdmin();

            // Add default tables if none exist
            addDefaultTables();

            // Add default menu items if none exist
            addDefaultMenuItems();
        } catch (SQLException e) {
            System.err.println("Error creating tables: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Add default admin account if none exists
    private static void addDefaultAdmin() {
        String checkQuery = "SELECT COUNT(*) FROM admin";
        String insertQuery = "INSERT INTO admin (admin_code, admin_password) VALUES (?, ?)";

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(checkQuery)) {

            if (rs.next() && rs.getInt(1) == 0) {
                // No admins exist, add default one
                try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
                    pstmt.setString(1, "admin");
                    pstmt.setString(2, "admin123");
                    pstmt.executeUpdate();
                    System.out.println("Default admin account created (code: admin, password: admin123)");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error adding default admin: " + e.getMessage());
        }
    }

    // Add default tables if none exist
    private static void addDefaultTables() {
        String checkQuery = "SELECT COUNT(*) FROM tables";

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(checkQuery)) {

            if (rs.next() && rs.getInt(1) == 0) {
                // No tables exist, add default ones
                String insertQuery = "INSERT INTO tables (max_capacity, availability) VALUES (?, ?)";

                try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
                    // Add 10 tables with different capacities
                    int[][] tables = { { 2, 1 }, { 2, 1 }, { 4, 1 }, { 4, 1 }, { 4, 1 }, { 6, 1 }, { 6, 1 }, { 8, 1 },
                            { 8, 1 }, { 10, 1 } };

                    for (int[] table : tables) {
                        pstmt.setInt(1, table[0]);
                        pstmt.setInt(2, table[1]);
                        pstmt.executeUpdate();
                    }

                    System.out.println("Default tables created (10 tables with various capacities)");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error adding default tables: " + e.getMessage());
        }
    }

    // Helper method to load image from resources
    private static byte[] loadImageFromResources(String imageName) {
        try {
            InputStream is = DatabaseManager.class.getResourceAsStream("images/" + imageName);
            if (is != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, bytesRead);
                }
                is.close();
                return baos.toByteArray();
            } else {
                System.err.println("Image not found: " + imageName);
                return null;
            }
        } catch (IOException e) {
            System.err.println("Error loading image " + imageName + ": " + e.getMessage());
            return null;
        }
    }

    // Save image to resources folder
    public static boolean saveImageToResources(byte[] imageData, String fileName) {
        if (imageData == null || fileName == null || fileName.isEmpty()) {
            return false;
        }

        try {
            // Get the path to the resources/images folder
            // First try to find the src/main/resources path in the project
            java.io.File currentDir = new java.io.File(System.getProperty("user.dir"));
            java.io.File resourcesDir = new java.io.File(currentDir, "src/main/resources/com/example/images");

            // Create the directory if it doesn't exist
            if (!resourcesDir.exists()) {
                resourcesDir.mkdirs();
            }

            // Create the file path
            java.io.File imageFile = new java.io.File(resourcesDir, fileName);

            // Write the image data to the file
            java.nio.file.Files.write(imageFile.toPath(), imageData);

            System.out.println("Image saved to resources: " + imageFile.getAbsolutePath());
            return true;
        } catch (IOException e) {
            System.err.println("Error saving image to resources: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Add default menu items if none exist
    private static void addDefaultMenuItems() {
        String checkQuery = "SELECT COUNT(*) FROM menu_items";

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(checkQuery)) {

            if (rs.next() && rs.getInt(1) == 0) {
                // No menu items exist, add default ones with images
                String insertQuery = "INSERT INTO menu_items (name, price, available, image_data) VALUES (?, ?, ?, ?)";

                try (PreparedStatement pstmt = connection.prepareStatement(insertQuery)) {
                    // Add default menu items with corresponding image files
                    Object[][] menuItems = {
                            { "Spicy Potato", 12.00, 1, "spicy_potato.jpg" },
                            { "Pasta", 15.00, 1, "pasta.jpg" },
                            { "Garlic Bread", 8.00, 1, "garlic_bread.jpg" },
                            { "Burger", 14.00, 1, "burger.jpg" },
                            { "Pizza", 18.00, 1, "pizza.jpg" },
                            { "Taco", 10.00, 1, "taco.jpg" }
                    };

                    for (Object[] item : menuItems) {
                        pstmt.setString(1, (String) item[0]);
                        pstmt.setDouble(2, (Double) item[1]);
                        pstmt.setInt(3, (Integer) item[2]);

                        // Load and set image data
                        byte[] imageData = loadImageFromResources((String) item[3]);
                        if (imageData != null) {
                            pstmt.setBytes(4, imageData);
                        } else {
                            pstmt.setNull(4, java.sql.Types.BLOB);
                        }

                        pstmt.executeUpdate();
                    }

                    System.out.println("Default menu items created with images (6 items)");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error adding default menu items: " + e.getMessage());
        }
    }

    // Menu Item CRUD Operations

    // Get all menu items
    public static java.util.List<MenuItem> getAllMenuItems() {
        java.util.List<MenuItem> menuItems = new java.util.ArrayList<>();
        String query = "SELECT id, name, price, available, image_data FROM menu_items ORDER BY name";

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                menuItems.add(new MenuItem(
                        rs.getInt("id"),
                        rs.getString("name"),
                        rs.getDouble("price"),
                        rs.getInt("available") == 1,
                        rs.getBytes("image_data")));
            }
        } catch (SQLException e) {
            System.err.println("Error getting menu items: " + e.getMessage());
        }

        return menuItems;
    }

    // Add new menu item
    public static boolean addMenuItem(String name, double price, boolean available, byte[] imageData) {
        String query = "INSERT INTO menu_items (name, price, available, image_data) VALUES (?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, name.trim());
            pstmt.setDouble(2, price);
            pstmt.setInt(3, available ? 1 : 0);
            pstmt.setBytes(4, imageData);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error adding menu item: " + e.getMessage());
            return false;
        }
    }

    // Update menu item
    public static boolean updateMenuItem(int id, String newName, double price, boolean available, byte[] imageData) {
        String query = "UPDATE menu_items SET name = ?, price = ?, available = ?, image_data = ? WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, newName.trim());
            pstmt.setDouble(2, price);
            pstmt.setInt(3, available ? 1 : 0);
            pstmt.setBytes(4, imageData);
            pstmt.setInt(5, id);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error updating menu item: " + e.getMessage());
            return false;
        }
    }

    // Delete menu item
    public static boolean deleteMenuItem(int id) {
        String query = "DELETE FROM menu_items WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, id);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error deleting menu item: " + e.getMessage());
            return false;
        }
    }

    // Toggle menu item availability
    public static boolean toggleMenuItemAvailability(int id) {
        String query = "UPDATE menu_items SET available = CASE WHEN available = 1 THEN 0 ELSE 1 END WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, id);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error toggling menu item availability: " + e.getMessage());
            return false;
        }
    }

    // Update menu items with images from resources (utility method for migration)
    public static void loadImagesForExistingMenuItems() {
        // Map of menu item names to their corresponding image files
        java.util.Map<String, String> imageMap = new java.util.HashMap<>();
        imageMap.put("Spicy Potato", "spicy_potato.jpg");
        imageMap.put("Pasta", "pasta.jpg");
        imageMap.put("Garlic Bread", "garlic_bread.jpg");
        imageMap.put("Burger", "burger.jpg");
        imageMap.put("Pizza", "pizza.jpg");
        imageMap.put("Taco", "taco.jpg");

        String updateQuery = "UPDATE menu_items SET image_data = ? WHERE name = ? AND (image_data IS NULL OR length(image_data) = 0)";

        try (PreparedStatement pstmt = connection.prepareStatement(updateQuery)) {
            int updatedCount = 0;
            for (java.util.Map.Entry<String, String> entry : imageMap.entrySet()) {
                byte[] imageData = loadImageFromResources(entry.getValue());
                if (imageData != null) {
                    pstmt.setBytes(1, imageData);
                    pstmt.setString(2, entry.getKey());
                    int rowsAffected = pstmt.executeUpdate();
                    if (rowsAffected > 0) {
                        updatedCount++;
                        System.out.println("Loaded image for: " + entry.getKey());
                    }
                }
            }
            if (updatedCount > 0) {
                System.out.println("Successfully loaded images for " + updatedCount + " menu items");
            } else {
                System.out.println("No menu items needed image updates");
            }
        } catch (SQLException e) {
            System.err.println("Error updating menu items with images: " + e.getMessage());
        }
    }

    // Table class to represent table data
    public static class Table {
        private int id;
        private int maxCapacity;
        private boolean availability;

        public Table(int id, int maxCapacity, boolean availability) {
            this.id = id;
            this.maxCapacity = maxCapacity;
            this.availability = availability;
        }

        public int getId() {
            return id;
        }

        public int getMaxCapacity() {
            return maxCapacity;
        }

        public boolean isAvailable() {
            return availability;
        }
    }

    // Reservation class to represent reservation data
    public static class Reservation {
        private int id;
        private String clientName;
        private String clientNumber;
        private int tableId;
        private int partySize;
        private String reservationDate;

        public Reservation(int id, String clientName, String clientNumber,
                int tableId, int partySize, String reservationDate) {
            this.id = id;
            this.clientName = clientName;
            this.clientNumber = clientNumber;
            this.tableId = tableId;
            this.partySize = partySize;
            this.reservationDate = reservationDate;
        }

        public int getId() {
            return id;
        }

        public String getClientName() {
            return clientName;
        }

        public String getClientNumber() {
            return clientNumber;
        }

        public int getTableId() {
            return tableId;
        }

        public int getPartySize() {
            return partySize;
        }

        public String getReservationDate() {
            return reservationDate;
        }
    }

    // Get all available tables
    public static java.util.List<Table> getAvailableTables() {
        java.util.List<Table> tables = new java.util.ArrayList<>();
        String query = "SELECT id, max_capacity, availability FROM tables WHERE availability = 1";

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                tables.add(new Table(
                        rs.getInt("id"),
                        rs.getInt("max_capacity"),
                        rs.getInt("availability") == 1));
            }
        } catch (SQLException e) {
            System.err.println("Error getting available tables: " + e.getMessage());
        }

        return tables;
    }

    // Get table by ID
    public static Table getTableById(int tableId) {
        String query = "SELECT id, max_capacity, availability FROM tables WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, tableId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Table(
                            rs.getInt("id"),
                            rs.getInt("max_capacity"),
                            rs.getInt("availability") == 1);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting table: " + e.getMessage());
        }

        return null;
    }

    // Reserve a table (set availability to 0)
    public static boolean reserveTable(int tableId) {
        String query = "UPDATE tables SET availability = 0 WHERE id = ? AND availability = 1";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, tableId);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error reserving table: " + e.getMessage());
            return false;
        }
    }

    // Release a table (set availability to 1)
    public static boolean releaseTable(int tableId) {
        String query = "UPDATE tables SET availability = 1 WHERE id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, tableId);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error releasing table: " + e.getMessage());
            return false;
        }
    }

    // Check if client exists by name and number
    public static boolean clientExists(String name, String number) {
        String query = "SELECT COUNT(*) FROM client WHERE name = ? AND number = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, name);
            pstmt.setString(2, number);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking client existence: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    // Check if client number already exists
    public static boolean isNumberExists(String number) {
        String query = "SELECT COUNT(*) FROM client WHERE number = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, number);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking if number exists: " + e.getMessage());
        }

        return false;
    }

    // Add new client to database
    public static boolean addClient(String name, String number) {
        // Check if number already exists
        if (isNumberExists(number)) {
            System.err.println("Client number already exists: " + number);
            return false;
        }

        String query = "INSERT INTO client (name, number) VALUES (?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, name);
            pstmt.setString(2, number);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error adding client: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Verify admin credentials
    public static boolean verifyAdmin(String adminCode, String password) {
        String query = "SELECT COUNT(*) FROM admin WHERE admin_code = ? AND admin_password = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, adminCode);
            pstmt.setString(2, password);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error verifying admin: " + e.getMessage());
            e.printStackTrace();
        }

        return false;
    }

    // Add new order to database
    public static boolean addOrder(String clientName, String clientNumber, String itemName,
            int quantity, double price, String orderDate) {
        // Get next order_id (group orders by client and date)
        int orderId = getNextOrderId(clientName, clientNumber, orderDate);

        // Get table number from reservation if exists
        int tableNumber = getClientTableNumber(clientName, clientNumber);

        System.out.println("Adding order for " + clientName + " " + clientNumber + " at table " + tableNumber);

        String query = "INSERT INTO orders (order_id, client_name, client_number, table_number, item_name, quantity, price, order_date, status) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, orderId);
            pstmt.setString(2, clientName);
            pstmt.setString(3, clientNumber);
            pstmt.setInt(4, tableNumber);
            pstmt.setString(5, itemName);
            pstmt.setInt(6, quantity);
            pstmt.setDouble(7, price);
            pstmt.setString(8, orderDate);
            pstmt.setString(9, Order.STATUS_PENDING);
            pstmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("Error adding order: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Get next order ID for grouping orders
    private static int getNextOrderId(String clientName, String clientNumber, String orderDate) {
        // For simplicity, use a sequential order ID starting from 1
        String query = "SELECT MAX(order_id) as max_id FROM orders";
        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {
            if (rs.next()) {
                int maxId = rs.getInt("max_id");
                return maxId + 1;
            }
        } catch (SQLException e) {
            System.err.println("Error getting next order ID: " + e.getMessage());
        }
        return 1; // Start with 1 if no orders exist
    }

    // Get table number from client's reservation (public for testing)
    public static int getClientTableNumber(String clientName, String clientNumber) {
        String query = "SELECT table_id FROM reservations WHERE TRIM(client_name) = TRIM(?) AND TRIM(client_number) = TRIM(?)";
        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, clientName);
            pstmt.setString(2, clientNumber);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    int tableId = rs.getInt("table_id");
                    System.out.println(
                            "Found reservation for '" + clientName + "' '" + clientNumber + "' at table " + tableId);
                    return tableId;
                } else {
                    System.out.println("No reservation found for '" + clientName + "' '" + clientNumber + "'");
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting client table number: " + e.getMessage());
        }
        return 0; // No reservation
    }

    // Get all orders for a specific client
    public static java.util.List<Order> getClientOrders(String clientName, String clientNumber) {
        java.util.List<Order> orders = new java.util.ArrayList<>();
        String query = "SELECT * FROM orders WHERE client_name = ? AND client_number = ? ORDER BY order_date DESC";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, clientName);
            pstmt.setString(2, clientNumber);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    orders.add(new Order(
                            rs.getInt("id"),
                            rs.getInt("order_id"),
                            rs.getString("client_name"),
                            rs.getString("client_number"),
                            rs.getInt("table_number"),
                            rs.getString("item_name"),
                            rs.getInt("quantity"),
                            rs.getDouble("price"),
                            rs.getString("order_date"),
                            rs.getString("status")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting client orders: " + e.getMessage());
            e.printStackTrace();
        }

        return orders;
    }

    // Get all orders (for admin view if needed)
    public static java.util.List<Order> getAllOrders() {
        java.util.List<Order> orders = new java.util.ArrayList<>();
        String query = "SELECT * FROM orders ORDER BY order_date DESC";

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                orders.add(new Order(
                        rs.getInt("id"),
                        rs.getInt("order_id"),
                        rs.getString("client_name"),
                        rs.getString("client_number"),
                        rs.getInt("table_number"),
                        rs.getString("item_name"),
                        rs.getInt("quantity"),
                        rs.getDouble("price"),
                        rs.getString("order_date"),
                        rs.getString("status")));
            }
        } catch (SQLException e) {
            System.err.println("Error getting all orders: " + e.getMessage());
            e.printStackTrace();
        }

        return orders;
    }

    // Get order summaries (grouped by order_id)
    public static java.util.List<OrderSummary> getOrderSummaries() {
        java.util.List<OrderSummary> summaries = new java.util.ArrayList<>();
        String query = "SELECT order_id, table_number, order_date, client_name, client_number, " +
                "SUM(quantity * price) as total, status " +
                "FROM orders " +
                "GROUP BY order_id, table_number, order_date, client_name, client_number, status " +
                "ORDER BY order_id ASC";

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                int orderId = rs.getInt("order_id");
                int tableNumber = rs.getInt("table_number");
                String date = rs.getString("order_date");
                String clientName = rs.getString("client_name");
                String clientNumber = rs.getString("client_number");
                double total = rs.getDouble("total");
                String status = rs.getString("status");

                // Get order items for this order
                java.util.List<Order> orderItems = getOrdersByOrderId(orderId);

                summaries.add(new OrderSummary(orderId, tableNumber, date, total, status,
                        clientName, clientNumber, orderItems));
            }
        } catch (SQLException e) {
            System.err.println("Error getting order summaries: " + e.getMessage());
            e.printStackTrace();
        }

        return summaries;
    }

    // Get orders by order_id
    private static java.util.List<Order> getOrdersByOrderId(int orderId) {
        java.util.List<Order> orders = new java.util.ArrayList<>();
        String query = "SELECT * FROM orders WHERE order_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setInt(1, orderId);
            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    orders.add(new Order(
                            rs.getInt("id"),
                            rs.getInt("order_id"),
                            rs.getString("client_name"),
                            rs.getString("client_number"),
                            rs.getInt("table_number"),
                            rs.getString("item_name"),
                            rs.getInt("quantity"),
                            rs.getDouble("price"),
                            rs.getString("order_date"),
                            rs.getString("status")));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting orders by order ID: " + e.getMessage());
        }

        return orders;
    }

    // Update order status
    public static boolean updateOrderStatus(int orderId, String status) {
        String query = "UPDATE orders SET status = ? WHERE order_id = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, status);
            pstmt.setInt(2, orderId);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error updating order status: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    // Add a new reservation
    public static boolean addReservation(String clientName, String clientNumber,
            int tableId, int partySize) {
        // First check if client already has a reservation
        if (getClientReservation(clientName.trim(), clientNumber.trim()) != null) {
            System.err.println("Client already has a reservation");
            return false;
        }

        // Check if table is available
        Table table = getTableById(tableId);
        if (table == null || !table.isAvailable()) {
            System.err.println("Table is not available");
            return false;
        }

        // Reserve the table
        if (!reserveTable(tableId)) {
            return false;
        }

        // Add reservation record
        String query = "INSERT INTO reservations (client_name, client_number, table_id, party_size, reservation_date) "
                +
                "VALUES (?, ?, ?, ?, datetime('now'))";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, clientName.trim());
            pstmt.setString(2, clientNumber.trim());
            pstmt.setInt(3, tableId);
            pstmt.setInt(4, partySize);
            pstmt.executeUpdate();
            System.out.println(
                    "Added reservation for " + clientName.trim() + " " + clientNumber.trim() + " at table " + tableId);
            return true;
        } catch (SQLException e) {
            System.err.println("Error adding reservation: " + e.getMessage());
            // Rollback table reservation
            releaseTable(tableId);
            return false;
        }
    }

    // Get reservation for a specific client
    public static Reservation getClientReservation(String clientName, String clientNumber) {
        String query = "SELECT * FROM reservations WHERE client_name = ? AND client_number = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, clientName);
            pstmt.setString(2, clientNumber);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return new Reservation(
                            rs.getInt("id"),
                            rs.getString("client_name"),
                            rs.getString("client_number"),
                            rs.getInt("table_id"),
                            rs.getInt("party_size"),
                            rs.getString("reservation_date"));
                }
            }
        } catch (SQLException e) {
            System.err.println("Error getting client reservation: " + e.getMessage());
        }

        return null;
    }

    // Cancel a client's reservation
    public static boolean cancelClientReservation(String clientName, String clientNumber) {
        Reservation reservation = getClientReservation(clientName, clientNumber);

        if (reservation == null) {
            return false;
        }

        // Delete reservation record
        String query = "DELETE FROM reservations WHERE client_name = ? AND client_number = ?";

        try (PreparedStatement pstmt = connection.prepareStatement(query)) {
            pstmt.setString(1, clientName);
            pstmt.setString(2, clientNumber);
            int rowsAffected = pstmt.executeUpdate();

            if (rowsAffected > 0) {
                // Release the table
                releaseTable(reservation.getTableId());
                return true;
            }
        } catch (SQLException e) {
            System.err.println("Error cancelling reservation: " + e.getMessage());
        }

        return false;
    }

    // Get all reservations (for admin view if needed)
    public static java.util.List<Reservation> getAllReservations() {
        java.util.List<Reservation> reservations = new java.util.ArrayList<>();
        String query = "SELECT * FROM reservations ORDER BY reservation_date DESC";

        try (Statement stmt = connection.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

            while (rs.next()) {
                reservations.add(new Reservation(
                        rs.getInt("id"),
                        rs.getString("client_name"),
                        rs.getString("client_number"),
                        rs.getInt("table_id"),
                        rs.getInt("party_size"),
                        rs.getString("reservation_date")));
            }
        } catch (SQLException e) {
            System.err.println("Error getting all reservations: " + e.getMessage());
        }

        return reservations;
    }

    // Close database connection
    public static void close() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("Error closing database: " + e.getMessage());
        }
    }
}
