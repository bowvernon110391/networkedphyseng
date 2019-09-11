package networkGeneric;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;

public abstract class GameClient {
	public static final int BUFFER_SIZE = 1024;
	public static final int INPUT_HISTORY_SIZE = 32;
	public static final long DEFAULT_DISCONNECT_TIMER = 5000;	// 5 secs inactivity
	
	public DatagramChannel socket;
	public SocketAddress addr;
	public int clientId = -1;
	public ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
	public long disconnectTimer = DEFAULT_DISCONNECT_TIMER;
	public long lastMessageTime = 0;
	
	// list of input and its historical
	public Input [] inputHistory = new Input[INPUT_HISTORY_SIZE]; 
	public int inputHistoryCount = 0;
	
	// method that needs implementing
	abstract public NetSerializable onParseMessage(int msgId, ByteBuffer buf);
	abstract public void onMessage(NetSerializable msg);
	abstract public void onConnected();
	abstract public void onDisconnected();
	
	// this handles the background message passing between client/server
	
	public GameClient(String serverAddr, int serverPort) {
		createSocket(serverAddr, serverPort);
	}
	
	// create socket
	public boolean createSocket(String serverAddr, int serverPort) {
		try {
			socket = DatagramChannel.open();
			socket.configureBlocking(false);
			
			addr = new InetSocketAddress(serverAddr, serverPort);
			
			return true;
			
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
	}
	
	// push input to be sent
	public void pushInput(Input inp) {
		// put in circular buffer
		inputHistory[(inputHistoryCount++) % INPUT_HISTORY_SIZE] = inp;
	}
	
	// flush it
	public void flushInputs(int backlog) {
		// send all input + n history
		int startId = inputHistoryCount - backlog - 1;
		int endId = inputHistoryCount;
		
		// send oldest first
		beginSend();
		
		for (int i=startId; i<endId; i++) {
			if (i < 0)
				continue;
			
			Input inp = inputHistory[i % INPUT_HISTORY_SIZE];
			if (inp != null)
				sendMessage(inp);
		}
		
		endSend();
	}
	
	// check connection status
	public boolean isConnected() {
		return clientId >= 0;
	}
	
	// for disconnection purpose
	public void disconnect() {
		if (!isConnected())
			return;
		
		beginSend();
		buffer.putShort(MessageID.SMSGC_DISCONNECT);
		endSend();
	}
	
	// bulk sending
	public void beginSend() {
		if (!isConnected())
			return;
		
		// clear buffer and add client id
		buffer.clear();
		buffer.putInt(clientId);
	}
	
	public void sendMessage(NetSerializable msg) {
		if (!isConnected())
			return;
		
		buffer.putShort(msg.getId());
		msg.onSerialize(buffer);
	}
	
	public void endSend() {
		if (!isConnected())
			return;
		
		// send it
		try {
			buffer.flip();
			socket.send(buffer, addr);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private NetSerializable defaultParser(int msgId, ByteBuffer buf) {
		switch (msgId) {
		case MessageID.SMSGS_ACCEPT:
			this.clientId = buf.getInt();
			this.onConnected();
			
			// also reset timer here
			lastMessageTime = System.currentTimeMillis();
			
			return null;
		}
		
		return onParseMessage(msgId, buf);
	}
	
	// attempt connect
	public void attemptConnect() throws IOException {
		buffer.clear();
		buffer.putInt(-1);
		buffer.putShort((short) -1);
		buffer.flip();
		
		socket.send(buffer, addr);
	}
	
	// handles message reading and connecting
	public void readMessages() {
		// check for inactivity
		if (lastMessageTime > 0) {
			if (System.currentTimeMillis() - lastMessageTime > disconnectTimer) {
				this.onDisconnected();
				
				return;
			}
		}
		
		try {
			
			while (true) {
				buffer.clear();
				SocketAddress sendAddr = socket.receive(buffer);
				buffer.flip();
				
				// no more message
				if (sendAddr == null)
					break;
				
				NetSerializable msg;
				do {
					if (buffer.remaining() < 2)
						break;
					
					int msgId = buffer.getShort();
					msg = defaultParser(msgId, buffer);
					
					if (msg != null) {
						this.onMessage(msg);
						
						// reset timer
						lastMessageTime = System.currentTimeMillis();
					}
				} while (msg != null);
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
