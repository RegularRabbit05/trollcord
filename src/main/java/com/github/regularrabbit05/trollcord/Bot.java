package com.github.regularrabbit05.trollcord;

import club.minnced.discord.jdave.interop.JDaveSessionFactory;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.audio.AudioModuleConfig;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.managers.AudioManager;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Bot extends ListenerAdapter implements Runnable {
    public static void main(String[] args) throws InterruptedException, IOException {
        final String token = System.getenv("BOT_TOKEN");
        final String url = System.getenv("BOT_URL");
        final String activity = System.getenv("BOT_ACTIVITY") == null ? "" : System.getenv("BOT_ACTIVITY");
        final String chance = System.getenv("BOT_CHANCE") == null ? "100" : System.getenv("BOT_CHANCE");
        final String trigger = System.getenv("BOT_TRIGGER").isBlank() ? null : System.getenv("BOT_TRIGGER");

        if (token == null || url == null || token.isBlank() || url.isBlank() || chance.isBlank()) {
            System.err.println("ENV: Bot token or bot url is empty");
            return;
        }

        int chanceInt;
        try {
            chanceInt = Integer.parseInt(chance);
            if (chanceInt <= 0) throw new NumberFormatException();
        } catch (Exception ignored) {
            System.err.println("Env: Bot change has been specified but is not an integer");
            return;
        }

        final Bot bot = new Bot(token, url, activity, chanceInt, trigger);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            LoggerFactory.getLogger(Bot.class).info("Shutting down...");
            bot.stop();
        }));

        bot.awaitShutdown();
    }

    private final JDA jda;
    private final ScheduledExecutorService scheduler;
    private final byte[] file;
    private final Random random;
    private final int chance;
    private final String trigger;
    public Bot(String token, String url, String activity, int chance, String trigger) throws IOException {
        try (InputStream in = URI.create(url).toURL().openStream()) {
            this.file = in.readAllBytes();
        }

        this.trigger = trigger;
        this.chance = chance;
        this.random = new Random();
        this.jda = JDABuilder.createDefault(token).enableCache(CacheFlag.VOICE_STATE)
                .enableIntents(GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT).addEventListeners(this)
                .disableCache(CacheFlag.MEMBER_OVERRIDES)
                .setAudioModuleConfig(new AudioModuleConfig().withDaveSessionFactory(new JDaveSessionFactory()))
                .setActivity(Activity.customStatus(activity))
                .setStatus(OnlineStatus.ONLINE)
                .build();
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.scheduler.scheduleAtFixedRate(this, 15, 30, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        if (random.nextInt(chance) != 0) return;
        this.jda.getGuilds().parallelStream().forEach(this::doForGuild);
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (!event.isFromGuild() || event.isWebhookMessage() || event.getAuthor().isSystem() || event.getAuthor().isBot()) return;
        if (event.getMessage().getContentStripped().equalsIgnoreCase(trigger)) doForGuild(event.getGuild());
    }

    private void doForGuild(final Guild g) {
        g.getVoiceChannels().stream()
                .filter(vc -> !vc.getMembers().isEmpty())
                .max(Comparator.comparingInt(vc -> vc.getMembers().size()))
                .ifPresent(vc -> {
                    final AudioManager audioManager = g.getAudioManager();
                    audioManager.closeAudioConnection();
                    audioManager.setSendingHandler(new StreamHandler(this::get20MsFile, audioManager::closeAudioConnection));
                    audioManager.openAudioConnection(vc);
                });
    }

    private byte[] get20MsFile(Integer i) {
        if (i >= file.length || i < 0) return null;
        final int CHUNK_SIZE = 3840;
        final int end = Math.min(file.length, i + CHUNK_SIZE);
        byte[] chunk = Arrays.copyOfRange(file, i, end);
        if (chunk.length < CHUNK_SIZE) chunk = Arrays.copyOf(file, CHUNK_SIZE);
        return chunk;
    }

    public void stop() {
        jda.shutdown();
        scheduler.shutdown();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public void awaitShutdown() throws InterruptedException {
        jda.awaitShutdown();
        scheduler.awaitTermination(1, TimeUnit.MINUTES);
    }
}
