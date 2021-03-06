package de.geosearchef;

import com.google.gson.Gson;
import de.geosearchef.scanner.ServiceScanner;
import de.geosearchef.scanner.SystemsScanner;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.login.LoginException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
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

		SystemsScanner.init();
		SystemsScanner.startPeriodicScanner();
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
		JDABuilder builder = JDABuilder.createDefault(new String(Files.readAllBytes(Paths.get("token"))))
				.enableIntents(GatewayIntent.GUILD_MEMBERS) // has to be enabled in discord developer panel
				.setMemberCachePolicy(MemberCachePolicy.ALL)
				.addEventListeners(new Listener());

		logger.debug("Starting bot...");
		try {
			JDA jda = builder.build();
		} catch(LoginException e) {
			logger.error("Could not create bot.", e);
			System.exit(1);
		}

		logger.info("Started.");
	}


	private static class Listener extends ListenerAdapter {

		@Override
		public void onMessageReceived(MessageReceivedEvent event) {
			if(event.getAuthor().isBot()) {
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


			Arrays.stream(config.getCommands())
					.filter(c -> c.getType().equals("serverStatus"))
					.filter(c -> Arrays.stream(c.getCmd()).anyMatch(s -> s.equalsIgnoreCase(messageIn))
					|| (c.getPredicate() != null && c.getPredicate().evaluate(messageIn) && Arrays.stream(c.getExclude()).noneMatch(it -> messageIn.toLowerCase().contains(it))))
					.findFirst()
					.ifPresent(c -> {
						event.getChannel().sendMessage(getSystemsStatusMessage()).queue();
					});

			Member guildMember = event.getGuild().getMember(event.getMessage().getAuthor());

			if (!event.getChannel().getName().equals("technical-help") || guildMember != null &&
					(guildMember.getRoles().stream().anyMatch(it -> it.getName().equals("Developer"))
							|| guildMember.getRoles().stream().anyMatch(it -> it.getName().equals("Tech Support"))
							|| guildMember.getRoles().stream().anyMatch(it -> it.getName().contains("Modder"))
							|| guildMember.getRoles().stream().anyMatch(it -> it.getName().contains("Moderator"))
							|| guildMember.getRoles().stream().anyMatch(it -> it.getName().contains("Mapper")))) {
				return;
			}

			//Check for triggered predicate
			Arrays.stream(config.getCommands())
					.filter(c -> c.getType().equals("echo"))
					.filter(c -> ! notifiedUsers.get(c).contains(event.getAuthor().getName()))
					.filter(c -> c.getPredicate() != null)
					.filter(c -> c.getPredicate().evaluate(messageIn))
					.filter(c -> c.getExclude() == null || Arrays.stream(c.getExclude()).noneMatch(it -> messageIn.toLowerCase().contains(it)))
					.findFirst()
					.ifPresent(cmd -> {
						event.getChannel()
								.getHistory()
								.retrievePast(cmd.getUserSentLimit() == null ? 1 : cmd.getUserSentLimit().getOutOf())
								.submit()
								.thenAccept(previousMessages -> {
							if(cmd.getUserSentLimit() != null && previousMessages.stream().filter(m -> m.getAuthor().getId().equals(event.getMessage().getAuthor().getId())).count() > cmd.getUserSentLimit().getMax()) {
								logger.info("Message send cancelled due to too many previous messages from this user");
							} else {
								event.getChannel().sendMessage(String.format(cmd.getResponse(), "<@" + event.getAuthor().getId() + "> ")).queue();
								notifiedUsers.get(cmd).add(event.getAuthor().getName());
							}
						});
					});
		}
	}

	public static String getSystemsStatusMessage() {
		StringBuilder sb = new StringBuilder();

		sb.append("**Is the server down?**\n");

		sb.append("**");
		long countOfReachableSystems = SystemsScanner.services.stream().filter(ServiceScanner::isUp).count();
		if(countOfReachableSystems == SystemsScanner.services.size()) {
			sb.append("No, all FAF systems are operational.");
		} else if(countOfReachableSystems == 0) {
			sb.append("Yes, I couldn't reach any FAF service.");
		} else {
			sb.append("Yes, some systems couldn't be reached.");
		}

		sb.append("**\n\n");
		SystemsScanner.services.forEach(s -> {
			String emoji = s.isUp() ? ":white_check_mark:" : ":x:";
			sb.append(String.format("%s %s",
					emoji,
					s.getRepresentation()
			));

			if(!s.isUp()) {
				sb.append(String.format("    (last reached %s)",
						s.getLastResponse() == null ? "\"never\"" : String.format("%d seconds ago", Duration.between(s.getLastResponse(), Instant.now()).toMillis() / 1000)
				));
			}

			sb.append("\n");
		});

		sb.append("\n");
		sb.append(String.format("(scanned %d seconds ago)", Duration.between(SystemsScanner.lastScan, Instant.now()).toMillis() / 1000));

		return sb.toString();
	}
}
