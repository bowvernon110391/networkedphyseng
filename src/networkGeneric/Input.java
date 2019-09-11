package networkGeneric;

public abstract class Input implements NetSerializable {
	protected int frameNum;
	
	public long timeCreated;
	
	public Input() {
		this.frameNum = 0;
		this.timeCreated = System.currentTimeMillis();
	}
	
	public Input(int frameNum) {
		setFrameNumber(frameNum);
		this.timeCreated = System.currentTimeMillis();
	}
	
	public int getFrameNumber() {
		return frameNum;
	}
	
	public void setFrameNumber(int frameNum) {
		this.frameNum = frameNum;
	}
}
