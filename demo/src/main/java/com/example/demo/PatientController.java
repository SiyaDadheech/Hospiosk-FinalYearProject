package com.example.demo;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.example.Patient;
import com.example.QueueManager;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*") // Allows your Frontend to connect
public class PatientController {

    QueueManager manager = new QueueManager();

    // 1. Fetch Patient via Aadhar (Simulated UIDAI Fetch)
    @GetMapping("/fetch-aadhar/{aadharNum}")
    public Patient getAadharDetails(@PathVariable String aadharNum) {
        // Simulate UIDAI lookup with a small demo map for better-looking names
        if (aadharNum == null) {
            return new Patient("Unknown", 0, "INVALID");
        }
        String cleaned = aadharNum.replaceAll("\\s+", "");
        if (cleaned.length() < 4) {
            return new Patient("Unknown", 0, "INVALID");
        }

        // Demo mapping: add known Aadhaar numbers here to return realistic names
        Map<String, Patient> demo = new HashMap<>();
        demo.put("123456789012", new Patient("Rahul Kumar", 28, "PENDING"));
        demo.put("987654321098", new Patient("Anita Sharma", 34, "PENDING"));
        demo.put("111122223333", new Patient("Suresh Patel", 45, "PENDING"));
        demo.put("444455556666", new Patient("Meena Gupta", 29, "PENDING"));    
        demo.put("555566667777", new Patient("Amit Joshi", 38, "PENDING")); 
        demo.put("888899990000", new Patient("Priya Reddy", 31, "PENDING"));

        if (demo.containsKey(cleaned)) {
            return demo.get(cleaned);
        }

        // Fallback: create a readable name from the last 4 digits
        String suffix = cleaned.substring(Math.max(0, cleaned.length() - 4));
        return new Patient("Patient " + suffix, 30, "PENDING");
    }

    // 1b. Biometric authentication endpoint (mock)
    // Accepts a JSON body with { "template": "<base64-or-string>", "mode": "mock|real" }
    // In `mock` mode this returns a demo Patient. In `real` mode you'll need to
    // integrate with UIDAI-authenticated endpoints (server-to-server), following
    // UIDAI rules: encrypted PID block, licensed device SDK, and registered client keys.
    @PostMapping("/biometric/authenticate")
    public ResponseEntity<?> authenticateBiometric(@RequestBody Map<String, String> payload) {
        if (payload == null || !payload.containsKey("template")) {
            return ResponseEntity.badRequest().body("{\"status\":\"BAD_REQUEST\",\"message\":\"Missing template\"}");
        }

        String template = payload.get("template");
        String mode = payload.getOrDefault("mode", "mock");

        // Mock mode: deterministically map a fingerprint template to one of demo patients
        if ("mock".equalsIgnoreCase(mode)) {
            Map<String, Patient> demo = new HashMap<>();
            demo.put("123456789012", new Patient("Rahul Kumar", 28, "PENDING"));
            demo.put("987654321098", new Patient("Anita Sharma", 34, "PENDING"));
            demo.put("111122223333", new Patient("Suresh Patel", 45, "PENDING"));
            demo.put("444455556666", new Patient("Meena Gupta", 29, "PENDING"));
            demo.put("555566667777", new Patient("Amit Joshi", 38, "PENDING"));
            demo.put("888899990000", new Patient("Priya Reddy", 31, "PENDING"));

            List<Patient> list = new ArrayList<>(demo.values());
            int idx = Math.abs(template.hashCode()) % list.size();
            Patient matched = list.get(idx);
            // Return matched patient object
            return ResponseEntity.ok(matched);
        }

        // REAL integration placeholder: implement server-side UIDAI auth here.
        // NOTE: Direct access to UIDAI APIs requires registration, HSM-backed keys,
        // secure PID generation on certified devices, and following UIDAI terms.
        // Do not attempt to POST raw fingerprints to UIDAI from the browser.
        return ResponseEntity.status(501).body("{\"status\":\"NOT_IMPLEMENTED\",\"message\":\"Integrate UIDAI server-side API here using certified device SDK and encrypted PID\"}");
    }

    // 2. Add Patient to SQL (The "Confirm & Pay" step)
    @PostMapping("/add-patient")
    public ResponseEntity<String> addPatient(@RequestBody Map<String, Object> payload) {
        // Accept a generic JSON payload (may include `doctor` object) and parse required fields
        try {
            if (payload == null) {
                return ResponseEntity.badRequest().body("{\"status\":\"BAD_REQUEST\",\"message\":\"Empty payload\"}");
            }
            Object nameObj = payload.get("name");
            Object ageObj = payload.get("age");
            if (nameObj == null || ageObj == null) {
                return ResponseEntity.badRequest().body("{\"status\":\"BAD_REQUEST\",\"message\":\"Missing name or age\"}");
            }
            String name = nameObj.toString();
            int age;
            if (ageObj instanceof Number) {
                age = ((Number) ageObj).intValue();
            } else {
                try {
                    age = Integer.parseInt(ageObj.toString());
                } catch (NumberFormatException nfe) {
                    return ResponseEntity.badRequest().body("{\"status\":\"BAD_REQUEST\",\"message\":\"Invalid age\"}");
                }
            }

            manager.addPatient(name, age);
            return ResponseEntity.ok("{\"status\": \"Success\"}");
        } catch (Exception ex) {
            if (ex instanceof SQLException) {
                SQLException e = (SQLException) ex;
                e.printStackTrace();
                String body = String.format("{\"status\": \"DB_ERROR\", \"message\": \"%s\"}",
                        e.getMessage().replaceAll("\"", "'"));
                return ResponseEntity.status(500).body(body);
            }
            ex.printStackTrace();
            return ResponseEntity.status(500).body("{\"status\":\"ERROR\",\"message\":\"Unexpected server error\"}");
        }
    }
}