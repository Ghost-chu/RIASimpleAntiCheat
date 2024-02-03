package com.ghostchu.plugins.riaanticheat;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.events.ListenerPriority;
import com.comphenix.protocol.events.PacketAdapter;
import com.comphenix.protocol.events.PacketEvent;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.viaversion.viaversion.api.Via;
import com.viaversion.viaversion.api.ViaAPI;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.sign.Side;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.TimeUnit;

public final class RIAAntiCheat extends JavaPlugin implements Listener {
    private Cache<UUID, CommandSender> CACHE_POOL = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.SECONDS)
            .build();

    @Override
    public void onEnable() {
        // Plugin startup logic
        Bukkit.getPluginManager().registerEvents(this, this);
        ProtocolLibrary.getProtocolManager().addPacketListener(new PacketAdapter(this, ListenerPriority.HIGH, PacketType.Play.Client.UPDATE_SIGN) {
            @Override
            public void onPacketReceiving(PacketEvent event) {
                CommandSender sender = CACHE_POOL.getIfPresent(event.getPlayer().getUniqueId());
                if(sender == null){
                    return;
                }
                StringBuilder builder = new StringBuilder();
                builder.append(event.getPlayer().getName()).append(" 客户端回报：\n");
                String[] strings = event.getPacket().getStringArrays().readSafely(0);
                for (String string : strings) {
                    builder.append(string).append("\n");
                }
                getLogger().info(builder.toString());
                sender.sendMessage(builder.toString());
                event.setCancelled(true);
                CACHE_POOL.invalidate(event.getPlayer().getUniqueId());
            }
        });
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("riaanticheat.admin")) return false;
        List<Player> targetPlayers = new ArrayList<>();

        if (args[0].equals("*")) {
            targetPlayers.addAll(Bukkit.getOnlinePlayers());
        } else {
            Player target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage("Target not online");
                return true;
            }
            targetPlayers.add(target);
        }

        List<String> testTargetTranslationString = new ArrayList<>(Arrays.asList(args).subList(1, args.length));
        if(testTargetTranslationString.size() > 16){
            sender.sendMessage("单次最多测试 16 组翻译键，每行 4 个");
            return true;
        }
        for (Player target : targetPlayers) {
            testPlayer(sender, target, new ArrayDeque<>(testTargetTranslationString));
        }

        return true;
    }

    private void testPlayer(CommandSender sender, Player target, Deque<String> tests) {
        if(Bukkit.getPluginManager().isPluginEnabled("ViaVersion")){
            ViaAPI api = Via.getAPI();
            if(api.getPlayerVersion(target) < 763){
                sender.sendMessage(target.getName()+" version too low, skipping...");
                return;
            }
        }

        Location location = target.getLocation().clone();
        location.setY(location.getWorld().getMaxHeight() - 1);
        while (!location.getBlock().getType().isAir()) {
            location.setY(location.getBlockY() - 1);
            if (location.getBlockY() < location.getWorld().getMinHeight()) {
                getLogger().info("Player " + target.getName() + " had no space around to place detection sign");
                break;
            }
        }

        Block block = location.getBlock();
        block.setType(Material.OAK_SIGN);
        getLogger().info("Sign placed at "+location);
        Sign sign = (Sign) block.getState();
        for (int i = 0; i < 4; i++) {
            Component base = Component.text("");
            for (int j = 0; j < 4; j++) {
                String s = tests.poll();
                if(s == null) break;
                base = base.append(Component.translatable(s,"#"));
            }
            sign.getSide(Side.FRONT).line(i, base);
        }
        sign.setEditable(true);
        sign.setWaxed(false);
        sign.update(true, false);
        target.sendBlockChange(sign.getLocation(), sign.getBlockData());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            CACHE_POOL.put(target.getUniqueId(),sender );
            target.openSign(sign, Side.FRONT);
            target.openInventory(Bukkit.createInventory(null, 9));
            target.closeInventory();
            location.getBlock().setType(Material.AIR);
        }, 2L);
    }

}
