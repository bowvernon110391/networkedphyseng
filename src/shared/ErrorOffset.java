package shared;

import com.bowie.javagl.Quaternion;
import com.bowie.javagl.RigidBody;
import com.bowie.javagl.Vector3;

public class ErrorOffset {
	public Vector3 posError;
	public Quaternion rotError;
	public Vector3 velError;		// for vel, snap directly
	public Vector3 angVelError;	// for angVel, snap directly
	public int bodyId;
	
	public static final float ERROR_TOLERANCE = 0.001f;	// TOLERANCE LIMIT
	
	public ErrorOffset(BodyState sA, BodyState sB) {
		posError = new Vector3();
		rotError = new Quaternion();
		velError = new Vector3();
		angVelError = new Vector3();
		
		computeOffset(sA, sB);
	}
	
	public ErrorOffset() {
		posError = new Vector3();
		rotError = new Quaternion();
		velError = new Vector3();
		angVelError = new Vector3();
	}
	
	public void reduceError() {
		// compute the factor
		float linFactor = 0.75f;	// set to lowest
		float angFactor = 0.75f;	// same
		
		float posErrorMag = posError.lengthSquared();
		Quaternion rotDiff = new Quaternion();
		Quaternion identRot = new Quaternion();
		Quaternion.sub(identRot, rotError, rotDiff);
		float rotErrorMag = Quaternion.dot(rotDiff, rotDiff);
		
		// compute each factor separately?
		if (posErrorMag < 2.f)
			linFactor = 0.95f;
		else if (posErrorMag < 100.f)
			linFactor = 0.855f;
		else if (posErrorMag < 900.f)
			linFactor = 0.795f;
		
		if (rotErrorMag < 0.1f)
			angFactor = 0.9f;
		else if (rotErrorMag < 0.4f)
			angFactor = 0.85f;
		else if (rotErrorMag < 0.8f)
			angFactor = 0.75f;
		
		// reduce them
		posError.scale(linFactor);
		
		// for rotation, slerp them towards identity
		Quaternion.slerp(rotError, identRot, 1.f-angFactor, rotError);
		
		// for the rest, do nothing I guess
		velError.scale(linFactor);
		angVelError.scale(angFactor);
	}
	
	public void applyToBody(RigidBody b) {
		Vector3 newPos = new Vector3();
		Vector3.add(posError, b.getPos(), newPos);
		b.setPos(newPos);
		
		Quaternion newRot = new Quaternion();
		Quaternion.mul(rotError, b.getRot(), newRot);
		b.setRot(newRot);
		
		Vector3 newVel = new Vector3();
		Vector3.add(velError, b.getVel(), newVel);
		b.setVel(newVel);
		
		Vector3 newAngVel = new Vector3();
		Vector3.add(angVelError, b.getAngVel(), newAngVel);
		b.setAngVel(newAngVel);
		
		// compute impulse instead
		
	}
	
	public boolean isSignificant() {
		Quaternion rotOffs = new Quaternion();
		Quaternion.sub(rotOffs, rotError, rotOffs);
		return posError.lengthSquared() >= ERROR_TOLERANCE
				|| Quaternion.dot(rotOffs, rotOffs) >= ERROR_TOLERANCE
				/*|| velError.lengthSquared() >= ERROR_TOLERANCE
				|| angVelError.lengthSquared() >= ERROR_TOLERANCE*/;
	}
	
	public void computeOffset(BodyState sA, BodyState sB) {
		bodyId = sB.id;
		// compute position error
		Vector3.sub(sB.pos, sA.pos, posError);
		
		// compute rotation error
		Quaternion tmp = sA.rot.conjugated();
		Quaternion.mul(sB.rot, tmp , rotError);
		
		// all else, snap directly (TODO?)
		Vector3.sub(sB.vel, sA.vel, velError);
		Vector3.sub(sB.angVel, sA.angVel, angVelError);
	}
	
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		// generate message
		sb.append("body: " + bodyId + "\n\t");
		
		if (posError.lengthSquared() >= ERROR_TOLERANCE)
			sb.append("POS=ERROR ");
		else
			sb.append("POS=OK ");
		sb.append(String.format("(%.2f %.2f %.2f)\n\t", posError.x, posError.y, posError.z));
		
		Quaternion rotOffset = new Quaternion();
		Quaternion.sub(rotOffset, rotError, rotOffset);
		
		if (Quaternion.dot(rotOffset, rotOffset) >= ERROR_TOLERANCE)
			sb.append("ROT=ERROR ");
		else
			sb.append("ROT=OK ");
		sb.append(String.format("(%.2f %.2f %.2f %.2f)\n\t", rotError.x, rotError.y, rotError.z, rotError.w));
		
		/*if (velError.lengthSquared() >= ERROR_TOLERANCE)
			sb.append("VEL=ERROR ");
		else
			sb.append("VEL=OK ");
		sb.append(String.format("(%.2f %.2f %.2f)\n\t", velError.x, velError.y, velError.z));
		
		if (angVelError.lengthSquared() >= ERROR_TOLERANCE)
			sb.append("ANGVEL=ERROR ");
		else
			sb.append("ANGVEL=OK ");
		sb.append(String.format("(%.2f %.2f %.2f)\n\t", angVelError.x, angVelError.y, angVelError.z));
		*/
		return sb.toString();
	}
}
