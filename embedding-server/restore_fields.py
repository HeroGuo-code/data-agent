import os, psycopg2, re

sql_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), os.pardir, "database.sql")
