package com.example.MedRational;

import com.example.MedRational.Config.EnvironmentFiles;
import io.github.cdimascio.dotenv.Dotenv;
import io.github.cdimascio.dotenv.DotenvEntry;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

@SpringBootApplication
// Runs WorkshopProjectServiceImpl.processAutomaticStateTransitions (status changes and reminders)
@EnableScheduling
public class MedRationalApplication {

	public static void main(String[] args) {
		String profile = EnvironmentFiles.resolveProfile(args, System.getenv(), System.getProperties());

		// .env.dev, .env.uat or .env.prod. dev still falls back to a plain .env
		String fileName = EnvironmentFiles.envFileName(profile);
		if (profile.equals(EnvironmentFiles.DEFAULT_PROFILE) && !Files.exists(Path.of(fileName))) {
			fileName = ".env";
		}

		Map<String, String> fileValues = new LinkedHashMap<>();
		for (DotenvEntry entry : Dotenv.configure().filename(fileName).ignoreIfMissing().load()
				.entries(Dotenv.Filter.DECLARED_IN_ENV_FILE)) {
			fileValues.put(entry.getKey(), entry.getValue());
		}
		EnvironmentFiles.valuesToApply(fileValues, System.getenv()).forEach(System::setProperty);
		System.setProperty("spring.profiles.active", profile);

		SpringApplication.run(MedRationalApplication.class, args);
	}

}
