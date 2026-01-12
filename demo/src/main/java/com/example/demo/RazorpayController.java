package com.example.demo;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/razorpay")
@CrossOrigin(origins = "*")
public class RazorpayController {

    @Value("${razorpay.key_id:}")
    private String keyId;

    @Value("${razorpay.key_secret:}")
    private String keySecret;

    private final ObjectMapper mapper = new ObjectMapper();

    @PostMapping("/create-order")
    public ResponseEntity<String> createOrder(@RequestBody Map<String, Object> body) throws Exception {
        // Expect { amount: number (INR), currency?: 'INR', receipt?: 'rcpt_1' }
        double amountInr = body.getOrDefault("amount", 0) instanceof Number ? ((Number) body.get("amount")).doubleValue() : Double.parseDouble(body.get("amount").toString());
        int amountPaise = (int) Math.round(amountInr * 100);
        String currency = (String) body.getOrDefault("currency", "INR");
        String receipt = (String) body.getOrDefault("receipt", "rcpt_" + System.currentTimeMillis());

        Map<String, Object> payload = new HashMap<>();
        payload.put("amount", amountPaise);
        payload.put("currency", currency);
        payload.put("receipt", receipt);
        payload.put("payment_capture", 1);

        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            // Return a mock order so frontend can proceed in demo mode
            Map<String, Object> mock = new HashMap<>();
            mock.put("id", "order_mock_" + System.currentTimeMillis());
            mock.put("amount", amountPaise);
            mock.put("currency", currency);
            mock.put("key", keyId);
            return ResponseEntity.ok(mapper.writeValueAsString(mock));
        }

        String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.razorpay.com/v1/orders"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + auth)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        // Return backend response directly
        return ResponseEntity.status(resp.statusCode()).body(resp.body());
    }

    @GetMapping("/public-key")
    public ResponseEntity<?> publicKey() {
        if (keyId == null || keyId.isBlank()) {
            return ResponseEntity.ok(Map.of("key", ""));
        }
        return ResponseEntity.ok(Map.of("key", keyId));
    }

    @PostMapping("/create-payment-link")
    public ResponseEntity<String> createPaymentLink(@RequestBody Map<String, Object> body) throws Exception {
        // Minimal implementation to create a payment link (test keys required)
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            Map<String, Object> mock = new HashMap<>();
            mock.put("short_url", "https://rzp.io/i/mock-payment-link");
            return ResponseEntity.ok(mapper.writeValueAsString(mock));
        }

        Map<String, Object> payload = new HashMap<>();
        // Razorpay expects amount in paise
        double amountInr = body.getOrDefault("amount", 0) instanceof Number ? ((Number) body.get("amount")).doubleValue() : Double.parseDouble(body.get("amount").toString());
        int amountPaise = (int) Math.round(amountInr * 100);
        payload.put("amount", amountPaise);
        payload.put("currency", body.getOrDefault("currency", "INR"));
        Map<String, String> customer = new HashMap<>();
        if (body.containsKey("customer")) {
            customer.put("name", String.valueOf(((Map) body.get("customer")).getOrDefault("name", "")));
            customer.put("email", String.valueOf(((Map) body.get("customer")).getOrDefault("email", "")));
            customer.put("contact", String.valueOf(((Map) body.get("customer")).getOrDefault("contact", "")));
        }
        payload.put("customer", customer);

        String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.razorpay.com/v1/payment_links"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + auth)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                .build();

        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        return ResponseEntity.status(resp.statusCode()).body(resp.body());
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> body) throws Exception {
        // body should contain: razorpay_order_id, razorpay_payment_id, razorpay_signature
        String orderId = body.get("razorpay_order_id");
        String paymentId = body.get("razorpay_payment_id");
        String signature = body.get("razorpay_signature");

        if (orderId == null || paymentId == null || signature == null) {
            return ResponseEntity.badRequest().body(Map.of("status", "BAD_REQUEST"));
        }

        if (keySecret == null || keySecret.isBlank()) {
            // In demo mode accept any signature
            return ResponseEntity.ok(Map.of("status", "VERIFIED_DEMO"));
        }

        String payload = orderId + "|" + paymentId;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(keySecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        String expected = bytesToHex(digest);

        // Razorpay gives signature as hex; however sometimes uses base64 — try both
        String sigLower = signature.toLowerCase();
        if (expected.equalsIgnoreCase(sigLower) || Base64.getEncoder().encodeToString(digest).equals(signature)) {
            return ResponseEntity.ok(Map.of("status", "VERIFIED"));
        }
        return ResponseEntity.status(400).body(Map.of("status", "INVALID_SIGNATURE"));
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}
