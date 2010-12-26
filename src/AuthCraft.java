
import java.io.File;
import java.io.FileWriter;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

public class AuthCraft extends Plugin {

    private static String NAME = "AuthCraft";
    private static int MAJOR = 1;
    private static int MINOR = 0;
    private static int REVISION = 0;

    private PropertiesFile properties;
    /**
     * The file where auths are saved to
     */
    private static String AUTH_FILE = "auths.db";
    /**
     * The users that have authenticated
     */
    public final List<String> authenticated = new ArrayList<String>();
    /**
     * The loaded auth values
     */
    public Map<String, String> authTable = new HashMap<String, String>();
    /**
     * When the last "You need to be identified to do that!" message was sent
     */
    public long lastAlert = 0;
    /**
     * The mode of the server
     */
    public boolean onlineMode;
    /**
     * If registrations are accepted while in offline mode
     */
    public boolean registerInOfflineMode;
    private AuthCraftListener listener = null;
    public boolean onlyAllowedUsersCanRegister;

    public void initialize() {
        //log("Registering listeners");

        listener = new AuthCraftListener(this);

        register(PluginLoader.Hook.BLOCK_CREATED);
        register(PluginLoader.Hook.BLOCK_DESTROYED);
        register(PluginLoader.Hook.CHAT);
        register(PluginLoader.Hook.COMMAND);
        register(PluginLoader.Hook.DISCONNECT);
        register(PluginLoader.Hook.LOGIN);
        register(PluginLoader.Hook.PLAYER_MOVE);
        register(PluginLoader.Hook.SERVERCOMMAND);
        register(PluginLoader.Hook.OPEN_INVENTORY);
        register(PluginLoader.Hook.ITEM_DROP);
    }

    /**
     * Register a hook
     *
     * @param hook the hook to register
     * @priority the priority to use
     */
    private void register(PluginLoader.Hook hook, PluginListener.Priority priority) {
        //log("MineListener -> " + hook.toString());

        etc.getLoader().addListener(hook, listener, this, priority);
    }

    /**
     * Register a hook with default priority
     *
     * @param hook the hook to register
     */
    private void register(PluginLoader.Hook hook) {
        register(hook, PluginListener.Priority.MEDIUM);
    }

    /**
     * Internal use - log a player in
     *
     * @param player
     *            the player to login
     */
    public boolean _login(Player player) {
        if (etc.getDataSource().doesPlayerExist(player.getName())) {
            player.setAdmin(etc.getDataSource().getPlayer(player.getName()).isAdmin());
            player.setCanModifyWorld(etc.getDataSource().getPlayer(player.getName()).canBuild());
            player.setCommands(etc.getDataSource().getPlayer(player.getName()).getCommands());
            player.setGroups(etc.getDataSource().getPlayer(player.getName()).getGroups());
            player.setIgnoreRestrictions(etc.getDataSource().getPlayer(player.getName()).canIgnoreRestrictions());
            //IF THE REGISTERED FEW TIME AGO, HE MIGHT NOT HAVE A BACKUP
        }
        return true;
    }

    /**
     * Add an auth to the valid auths table
     *
     * @param username
     * @param password
     */
    public void addAuth(String username, String password) {
        if (authTable.containsKey(username.toLowerCase())) {
            authTable.remove(username);
        }

        authTable.put(username.toLowerCase(), password);
        saveAuthEntries();
    }

    /**
     * Validate a player's auth
     *
     * @param player
     *            the Player to check
     * @return true if the player is not authenticated. false if the player is
     *         authenticated or not registered
     */
    public boolean checkAuth(Player player) {
        /*if (!etc.getDataSource().doesPlayerExist(player.getName())) {
            return true;
        }*/
    	
        if (!authenticated.contains(player.getName().toLowerCase())) {
            if (authTable.containsKey(player.getName().toLowerCase())) {
                if (lastAlert == 0
                        || System.currentTimeMillis() - lastAlert > 1000) {
                    player.sendMessage(Colors.Rose
                            + "Please identify yourself with /login <password>");
                    lastAlert = System.currentTimeMillis();
                }

                return true;
            } else if (onlineMode || registerInOfflineMode) {
                if (lastAlert == 0
                        || System.currentTimeMillis() - lastAlert > 1000) {
                    player.sendMessage(Colors.Rose
                            + "Please register yourself with /register <password>");
                    lastAlert = System.currentTimeMillis();
                }

                return true;
            }
        }

        return false;
    }

    /**
     * Copy key vars from one Player instance to another
     *
     * @param player1
     *            the player to copy from
     * @param player2
     *            the player to copy to
     * @return the copied player
     */
    private Player copyAccountData(Player player, Player player_) {
        player_.setAdmin(player.isAdmin());
        player_.setCanModifyWorld(player.canModifyWorld());
        player_.setCommands(player.getCommands());
        player_.setGroups(player.getGroups());
        player_.setIgnoreRestrictions(player.canIgnoreRestrictions());

        return player_;
    }

    /**
     * When the plugin is disabled the plugin will cease to function
     */
    @Override
    public void disable() {
        log(this.NAME + " - Server-side authentication disabled!");
        saveAuthEntries();
    }

    /**
     * When the plugin is enabled (including when the server is just started)
     */
    @Override
    public void enable() {
    	
        setName(NAME);
        log(this.NAME + " - Server-side authentication enabled.");

        properties = new PropertiesFile("server.properties");
        onlineMode = properties.getBoolean("online-mode", true);
        onlyAllowedUsersCanRegister = properties.getBoolean("require-register-command", false);
        registerInOfflineMode = properties.getBoolean("register-offline", false);

        loadAuthEntries();
        saveAuthEntries(); // create the file initially if it does not exist

        log("Loaded " + authTable.size() + " registrations");
    }

    /**
     * The encryption implementation to store passwords [as md5 (default)]
     *
     * @param string
     *            the string to encrypt
     * @default the encrypted string
     */
    public String encrypt(String string) {
        try {
            final MessageDigest m = MessageDigest.getInstance("MD5");
            final byte[] bytes = string.getBytes();
            m.update(bytes, 0, bytes.length);
            final BigInteger i = new BigInteger(1, m.digest());

            return String.format("%1$032X", i).toLowerCase();
        } catch (final Exception e) {
        }

        return "";
    }

    /**
     * Load the saved auths (if there are any!)
     */
    private void loadAuthEntries() {
        final File file = new File(AUTH_FILE);

        if (!file.exists()) {
            return;
        }

        Scanner reader = null;
        int lineCount = 0;
        try {
            reader = new Scanner(file);
            while (reader.hasNextLine()) {
                lineCount++;
                reader.nextLine();
            }
        } catch (final Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
        if (lineCount > 150) {
            authTable = new HashMap<String, String>(lineCount + ((int) (lineCount * 0.40)));
            
        }

        try {
            reader = new Scanner(file);
            while (reader.hasNextLine()) {
                final String line = reader.nextLine();

                if (!line.contains(":")) {
                    /*
                     * Invalid!
                     */
                    continue;
                }

                final String[] in = line.split(":");

                if (in.length != 2) {
                    continue;
                }

                final String username = in[0].toLowerCase();
                final String password = in[1];

                addAuth(username, password);
            }
        } catch (final Exception e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    /**
     * Log a message
     *
     * @param str
     *            the string to log
     */
    public void log(String str) {
        System.out.println("[" + getName() + "] [v" + MAJOR + "." + MINOR + "."
                + REVISION + "] " + str);
    }

    /**
     * Get all of the inherited groups for a player
     *
     * @param player the player to get the inherited groups for
     * @return the array of inherited groups
     */
    public ArrayList<String> getInherited(String group_) {
        ArrayList<String> list = new ArrayList<String>();

        Group group = etc.getDataSource().getGroup(group_);

        if (group == null) {
            return null;
        }
        if (group.Name.equals(etc.getInstance().getDefaultGroup().Name)) {
            return list;
        }

        for (String g : group.InheritedGroups) {
            if (g.length() == 0) {
                continue;
            }

            list.add(g);

            for (String _g : getInherited(g)) {
                list.add(_g);
            }
        }

        return list;
    }

    /**
     * Save the auths
     */
    public void saveAuthEntries() {
        final File file = new File(AUTH_FILE);

        if (file.exists()) {
            file.delete();
        }

        FileWriter writer = null;

        try {
            file.createNewFile();

            writer = new FileWriter(file);

            for (final String username : authTable.keySet()) {
                final String password = authTable.get(username);

                writer.write(username + ":" + password + "\r\n");
                writer.flush();
            }

            writer.close();
        } catch (final Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * Transform a string into one char
     *
     * @param str
     *            The string to transform
     * @param chr
     *            The char to transform all chars to (ie '*')
     * @return the transformed string
     */
    public String transform(String str, char chr) {
        final char[] charArray = str.toCharArray();

        for (int i = 0; i < charArray.length; i++) {
            charArray[i] = '*';
        }

        return new String(charArray);
    }

    /**
     * Update the player cache
     *
     * @param player
     *            the player to update
     */
    public void updatePlayerCache(Player player) {
        player.setCanModifyWorld(false); // lock them down!!
        player.setIgnoreRestrictions(false);
        player.setAdmin(false);
        if (canRegister(player)) {
            player.setCommands(new String[]{"register", "login"});
        } else {
            player.setCommands(new String[0]);
        }
        player.setGroups(new String[0]);
        if (etc.getDataSource().doesPlayerExist(player.getName())) {
            Player p = etc.getInstance().getDataSource().getPlayer(player.getName());
            if (p.canBuild()) {
                player.setCanModifyWorld(true);
                //playerCache.put(player.getName().toLowerCase(), new InventoryBackup(player));
            }
        }

    }
    public boolean canRegister(Player player){
        return !onlyAllowedUsersCanRegister || player.canUseCommand("/register");
    }
}
