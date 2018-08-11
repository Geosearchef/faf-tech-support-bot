package de.geosearchef;

import com.google.gson.Gson;
import net.dv8tion.jda.core.AccountType;
import net.dv8tion.jda.core.JDABuilder;
import net.dv8tion.jda.core.events.message.MessageReceivedEvent;
import net.dv8tion.jda.core.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class TechSupportBot {

	private static Logger logger = LoggerFactory.getLogger(TechSupportBot.class);
	private static Gson gson = new Gson();

	private static Map<Command, Set<String>> notifiedUsers = new HashMap<>();
	private static long lastCommandUsage = 0;

	private static Config config;

	public static void main(String args[]) throws IOException {
		loadConfig();

		connectToDiscord();
	}

	private static void loadConfig() {
		logger.debug("Loading commands from json...");
		try {
			config = gson.fromJson(Files.readAllLines(Paths.get("commands.json")).stream().collect(Collectors.joining()), Config.class);
		} catch (IOException e) {
			logger.error("Could not load commands.", e);
		}
		logger.info("Found {} commands.", config.getCommands().length);

		Arrays.stream(config.getCommands()).forEach(c -> notifiedUsers.put(c, new HashSet<>()));
	}

	private static void connectToDiscord() throws IOException {
		JDABuilder builder = new JDABuilder(AccountType.BOT);
		//Whoever kidnapped my bot, you're not funny ;)
		builder.setToken(new String(Files.readAllBytes(Paths.get("token"))));
		builder.addEventListener(new Listener());

		logger.debug("Starting bot...");
		try {
			builder.build();
		} catch(LoginException e) {
			logger.error("Could not create bot.", e);
			System.exit(1);
		}

		logger.info("Started.");
	}


	private static class Listener extends ListenerAdapter {
		@Override
		public void onMessageReceived(MessageReceivedEvent event) {
			if(event.getAuthor().isBot() || !event.getChannel().getName().equals("technical-help")) {
				return;
			}

			String messageIn = event.getMessage().getContentRaw().toLowerCase();

			//Check for command
			Arrays.stream(config.getCommands())
					.filter(c -> c.getType().equals("echo"))
					.filter(c -> Arrays.stream(c.getCmd()).anyMatch(s -> s.equalsIgnoreCase(messageIn)))
					.findFirst()
					.ifPresent(cmd -> {
						if(System.currentTimeMillis() - lastCommandUsage < config.getCommandCooldown()) {
							return;
						}
						event.getChannel().sendMessage(String.format(cmd.getResponse(), "")).queue();
						lastCommandUsage = System.currentTimeMillis();
					});

			//Check for triggered predicate
			Arrays.stream(config.getCommands())
					.filter(c -> c.getType().equals("echo"))
					.filter(c -> ! notifiedUsers.get(c).contains(event.getAuthor().getName()))
					.filter(c -> c.getPredicate() != null)
					.filter(c -> c.getPredicate().evaluate(messageIn))
					.findFirst()
					.ifPresent(cmd -> {
						event.getChannel().sendMessage(String.format(cmd.getResponse(), "<@" + event.getAuthor().getId() + "> ")).queue();
						notifiedUsers.get(cmd).add(event.getAuthor().getName());
					});

//			System.out.printf("Message from %s in %s: %s", event.getAuthor().getName(), event.getChannel().getName(), event.getMessage());
		}
	}
}
