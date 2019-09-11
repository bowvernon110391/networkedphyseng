package networkGeneric;

import java.nio.ByteBuffer;

public interface NetSerializable {
	public boolean onSerialize(ByteBuffer buf);
	public boolean onDeserialize(ByteBuffer buf);
	public short getId();
}
