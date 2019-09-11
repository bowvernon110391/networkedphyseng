package networkMessages;

import java.nio.ByteBuffer;

import com.bowie.javagl.RigidBody;
import com.bowie.javagl.Vector3;

import networkGeneric.Input;
import networkGeneric.MessageID;

public class MsgInput extends Input {
	public static final float MAX_FORCE = 10000;
	public static final byte 
		KEY_JUMP = 1,
		KEY_RESET = 1<<1;
						
	
	// the input
	public short fX, fZ, fY;
	public byte keyPress;
	
	public MsgInput() {
		super();
	}
	
	public MsgInput(int frameNum) {
		super(frameNum);
	}
	
	public void apply(RigidBody b) {
		if (b == null)
			return;
		
		Vector3 f = deQuantize(MsgInput.MAX_FORCE);
		Vector3 t = computeTorque();
		
		b.applyForce(f);
		b.applyTorque(t);
	}
	
	public void setKeyPress(byte val) {
		keyPress = val;
	}
	
	public boolean isKeyPressed(byte keyCode) {
		return (keyPress & keyCode) != 0;
	}
	
	public void setForce(Vector3 f) {
		quantize(f, MAX_FORCE);
	}
	
	public void quantize(Vector3 f, float scale) {
		fX = (short) (f.x/scale * 32767);
		fY = (short) (f.y/scale * 32767);
		fZ = (short) (f.z/scale * 32767);
	}
	
	public Vector3 deQuantize(float scale) {
		return new Vector3(
				fX * scale / 32767,
				fY * scale / 32767,
				fZ * scale / 32767
				);
	}
	
	public Vector3 computeTorque() {
		Vector3 t = new Vector3(0, 0, 0);
		if (this.isKeyPressed(KEY_JUMP))
			t.y = MAX_FORCE;
		
		return t;
	}

	@Override
	public boolean onSerialize(ByteBuffer buf) {
		// must check if we still got space
		if (buf.remaining() < 11)
			return false;
		
		// message type goes here
		
		// [0] int : frameNum
		buf.putInt(frameNum);
		// [4] short[3] : fX fY fZ
		buf.putShort(fX);
		buf.putShort(fY);
		buf.putShort(fZ);
		// [10] byte : keyPress
		buf.put(keyPress);
		return true;
	}

	@Override
	public boolean onDeserialize(ByteBuffer buf) {
//		if (buf.remaining() < 11)
//			return false;
		
		frameNum = buf.getInt();	// 4
		fX = buf.getShort();		// 2
		fY = buf.getShort();		// 2
		fZ = buf.getShort();		// 2
		keyPress = buf.get();		// 1
		
		return true;
	}

	@Override
	public short getId() {
		return MessageID.MSGC_COMMAND;
	}

}
