CREATE DATABASE IF NOT EXISTS ltm_db;

USE ltm_db;

CREATE TABLE users(
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      username VARCHAR(50) UNIQUE NOT NULL,
                      password VARCHAR(100) NOT NULL
);

CREATE TABLE rooms(
                      id INT AUTO_INCREMENT PRIMARY KEY,
                      room_name VARCHAR(100) NOT NULL
);

CREATE TABLE messages(
                         id INT AUTO_INCREMENT PRIMARY KEY,
                         sender VARCHAR(50) NOT NULL,
                         receiver VARCHAR(50) NOT NULL,
                         content TEXT NOT NULL,
                         created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO users(username,password)
VALUES
    ('admin','123'),
    ('kiet','123'),
    ('an','123');

INSERT INTO rooms(room_name)
VALUES
    ('General'),
    ('Java'),
    ('Study');

