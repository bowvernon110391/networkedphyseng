package networkMessages;

import java.nio.ByteBuffer;

import networkGeneric.MessageID;
import networkGeneric.NetSerializable;

public class MsgInitialData implements NetSerializable {
	
	public int bodyId;
	
	public MsgInitialData(int bodyId) {
		this.bodyId = bodyId;
	}
	
	public MsgInitialData() {
		bodyId = -1;
	}

	@Override
	public boolean onSerialize(ByteBuffer buf) {
		buf.putInt(bodyId);
		return true;
	}

	@Override
	public boolean onDeserialize(ByteBuffer buf) {
		bodyId = buf.getInt();
		return true;
	}

	@Override
	public short getId() {
		return MessageID.MSGS_INIT_DATA;
	}
}
