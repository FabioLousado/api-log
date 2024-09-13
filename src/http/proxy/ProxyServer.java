package http.proxy;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import io.github.cdimascio.dotenv.Dotenv;

public class ProxyServer {
	public static void main(String[] args) throws IOException {
		
		Dotenv dotenv = Dotenv.load();

        int port = Integer.valueOf(dotenv.get("PORT"));

        
		// Start the server on port 8081
		HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
		server.createContext("/", new ProxyHandler());
		server.setExecutor(null); // Creates a default executor
		server.start();
		System.out.println("Proxy server started on port " + port + " ...");
	}
}

class Logger {

	private static final String LOG_FILE_PATH = "logfile.txt"; // Path to your log file
	private static final Set<String> TARGET_URLS = new HashSet<>();

	static {
		// Add the URLs you want to log to this set
		TARGET_URLS.add("/utilisateur/connexion");
		TARGET_URLS.add("/file/archive");
		TARGET_URLS.add("/client/relation");

	}

	public static void logRequest(String url, String mail) {
		
		if(Objects.nonNull(mail) || url.contains("connexion")) {
			if (TARGET_URLS.stream().anyMatch(t -> url.startsWith(t))) {
				LocalDateTime now = LocalDateTime.now();
				String logEntry = String.format("Request intercepted: %s - %s by %s", now, url, mail);

				try (BufferedWriter writer = new BufferedWriter(new FileWriter(LOG_FILE_PATH, true))) {
					writer.write(logEntry);
					writer.newLine(); // Add a new line after each log entry
				} catch (IOException e) {
					e.printStackTrace(); // Handle exceptions as needed
				}
			}
		}
	}
}

class ProxyHandler implements HttpHandler {
	@Override
	public void handle(HttpExchange exchange) throws IOException {
		
		var dotenv = Dotenv.load();

        // Retrieve the backend URL from the environment variables
        String backendUrl = dotenv.get("URL", "http://localhost:8080/api");
		String method = exchange.getRequestMethod();
		URI requestURI = exchange.getRequestURI();
		String api = requestURI.toString();

		// mail de l'utilisateur connecté
		var mail = exchange.getRequestHeaders().getFirst("Mail");

		Logger.logRequest(api, mail);
		
		var fullUrl = backendUrl + api;

		// Handle CORS Preflight Request
		if ("OPTIONS".equalsIgnoreCase(method)) {
			handlePreflight(exchange);
			return;
		}

		// Forward the request to the actual backend server
		HttpURLConnection connection = (HttpURLConnection) new URL(fullUrl).openConnection();
		connection.setRequestMethod(method);

		// Copy headers from the original request
		exchange.getRequestHeaders()
				.forEach((key, value) -> connection.setRequestProperty(key, String.join(",", value)));

		if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
			connection.setDoOutput(true);
			try (OutputStream os = connection.getOutputStream()) {
				byte[] input = exchange.getRequestBody().readAllBytes();
				os.write(input);
				os.flush();
			}
		}

		// Get the response from the backend server
		int responseCode = connection.getResponseCode();
		InputStream responseStream = responseCode < HttpURLConnection.HTTP_BAD_REQUEST ? connection.getInputStream()
				: connection.getErrorStream();

		// Set CORS headers
		exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "http://localhost:4200"); // Adjust as needed
																									// for security
		exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
		exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, Mail");
		exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");

		// Set the content type and content length
		String contentType = connection.getHeaderField("Content-Type");
		String contentDisposition = connection.getHeaderField("Content-Disposition");
		if (contentType != null) {
			exchange.getResponseHeaders().add("Content-Type", contentType);
		}
		if (contentDisposition != null) {
			exchange.getResponseHeaders().add("Content-Disposition", contentDisposition);
		}

		// Send response with the correct content length
		int contentLength = connection.getContentLength();
		exchange.sendResponseHeaders(responseCode, contentLength == -1 ? 0 : contentLength);

		// Stream the response bytes back to the client
		try (OutputStream os = exchange.getResponseBody()) {
			byte[] buffer = new byte[8192];
			int bytesRead;
			while ((bytesRead = responseStream.read(buffer)) != -1) {
				os.write(buffer, 0, bytesRead);
			}
		} finally {
			responseStream.close();
		}
	}

	private void handlePreflight(HttpExchange exchange) throws IOException {
		// Set CORS headers for the preflight response
		exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "http://localhost:4200"); // Adjust as needed
																									// for security
		exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
		exchange.getResponseHeaders().add("Access-Control-Allow-Headers",
				"Content-Type, Authorization, Content-Length, X-Requested-With, Mail");
		exchange.getResponseHeaders().add("Access-Control-Allow-Credentials", "true");

		// Send an empty response with 200 OK status
		exchange.sendResponseHeaders(200, -1);
	}
}
