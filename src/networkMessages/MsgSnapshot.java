package networkMessages;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import shared.BodyState;

import com.bowie.javagl.Physics;
import com.bowie.javagl.RigidBody;

import networkGeneric.Input;
import networkGeneric.MessageID;
import networkGeneric.NetSerializable;

public class MsgSnapshot implements NetSerializable {
	
	
	public MsgSnapshot(int serverTick, Physics world) {
		this.serverTick = serverTick;
		this.buildSnapshot(world);
	}
	
	public MsgSnapshot() {
		this.serverTick = -1;
		this.lastCommand = -1;	
	}

	public void buildSnapshot(Physics world) {
		for (RigidBody b : world.getBodies()) {
			// skip fixed bodies
			if (b.isFixed())
				continue;
			
			states.add(new BodyState(b));
		}
	}
	
	public void setLastCommand(int cmdNum) {
		this.lastCommand = cmdNum;
	}
	
	public BodyState getStateById(int id) {
		for (BodyState sb : states) {
			if (sb.id == id)
				return sb;
		}
		
		return null;
	}
	
	@Override
	public boolean onSerialize(ByteBuffer buf) {
		
		// skip byte check, write data directly
		buf.putInt(serverTick);
		buf.putInt(lastCommand);
		buf.put((byte) states.size());
		
		for (BodyState bs : states) {
			// put id
			buf.putInt(bs.id);
			// put pos
			buf.putFloat(bs.pos.x);
			buf.putFloat(bs.pos.y);
			buf.putFloat(bs.pos.z);
			
			// put rot
			buf.putFloat(bs.rot.x);
			buf.putFloat(bs.rot.y);
			buf.putFloat(bs.rot.z);
			buf.putFloat(bs.rot.w);
			
			// put vel
			buf.putFloat(bs.vel.x);
			buf.putFloat(bs.vel.y);
			buf.putFloat(bs.vel.z);
			
			// put ang vel
			buf.putFloat(bs.angVel.x);
			buf.putFloat(bs.angVel.y);
			buf.putFloat(bs.angVel.z);
		}
		
		return false;
	}

	@Override
	public boolean onDeserialize(ByteBuffer buf) {
		serverTick = buf.getInt();
		lastCommand = buf.getInt();
		
		int numBodies = Byte.toUnsignedInt(buf.get());	// cant be more than 256 I guess
		
		// sanity check
		if (numBodies > 256)
			return false;
		// okay, read em
		for (int i=0; i<numBodies; i++) {
			BodyState bs = new BodyState();
			
			bs.id = buf.getInt();
			
			bs.pos.setTo(buf.getFloat(), buf.getFloat(), buf.getFloat());
			bs.rot.setTo(buf.getFloat(), buf.getFloat(), buf.getFloat(), buf.getFloat());
			bs.vel.setTo(buf.getFloat(), buf.getFloat(), buf.getFloat());
			bs.angVel.setTo(buf.getFloat(), buf.getFloat(), buf.getFloat());
			
			// add to list
			states.add(bs);
		}
		
		return true;
	}
	
	public int lastCommand;
	public int serverTick;
	public List<BodyState> states = new ArrayList<>();
	
	// optional and not serialized
	public Input lastInput;
	
	public void setLastInput(Input inp) {
		lastInput = inp;
	}
	
	public Input getLastInput() {
		return lastInput;
	}
	
	@Override
	public short getId() {
		return MessageID.MSGS_SNAPSHOT;
	}
	
	// static helper function
	public static List<BodyState> getUnionState(MsgSnapshot newState, MsgSnapshot oldState) {
		// make a hash set
		Set<BodyState> uniqueStates = new HashSet<>();
		
		// add newstate first, so it won't get replaced
		// if similar state exists in old state
		if (newState != null)
			uniqueStates.addAll(newState.states);
		
		// add oldStates.
		if (oldState != null)
			uniqueStates.addAll(oldState.states);
		
		return new ArrayList<>(uniqueStates);
	}
}
