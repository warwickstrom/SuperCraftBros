/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */
package org.mcsg.double0negative.supercraftbros.event;

import java.util.ArrayList;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.util.Vector;
import org.mcsg.double0negative.supercraftbros.Game;
import org.mcsg.double0negative.supercraftbros.GameManager;
import org.mcsg.double0negative.supercraftbros.SettingsManager;
import org.mcsg.double0negative.supercraftbros.Game.State;

public class PlayerClassEvents implements Listener{

	GameManager gm;
	private boolean arenaTeleport;
	
	ArrayList<UUID> fire = new ArrayList<UUID>();
	ArrayList<UUID> sugar = new ArrayList<UUID>();
	ArrayList<UUID> doublej = new ArrayList<UUID>();
	ArrayList<UUID> fsmash = new ArrayList<UUID>();

	public PlayerClassEvents(){
		gm = GameManager.getInstance();
		Boolean arenaTp = SettingsManager.getInstance().getConfig().getBoolean("use-arena-teleport");
		arenaTeleport = arenaTp != null ? arenaTp : false;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void blockFire(BlockIgniteEvent e){
		final Block b = e.getBlock();
		Bukkit.getScheduler().scheduleSyncDelayedTask(GameManager.getInstance().getPlugin(), new Runnable(){
			@SuppressWarnings("deprecation")
			public void run(){
				b.setTypeId(0);
				b.getState().update();
			}
		}, 60);
	}
	
	
	@EventHandler
	public void onHunger(FoodLevelChangeEvent event){
		if (event.getEntity() instanceof Player){
			Player p = (Player)event.getEntity();
			String id = gm.getPlayerGameId(p);
			if(!(id == null)){
				event.setCancelled(true);
				p.setFoodLevel(20);
			}	
		}
	}
	@SuppressWarnings("deprecation")
	@EventHandler
	public void onInteract(PlayerInteractEvent e){
		final Player p = e.getPlayer();
		final UUID pid = p.getUniqueId();
		String id = gm.getPlayerGameId(p);
		if(!(id == null)){
			Game g = gm.getGame(id);
			if(g.getState() == Game.State.INGAME){
				if(e.getPlayer().getItemInHand().getType() == Material.DIAMOND_AXE){
					Smash(p);
				}else if(p.getItemInHand().getType() == Material.EYE_OF_ENDER){
					e.setCancelled(true);
				}else if(!fire.contains(pid)){
					if(p.getItemInHand().getType() == Material.FIREBALL){
						Fireball f = p.launchProjectile(Fireball.class);
						f.setVelocity(f.getVelocity().multiply(10));
						fire.add(pid);
                        Bukkit.getScheduler().scheduleSyncDelayedTask(GameManager.getInstance().getPlugin(), new Runnable(){
                			public void run(){
                				fire.remove(pid);
                			}
                		}, 600);
					}
				}else if(!sugar.contains(pid)){
                    if(p.getItemInHand().getType() == Material.SUGAR && (e.getAction() == Action.LEFT_CLICK_AIR || e.getAction() == Action.LEFT_CLICK_BLOCK)){
                        p.setVelocity(new Vector(0, 2, 0));
                        sugar.add(pid);
                        Bukkit.getScheduler().scheduleSyncDelayedTask(GameManager.getInstance().getPlugin(), new Runnable(){
                			public void run(){
                				sugar.remove(pid);
                			}
                		}, 100);
                    }
                    if(p.getItemInHand().getType() == Material.SUGAR && (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK)){
                        p.setVelocity(p.getLocation().getDirection().multiply(4));
                        sugar.add(pid);
                        Bukkit.getScheduler().scheduleSyncDelayedTask(GameManager.getInstance().getPlugin(), new Runnable(){
                			public void run(){
                				sugar.remove(pid);
                			}
                		}, 100);
                    }
				}
			}else{
				e.setCancelled(true);
			}
		}

	}
	
	public void Smash(Player p){
		
	}

	@SuppressWarnings("deprecation")
	public boolean isOnGround(Player p){
		Location l = p.getLocation();
		l = l.add(0, -1, 0);
		return l.getBlock().getState().getTypeId() != 0;
	}

	public void explodePlayers(Player p){
		String i = GameManager.getInstance().getPlayerGameId(p);
		if(!(i == null)){
			Location l = p.getLocation();
			l = l.add(0, -1, 0);
			for(int x = l.getBlockX() - 1; x<=l.getBlockX()+1; x++){
				for(int z = l.getBlockZ() - 1; z<=l.getBlockZ()+1; z++){
				 //SendPacketToAll(new PacketPlayOutWorldEvent(2001,x, l.getBlockY()+1, z, l.getBlock().getState().getTypeId(), false));
					explodeBlocks(p, new Location(l.getWorld(), x, l.getBlockY(), z));
				}
			}
			for(Entity pl:p.getWorld().getEntities()){
				if(pl != p){
					Location l2 = pl.getLocation();
					double d = pl.getLocation().distance(p.getLocation());
					if(d < 5){
						d = d / 5;
						pl.setVelocity(new Vector((1.5-d) * getSide(l2.getBlockX(), l.getBlockX()), 1.5-d, (1.5-d)*getSide(l2.getBlockZ(), l.getBlockZ())));
						
					}
				}
			}
		}
	}

	@SuppressWarnings("deprecation")
	public void explodeBlocks(Player p, Location l){
		Location l2 = p.getLocation();
		Location l3 = l.add(0, 1, 0);
		if(l.getBlock().getState().getTypeId() != 0 && l3.getBlock().getState().getTypeId() == 0){
			final Entity e  = l.getWorld().spawnFallingBlock(l3, l.getBlock().getState().getTypeId(), l.getBlock().getState().getData().getData());
			e.setVelocity(new Vector((getSide(l.getBlockX(), l2.getBlockX()) * 0.3),.1, (getSide(l.getBlockZ(), l2.getBlockZ()) * 0.3)));
			Bukkit.getScheduler().scheduleSyncDelayedTask(GameManager.getInstance().getPlugin(), new Runnable(){
				public void run(){
					e.remove();
				}
			}, 5);
		}
	}
	
	/*@SuppressWarnings("rawtypes")
	public void SendPacketToAll(Packet p, Player player){
		for(Player pl: GameManager.getInstance().getGame(GameManager.getInstance().getPlayerGameId(player)).getActivePlayers()){
			((CraftPlayer)pl).getHandle().playerConnection.sendPacket(p);
		}
	}*/

	@EventHandler
	public void onMove(PlayerMoveEvent e){
		Player p = e.getPlayer();
		UUID pid = p.getUniqueId();
		String id = gm.getPlayerGameId(p);
		if(!(id == null)){
			Game g = gm.getGame(id);
			if(g.getState() == Game.State.INGAME){
				if(p.isFlying()){
					p.setFlying(false);
					p.setAllowFlight(false);
					Vector v = p.getLocation().getDirection().multiply(.5);
					v.setY(.75);
					p.setVelocity(v);
					doublej.add(pid);
				}
				if(isOnGround(p)){
					p.setAllowFlight(true);
					if(fsmash.contains(pid)){
						if(p.isSneaking()) explodePlayers(p);
						fsmash.remove(pid);
					}
					doublej.remove(pid);

				}
				if(doublej.contains(pid) && p.isSneaking()){
					p.setVelocity(new Vector(0, -1, 0));
					fsmash.add(pid);
				}
			}	
		}
	}

	public int getSide(int i, int u){
		if(i > u) return 1;
		if(i < u)return -1;
		return 0;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onEntityExplode(EntityExplodeEvent e){
		Location l = e.getLocation();
		String game =  GameManager.getInstance().getBlockGameId(l);
		if(!(game == null)){
			e.blockList().clear();
		}
		if(e.getEntity() instanceof Fireball){
			e.setCancelled(true);
			double x = l.getX();
			double y = l.getY();
			double z = l.getZ();
			l.getWorld().createExplosion(x, y, z, 3, false, false);
		}
	}


	@EventHandler
	public void onEntityRespawn(PlayerRespawnEvent e){
		final Player p = e.getPlayer();
		Bukkit.getScheduler().scheduleSyncDelayedTask(GameManager.getInstance().getPlugin(), new Runnable(){
			public void run(){
				String id = gm.getPlayerGameId(p);
				if(!(id == null)){
					gm.getGame(id).spawnPlayer(p);
				}
				else if(!arenaTeleport){
					p.teleport(SettingsManager.getInstance().getLobbySpawn());
				}
			}
		}, 1);
	}

	@EventHandler
	public void onPlayerPlaceBlock(BlockPlaceEvent e){
		String id = gm.getPlayerGameId(e.getPlayer());
		if(!(id == null)){
			if(gm.getGame(id).getState() == State.INGAME){
			  if(e.getBlockPlaced().getType() == Material.TNT){
				  Location l = e.getBlockPlaced().getLocation();
				  l.getWorld().spawnEntity(l, EntityType.PRIMED_TNT);
				  e.getBlockPlaced().setType(Material.AIR);;
			  }
		}
	}
}
}	
