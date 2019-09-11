package shared;

import com.bowie.javagl.Quaternion;
import com.bowie.javagl.RigidBody;
import com.bowie.javagl.Vector3;

public class RenderState {
	public RigidBody body;
	public BodyState state;
	public ErrorOffset offset;
	
	public RenderState(RigidBody b) {
		setBody(b);
	}
	
	public void setBody(RigidBody b) {
		this.body = b;
		
		if (state == null || offset == null && body != null) {
			// initialize
			this.state = new BodyState(body);
			this.offset = new ErrorOffset();
		}
	}
	
	public void updateState() {
		if (body != null) {
			this.state.updateStateFrom(body);
			// reduce error
			this.offset.reduceError();
			// append
			this.state.offsetBy(offset);
		}
	}
	
	public void recalculateErrorOffset() {
		BodyState newState = new BodyState(body);
		this.offset.computeOffset(newState, state);
		
//		if (this.offset.isSignificant()) {
//			System.out.println(this.offset);
//		}
		// also, set state
//		updateState();
//		this.state.setTo(newState);
	}
	
	public void calculateRenderData(Vector3 pos, Quaternion rot, float dt) {
		// just for fun, try to compute smooth shit
		if (pos != null) {
			Vector3 vel = state.vel;
			Vector3 scaledVel = new Vector3(vel);
			scaledVel.scale(dt);
//			pos.setTo(this.state.pos);
			Vector3.add(state.pos, scaledVel, pos);
		}
		
		if (rot != null) {
			Vector3 angVel = state.angVel;
			Quaternion scaledAngvel = new Quaternion(
					angVel.x,
					angVel.y,
					angVel.z,
					0
					);
			scaledAngvel.scale(.5f);
			Quaternion.mul(scaledAngvel, state.rot, scaledAngvel);
			scaledAngvel.scale(dt);
			Quaternion.add(state.rot, scaledAngvel, rot);
			rot.normalize();
//			rot.setTo(this.state.rot);
		}
	}
}
