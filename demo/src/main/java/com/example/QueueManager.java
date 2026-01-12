package com.example;

import java.sql.*;
import java.util.Stack;
import java.util.List;
import java.util.ArrayList;

import com.example.demo.DBConnection;

public class QueueManager {
    private Stack<Patient> undoStack = new Stack<>();
    // Fallback in-memory storage when DB is unavailable
    private List<Patient> inMemoryPatients = new ArrayList<>();
    public void addPatient(String name, int age) {
        String token = generateNextToken();

        try (Connection conn = DBConnection.getConnection()) {
            String sql = "INSERT INTO patients (name, age, token) VALUES (?, ?, ?)";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, name);
            stmt.setInt(2, age);
            stmt.setString(3, token);
            stmt.executeUpdate();

            Patient p = new Patient(name, age, token);
            undoStack.push(p);
            System.out.println("Patient added (DB): " + token);
        } catch (SQLException e) {
            // Log and fall back to in-memory storage so the kiosk can continue
            e.printStackTrace();
            Patient p = new Patient(name, age, token);
            inMemoryPatients.add(p);
            undoStack.push(p);
            System.out.println("Patient added (in-memory fallback): " + token);
        }
    }

    public void deletePatientByToken(String token) {
        try (Connection conn = DBConnection.getConnection()) {
            String sql = "DELETE FROM patients WHERE token = ?";
            PreparedStatement stmt = conn.prepareStatement(sql);
            stmt.setString(1, token);
            int rows = stmt.executeUpdate();
            if (rows > 0) {
                System.out.println("Patient with token " + token + " deleted.");
            } else {
                System.out.println("No patient found with token " + token);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public void undoLastEntry() {
        if (undoStack.isEmpty()) {
            System.out.println("No patient to undo.");
            return;
        }
        Patient last = undoStack.pop();
        deletePatientByToken(last.getToken());
    }

    public void showAllPatients() {
        try (Connection conn = DBConnection.getConnection()) {
            String sql = "SELECT * FROM patients";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            boolean found = false;
            while (rs.next()) {
                found = true;
                System.out.println("Token: " + rs.getString("token") + ", Name: " + rs.getString("name") + ", Age: " + rs.getInt("age"));
            }
            if (!found) {
                System.out.println("Queue is empty.");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private String generateNextToken() {
        String prefix = "HOS";
        int nextNumber = 1;
        try (Connection conn = DBConnection.getConnection()) {
            String sql = "SELECT token FROM patients ORDER BY token DESC LIMIT 1";
            Statement stmt = conn.createStatement();
            ResultSet rs = stmt.executeQuery(sql);

            if (rs.next()) {
                String lastToken = rs.getString("token");
                int lastNum = Integer.parseInt(lastToken.substring(3));
                nextNumber = lastNum + 1;
            }
        } catch (SQLException e) {
            // If DB query fails, log and fall back to default numbering
            e.printStackTrace();
        }

        return prefix + String.format("%03d", nextNumber);
    }
}
