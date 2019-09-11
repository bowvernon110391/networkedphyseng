package networkGeneric;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Stack;

public abstract class GameServer {
	public static final int CIRCULAR_ARR_SIZE = 32;
	public static final int BUFFER_SIZE = 1024;
	// here we make generic version of game server
	// on server, there are several event
	
	// on message, when there's message to handle
	
	
	// on connect, when player tries to connect
	public abstract Client onConnect(SocketAddress addrFrom);
	// on disconnect, when player disconnects
	public abstract boolean onDisconnect(Client cl);
	// on network tick, when it's time to send updates
	public abstract void onNetworkTick();
	// on tick, for game update
	public abstract void onTick(float dt);
	// on parse, for parsing
	public abstract NetSerializable onParseMessage(short msgId, ByteBuffer buf, Client cl);
	/// when got message
	
	// on init
	public abstract boolean onInit();
	
	
	// server has client data
	public abstract class Client {
		// client has reference to
		public GameServer server;	// server, for flushing data
		public Object data;			// the custom data
		public SocketAddress addr;	// its address
		public int id;				// id
		public long lastRecv = -1;		// when last receiving?
		public long disconnectTimer = 0;
		
		// client also has list of pending inputs
		// make it 32
		public int inputCount = 0;
		public int pendingInputCount = 0;
		public int lastProcessedInput = -1;
		public Input [] pendingInput = new Input[CIRCULAR_ARR_SIZE];
		public int maxPendingInput = -1;
		
		public int getMaxPendingInput() {
			return maxPendingInput;
		}

		public void setMaxPendingInput(int maxPendingInput) {
			this.maxPendingInput = maxPendingInput;
		}



		// this is to hold game messages
		public List<NetSerializable> pendingMessages = new ArrayList<>();
		
		// this is the bare minimum
		public Client(GameServer master, SocketAddress addr, Object data) {
			this.server = master;
			this.data = data;
			this.addr = addr;
			this.id = master.getFreeId();
			this.disconnectTimer = 10000;	// 10 seconds disconnects
		}
		
		abstract public void onCreated();
		abstract public void onDestroyed();
		abstract public int onProcessInput(Input inp); 
		abstract public boolean onMessage(NetSerializable msg);
		
		public void setData(Object data) {
			this.data = data;
		}
		
		public Object getData() {
			return this.data;
		}
		
		public boolean idleTooLong(float dt) {
			this.disconnectTimer -= dt * 1000;
			
			return this.disconnectTimer <= 0;
		}
		
		public void resetDisconnectTimer(long val) {
			this.disconnectTimer = val;
		}
		
		public void sendAcceptance() {
			this.beginSend();
			
			server.sendBuf.putShort(MessageID.SMSGS_ACCEPT);
			server.sendBuf.putInt(id);
			
			this.endSend();
		}
		
		protected int findPendingInput(int frameId) {
			if (pendingInputCount <= 0)
				return -1;
			
			int startId = inputCount - pendingInputCount;
			int endId = inputCount;
			
			for (int i=startId; i<endId; i++) {
				Input inp = pendingInput[i % CIRCULAR_ARR_SIZE];
				if (inp != null)
					if (inp.frameNum == frameId)
						return i;
			}
			
			return -1;
		}
		
		public void pushInput(Input inp) {
			// as long as it's > lastProcessedInput, accept
			if (inp.getFrameNumber() > lastProcessedInput && findPendingInput(inp.getFrameNumber()) < 0) {
				pendingInput[(inputCount++) % CIRCULAR_ARR_SIZE] = inp;
				pendingInputCount++;
			} else {
				System.out.println(String.format("Client %d obsolete input %d <= %d", id, inp.frameNum, lastProcessedInput));
			}
		}
		
		public void purgeOldInputs() {
			// remove old input, because client simulate too far ahead!
			if (maxPendingInput > 0) {
				while (pendingInputCount > maxPendingInput) 
					popInput();
			}
		}
		
		public Input popInput() {
			if (inputCount <= 0 || pendingInputCount <= 0)
				return null;
			
			Input p = pendingInput[(inputCount - (pendingInputCount--)) % CIRCULAR_ARR_SIZE];
			
			System.out.println(String.format("Client %d pending %d inputs", id, pendingInputCount));
			
			return p;
		}
		
		public void beginSend() {
			this.server.sendBuf.clear();
		}
		
		public void sendMessage(NetSerializable msg) {
			if (msg != null) {
				this.server.sendBuf.putShort(msg.getId());
				msg.onSerialize(this.server.sendBuf);
			}
		}
		
		public void endSend() {
			this.server.sendBuf.flip();
			try {
				this.server.socket.send(sendBuf, addr);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		public int processInput() {
			// sort pending
			sortPendingInputs();
			
			// purge old inputs
			purgeOldInputs();
			
			// just pop input
			Input inp = this.popInput();
			
			if (inp == null)
				return 0;
			
			// update last processed
			this.lastProcessedInput = inp.getFrameNumber();
			
			// process it
			return this.onProcessInput(inp);
		}
		
		
		
		public void sortPendingInputs() {
			if (pendingInputCount <= 0)
				return;
			
			int startId = inputCount-pendingInputCount;
			int endId = inputCount;
			
			int sorted = 0;
			// sort em. (MUST BE CALLED BEFORE PROCESSING)
			for (int i=startId; i<endId-1; i++) {
				for (int j=startId; j<endId-1; j++) {
					
					Input a = pendingInput[j % CIRCULAR_ARR_SIZE];
					Input b = pendingInput[(j+1) % CIRCULAR_ARR_SIZE];
					
					if (a.getFrameNumber() > b.getFrameNumber()) {
						// uh oh, swap
						pendingInput[j % CIRCULAR_ARR_SIZE] = b;
						pendingInput[(j+1) % CIRCULAR_ARR_SIZE] = a;
						
						sorted++;
					}
					
				}
			}
			
			System.out.println(String.format("Client %d sorting %d, sorted %d", id, pendingInputCount, sorted));
		}
	}
	// client has ID, so let's implement them as array
	public HashMap<SocketAddress, Integer> clientMapId = new HashMap<>();
	public List<Client> clients = new ArrayList<>();
	public Stack<Integer> freeId = new Stack<>();
	public DatagramChannel socket;
	public ByteBuffer recvBuf = ByteBuffer.allocate(BUFFER_SIZE);
	public ByteBuffer sendBuf = ByteBuffer.allocate(BUFFER_SIZE);
	public int tickNumber = 0;
	
	
	public int updateTickRate = 30;
	public int networkTickRate = 10;
	
	public long autoDisconnectTime = 30000;	// 30 secs
	
	public float updateTime = 1.f / 30;
	public float networkUpdateTime = 1.f / 10;
	
	public float updateTimer = 0;
	public float networkUpdateTimer = 0;
	
	public GameServer(String addr, int port) {
		this.createSocket(addr, port);
	}
	
	public ByteBuffer getSendBuffer() {
		return sendBuf;
	}
	
	public boolean createSocket(String bindAddr, int port) {
		try {
			socket = DatagramChannel.open();
			socket.configureBlocking(false);
			
			socket.bind(new InetSocketAddress(bindAddr, port));
			
			System.out.println(String.format("Server created @ %s:%d", bindAddr, port));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			return false;
		}
		
		return true;
	}
	
	public void setTickRate(int updateTick, int networkTick) {
		updateTick = Math.min(1, updateTick);
		networkTick = Math.min(1, networkTick);
		
		this.updateTickRate = updateTick;
		this.networkTickRate = networkTick;
		
		// compute and reset timer
		this.updateTimer = this.networkUpdateTimer = 0;
		this.updateTime = 1.f/updateTick;
		this.networkUpdateTime = 1.f/networkTick;
	}
	
	public int getFreeId() {
		if (freeId.empty())
			return clients.size();
		
		return freeId.pop();
	}
	
	public Client getClientById(int id) {
		if (id >= 0 && id < clients.size())
			return clients.get(id);
		
		return null;
	}
	
	public Client getClientByAddress(SocketAddress addr) {
		if (clientMapId.get(addr) == null)
			return null;
		
		int clId = clientMapId.get(addr);
		
		if (clId >= 0 && clId < clients.size())
			return clients.get(clId);
		
		return null;
	}
	
	public boolean addClient(Client cl) {
		if (cl == null)
			return false;
		
		// make sure it's not invalid
		if (cl.id < 0)
			return false;
		
		// if it's new, add
		if (cl.id >= clients.size()) { 
			clients.add(cl);
		} else if (cl.id < clients.size()) {
			// grab it first
			if (clients.get(cl.id) != null)
				return false;
			
			// can set instead
			clients.set(cl.id, cl);
		}
		
		// callback
		cl.onCreated();
		
		return true;
	}
	
	public void removeClientById(int id) {
		if (id < 0 || id > clients.size())
			return;
		
		// grab client
		Client cl = getClientById(id);
		
		if (cl != null) {
			// call removal
			cl.onDestroyed();
			
			// remove map too
			if (clientMapId.containsKey(cl.addr))
				clientMapId.remove(cl.addr);
		}
		
		// nullify, and push free id
		freeId.push(id);
		clients.set(id, null);
//		clients.remove(id);
		
		
	}
	
	// server's main function
	public void readMessages() {
		while (true) {
			try {
				recvBuf.clear();
				SocketAddress sendAddr = socket.receive(recvBuf);
				recvBuf.flip();
				
				// no more packet available
				if (sendAddr == null)
					break;
				
				System.out.println(String.format("%s has %d bytes", sendAddr, recvBuf.remaining()));
				
				// wew, get message, process it?
				// first 4 byte is client id
				// next, it's message id
				
				// MESSAGE consists of CLIENT_ID followed by MSG_JOIN_REQUEST
				// small buffer size means shit
				if (recvBuf.remaining() < 6)
					break;
				
				// check if it's valid
				int clientId = recvBuf.getInt();
				
				if (clientId == -1) {
					// perhaps it's requesting to join?
					if (recvBuf.getShort() == MessageID.SMSGC_CONNECT) {
						
						// it's trying to join, so handle it
						Client newClient = this.onConnect(sendAddr);
						
						// Accepted client is either this one, or the one already
						// in list
						Client accepted = (newClient == null ? getClientByAddress(sendAddr) : newClient );
						
						if (accepted != null)
							accepted.sendAcceptance();
						
						// mark it if accepted
						if (newClient != null) {
							clientMapId.put(sendAddr, newClient.id);
							newClient.resetDisconnectTimer(autoDisconnectTime);
							this.addClient(newClient);
						} else
							System.out.println(String.format("rejected connection from %s", sendAddr));
						
						// ok, just skip the rest of the bytes
						continue;
					}
				}
				
				// well, let's see if it's a match
				if (clientMapId.get(sendAddr) == null) {
					// not found, continue
					continue;
				}
				
				int clId = clientMapId.get(sendAddr);
				
				// not match!!
				if (clientId != clId) {
					System.out.println(String.format("REJECT_NOT_MATCH_SENDER_ID: %s,  %d != %d", sendAddr, clientId, clId));
					continue;
				}
				
				// match!! read em
				
				Client cl = getClientById(clId);
				
				if (cl == null) {
					// no such client, reject
					continue;
				}
				
				// mark last recv
				cl.lastRecv = System.currentTimeMillis();
				cl.resetDisconnectTimer(this.autoDisconnectTime);
				
				NetSerializable msg = null;
				// read all messages in the packet from this particular client
				do {
					msg = this.defaultParser(recvBuf, cl);
					
					if (msg != null) {
						// callback
						cl.onMessage(msg);
					}
				} while (msg  != null);
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
				break;
			}
		}
	}
	
	public void processInputs() {
		int numCl = 0;
		int numInp = 0;
		// loop over all clients
		for (Client cl : clients) {
			if (cl != null) {
				numCl++;
				// ok, let's 
				numInp += cl.processInput();
			}
		}
		
		if (numCl != 0 && numInp != 0) {
			System.out.println(String.format("processed %d of %d clients", numInp, numCl));
		}
	}
	
	public void purgeIdleClients(float dt) {
		Iterator<Client> it = clients.iterator();
		
		List<Client> toBeRemoved = new ArrayList<>();
		
		while (it.hasNext()) {
			Client cl = (Client) it.next();
			
			if (cl != null) {
				if (cl.idleTooLong(dt)) {
					toBeRemoved.add(cl);
				}
			}
		}
		
		for (Client cl : toBeRemoved) {
			this.removeClientById(cl.id);
		}
	}
	
	// the update tick?
	public void serverUpdate(float dt) {
		// 1st read messages (this also handles connection)
		this.readMessages();
		
		// next, process inputs
		this.processInputs();
		
		// next, tick
		this.onTick(dt);
		
		// increase tick number
		this.tickNumber++;
		
		// also, purge client whose idle too long
		this.purgeIdleClients(dt);
	}
	
	// handle generic message
	private NetSerializable defaultParser(ByteBuffer buf, Client cl) {
		if (!buf.hasRemaining() || buf.remaining() < 2)
			return null;
		
		short msgId = buf.getShort();
		
		// handle server specific message
		switch (msgId) {
		
		case MessageID.SMSGC_DISCONNECT:
			this.onDisconnect(cl);
			return null;	// no more message to process then
		}
		
		return this.onParseMessage(msgId, buf, cl);
	}
	
	// the network tick
	public void networkUpdate() {
		this.onNetworkTick();
	}
	
	public void update(float elapsed) {
		// first, do normal update
		updateTimer += elapsed;
		networkUpdateTimer += elapsed;
		
		while (updateTimer >= updateTime) {
			updateTimer -= updateTime;
			this.serverUpdate(updateTime);
		}
		
		while (networkUpdateTimer >= networkUpdateTime) {
			networkUpdateTimer -= networkUpdateTime;
			this.networkUpdate();
		}
	}
	
	public void runServerThread() {
		
		Thread t = new Thread(new Runnable() {
			
			@Override
			public void run() {
				// run init code
				if (!GameServer.this.onInit()) {
					System.out.println("Server init failed!");
					return ;
				}
				
				System.out.println("Server initialized.");
				
				long lastTime = System.nanoTime();
				
				while (true) {
					long currTime = System.nanoTime();
					long elapsedNanosec = (currTime - lastTime);
					float elapsed = elapsedNanosec/1000000000.f;
					lastTime = currTime;
					
					GameServer.this.update(elapsed);
					
					// sleep remaining
					long endTime = System.nanoTime();
					long simTime = (endTime - currTime) / 1000000;
					long normalTime = 1000 / GameServer.this.updateTickRate;
					
					long sleepTime = normalTime - simTime;
					
					if (sleepTime > 0) {
						try {
							Thread.sleep(normalTime - simTime);
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
		});
		
		t.start();
	}
}
