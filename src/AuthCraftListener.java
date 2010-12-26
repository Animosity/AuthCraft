import java.util.ArrayList;

public class AuthCraftListener extends PluginListener {
	boolean _DEBUG = false;

	private AuthCraft parent = null;

	public AuthCraftListener(AuthCraft parent) {
		this.parent = parent;
	}

	public boolean onBlockCreate(Player player, Block blockPlaced, Block blockClicked, int itemInHand) {
		/*if (_DEBUG) {
			System.out.println(Thread.currentThread().getStackTrace()[1].getMethodName());
			System.out.println(Boolean.toString(parent.checkAuth(player)));
		}*/
		return parent.checkAuth(player);
	}

	public boolean onBlockDestroy(Player player, Block block) {
		return parent.checkAuth(player);
	}

	public boolean onChat(Player player, String message) {
		return parent.checkAuth(player);
	}

	@Override
	public boolean onOpenInventory(Player player, Inventory inventory) {
		return parent.checkAuth(player);
	}

	public boolean onItemDrop(Player player, Item item) {
		return parent.checkAuth(player);
	}

	/**
	 * Login/register users!
	 * 
	 * sidenote: why a string array???? wat.
	 */
	@Override
	public boolean onCommand(Player player, String[] split) {
		if (split.length == 0) {
			return false;
		}

		final String command = split[0].substring(1);

		if (command.equalsIgnoreCase("login")) {
			if (split.length != 2) {
				player.sendMessage("Usage: /login <pass>");
				return true;
			}

			final String username = player.getName().toLowerCase();
			final String password = split[1];

			/*
			 * They are registered! If they aren't - don't do anything
			 */
			if (parent.authTable.containsKey(username) && !parent.authenticated.contains(username)) {
				final String realPassword = parent.authTable.get(username);

				if (!realPassword.equals(parent.encrypt(password))) {
					parent.log("Warning: " + player.getName().toLowerCase() + " gave the wrong password !! IP: "
							+ player.getIP());
					player.kick("Invalid password."); // be forceful! leave no
					// prisoners!
				} else {
					player.sendMessage(Colors.Green + "Password accepted. Welcome.");
					parent.authenticated.add(player.getName().toLowerCase());

					return parent._login(player);
				}
			}

			return true;
		} else if (command.equalsIgnoreCase("register")) {
			if (!parent.onlineMode && !parent.registerInOfflineMode) {
				return true;
			}

			if (split.length != 2) {
				player.sendMessage("Usage: /register <pass>");
				return true;
			}

			final String username = player.getName().toLowerCase();
			final String password = split[1];

			if (parent.authTable.containsKey(username)) {
				player.sendMessage(Colors.Red + "Your nick is already registered!");
			} else {
				parent.addAuth(username, parent.encrypt(password));

				player.sendMessage(Colors.Green + "Registered using the password: " + Colors.LightBlue
						+ parent.transform(password, '*'));
				parent.authenticated.add(player.getName().toLowerCase());

				parent._login(player);

				return true;
			}

			return true;
		} else if (command.equalsIgnoreCase("pass")) {
			if (!parent.authenticated.contains(player.getName().toLowerCase())) {
				player.sendMessage(Colors.Rose + "You must be authenticated to change your password");
				return true;
			}

			if (split.length != 3) {
				player.sendMessage("Usage: /pass <oldpass> <newpass>");
				return true;
			}

			final String oldPass = split[1];
			final String newPass = split[2];

			if (!parent.authTable.get(player.getName().toLowerCase()).equals(parent.encrypt(oldPass))) {
				player.sendMessage(Colors.Rose + "Invalid password.");
				return true;
			}

			parent.authTable.remove(player.getName().toLowerCase());
			parent.addAuth(player.getName(), parent.encrypt(newPass));
			parent.authenticated.remove(player.getName().toLowerCase());

			player.sendMessage(Colors.Green + "Changed your password to: " + Colors.LightBlue
					+ parent.transform(newPass, '*'));
			player.sendMessage(Colors.Rose + "Please reauthenticate");
			parent.updatePlayerCache(player);

			return true;
		} else if (command.equalsIgnoreCase("reset")) {
			// player.sendMessage("can't use");
			if (!player.canUseCommand("/" + command)) {
				return false;
			}
			// player.sendMessage("can use");
			for (int i = 1; i < split.length; i++) {
				// player.sendMessage("inside for");
				final String playerName = split[i].toLowerCase();
				Player _player = etc.getDataSource().getPlayer(playerName);

				if (_player == null) {
					player.sendMessage(Colors.Rose + "Player " + Colors.LightBlue + playerName + Colors.Rose
							+ " not found.");
					continue;
				}
				Group mainGroup;
				if (_player.getGroups().length > 0) {
					mainGroup = etc.getDataSource().getGroup(_player.getGroups()[0]);
				} else {
					mainGroup = etc.getInstance().getDefaultGroup();
				}

				if (mainGroup == null) {
					player.sendMessage(Colors.Rose + "Player " + Colors.LightBlue + playerName + Colors.Rose
							+ " has invalid group on Data.");
					continue;
				}

				ArrayList<String> inherited = parent.getInherited(mainGroup.Name);

				if (inherited == null) {
					player.sendMessage(Colors.Rose + "Player " + Colors.LightBlue + playerName + Colors.Rose
							+ " not found.");
					continue;
				}

				boolean match = false;

				for (String group : player.getGroups()) {
					// player.sendMessage("inside for, of checking groups");
					if (group.equals("admins")) {
						match = false;
						break;
					}

					if (mainGroup.Name.equals(group)) {
						match = true;
						break;
					}

					if (inherited.contains(group)) {
						match = true;
						break;
					}
				}

				if (match) {
					player.sendMessage(Colors.Rose + "You do not have enough rights to reset " + Colors.LightBlue
							+ playerName);
					continue;
				}

				if (parent.authTable.containsKey(playerName)) {
					// player.sendMessage("removing authentication");
					parent.authTable.remove(playerName);

					if (parent.authenticated.contains(playerName)) {
						parent.authenticated.remove(playerName);
					}

					player.sendMessage(Colors.Green + "Player registration " + Colors.LightBlue + playerName
							+ Colors.Green + " reset");
				} else {
					player.sendMessage(Colors.Rose + "Player " + Colors.LightBlue + playerName + Colors.Rose
							+ " is not registered.");
				}
			}
			// player.sendMessage("Outside for!");
			parent.saveAuthEntries();

			return true;
		}

		if (!parent.authenticated.contains(player.getName().toLowerCase())) {
			return true;
		}

		return false;

	}

	public void onDisconnect(Player player) {
		if (parent.authenticated.contains(player.getName().toLowerCase())) {
			parent.authenticated.remove(player.getName().toLowerCase());
		}
	}

	/**
	 * Log the player in ! :)
	 */

	public void onLogin(Player player) {
		if (parent.authTable.containsKey(player.getName().toLowerCase())) {
			player.sendMessage(Colors.Rose + "Please identify yourself with /login <password>");
		} else if (parent.onlineMode || parent.registerInOfflineMode) {
			if (parent.canRegister(player)) {
				player.sendMessage(Colors.Rose + "You need to register your account before playing.");
				player.sendMessage(Colors.Yellow + "Do NOT use your forum");
				player.sendMessage(Colors.Yellow + "or actual Minecraft account password!");
				player.sendMessage(Colors.Rose + "To register, type /register <password>");
			}
		}

		parent.updatePlayerCache(player);
	}

	@Override
	public void onPlayerMove(Player player, Location from, Location to) {
		final boolean result = parent.checkAuth(player);

		if (result) {
			player.teleportTo(from);
		}
	}

	/**
	 * Called before the console command is parsed. Return true if you don't
	 * want the server command to be parsed by the server.
	 * 
	 * @param split
	 * @return false if you want the command to be parsed.
	 */
	@Override
	public boolean onConsoleCommand(String[] split) {
		if (split[0].contains("save-all") || split[0].contains("stop") || split[0].contains("reload")) {

		}
		return false;
	}
}
