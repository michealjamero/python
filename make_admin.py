import sqlite3
from werkzeug.security import generate_password_hash

# Connect to database
conn = sqlite3.connect('music_app.db')
conn.row_factory = sqlite3.Row

try:
    # Check if users table exists
    cursor = conn.cursor()
    cursor.execute("SELECT name FROM sqlite_master WHERE type='table' AND name='users'")
    table_exists = cursor.fetchone()
    
    if not table_exists:
        print("Users table doesn't exist. Creating it...")
        # Create users table
        conn.execute('''CREATE TABLE users (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            username TEXT UNIQUE NOT NULL,
            email TEXT UNIQUE NOT NULL,
            password TEXT NOT NULL,
            is_admin INTEGER DEFAULT 0,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )''')
        conn.commit()
        print("Users table created.")
    
    # Check existing users
    users = conn.execute("SELECT id, username, email, is_admin FROM users").fetchall()
    print("Existing users:")
    for user in users:
        print(f"ID: {user['id']}, Username: {user['username']}, Email: {user['email']}, Admin: {user['is_admin']}")
    
    if not users:
        # Create a test admin user
        print("No users found. Creating test admin user...")
        hashed_password = generate_password_hash('admin123')
        conn.execute(
            "INSERT INTO users (username, email, password, is_admin) VALUES (?, ?, ?, ?)",
            ('admin', 'admin@test.com', hashed_password, 1)
        )
        conn.commit()
        print("Test admin user created: username='admin', password='admin123'")
    else:
        # Make user ID 1 an admin
        conn.execute("UPDATE users SET is_admin = 1 WHERE id = 1")
        conn.commit()
        print(f"\nUser ID 1 has been granted admin privileges.")

except Exception as e:
    print(f"Error: {e}")

conn.close()