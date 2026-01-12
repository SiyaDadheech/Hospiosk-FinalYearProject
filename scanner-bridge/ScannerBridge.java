// Minimal local scanner bridge scaffold. Replace mock capture with vendor SDK calls.
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.io.OutputStream;
import java.util.Base64;

public class ScannerBridge {
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(5000), 0);
        server.createContext("/capture", exchange -> {
            // TODO: call the Mantra SDK capture method here and return real template bytes
            byte[] template = ("mock-template-" + System.currentTimeMillis()).getBytes();

            String base64 = Base64.getEncoder().encodeToString(template);
            String resp = "{\"template\":\"" + base64 + "\"}";
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.getBytes().length);
            OutputStream os = exchange.getResponseBody();
            os.write(resp.getBytes());
            os.close();
        });
        server.start();
        System.out.println("ScannerBridge running on http://localhost:5000/capture");
    }
}
