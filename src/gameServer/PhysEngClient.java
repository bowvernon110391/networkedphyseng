package gameServer;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import shared.BodyState;
import shared.ErrorOffset;
import shared.RenderState;
import shared.Shared;

import com.bowie.javagl.Physics;
import com.bowie.javagl.RigidBody;

import networkGeneric.GameClient;
import networkGeneric.MessageID;
import networkGeneric.NetSerializable;
import networkMessages.MsgInitialData;
import networkMessages.MsgInput;
import networkMessages.MsgSnapshot;

public class PhysEngClient extends GameClient {
	public final static int CIRCULAR_BUFFER_SIZE = 32;
	
	public Physics world;
	public Map<Integer, BodyState> lastServerState = new HashMap<>();
	public List<RenderState> renderStates = new ArrayList<>();
	public RigidBody body;
	public RenderState localRs;
	public int bodyId = -1;
	public boolean initialized = false;
	
	// list of our snapshot
	public MsgSnapshot [] snapshots = new MsgSnapshot[CIRCULAR_BUFFER_SIZE];
	public int snapshotCount  = 0;
	public int lastSnapshotId = -1;
	
	// command bookkeeping
	public int clientTick = 0;

	public PhysEngClient(String serverAddr, int serverPort) {
		super(serverAddr, serverPort);
		
		// initialize world
		world = new Physics(10, 7, 0.04f, 0.1f);
		world.registerCommonCollisionCallback();
		Shared.initPhysicsWorld(world);
		
//		Shared.addClientBody(world, null);
	}
	
	public void tick(MsgInput input, float dt) {
		// only valid if we're initialized
		if (!initialized)
			return;
		// first, we record our tick number
		input.setFrameNumber(clientTick);
		
		// apply it
		input.apply(body);
		
		// tick it
		world.step(dt);
		// update render state too
		for (RenderState rs : renderStates) {
			rs.updateState();
		}
		
		// send the command
		pushInput(input);
		flushInputs(2);
		
		// create snapshot and add it
		MsgSnapshot s = new MsgSnapshot(clientTick, world);
		// record the input
		s.setLastInput(input);
		addSnapshot(s);
		
		// tick number increase
		clientTick++;
	}
	
	public MsgSnapshot getLastSnapshot() {
		if (snapshotCount > 0)
			return snapshots[(snapshotCount-1) % CIRCULAR_BUFFER_SIZE];
		return null;
	}
	
	public boolean isServerSnapshotValid(MsgSnapshot s) {
		if (s != null) {
			if (s.serverTick > lastSnapshotId) {
				lastSnapshotId = s.serverTick;
				return true;
			}
		}
		
		return false;
	}
	
	public int getSnapshotPointerByFrameId(int frameNum) {
		// loop over
		int startId = snapshotCount-1;
		int endId = startId - CIRCULAR_BUFFER_SIZE;
		
		for (int i = startId; i > endId; --i) {
			if (i < 0)
				return -1;
			
			MsgSnapshot s = snapshots[i % CIRCULAR_BUFFER_SIZE];
			
			if (s != null)
				if (s.serverTick == frameNum)
					return i;
		}
		
		return -1;
	}
	
	public MsgSnapshot getSnapshotByFrameId(int frameNum) {
		// loop over
		int ptrId = getSnapshotPointerByFrameId(frameNum);
		if (ptrId >= 0)
			return snapshots[ptrId % CIRCULAR_BUFFER_SIZE];
		
		return null;
	}
	
	public int addSnapshot(MsgSnapshot s) {
		// only add if we have no snapshot
		// or we have, but it's newer
		
		snapshots[(snapshotCount++) % CIRCULAR_BUFFER_SIZE] = s;
		
		return snapshotCount-1;
	}
	
	public void initUsingSnapshot(MsgSnapshot s) {
		if (bodyId < 0) {
			System.out.println(String.format("Cannot init world using snapshot %d. Body ID missing.", s.serverTick));
			return;
		}
		
		if (initialized) {
			System.out.println("World already initialized.");
			return;
		}
		
		// well, let's do this
		for (BodyState state : s.states) {
			RigidBody b = spawnObjectUsingState(state);
			
			if (b.getId() == bodyId)
				body = b;
		}	
		
		System.out.println(String.format("World initialized using tick %d body %d", s.serverTick, bodyId));
		initialized = true;
	}
	
	public RenderState findRenderStateById(int id) {
		for (RenderState rs : renderStates) {
			if (rs.state.id == id)
				return rs;
		}
		return null;
	}
	
	public RigidBody spawnObjectUsingState(BodyState bs) {
		// if we have no data, request to server
		// and bail
		
		// otherwise, spawn here
		RigidBody b = Shared.addClientBody(world, bs);
		RenderState rs = new RenderState(b);
		// also add render state
		renderStates.add(rs);
		
		return b;
	}
	
	public void doServerReconcilliation(MsgSnapshot s) {
		// here, do server reconcilliation
		System.out.println(String.format("Doing server recon for cmd %d @ tick %d", s.lastCommand, clientTick));
		
		int snapshotPtrId = getSnapshotPointerByFrameId(s.lastCommand);
		MsgSnapshot ls = null;
				
		if (snapshotPtrId >= 0)
			ls = snapshots[snapshotPtrId % CIRCULAR_BUFFER_SIZE];
		
		if (ls == null)
			return;
		
		int tickToResimulate = clientTick - s.lastCommand;
		System.out.println("MUST RESIMULATE : " + tickToResimulate);
		
		long rtt = System.currentTimeMillis() - ls.lastInput.timeCreated;
		
		// Okay, we found our original frame. Offset them all then
		System.out.println(String.format("Found local snapshot @ %d ptr %d RTT: %d ms", ls.serverTick, snapshotPtrId, rtt));
		
		// 1. generate valid states using old and server state
		// server's state overriding our old state
		List<BodyState> mergedStates = MsgSnapshot.getUnionState(s, ls);
		
		// 2. loop all over em, snap em back
		for (BodyState bs : mergedStates) {
			// grab body by id
			RigidBody b = world.getBodyById(bs.id);
			
			// if it exists, snap
			if (b != null) {
				bs.snap(b);
			} else {
				// it doesn't exist, spawn new body
				// using state.
				// IT IS BEING SPAWNED IN THE PAST THO
				spawnObjectUsingState(bs);
			}
			
			// record their last server's state
			lastServerState.put(bs.id, bs);
		}
		
		// for now, just snap em (reset state)
		/*for (BodyState sb : s.states) {
			// find the body. if synchronized enough, we should find it
			RigidBody b = world.getBodyById(sb.id);
			
			if (b != null) {
				// save state here for error offset computation
				
				// snap state before resimulation
				sb.snap(b);					
				
				// store server state for rendering purpose
				lastServerState.put(b.getId(), sb);
				
			} else {
				// we have none of that body, gotta add
				// TODO: request server about full info if required though
				Shared.addClientBody(world, sb);
			}
		}*/
		
		// now resimulate several times to catch up to current time
		int simCount = 0;
		// HARD LIMIT ON RESIMULATE AMOUNT
		int maxResim = 24;
		for (int i=snapshotPtrId+1; i<snapshotCount; i++) {
			if (simCount > maxResim)
				break;
			
			simCount++;
			MsgSnapshot snapData = snapshots[i % CIRCULAR_BUFFER_SIZE];
			
			if (snapData == null)
				continue;
			
			
			MsgInput inp = (MsgInput) snapData.getLastInput();
			if (inp != null) {
				inp.apply(body);
			}			
			
			world.step(1.f/30);
		}
		
		System.out.println("SIMULATED: " + simCount);
		
		// compute offset here
		for (RenderState rs : renderStates) {
			rs.recalculateErrorOffset();
		}
	}

	@Override
	public NetSerializable onParseMessage(int msgId, ByteBuffer buf) {
		// parse available message here
		switch (msgId) {
		case MessageID.MSGS_INIT_DATA:
			MsgInitialData initData = new MsgInitialData();
			initData.onDeserialize(buf);
			
			return initData;
			
		case MessageID.MSGS_SNAPSHOT:
			MsgSnapshot snapshot = new MsgSnapshot();
			snapshot.onDeserialize(buf);
			
			return snapshot;
		}
		return null;
	}

	@Override
	public void onMessage(NetSerializable msg) {
		switch (msg.getId()) {
		case MessageID.MSGS_INIT_DATA:
			// got initial data, set it
			MsgInitialData initData = (MsgInitialData) msg;
			this.bodyId = initData.bodyId;
			
			System.out.println("Got initial data? " + bodyId);
			break;
			
		case MessageID.MSGS_SNAPSHOT:
			MsgSnapshot s = (MsgSnapshot) msg;
			
			// use as initialization
			if (!initialized)
				initUsingSnapshot(s);
			
			// add it
			if (isServerSnapshotValid(s)) {
				// it's a valid one, so reconcile
				doServerReconcilliation(s);
			}
			break;
		}
	}

	@Override
	public void onConnected() {
		System.out.println("[CLIENT]: Connected to server, id = " + clientId);
	}

	@Override
	public void onDisconnected() {
		System.out.println("[CLIENT]: DISCONNECTED DUE TO CONNECTION FAILURE!");
		
	}
	
	
}
