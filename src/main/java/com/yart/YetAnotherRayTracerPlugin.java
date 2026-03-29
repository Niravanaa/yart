package com.yart;

import com.yart.command.RaytraceCommand;
import com.yart.control.RaytraceControlListener;
import com.yart.render.BlockPalette;
import com.yart.render.BlockPaletteLoader;
import com.yart.render.RaytraceSession;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Map.Entry;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class YetAnotherRayTracerPlugin extends JavaPlugin {
    private final Map<UUID, RaytraceSession> sessions = new ConcurrentHashMap<>();
    private BlockPalette blockPalette;

    @Override
    public void onEnable() {
        createDataDirectories();
        blockPalette = BlockPaletteLoader.load(this);

        RaytraceCommand raytraceCommand = new RaytraceCommand(this);
        PluginCommand command = getCommand("raytrace");
        if (command == null) {
            getLogger().severe("Command 'raytrace' is missing from plugin.yml");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        command.setExecutor(raytraceCommand);
        command.setTabCompleter(raytraceCommand);
        getServer().getPluginManager().registerEvents(new RaytraceControlListener(this), this);

        getLogger().info("YetAnotherRayTracer enabled.");
        getLogger()
                .info("Put models in plugins/YetAnotherRayTracer/models and use /raytrace load <file.obj|file.json>");
    }

    @Override
    public void onDisable() {
        sessions.values().forEach(RaytraceSession::stop);
        sessions.clear();
    }

    public Map<UUID, RaytraceSession> getSessions() {
        return sessions;
    }

    public RaytraceSession getSession(UUID playerId) {
        RaytraceSession direct = sessions.get(playerId);
        if (direct != null) {
            return direct;
        }

        for (Entry<UUID, RaytraceSession> entry : sessions.entrySet()) {
            RaytraceSession session = entry.getValue();
            if (session == null || !playerId.equals(session.playerId())) {
                continue;
            }

            sessions.put(playerId, session);
            if (!playerId.equals(entry.getKey())) {
                sessions.remove(entry.getKey(), session);
            }
            return session;
        }

        return null;
    }

    public RaytraceSession removeSession(UUID playerId) {
        RaytraceSession direct = sessions.remove(playerId);
        if (direct != null) {
            return direct;
        }

        for (Entry<UUID, RaytraceSession> entry : sessions.entrySet()) {
            RaytraceSession session = entry.getValue();
            if (session == null || !playerId.equals(session.playerId())) {
                continue;
            }
            if (sessions.remove(entry.getKey(), session)) {
                return session;
            }
        }

        return null;
    }

    public BlockPalette getBlockPalette() {
        return blockPalette;
    }

    private void createDataDirectories() {
        if (!getDataFolder().exists() && !getDataFolder().mkdirs()) {
            getLogger().warning("Failed to create plugin data folder: " + getDataFolder().getAbsolutePath());
        }

        File modelsDir = new File(getDataFolder(), "models");
        if (!modelsDir.exists() && !modelsDir.mkdirs()) {
            getLogger().warning("Failed to create models folder: " + modelsDir.getAbsolutePath());
        }
    }
}
