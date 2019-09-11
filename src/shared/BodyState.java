package shared;

import com.bowie.javagl.Quaternion;
import com.bowie.javagl.RigidBody;
import com.bowie.javagl.Vector3;

/**
 * 
 * @author ENPEESEE
 * class BodyState
 * this class contains data for a single rigid body
 * and it's used when synchronizing the simulation
 */
public class BodyState {
	public int id;
	public Vector3 pos, vel, angVel;
	public Quaternion rot;
	
	// generic ctor
	public BodyState() {
		id = -1;
		pos = new Vector3();
		rot = new Quaternion();
		vel = new Vector3();
		angVel = new Vector3();
	}
	
	public BodyState(RigidBody b) {
		// copy all state
		this.id = b.getId();
		this.pos = new Vector3(b.getPos());
		this.rot = new Quaternion(b.getRot());
		this.vel = new Vector3(b.getVel());
		this.angVel = new Vector3(b.getAngVel());
	}
	
	public void updateStateFrom(RigidBody b) {
		if (b == null)
			return;
		
		// just update relevant data
		this.pos.setTo(b.getPos());
		this.rot.setTo(b.getRot());
		
		// derivative data
		this.vel.setTo(b.getVel());
		this.angVel.setTo(b.getAngVel());
	}
	
	public void setTo(BodyState s) {
		if (s == null)
			return ;
		
		this.id = s.id;
		this.pos.setTo(s.pos);
		this.rot.setTo(s.rot);
		this.vel.setTo(s.vel);
		this.angVel.setTo(s.angVel);
	}
	
	public void snap(RigidBody b) {
		if (b == null)
			return;
		
		b.setPos(new Vector3(pos));
		b.setRot(new Quaternion(rot));
		b.setVel(new Vector3(vel));
		b.setAngVel(new Vector3(angVel));
	}
	
	public void offsetBy(ErrorOffset o) {
		if (o == null)
			return;
		
		// just add and multiply em
		Vector3.add(o.posError, new Vector3(pos), pos);
		Quaternion.mul(o.rotError, new Quaternion(rot), rot);
		
		// this might not needed tho
		Vector3.add(o.velError, new Vector3(vel), vel);
		Vector3.add(o.angVelError, new Vector3(angVel), angVel);
		
		rot.normalize();

	}
	
	@Override
	public int hashCode() {
		// just return its id
		return id;
	}
	
	// this is necessary for merging similar state
	@Override
	public boolean equals(Object o) {
		// cannot be equal to null
		if (o == null)
			return false;
		
		// self referencing?
		if (o == this)
			return true;
		
		// only compare id
		BodyState bO = (BodyState) o;
		return id == bO.id;
	}
}