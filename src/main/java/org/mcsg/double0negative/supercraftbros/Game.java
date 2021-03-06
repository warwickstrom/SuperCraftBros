/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mcsg.double0negative.supercraftbros;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Random;
import java.util.Set;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitScheduler;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.util.Vector;

import net.milkbowl.vault.economy.Economy;

public class Game {

	public enum State{
		INGAME, LOBBY, DISABLED, WAITING
	}

	private String gameID;
	private int spawnCount;
	private Arena arena;
	private State state;
	private GameMode returnMode;
	private boolean usePercents;
	private int countdown;
	private int ecoValue;
	private int min;
	private int max;
	
	boolean started = false;
	int count = 20;
	int tid = 0;

	private HashMap<Player, Integer> players = new HashMap<Player, Integer>();
	private ArrayList<Player> spectators = new ArrayList<Player>();
	private HashMap<Player, Double> damage = new HashMap<Player, Double>();
	private HashMap<Player, String> pClasses = new HashMap<Player, String>();
	private ArrayList<Player> queue = new ArrayList<Player>();
	
	private BukkitScheduler respawnClock = Bukkit.getScheduler();
	private HashMap<Player, Integer> playerTask = new HashMap<Player, Integer>();

	public Game(String a) {
		this.gameID = a;
		init();
	}

	public void init(){
		FileConfiguration s = SettingsManager.getInstance().getSystemConfig();
		int x = s.getInt("system.arenas." + gameID + ".x1");
		int y = s.getInt("system.arenas." + gameID + ".y1");
		int z = s.getInt("system.arenas." + gameID + ".z1");
		int x1 = s.getInt("system.arenas." + gameID + ".x2");
		int y1 = s.getInt("system.arenas." + gameID + ".y2");
		int z1 = s.getInt("system.arenas." + gameID + ".z2");
		Location max = new Location(SettingsManager.getGameWorld(gameID), Math.max(x, x1), Math.max(y, y1), Math.max(z, z1));
		Location min = new Location(SettingsManager.getGameWorld(gameID), Math.min(x, x1), Math.min(y, y1), Math.min(z, z1));
		
		GameMode gm = GameMode.valueOf(SettingsManager.getInstance().getConfig().getString("return-gamemode"));
		returnMode = gm != null ? gm : GameMode.ADVENTURE;
		Integer eco = SettingsManager.getInstance().getConfig().getInt("winning-economy");
		ecoValue = eco != null ? eco : 1000;
		Boolean percents = SettingsManager.getInstance().getConfig().getBoolean("use-percents");
		usePercents = percents != null ? percents : false;
		Integer count = SettingsManager.getInstance().getConfig().getInt("countdown");
		countdown = count != null ? count : 60;
		
		this.max = SettingsManager.getInstance().getSystemConfig().getInt("system.arenas." + gameID + ".max");
		this.min = SettingsManager.getInstance().getSystemConfig().getInt("system.arenas." + gameID + ".min");

		arena = new Arena(min, max);

		state = State.LOBBY;

		spawnCount = SettingsManager.getInstance().getSpawnCount(gameID);
	}

	public void addPlayer(Player p){
		String game = GameManager.getInstance().getPlayerGameId(p);
		if(state == State.LOBBY && players.size() < max && game == null){
			p.teleport(SettingsManager.getInstance().getGameLobbySpawn(gameID));
			p.getInventory().clear();

			players.put(p , 3);
			damage.put(p, 0D);
			p.setGameMode(GameMode.SURVIVAL);
			p.setHealth(20); p.setFoodLevel(20);

			Message.send(p, ChatColor.YELLOW + "" + ChatColor.BOLD + "Joined arena " + gameID + ". Select a class!");
			msgAll(ChatColor.GREEN + p.getName()+ " joined the game!");
			updateTabAll();
			updateSigns();
			playerTask.put(p, -1);
		}
		else if(state == State.INGAME){
			Message.send(p, ChatColor.RED + "Game already started!");
		}
		else if(players.size() >= max){
			Message.send(p, ChatColor.RED + "Game Full!");
		}
		else if(!(game == null)){
			Message.send(p, ChatColor.RED + "Already in game!");
		}
		else{
			Message.send(p, ChatColor.RED + "Arena is disabled!");
		}
	}
	
	public void addSpectator(Player p){
		if(state == State.INGAME){
			spectators.add(p);
			p.teleport(SettingsManager.getInstance().getSpawnPoint(gameID, 1));
			p.getInventory().clear();
			p.setGameMode(GameMode.SPECTATOR);
			p.setHealth(20); p.setFoodLevel(20);
			updateTab(p);
			Message.send(p, ChatColor.YELLOW + "" + ChatColor.BOLD + "Spectating arena " + gameID + ". Type /scb leave to stop spectating!");
		}
		else if(state == State.LOBBY || state == State.WAITING){
			Message.send(p, ChatColor.RED + "Game hasn't started!");
		}
		else{
			Message.send(p, ChatColor.RED + "Arena is disabled!");
		}
	}
	
	public void removeSpectator(Player p){
		p.teleport(SettingsManager.getInstance().getLobbySpawn());
		p.setGameMode(returnMode);
		final ScoreboardManager m = Bukkit.getScoreboardManager();
		final Scoreboard board = m.getNewScoreboard();
		p.setScoreboard(board);
		spectators.remove(p);
	}
	
	public void updateSigns(){
		for(Location loc : SuperCraftBros.joinSigns.keySet()){
			if(SuperCraftBros.joinSigns.get(loc).equalsIgnoreCase(gameID)){
				Block b = loc.getBlock();
				if(!(b.getState() instanceof Sign)){
					SuperCraftBros.joinSigns.remove(loc);
					String location = loc.getWorld().getName() + ": " + loc.getX() + "," + loc.getBlockY() + "," + loc.getZ();
					Bukkit.getLogger().log(Level.INFO, "[SCB] No sign detected, removing entry at location " + location + " for arena " + gameID);
					continue;
				}
				Sign s = (Sign) b.getState();
				int i1 = players.size();
				if(state == State.LOBBY){
					if(players != null){
						s.setLine(3, ChatColor.GREEN + "" + i1 + " / " + max);
						s.update();
					}else{
						s.setLine(3, ChatColor.GREEN + "0 / " + max);
						s.update();	
					}
				}else if(state == State.INGAME){
					s.setLine(3, ChatColor.YELLOW + "IN-GAME");
					s.update();
				}else{
					s.setLine(3, ChatColor.RED + "DISABLED");
					s.update();	
				}
			}
		}
		GameManager.getInstance().saveSigns();
	}

	public void startGame(){
		if(players.size() < 2){
			msgAll("Not enough players");
			started = false;
			return;
		}
		state = State.INGAME;
		updateSigns();

		for(Player p: players.keySet().toArray(new Player[0])){
			if(pClasses.containsKey(p)){
				spawnPlayer(p);
			}
			else{
				removePlayer(p, false);
				Message.send(p, ChatColor.RED + "You didn't pick a class!");
			}
		}
	}

	public void countdown(int time) {
		count = time;
		Bukkit.getScheduler().cancelTask(tid);
		if (state == State.LOBBY) {
			tid = Bukkit.getScheduler().scheduleSyncRepeatingTask((Plugin) GameManager.getInstance().getPlugin(), new Runnable() {
				public void run() {
					if (count > 0) {
						if (count % 10 == 0) {
							msgAll(ChatColor.BLUE + "Game starting in "+count);
						}
						if (count < 6) {
							msgAll(ChatColor.BLUE + "Game starting in "+count);
						}
						count--;
					} else {
						startGame();
						Bukkit.getScheduler().cancelTask(tid);
					}
				}
			}, 0, 20);
		}
	}
	
	public void addDamage(Player p, double i){
		double d = getDamage(p);
		double nd = d + (i*10);
		damage.put(p, nd);
		updateTabAll();
	}

	public double getDamage(Player p){
		double i = damage.get(p);
		return i;
	}


	public void setPlayerClass(Player player, String playerClass){
		if(player.hasPermission("scb.class." + playerClass) || player.hasPermission("scb.class.*")){
			clearPotions(player);
			Message.send(player, ChatColor.GREEN + "You choose " + playerClass.toUpperCase() + "!");
			//int prev = pClasses.keySet().size();
			pClasses.put(player, playerClass);
			updateTabAll();
			if(!started && pClasses.keySet().size()>= min && players.size() >= min ){
				countdown(countdown);
				started = true;
			}
		}
		else{
			Message.send(player, ChatColor.RED + "You do not have permission for this class!");
		}
	}

	public void killPlayer(final Player p, String msg){
		clearPotions(p);
		if(msg != null) msgAll(ChatColor.GOLD + msg);
		int lives = players.get(p)-1;
		if(lives <= 0){
			playerEliminate(p);
		}
		else{
			players.put(p, lives);
			damage.put(p, 0D);
			msgAll(p.getName() + " has " + lives + " lives left");
			int i = respawnClock.scheduleSyncDelayedTask(GameManager.getInstance().getPlugin(), new Runnable(){
				public void run(){
					playerEliminate(p);
					Message.send(p, "You were eliminated for waiting too long before respawning!");
				}
			}, 200);
			playerTask.put(p, i);
		}
		updateTabAll();
	}

	public void playerEliminate(Player p){
		started = false;
		msgAll(ChatColor.DARK_RED + p.getName() + " has been eliminated!");

		players.remove(p);
		//pClasses.remove(p);
		p.getInventory().clear();
		p.getInventory().setArmorContents(new ItemStack[4]);
		p.updateInventory();
		p.setAllowFlight(false);
		p.setFlying(false);
		clearPotions(p);
		p.setVelocity(new Vector(0, 0, 0));
		addSpectator(p);
		if(players.keySet().size() <= 1 && state == State.INGAME){
			Player pl = null;
			for(Player pl2 : players.keySet()){
				pl = pl2;
			}
			if(!(Bukkit.getPluginManager().getPlugin("Vault") == null)){
				RegisteredServiceProvider<Economy> economyProvider = Bukkit.getServer().getServicesManager().getRegistration(net.milkbowl.vault.economy.Economy.class);
		        if (economyProvider != null) {
		            economyProvider.getProvider().depositPlayer(pl, ecoValue);
		        }
			}
			Bukkit.broadcastMessage(ChatColor.BLUE + pl.getName() + " won Super Craft Bros on arena " + gameID.toUpperCase());
			gameEnd();
		}
		updateTabAll();

	}

	public void clearPotions(Player p){
		for(PotionEffectType e: PotionEffectType.values()){
			if(e != null && p.hasPotionEffect(e))
				p.removePotionEffect(e);
		}
	}

	public void gameEnd(){
		/*for(Entity e:SettingsManager.getGameWorld(gameID).getEntities()){
			if(arena.containsBlock(e.getLocation())){
				e.remove();
			}
		}*/
		final ScoreboardManager m = Bukkit.getScoreboardManager();
		final Scoreboard board = m.getNewScoreboard();
		for(Player p:players.keySet()){
			p.getInventory().clear();
			p.getInventory().setArmorContents(new ItemStack[4]);
			p.updateInventory();
			p.teleport(SettingsManager.getInstance().getLobbySpawn());
			p.setScoreboard(board);
			clearPotions(p);
			p.setGameMode(returnMode);
			p.setFlying(false);
			p.setAllowFlight(false);
		}
		for(Player p:spectators){
			p.teleport(SettingsManager.getInstance().getLobbySpawn());
			p.setScoreboard(board);
			p.setGameMode(returnMode);
			p.setFlying(false);
			p.setAllowFlight(false);
		}
		players.clear();
		pClasses.clear();
		spectators.clear();
		state = State.LOBBY;
		updateSigns();
	}


	public void updateTabAll(){
		for(Player p: players.keySet()){
			updateTab(p);
		}
		for(Player p: spectators){
			updateTab(p);
		}
	}

	public void updateTab(Player p){
		final ScoreboardManager m = Bukkit.getScoreboardManager();
		final Scoreboard board = m.getNewScoreboard();
		final Objective o = board.registerNewObjective("title", "dummy");
		o.setDisplaySlot(DisplaySlot.SIDEBAR);
		o.setDisplayName(ChatColor.GOLD + "SuperCraftBros");
		for(Player pl: players.keySet()){
			if(usePercents){
				Score score = o.getScore(ChatColor.YELLOW + pl.getName() + " [" + Math.round(damage.get(pl)) + "]");
				score.setScore(players.get(pl));
			}else{
				Score score = o.getScore(ChatColor.YELLOW + pl.getName());
				score.setScore(players.get(pl));
			}
			p.setScoreboard(board);
		}
	}

	public void spawnPlayer(Player p){
		if(players.containsKey(p)){
			p.setAllowFlight(true);
			Random r = new Random();
			Location l = SettingsManager.getInstance().getSpawnPoint(gameID, r.nextInt(spawnCount)+1);
			p.teleport(getSafePoint(l));
			setInventory(p);
			if(playerTask.get(p) != -1){
				respawnClock.cancelTask(playerTask.get(p));
			}
		}
	}

	public void setInventory(Player p){
		p.getInventory().clear();
		for(ItemStack i : GameManager.getInstance().classList.get(pClasses.get(p))){
			p.getInventory().addItem(i);
		}
		p.getInventory().setHelmet(GameManager.getInstance().classHelmet.get(pClasses.get(p)));
		p.getInventory().setChestplate(GameManager.getInstance().classChest.get(pClasses.get(p)));
		p.getInventory().setLeggings(GameManager.getInstance().classLeg.get(pClasses.get(p)));
		p.getInventory().setBoots(GameManager.getInstance().classBoots.get(pClasses.get(p)));
		for(PotionEffect e : GameManager.getInstance().classEffects.get(pClasses.get(p))){
			p.addPotionEffect(e);
		}
	}
	
	@SuppressWarnings("deprecation")
	public Location getSafePoint(Location l){
		if(isInVoid(l)){
			while(l.getBlockY() < 256){
				if(l.getBlock().getTypeId() != 0){
					return l.add(0,1,0);
				}
				else{
					l.add(0,1,0);
				}
			}
		}
		return l; //nothing safe at this point
	}

	@SuppressWarnings("deprecation")
	public boolean isInVoid(Location l){
		Location loc = l.clone();
		while(loc.getBlockY() > 0){
			loc.add(0,-1,0);
			if(loc.getBlock().getTypeId() != 0){
				return false;
			}
		}
		return true;
	}
	
	public String getID() {
		return gameID;
	}


	public boolean isBlockInArena(Location v) {
		return arena.containsBlock(v);
	}


	public void addSpawn() {
		spawnCount++;
	}


	public boolean isPlayerActive(Player p) {
		return (players.keySet().contains(p) || spectators.contains(p));
	}
	
	public boolean isPlaying(Player p){
		return players.keySet().contains(p);
	}

	public boolean isSpectating(Player p){
		return spectators.contains(p);
	}

	public boolean isInQueue(Player p) {
		return queue.contains(p);
	}


	public void removeFromQueue(Player p) {
		queue.remove(p);
	}


	public void removePlayer(Player p, boolean b) {
		if(started && state != State.INGAME && players.keySet().size() < min){
			started = false;
			Bukkit.getScheduler().cancelTask(tid);
		}
		players.remove(p);
		p.getInventory().clear();
		p.updateInventory();
		clearPotions(p);
		playerEliminate(p);
		p.teleport(SettingsManager.getInstance().getLobbySpawn());
		final ScoreboardManager m = Bukkit.getScoreboardManager();
		final Scoreboard board = m.getNewScoreboard();
		p.setScoreboard(board);
		msgAll(ChatColor.RED + p.getName() + " left the game!");
		updateSigns();
	}

	public void msgAll(String msg){
		for(Player p: players.keySet()){
			Message.send(p, msg);
		}
		for(Player p: spectators){
			Message.send(p, msg);
		}
	}

	public void enable(){
		disable();
		state = State.LOBBY;
	}

	public void disable() {
		msgAll(ChatColor.RED + "Game Disabled");
		gameEnd();
		state = State.DISABLED;
	}


	public State getState() {
		return state;
	}

	public String getPlayerClass(Player p) {
		return pClasses.get(p).toUpperCase();
	}

	public Set<Player> getActivePlayers(){
		return players.keySet();
	}


}
