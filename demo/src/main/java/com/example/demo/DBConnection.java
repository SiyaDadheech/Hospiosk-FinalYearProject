package com.example.demo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public class DBConnection {
    // Connection that points to the specific database
// Allow public key retrieval for local MySQL setups using caching_sha2_password
private static final String URL_WITH_DB = "jdbc:mysql://localhost:3306/hospital_queue?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
private static final String URL_NO_DB = "jdbc:mysql://localhost:3306/?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
private static final String USER = "root";
private static final String PASSWORD = "siya1234"; // replace with your MySQL password


    public static Connection getConnection() throws SQLException {
        try {
            Connection conn = DriverManager.getConnection(URL_WITH_DB, USER, PASSWORD);
            // Ensure the patients table exists so inserts won't fail
            try (Statement stmt = conn.createStatement()) {
                String createTable = "CREATE TABLE IF NOT EXISTS patients ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "name VARCHAR(255),"
                        + "age INT,"
                        + "token VARCHAR(50)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
                stmt.executeUpdate(createTable);
            } catch (SQLException e) {
                // Log but continue; caller will see failures on actual operations
                e.printStackTrace();
            }
            return conn;
        } catch (SQLException firstEx) {
            // Try to create the database if it doesn't exist, then reconnect
            try (Connection conn = DriverManager.getConnection(URL_NO_DB, USER, PASSWORD);
                 Statement stmt = conn.createStatement()) {
                String createDb = "CREATE DATABASE IF NOT EXISTS hospital_queue CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci";
                stmt.executeUpdate(createDb);
            } catch (SQLException createEx) {
                // If we can't create the DB, rethrow original exception with both messages
                SQLException e = new SQLException("Failed to connect and failed to create DB: "
                        + firstEx.getMessage() + " | " + createEx.getMessage());
                e.initCause(createEx);
                throw e;
            }

            // Try connecting again now that DB should exist, and ensure table exists
            Connection conn = DriverManager.getConnection(URL_WITH_DB, USER, PASSWORD);
            try (Statement stmt = conn.createStatement()) {
                String createTable = "CREATE TABLE IF NOT EXISTS patients ("
                        + "id INT AUTO_INCREMENT PRIMARY KEY,"
                        + "name VARCHAR(255),"
                        + "age INT,"
                        + "token VARCHAR(50)"
                        + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4";
                stmt.executeUpdate(createTable);
            } catch (SQLException e) {
                e.printStackTrace();
            }
            return conn;
        }
    }
}