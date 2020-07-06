package com.darkender.plugins.gravitygun;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;

import java.util.HashMap;
import java.util.HashSet;
import java.util.UUID;

public class GravityGun extends JavaPlugin implements Listener
{
    public static NamespacedKey gravityGunKey;
    private HashMap<UUID, HeldEntity> heldEntities;
    private HashSet<UUID> justClicked;
    
    @Override
    public void onEnable()
    {
        gravityGunKey = new NamespacedKey(this, "gravity-gun");
        heldEntities = new HashMap<>();
        justClicked = new HashSet<>();
        
        GravityGunCommand gravityGunCommand = new GravityGunCommand();
        getCommand("gravitygun").setExecutor(gravityGunCommand);
        getCommand("gravitygun").setTabCompleter(gravityGunCommand);
        
        getServer().getPluginManager().registerEvents(this, this);
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable()
        {
            @Override
            public void run()
            {
                justClicked.clear();
                heldEntities.entrySet().removeIf(heldEntityEntry ->
                {
                    if(!(heldEntityEntry.getValue().tick()))
                    {
                        Player p = heldEntityEntry.getValue().getHolder();
                        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.6f);
                        return true;
                    }
                    return false;
                });
            }
        }, 1L, 1L);
    }
    
    public static ItemStack getGravityGun()
    {
        ItemStack gravityGun = new ItemStack(Material.GOLDEN_HORSE_ARMOR, 1);
        ItemMeta meta = gravityGun.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Gravity Gun");
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(gravityGunKey, PersistentDataType.BYTE, (byte) 1);
        gravityGun.setItemMeta(meta);
        return gravityGun;
    }
    
    public static boolean isGravityGun(ItemStack item)
    {
        if(item == null || !item.hasItemMeta())
        {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(gravityGunKey, PersistentDataType.BYTE);
    }
    
    private RayTraceResult raytraceFor(Player player)
    {
        Location rayStart = player.getEyeLocation();
        RayTraceResult rayTraceResult = rayStart.getWorld().rayTrace(rayStart, player.getEyeLocation().getDirection(),
                20.0, FluidCollisionMode.NEVER, true, 0.0, entity ->
                {
                    // Ensure the raytrace doesn't collide with the player
                    if(entity instanceof Player)
                    {
                        Player p = (Player) entity;
                        return (p.getEntityId() != player.getEntityId() && p.getGameMode() != GameMode.SPECTATOR && p.getGameMode() != GameMode.CREATIVE);
                    }
                    else
                    {
                        return (entity instanceof LivingEntity);
                    }
                });
        return rayTraceResult;
    }
    
    private void pickupBlock(Player player, Block block)
    {
        ArmorStand stand = player.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5),
                ArmorStand.class, armorStand ->
                {
                    armorStand.setHelmet(new ItemStack(block.getType()));
                    armorStand.setGravity(false);
                    armorStand.setVisible(false);
                    armorStand.setInvulnerable(true);
                    armorStand.setSilent(true);
                });
        heldEntities.put(player.getUniqueId(), new HeldEntity(player, stand, true));
        block.setType(Material.AIR);
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1.0f, 1.5f);
    }
    
    private void pickupEntity(Player player, Entity entity)
    {
        heldEntities.put(player.getUniqueId(), new HeldEntity(player, entity, false));
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS, 1.0f, 1.5f);
    }

    private void drop(Player player)
    {
        HeldEntity heldEntity = heldEntities.get(player.getUniqueId());
        if(heldEntity.isBlockEntity())
        {
            ArmorStand stand = (ArmorStand) heldEntity.getHeld();
            FallingBlock fallingBlock = stand.getWorld().spawnFallingBlock(stand.getLocation().add(0, 1.7, 0),
                    stand.getHelmet().getType().createBlockData());
            fallingBlock.setVelocity(heldEntity.getVelocity());
            stand.remove();
        }
        else
        {
            Entity held = heldEntity.getHeld();
            held.setVelocity(heldEntity.getVelocity());
            held.setFallDistance(0.0F);
        }
    
        heldEntities.remove(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1.0f, 0.6f);
    }
    
    @EventHandler
    private void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event)
    {
        PlayerInventory i = event.getPlayer().getInventory();
        if(isGravityGun(event.getHand() == EquipmentSlot.HAND ? i.getItemInMainHand() : i.getItemInOffHand()))
        {
            event.setCancelled(true);
            Player p = event.getPlayer();
    
            // Keep from accidentally clicking more than once per second
            if(p.hasMetadata("last-used") && p.getMetadata("last-used").get(0).asLong() > (System.nanoTime() - (1000 * 1000000)))
            {
                return;
            }
            p.setMetadata("last-used", new FixedMetadataValue(this, System.nanoTime()));
    
            if(heldEntities.containsKey(p.getUniqueId()))
            {
                drop(p);
            }
            else
            {
                pickupEntity(p, event.getRightClicked());
            }
            justClicked.add(p.getUniqueId());
        }
    }
    
    @EventHandler(ignoreCancelled = true)
    private void onInventoryOpen(InventoryOpenEvent event)
    {
        if(justClicked.contains(event.getPlayer().getUniqueId()))
        {
            event.setCancelled(true);
        }
    }
    
    @EventHandler
    private void onPlayerInteract(PlayerInteractEvent event)
    {
        if(isGravityGun(event.getItem()))
        {
            event.setCancelled(true);
            
            // Right click - pick up or drop
            if(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
            {
                Player p = event.getPlayer();
                
                // Keep from accidentally clicking more than once per second
                if(p.hasMetadata("last-used") && p.getMetadata("last-used").get(0).asLong() > (System.nanoTime() - (1000 * 1000000)))
                {
                    return;
                }
                p.setMetadata("last-used", new FixedMetadataValue(this, System.nanoTime()));
                
                if(heldEntities.containsKey(p.getUniqueId()))
                {
                    drop(p);
                }
                else
                {
                    RayTraceResult ray = raytraceFor(p);
                    if(ray != null)
                    {
                        if(ray.getHitBlock() != null)
                        {
                            pickupBlock(p, ray.getHitBlock());
                        }
                        else if(ray.getHitEntity() != null)
                        {
                            pickupEntity(p, ray.getHitEntity());
                        }
                    }
                }
            }
        }
    }
    
    @EventHandler
    private void onPlayerQuit(PlayerQuitEvent event)
    {
        if(heldEntities.containsKey(event.getPlayer().getUniqueId()))
        {
            drop(event.getPlayer());
        }
    }
    
    @EventHandler(ignoreCancelled = true)
    private void onInventoryClick(InventoryClickEvent event)
    {
        if(event.getInventory() instanceof HorseInventory && isGravityGun(event.getCurrentItem()))
        {
            event.setCancelled(true);
        }
    }
}
