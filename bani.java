package org.RedemptionMC;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class bani extends JavaPlugin implements Listener {
    private Connection connection;

    private String databasePath;

    public void onEnable() {
        getLogger().info("REDEMPTION MC has been enabled!");
        getServer().getPluginManager().registerEvents(this, (Plugin)this);
        File configFile = new File(getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            getConfig().options().copyDefaults(true);
            getConfig().addDefault("Death1", "world_nether");
            getConfig().addDefault("Death2", "world_nether");
            getConfig().addDefault("Death3", "world_nether");
            getConfig().addDefault("Death4", "world_nether");
            getConfig().addDefault("Death5", "world_nether");
            saveConfig();
        }
        this.databasePath = getDataFolder().getAbsolutePath() + File.separator + "RedemptionMC.db";
        createConfigFolder();
        try {
            initializeDatabase();
        } catch (SQLException e) {
            e.printStackTrace();
        }
        createWorldsFromConfig();
    }

    private void createConfigFolder() {
        File dataFolder = getDataFolder();
        if (!dataFolder.exists())
            dataFolder.mkdirs();
    }

    private int fetchDeathCount(String playerName) throws SQLException {
        PreparedStatement statement = this.connection.prepareStatement("SELECT death_count FROM player_deaths WHERE player_name = ?");
        statement.setString(1, playerName);
        ResultSet resultSet = statement.executeQuery();
        if (resultSet.next())
            return resultSet.getInt("death_count");
        return 0;
    }

    private int incrementDeathCount(String playerName) throws SQLException {
        int currentDeathCount = fetchDeathCount(playerName);
        currentDeathCount++;
        PreparedStatement statement = this.connection.prepareStatement("UPDATE player_deaths SET death_count = ? WHERE player_name = ?");
        statement.setInt(1, currentDeathCount);
        statement.setString(2, playerName);
        statement.executeUpdate();
        return currentDeathCount;
    }

    private void initializeDatabase() throws SQLException {
        try {
            Class.forName("org.sqlite.JDBC");
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + this.databasePath);
            PreparedStatement statement = this.connection.prepareStatement("CREATE TABLE IF NOT EXISTS player_deaths (id INTEGER PRIMARY KEY, player_name TEXT, death_count INTEGER)");
            statement.executeUpdate();
        } catch (ClassNotFoundException|SQLException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player != null) {
            event.setKeepInventory(false);
            String playerName = player.getName();
            ConsoleCommandSender consoleCommandSender = getServer().getConsoleSender();
        }
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        Location location = player.getLocation();
        World world = location.getWorld();
        if (world.getName().equals("redemptionchamber")) {
            int playerX = location.getBlockX();
            int playerY = location.getBlockY();
            int playerZ = location.getBlockZ();
            if (world.getBlockAt(playerX, playerY - 1, playerZ).getType() == Material.DIAMOND_BLOCK) {
                String targetWorld = "world";
                String playerName = player.getName();
                String mvtpCommand = "mvtp " + playerName + " " + targetWorld;
                String roleCommand = "lp user " + playerName + " parent set role1";
                ConsoleCommandSender console = getServer().getConsoleSender();
                getServer().dispatchCommand((CommandSender)console, mvtpCommand);
                getServer().dispatchCommand((CommandSender)console, roleCommand);
            }
        }
    }
    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            Location bedSpawnLocation = player.getBedSpawnLocation();

            if (bedSpawnLocation != null) {
                event.setRespawnLocation(bedSpawnLocation);
            } else {
                String playerName = player.getName();
                try {
                    int deathCount = fetchDeathCount(playerName);
                    if (deathCount == 0) {
                        deathCount = 1;
                        PreparedStatement insertStatement = this.connection.prepareStatement("INSERT INTO player_deaths (player_name, death_count) VALUES (?, ?)");
                        insertStatement.setString(1, playerName);
                        insertStatement.setInt(2, deathCount);
                        insertStatement.executeUpdate();
                    } else {
                        deathCount = incrementDeathCount(playerName);
                    }
                    String configKey = "Death" + deathCount;
                    String worldName = getConfig().getString(configKey);
                    if (worldName != null) {
                        int finalDeathCount = deathCount;
                        Bukkit.getScheduler().runTaskLater((Plugin) this, () -> {
                            ConsoleCommandSender console = getServer().getConsoleSender();
                            String command = "mvtp " + playerName + " " + worldName;
                            getServer().dispatchCommand((CommandSender) console, command);
                            changePlayerRole(playerName, finalDeathCount);
                        }, 5L);
                    } else {
                        getLogger().warning("World not found in the configuration for death count: " + deathCount);
                    }
                } catch (SQLException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private void createWorldsFromConfig() {
        for (int i = 1; i <= 5; i++) {
            String configKey = "Death" + i;
            String worldName = getConfig().getString(configKey);
            if (worldName != null && !worldExists(worldName))
                createWorld(worldName);
        }
    }

    private boolean worldExists(String worldName) {
        World world = Bukkit.getWorld(worldName);
        return (world != null);
    }

    private void createWorld(String worldName) {
        ConsoleCommandSender console = getServer().getConsoleSender();
        String command = "mv create " + worldName + " normal";
        getServer().dispatchCommand((CommandSender)console, command);
    }

    private void changePlayerRole(String playerName, int deathCount) {
        ConsoleCommandSender console = getServer().getConsoleSender();
        int previousDeathCount = Math.max(deathCount - 1, 0);
        String commandAddRole = "lp user " + playerName + " parent add role" + deathCount;
        String commandRemoveDefaultRole = "lp user " + playerName + " parent remove default";
        String commandRemoveDeleterole = "lp user " + playerName + " parent remove" + previousDeathCount;
        getServer().dispatchCommand((CommandSender)console, commandAddRole);
        getServer().dispatchCommand((CommandSender)console, commandRemoveDeleterole);
        getServer().dispatchCommand((CommandSender)console, commandRemoveDefaultRole);
    }
}
