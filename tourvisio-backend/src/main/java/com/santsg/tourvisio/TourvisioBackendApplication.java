package com.santsg.tourvisio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import java.io.File;
import java.nio.file.Files;
import java.util.List;

@SpringBootApplication
@org.springframework.scheduling.annotation.EnableScheduling
public class TourvisioBackendApplication {

	static {
		try {
			File envFile = new File(".env");
			if (envFile.exists()) {
				List<String> lines = Files.readAllLines(envFile.toPath());
				for (String line : lines) {
					line = line.trim();
					if (line.isEmpty() || line.startsWith("#")) {
						continue;
					}
					int eqIdx = line.indexOf('=');
					if (eqIdx > 0) {
						String key = line.substring(0, eqIdx).trim();
						String val = line.substring(eqIdx + 1).trim();
						if (val.startsWith("\"") && val.endsWith("\"")) {
							val = val.substring(1, val.length() - 1);
						} else if (val.startsWith("'") && val.endsWith("'")) {
							val = val.substring(1, val.length() - 1);
						}
						System.setProperty(key, val);
					}
				}
			}
		} catch (Exception e) {
			System.err.println("Failed to load .env file: " + e.getMessage());
		}
	}

	public static void main(String[] args) {
		SpringApplication.run(TourvisioBackendApplication.class, args);
	}

}

