# Restaurant Management System

A JavaFX-based restaurant management application with menu management, order processing, reservations, and admin features.

## Setup Instructions

### Prerequisites
- Java 21 or higher
- Maven

### Running the Application

1. Clone the repository
2. Navigate to the demo directory:
   ```bash
   cd demo
   ```

3. Run the application using Maven:
   ```bash
   mvn javafx:run
   ```

## Important: Menu Images

### For Fresh Installation
When you first run the application, it will automatically:
- Create a new `restaurant.db` file
- Load default menu items with images from the `src/main/resources/com/example/images/` folder
- The images are automatically stored in the database as BLOB data

### If Images Are Not Showing

If you have an existing database file without images, the application will automatically detect this and load images from the resources folder on the next run.

Alternatively, you can:
1. **Delete the database** and restart:
   - Delete the `restaurant.db` file (located in the demo directory)
   - Run the application again - it will create a fresh database with images

2. **Keep existing data**: The app now automatically loads missing images from resources when it starts

### Menu Item Images
The following default menu items are included with images:
- Spicy Potato → `spicy_potato.jpg`
- Pasta → `pasta.jpg`
- Garlic Bread → `garlic_bread.jpg`
- Burger → `burger.jpg`
- Pizza → `pizza.jpg`
- Taco → `taco.jpg`

All images are located in: `src/main/resources/com/example/images/`

## Features

- **Client Portal**: Browse menu, place orders, make reservations
- **Admin Portal**: Manage menu items, view orders, generate reports
- **Order Management**: Track order status and history
- **Table Reservations**: Reserve tables based on party size
- **Menu Management**: Add/edit/delete menu items with images
  - When admin adds a menu item with an image, it's automatically saved to both the database and the resources folder
  - Images are stored in `src/main/resources/com/example/images/`
  - This ensures images are included when the project is forked or shared

## Default Login Credentials

**Admin Access:**
- Code: `admin`
- Password: `admin123`

## Troubleshooting

### Images not displaying after forking?
The images are now automatically loaded from the resources folder. If you still don't see images:
1. Ensure all image files exist in `src/main/resources/com/example/images/`
2. Delete `restaurant.db` and run the app again
3. Check the console output for any error messages about loading images

### Database issues?
The database file (`restaurant.db`) is created automatically in the demo directory. If you encounter issues, delete it and restart the application.
