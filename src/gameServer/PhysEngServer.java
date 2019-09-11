package gameServer;

import java.net.SocketAddress;
import java.nio.ByteBuffer;

import shared.Shared;

import com.bowie.javagl.Physics;
import com.bowie.javagl.RigidBody;
import com.bowie.javagl.Vector3;

import networkGeneric.GameServer;
import networkGeneric.Input;
import networkGeneric.MessageID;
import networkGeneric.NetSerializable;
import networkMessages.MsgInitialData;
import networkMessages.MsgInput;
import networkMessages.MsgSnapshot;

public class PhysEngServer extends GameServer {
	
	public class PhysEngClient extends GameServer.Client {
		
		// this will hold rigid body data
		public RigidBody body;

		public PhysEngClient(GameServer master, SocketAddress addr, Object data) {
			super(master, addr, data);
		}

		@Override
		public void onCreated() {
			
			synchronized (world) {
				RigidBody b = Shared.addClientBody(world, null);
				this.body = b;
				
				// grab its id and send initial data
				MsgInitialData msg = new MsgInitialData(b.getId());
				
				beginSend();
				sendMessage(msg);
				endSend();
				
				System.out.println("Created body : " + b.getId());
			}
		}

		@Override
		public void onDestroyed() {
			// for now, do nothing
			System.out.println("Client destroyed: " + id);
		}

		@Override
		public int onProcessInput(Input inp) {
			if (inp != null && this.body != null) {
				// welp, let's compute and apply force
				MsgInput pInp = (MsgInput) inp;
				
				pInp.apply(body);
				
				return 1;
			}
			return 0;
		}

		@Override
		public boolean onMessage(NetSerializable msg) {
			switch (msg.getId()) {
			case MessageID.MSGC_COMMAND:
				// well, it's an input. apply it?
				MsgInput inp = (MsgInput) msg;
				this.pushInput(inp);
				
				System.out.println(String.format("Client %d input id %d", this.id, inp.getFrameNumber()));
				return true;
			}
			return false;
		}
		
	}

	public PhysEngServer(String addr, int port) {
		super(addr, port);
	}

	@Override
	public Client onConnect(SocketAddress addrFrom) {
		// gotta check if already conencted?
		
		PhysEngClient cl = (PhysEngClient) getClientByAddress(addrFrom);
		if (cl != null) {
			// already connected. bail
			System.out.println("Client already connected: " + cl.id);
			
			return null;
		} else {
			// create new client
			cl = new PhysEngClient(this, addrFrom, null);
			
			// set pending
			cl.setMaxPendingInput(16);	// maximum is 16 ahead of server
		}
		
		return cl;
	}

	@Override
	public boolean onDisconnect(Client cl) {
		if (cl != null) {
			System.out.println(String.format("Client %d disconnected", cl.id));
			this.removeClientById(cl.id);
		}
		return true;
	}

	@Override
	public void onNetworkTick() {
		// record the last tick number
		int lastTick = this.tickNumber - 1;
		
		// build snapshot
		synchronized (world) {
			lastSnapshot = new MsgSnapshot(lastTick, world);
		}
		
		
		// now, send to all clients
		for (Client cl : clients) {
			PhysEngClient client = (PhysEngClient) cl;
			
			if (client != null) {
				client.beginSend();
				lastSnapshot.setLastCommand( client.lastProcessedInput );
				client.sendMessage(lastSnapshot);
				client.endSend();
			}
		}
	}

	@Override
	public void onTick(float dt) {
		// all client inputs are already applied here
		// so just step the world and build snapshot
		synchronized(world) {
			// might spawn something if we have pending spawn
			if (initialSpawn > 0 && spawnTimer <= 0) {
				Shared.addClientBody(world, null);
				spawnTimer += 2.f;
				initialSpawn--;
			} else
				spawnTimer-= dt;
//			System.out.println("Update: " + tickNumber);
			world.step(dt);
			
			// reset object if fall outside of world
			for (RigidBody b : world.getBodies()) {
				if (b.isFixed())
					continue;
				if (b.isSleeping())
					continue;
				
				// check pos
				if (b.getPos().y < -10.f) {
					// reset all state
					b.setPos(new Vector3(0, 10, 0));
					b.setVel(new Vector3());
				}
			}
		}
		
	}

	@Override
	public NetSerializable onParseMessage(short msgId, ByteBuffer buf, Client cl) {
		// try to parse message, return a packet to process
		switch (msgId) {
		case MessageID.MSGC_COMMAND:
			MsgInput msg = new MsgInput();
			if (msg.onDeserialize(buf))
				return msg;
			return null;
		}
		
		return null;
	}

	@Override
	public boolean onInit() {
		// init our physics engine here
		world = new Physics(10, 7, 0.04f, 0.1f);
		world.registerCommonCollisionCallback();
		
		// create world
		Shared.initPhysicsWorld(world);
		
		return true;
	}
	
	public Physics world = null;
	public MsgSnapshot lastSnapshot = null;
	public int initialSpawn = 0;
	public float spawnTimer = 0;
}
