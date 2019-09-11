package app;

import gameServer.PhysEngServer;

public class ConsoleServerApp {

	public static void main(String[] args) {
		String serverAddr = "localhost";
		int serverPort = 3232;
		int initialSpawn = 0;
		
		if (args.length >= 1)
			serverAddr = args[0];
		
		if (args.length >= 2)
			serverPort = Integer.parseInt(args[1]);
		
		if (args.length >= 3)
			initialSpawn = Integer.parseInt(args[2]);
		
		PhysEngServer server = new PhysEngServer(serverAddr, serverPort);
		server.initialSpawn = initialSpawn;
		
		server.runServerThread();
	}

}
