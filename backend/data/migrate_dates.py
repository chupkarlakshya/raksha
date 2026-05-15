import sqlite3
import os

db_path = r"c:\Users\jlaak\Desktop\raksha-main_final\raksha-main\backend\data\safepath.db"
if not os.path.exists(db_path):
    print(f"DB not found at {db_path}")
    exit(1)

conn = sqlite3.connect(db_path)
try:
    # Fix SOS events
    conn.execute("UPDATE sos_events SET created_at = REPLACE(created_at, '+00:00Z', 'Z')")
    # Fix incidents
    conn.execute("UPDATE incidents SET created_at = REPLACE(created_at, '+00:00Z', 'Z')")
    # Also handle cases where it might just be the +00:00 without the Z (from isoformat)
    conn.execute("UPDATE sos_events SET created_at = REPLACE(created_at, '+00:00', 'Z') WHERE created_at LIKE '%+00:00'")
    conn.execute("UPDATE incidents SET created_at = REPLACE(created_at, '+00:00', 'Z') WHERE created_at LIKE '%+00:00'")
    
    conn.commit()
    print("Database migration successful: All timestamps sanitized.")
except Exception as e:
    print(f"Error during migration: {e}")
finally:
    conn.close()
