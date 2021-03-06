package com.darkender.plugins.gravitygun;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.*;

public class GravityGun extends JavaPlugin implements Listener
{
    public static NamespacedKey gravityGunKey;
    public static NamespacedKey heldBlockKey;
    private HashMap<UUID, HeldEntity> heldEntities;
    private HashSet<UUID> justClicked;
    
    @Override
    public void onEnable()
    {
        gravityGunKey = new NamespacedKey(this, "gravity-gun");
        heldBlockKey = new NamespacedKey(this, "held-block");
        heldEntities = new HashMap<>();
        justClicked = new HashSet<>();
        GravityGunConfig.init(this);
        GravityGunConfig.reload();
        
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
                
                // Tick all held entities and drop them if they're invalid
                heldEntities.entrySet().removeIf(heldEntityEntry ->
                {
                    Player p = heldEntityEntry.getValue().getHolder();
                    if(!(heldEntityEntry.getValue().tick()) || !isGravityGun(p.getInventory().getItemInMainHand()))
                    {
                        dropEntityLogic(p);
                        return true;
                    }
                    return false;
                });
            }
        }, 1L, 1L);
        
        // Check for old held blocks when loaded
        for(World world : getServer().getWorlds())
        {
            for(Chunk chunk : world.getLoadedChunks())
            {
                cleanChunk(chunk);
            }
        }
        
        // Add the recipe
        Material outsideMaterial;
        try
        {
            outsideMaterial = Material.valueOf("NETHERITE_INGOT");
        }
        catch(Exception e)
        {
            outsideMaterial = Material.IRON_BLOCK;
        }
        ShapedRecipe gravitygunRecipe = new ShapedRecipe(gravityGunKey, getGravityGun());
        gravitygunRecipe.shape("OOR", " SG", "OOR");
        gravitygunRecipe.setIngredient('G', Material.GOLD_INGOT);
        gravitygunRecipe.setIngredient('R', Material.REDSTONE);
        gravitygunRecipe.setIngredient('S', Material.NETHER_STAR);
        gravitygunRecipe.setIngredient('O', outsideMaterial);
        Bukkit.addRecipe(gravitygunRecipe);
    }
    
    @Override
    public void onDisable()
    {
        for(UUID key : heldEntities.keySet())
        {
            drop(Bukkit.getPlayer(key));
        }
    
        // Remove added recipes on plugin disable
        Iterator<Recipe> iter = getServer().recipeIterator();
        while(iter.hasNext())
        {
            Recipe check = iter.next();
            if(check instanceof ShapedRecipe && ((ShapedRecipe) check).getKey().equals(gravityGunKey))
            {
                getLogger().info("Removed recipe");
                iter.remove();
            }
        }
    }
    
    public static ItemStack getGravityGun()
    {
        ItemStack gravityGun = new ItemStack(Material.IRON_HORSE_ARMOR, 1);
        ItemMeta meta = gravityGun.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD + "Gravity Gun");
        meta.addEnchant(Enchantment.DURABILITY, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        String blockOrMob = "";
        if(GravityGunConfig.areBlocksAllowed())
        {
            blockOrMob += "block";
        }
        if(GravityGunConfig.areEntitiesAllowed())
        {
            if(GravityGunConfig.areBlocksAllowed())
            {
                blockOrMob += " or ";
            }
            blockOrMob += "mob";
        }
        List<String> lore = new ArrayList<>(Arrays.asList(
                ChatColor.DARK_AQUA + "Right Click",
                ChatColor.BLUE + " \u2022 Pick up / drop " + blockOrMob,
                "",
                ChatColor.DARK_AQUA + "Left Click"));
        if(GravityGunConfig.isAreaRepelAllowed())
        {
            lore.add(ChatColor.BLUE + " \u2022 Repel surrounding mobs");
        }
        if(GravityGunConfig.isHeldRepelAllowed())
        {
            lore.add(ChatColor.BLUE + " \u2022 Repel held " + blockOrMob);
        }
        meta.setLore(lore);
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
        if(GravityGunConfig.areEntitiesAllowed())
        {
            return rayStart.getWorld().rayTrace(rayStart, player.getEyeLocation().getDirection(),
                GravityGunConfig.getPickupRange(), FluidCollisionMode.NEVER, true, 0.0, entity ->
                {
                    // Ensure the raytrace doesn't collide with the player
                    if(entity instanceof Player)
                    {
                        Player p = (Player) entity;
                        return (p.getEntityId() != player.getEntityId() && p.getGameMode() != GameMode.SPECTATOR);
                    }
                    else
                    {
                        return (entity instanceof LivingEntity);
                    }
                });
        }
        else
        {
            return rayStart.getWorld().rayTraceBlocks(rayStart, player.getEyeLocation().getDirection(),
                    GravityGunConfig.getPickupRange(), FluidCollisionMode.NEVER, true);
        }
    }
    
    private void pickupBlock(Player player, Block block)
    {
        // Create an armor stand with a block on its head
        // Other possible solutions include falling sand and actually placing the block in the world
        // Falling sand has an update interval of 20 ticks instead of every tick - https://hub.spigotmc.org/jira/browse/SPIGOT-2749
        // Placing a block in the world can have unintended consequences
        ArmorStand stand = player.getWorld().spawn(block.getLocation().add(0.5, 0, 0.5),
                ArmorStand.class, armorStand ->
                {
                    armorStand.setHelmet(new ItemStack(block.getType()));
                    armorStand.setGravity(false);
                    armorStand.setVisible(false);
                    armorStand.setInvulnerable(true);
                    armorStand.setSilent(true);
                    armorStand.getPersistentDataContainer().set(heldBlockKey, PersistentDataType.BYTE, (byte) 1);
                });
        heldEntities.put(player.getUniqueId(), new HeldEntity(player, stand, true));
        block.setType(Material.AIR);
        GravityGunConfig.playPickupSound(player.getLocation());
    }
    
    private void pickupEntity(Player player, Entity entity)
    {
        heldEntities.put(player.getUniqueId(), new HeldEntity(player, entity, false));
        GravityGunConfig.playPickupSound(player.getLocation());
    }

    private Entity dropEntityLogic(Player player)
    {
        HeldEntity heldEntity = heldEntities.get(player.getUniqueId());
        Entity newEntity = null;
        if(heldEntity.isValid())
        {
            if(heldEntity.isBlockEntity())
            {
                // Spawns in falling sand for the picked up block
                ArmorStand stand = (ArmorStand) heldEntity.getHeld();
                FallingBlock fallingBlock = stand.getWorld().spawnFallingBlock(stand.getLocation().add(0, 1.7, 0),
                        stand.getHelmet().getType().createBlockData());
                fallingBlock.setVelocity(heldEntity.getVelocity());
                stand.remove();
                newEntity = fallingBlock;
            }
            else
            {
                Entity held = heldEntity.getHeld();
                held.setVelocity(heldEntity.getVelocity());
                held.setFallDistance(0.0F);
                newEntity = held;
            }
        }
        GravityGunConfig.playDropSound(player.getLocation());
        return newEntity;
    }
    
    private Entity drop(Player player)
    {
        Entity newEntity = dropEntityLogic(player);
        heldEntities.remove(player.getUniqueId());
        return newEntity;
    }
    
    /**
     * Checks the chunk for any armor stands used to hold blocks and removes them
     * @param chunk the chunk to check
     */
    private void cleanChunk(Chunk chunk)
    {
        for(Entity e : chunk.getEntities())
        {
            if(e.getType() == EntityType.ARMOR_STAND && e.getPersistentDataContainer().has(heldBlockKey, PersistentDataType.BYTE))
            {
                e.remove();
            }
        }
    }
    
    private boolean passesTimeout(Player player)
    {
        // Used to keep from accidentally clicking more than once per second
        if(player.hasMetadata("last-used") &&
                player.getMetadata("last-used").get(0).asLong() > (System.nanoTime() - (1000 * 1000000)))
        {
            return false;
        }
        player.setMetadata("last-used", new FixedMetadataValue(this, System.nanoTime()));
        return true;
    }
    
    private boolean isInHoldingChain(Player base, Player check)
    {
        if(!heldEntities.containsKey(check.getUniqueId()))
        {
            return false;
        }
        Entity held = heldEntities.get(check.getUniqueId()).getHeld();
        if(held.getType() == EntityType.PLAYER)
        {
             if(held.getUniqueId().equals(base.getUniqueId()))
             {
                 return true;
             }
             else
             {
                 return isInHoldingChain(base, (Player) held);
             }
        }
        else
        {
            return false;
        }
    }
    
    private boolean isBeingHeld(Entity check)
    {
        for(HeldEntity heldEntity : heldEntities.values())
        {
            if(heldEntity.getHeld().equals(check))
            {
                return true;
            }
        }
        return false;
    }
    
    @EventHandler
    private void onPlayerInteractAtEntity(PlayerInteractAtEntityEvent event)
    {
        PlayerInventory i = event.getPlayer().getInventory();
        if(isGravityGun(event.getHand() == EquipmentSlot.HAND ? i.getItemInMainHand() : i.getItemInOffHand()))
        {
            event.setCancelled(true);
            Player p = event.getPlayer();
    
            if(heldEntities.containsKey(p.getUniqueId()) && passesTimeout(p))
            {
                drop(p);
            }
            else if(GravityGunConfig.areEntitiesAllowed() &&
                    !GravityGunConfig.isBannedEntity(event.getRightClicked().getType()) &&
                    passesTimeout(p))
            {
                pickupEntity(p, event.getRightClicked());
            }
            // Ensure the player doesn't open any entity inventories
            justClicked.add(p.getUniqueId());
        }
        
        // Prevent interactions with held block armor stands
        if(event.getRightClicked().getType() == EntityType.ARMOR_STAND &&
                event.getRightClicked().getPersistentDataContainer().has(heldBlockKey, PersistentDataType.BYTE))
        {
            event.setCancelled(true);
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
    
    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event)
    {
        // Prevent the player from damaging entities with the gravity gun
        if(event.getDamager().getType() == EntityType.PLAYER)
        {
            Player damager = (Player) event.getDamager();
            if(isGravityGun(damager.getInventory().getItemInMainHand()))
            {
                event.setCancelled(true);
            }
        }
    
        // Prevent held block armor stands from being damaged
        if(event.getEntityType() == EntityType.ARMOR_STAND &&
                event.getEntity().getPersistentDataContainer().has(heldBlockKey, PersistentDataType.BYTE))
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
            Player p = event.getPlayer();
            
            // Right click - pick up or drop
            if(event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)
            {
                if(heldEntities.containsKey(p.getUniqueId()) && passesTimeout(p))
                {
                    drop(p);
                }
                else
                {
                    RayTraceResult ray = raytraceFor(p);
                    if(ray != null)
                    {
                        if(ray.getHitBlock() != null && GravityGunConfig.areBlocksAllowed())
                        {
                            Block block = ray.getHitBlock();
                            if((block.getState() instanceof TileState && !GravityGunConfig.areTilesAllowed()) ||
                                    GravityGunConfig.isBannedBlock(block.getType()) ||
                                    !p.hasPermission("gravitygun.pickup.block") ||
                                    !passesTimeout(p))
                            {
                                return;
                            }
                            pickupBlock(p, ray.getHitBlock());
                        }
                        else if(ray.getHitEntity() != null && GravityGunConfig.areEntitiesAllowed())
                        {
                            // Ensure it's not a banned entity type or in a holding chain
                            // A "holding chain" is when two or more players are holding each other with the gravity gun
                            if(GravityGunConfig.isBannedEntity(ray.getHitEntity().getType()) ||
                                    (ray.getHitEntity().getType() == EntityType.PLAYER &&
                                            isInHoldingChain(p, (Player) ray.getHitEntity())) ||
                                    isBeingHeld(ray.getHitEntity()) ||
                                    !p.hasPermission("gravitygun.pickup.entity") ||
                                    !passesTimeout(p))
                            {
                                return;
                            }
                            pickupEntity(p, ray.getHitEntity());
                        }
                    }
                }
            }
            else
            {
                // Left click - repel
                if(heldEntities.containsKey(p.getUniqueId()))
                {
                    if(GravityGunConfig.isHeldRepelAllowed() &&
                            p.hasPermission("gravitygun.repelheld") && passesTimeout(p))
                    {
                        Entity newEntity = drop(p);
                        newEntity.setVelocity(newEntity.getVelocity().add(p.getEyeLocation().getDirection().multiply(
                                GravityGunConfig.getHeldRepelPower())));
                        GravityGunConfig.playRepelSound(p.getLocation());
                    }
                }
                else if(GravityGunConfig.isAreaRepelAllowed() && p.hasPermission("gravitygun.repelarea"))
                {
                    List<Entity> nearby = p.getNearbyEntities(5, 5, 5);
                    if(nearby.size() > 0 && passesTimeout(p))
                    {
                        for(Entity e : nearby)
                        {
                            Vector between = e.getLocation().toVector().subtract(p.getLocation().toVector());
                            e.setVelocity(e.getVelocity().add(between.normalize().multiply(
                                    GravityGunConfig.getAreaRepelPower() / (between.length() + 1))));
                        }
                        GravityGunConfig.playRepelSound(p.getLocation());
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
        // Prevent the player from using the gun as horse armor
        if(event.getInventory() instanceof HorseInventory && isGravityGun(event.getCurrentItem()))
        {
            event.setCancelled(true);
        }
    }
    
    @EventHandler(ignoreCancelled = true)
    private void onEntityDamage(EntityDamageEvent event)
    {
        if(event.getCause() == EntityDamageEvent.DamageCause.SUFFOCATION && GravityGunConfig.shouldPreventSuffocationDamage())
        {
            for(HeldEntity heldEntity : heldEntities.values())
            {
                if(heldEntity.getHeld().equals(event.getEntity()))
                {
                    event.setCancelled(true);
                    break;
                }
            }
        }
    }
    
    @EventHandler(ignoreCancelled = true)
    private void onChunkLoad(ChunkLoadEvent event)
    {
        cleanChunk(event.getChunk());
    }
    
    @EventHandler(ignoreCancelled = true)
    private void onChunkUnload(ChunkUnloadEvent event)
    {
        cleanChunk(event.getChunk());
    }
}
