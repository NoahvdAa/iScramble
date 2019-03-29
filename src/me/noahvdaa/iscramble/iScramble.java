package me.noahvdaa.iscramble;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.logging.Logger;

import org.apache.commons.lang.StringUtils;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

import me.noahvdaa.metrics.Metrics;

public class iScramble extends JavaPlugin implements Listener {
	
	private FileConfiguration pluginConfig;
	private FileConfiguration wordList;
	private File wordListFile;
	
	private Logger logger;
	private Metrics bstats;
	
	private String unscrambledWord;
	private Long lastGame = System.currentTimeMillis() / 100L;
	private Long startTime = System.currentTimeMillis() / 100L;
	private boolean gameRunning = false;
	

	public void onEnable() {
		// Set the logger
		logger = getLogger();
		
		// Create and load plugin config.
		saveDefaultConfig();
		pluginConfig = getConfig();
		
		// Create and load word list.
		createWordList();
		if(wordList.getStringList("words").size() == 0) {
			logger.warning("ERROR: The word list cannot be empty.");
			getServer().getPluginManager().disablePlugin(this);
		}
		int wordcount = wordList.getStringList("words").size();
		if(wordcount == 1) {
			logger.info("Loaded 1 word.");
		}else {
			logger.info("Loaded "+wordcount+" words.");
		}
		
		// Enable Metrics
		bstats = new Metrics(this);
		
		// bStats custom Charts
		bstats.addCustomChart(new Metrics.SingleLineChart("words", new Callable<Integer>() {
			@Override
			public Integer call() throws Exception {
				return wordList.getStringList("words").size();
			}
		}));
		
		// Register events
		getServer().getPluginManager().registerEvents(this, this);
		
		// Start main game scheduler.
		BukkitScheduler scheduler = getServer().getScheduler();
        scheduler.scheduleSyncRepeatingTask(this, new Runnable() {
            @Override
            public void run() {
            	if(getServer().getOnlinePlayers().size() == 0) {
            		if(gameRunning) {
            			gameRunning = false;
            			// Cancel game because nobody is online.
            		}
            		return;
            	}
            	
            	Random rand = new Random();
            	
            	long currentTime = System.currentTimeMillis() / 100L;
                if(currentTime-(pluginConfig.getInt("delay",30)*10) > lastGame) {
                	if(gameRunning) {
                		if(currentTime-300 > startTime) {
                			// Nobody answered in time.
                			gameRunning = false;
                			lastGame = currentTime;
                			
                			String msg = pluginConfig.getString("noAnswerMessage");
                    		msg = ChatColor.translateAlternateColorCodes('&', msg);
                    		msg = msg.replaceAll("%word%", unscrambledWord);
                    		
                    		Bukkit.broadcastMessage(msg);
                		}
                	}else {
                		// Start the game!
                		gameRunning = true;
                		
                		unscrambledWord = wordList.getStringList("words").get(rand.nextInt(wordList.getStringList("words").size()));
                		
                		int i = 0;
                		String[] scrambled = new String[unscrambledWord.split(" ").length];
                		for(String word : unscrambledWord.split(" ")) {
                			scrambled[i] = getScrambled(word);
                			i++;
                		}
                		
                		String msg = pluginConfig.getString("message");
                		msg = ChatColor.translateAlternateColorCodes('&', msg);
                		msg = msg.replaceAll("%word%", StringUtils.join(scrambled, " "));
                		
                		Bukkit.broadcastMessage(msg);
                		
                		startTime = currentTime;
                		
                	}
                }
            }
        }, 5L, 5L);
	}
	
	@EventHandler
	public void chatEvent(AsyncPlayerChatEvent e) {
		if(!gameRunning) return;
		if(!e.getMessage().equals(unscrambledWord)) return;
		Player p = e.getPlayer();
		
		// Someone entered the right answer!
		// Cancel the event so we can display our own message.
		e.setCancelled(true);
		
		// Reset the "lastGame" & gameRunning value.
		long currentTime = System.currentTimeMillis() / 100L;
		lastGame = currentTime;
		gameRunning = false;
		
		long timeItTook = (currentTime-startTime) / 10;
		
		String msg = pluginConfig.getString("winMessage");
		msg = ChatColor.translateAlternateColorCodes('&', msg);
		msg = msg.replaceAll("%player%", p.getName());
		msg = msg.replaceAll("%seconds%", Long.toString(timeItTook));
		msg = msg.replaceAll("%word%", unscrambledWord);
		
		Bukkit.broadcastMessage(msg);
		
		// We have to run the commands synchronous.
		BukkitScheduler scheduler = getServer().getScheduler();
		scheduler.runTask(this, new Runnable() {
			@Override
			public void run() {
				ConsoleCommandSender console = getServer().getConsoleSender();
				for (String command : pluginConfig.getStringList("rewards")) {
					command = command.replaceAll("%player%", p.getName()).replaceAll("%death_x%", p.getName());
					
					Bukkit.dispatchCommand(console, command);
				}
			}
		});
	}

	private String getScrambled(String s) {
		String[] scram = s.split("");
		List<String> letters = Arrays.asList(scram);
		Collections.shuffle(letters);
		StringBuilder sb = new StringBuilder(s.length());
		for (String c : letters) {
			sb.append(c);
		}
		return sb.toString();
	}
	
	private void createWordList() {
        wordListFile = new File(getDataFolder(), "words.yml");
        if (!wordListFile.exists()) {
        	wordListFile.getParentFile().mkdirs();
            saveResource("words.yml", false);
         }

        wordList = new YamlConfiguration();
        try {
            wordList.load(wordListFile);
        } catch (IOException | InvalidConfigurationException e) {
        	logger.warning("ERROR while loading world list:");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }
}